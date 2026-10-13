package com.catfeed.stream.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.catfeed.stream.R
import com.catfeed.stream.data.api.CataasApiClient
import com.catfeed.stream.data.db.CatDatabase
import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.data.model.FilterMode
import com.catfeed.stream.data.repository.CatFeedRepository
import com.catfeed.stream.databinding.ActivityMainBinding
import com.catfeed.stream.ui.feed.CatFeedAdapter
import com.catfeed.stream.util.AppPreferences
import com.catfeed.stream.util.CatBufferManager
import com.catfeed.stream.util.CatMediaExporter
import com.catfeed.stream.util.CatShareHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var feedAdapter: CatFeedAdapter
    private lateinit var preferences: AppPreferences
    private lateinit var bufferManager: CatBufferManager

    private var hintBounceAnimator: ObjectAnimator? = null
    private var pendingDownloadCat: CatItem? = null

    private val viewModel: CatFeedViewModel by viewModels {
        val database = CatDatabase.getInstance(applicationContext)
        val apiClient = CataasApiClient()
        val repository = CatFeedRepository(apiClient, database.likedCatDao())
        CatFeedViewModel.Factory(repository)
    }

    // Runtime permission launcher for Android 9 and below gallery write
    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingDownloadCat?.let { downloadCat(it) }
        } else {
            Toast.makeText(this, "Permission needed to save to gallery", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadCat = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        val database = CatDatabase.getInstance(applicationContext)
        val apiClient = CataasApiClient()
        bufferManager = CatBufferManager(
            context = applicationContext,
            apiClient = apiClient,
            bufferedCatDao = database.bufferedCatDao()
        )

        setupRecyclerView()
        setupFilterChips()
        setupSwipeRefresh()
        setupFirstTimeHint()
        observeViewModel()

        // Kick off background buffer fill
        lifecycleScope.launch {
            bufferManager.replenishBuffer()
        }
    }

    private fun setupRecyclerView() {
        feedAdapter = CatFeedAdapter(
            onLikeClicked = { cat -> viewModel.toggleLike(cat) },
            onShareClicked = { cat -> lifecycleScope.launch { shareCat(cat) } },
            onDownloadClicked = { cat -> handleDownloadRequest(cat) }
        )

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.feedRecyclerView.layoutManager = layoutManager
        binding.feedRecyclerView.adapter = feedAdapter

        // Buttery TikTok/Shorts style vertical card snapping
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.feedRecyclerView)

        binding.feedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                dismissSwipeHint()
                val totalItemCount = layoutManager.itemCount
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                if (totalItemCount > 0 && lastVisiblePosition >= totalItemCount - 3) {
                    viewModel.loadMore()
                }
            }
        })

        binding.btnRefreshFeed.setOnClickListener {
            viewModel.refresh()
            binding.feedRecyclerView.smoothScrollToPosition(0)
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedMode = when (checkedIds.first()) {
                R.id.chipAll -> FilterMode.ALL
                R.id.chipPhotos -> FilterMode.PHOTOS
                R.id.chipGifs -> FilterMode.GIFS
                R.id.chipLiked -> FilterMode.LIKED
                else -> FilterMode.ALL
            }
            viewModel.setFilter(selectedMode)
            binding.feedRecyclerView.scrollToPosition(0)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.secondary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface)
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupFirstTimeHint() {
        if (!preferences.hasSeenSwipeHint) {
            binding.swipeHintOverlay.visibility = View.VISIBLE
            hintBounceAnimator = ObjectAnimator.ofFloat(
                binding.swipeArrowIcon,
                View.TRANSLATION_Y,
                0f,
                -16f,
                0f
            ).apply {
                duration = 850
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
            }
        } else {
            binding.swipeHintOverlay.visibility = View.GONE
        }
    }

    private fun dismissSwipeHint() {
        if (binding.swipeHintOverlay.visibility == View.VISIBLE) {
            preferences.hasSeenSwipeHint = true
            hintBounceAnimator?.cancel()
            hintBounceAnimator = null
            binding.swipeHintOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { binding.swipeHintOverlay.visibility = View.GONE }
                .start()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.feedItems.collect { items ->
                        feedAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefreshLayout.isRefreshing = refreshing
                    }
                }
                launch {
                    viewModel.isLoadingMore.collect { loadingMore ->
                        binding.bottomProgressBar.visibility =
                            if (loadingMore) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isEmpty.collect { empty ->
                        binding.emptyStateLayout.visibility =
                            if (empty) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    private fun handleDownloadRequest(cat: CatItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingDownloadCat = cat
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        downloadCat(cat)
    }

    private fun downloadCat(cat: CatItem) {
        lifecycleScope.launch {
            val saved = CatMediaExporter.saveToGallery(
                context = this@MainActivity,
                cat = cat,
                bufferManager = bufferManager
            )
            Toast.makeText(
                this@MainActivity,
                if (saved) "Saved to your Gallery!" else "Oops, could not save. Try again!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    private suspend fun shareCat(cat: CatItem) {
        CatShareHelper.shareImage(
            context = this,
            cat = cat,
            bufferManager = bufferManager
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        hintBounceAnimator?.cancel()
        hintBounceAnimator = null
    }
}
