package com.catfeed.stream.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var feedAdapter: CatFeedAdapter
    private lateinit var preferences: AppPreferences

    private var hintBounceAnimator: ObjectAnimator? = null

    private val viewModel: CatFeedViewModel by viewModels {
        val database = CatDatabase.getInstance(applicationContext)
        val apiClient = CataasApiClient()
        val repository = CatFeedRepository(apiClient, database.likedCatDao())
        CatFeedViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        setupRecyclerView()
        setupFilterChips()
        setupSwipeRefresh()
        setupFirstTimeHint()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        feedAdapter = CatFeedAdapter(
            onLikeClicked = { cat -> viewModel.toggleLike(cat) },
            onShareClicked = { cat -> shareCat(cat) },
            onCopyClicked = { cat -> copyCatUrl(cat) }
        )

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.feedRecyclerView.layoutManager = layoutManager
        binding.feedRecyclerView.adapter = feedAdapter

        // PagerSnapHelper ensures buttery-smooth TikTok/Shorts style vertical card snapping
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.feedRecyclerView)

        binding.feedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Dismiss first-time hint on first user scroll
                dismissSwipeHint()

                // Infinite paging check
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
                -14f,
                0f
            ).apply {
                duration = 900
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
                .withEndAction {
                    binding.swipeHintOverlay.visibility = View.GONE
                }
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
                        binding.bottomProgressBar.visibility = if (loadingMore) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isEmpty.collect { empty ->
                        binding.emptyStateLayout.visibility = if (empty) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun shareCat(cat: CatItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_cat_text, cat.url))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_action)))
    }

    private fun copyCatUrl(cat: CatItem) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PurrFeed Cat", cat.url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        hintBounceAnimator?.cancel()
        hintBounceAnimator = null
    }
}
