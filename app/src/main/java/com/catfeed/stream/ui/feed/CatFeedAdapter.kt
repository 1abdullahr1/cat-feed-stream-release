package com.catfeed.stream.ui.feed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.catfeed.stream.R
import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.databinding.ItemCatFeedBinding
import kotlin.math.min

private val KID_CAT_NAMES = listOf(
    "Fluffy Kitten",
    "Silly Whiskers",
    "Sleepy Paws",
    "Happy Kitty",
    "Bouncy Furball",
    "Tiny Meow",
    "Purring Friend",
    "Cuddly Cat",
    "Snuggly Furball",
    "Playful Paws"
)

class CatFeedAdapter(
    private val onLikeClicked: (CatItem) -> Unit,
    private val onShareClicked: (CatItem) -> Unit,
    private val onDownloadClicked: (CatItem) -> Unit
) : ListAdapter<CatItem, CatFeedAdapter.CatViewHolder>(CatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatViewHolder {
        val binding = ItemCatFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CatViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)

        // 5-item predictive lookahead prefetching for zero-latency swipe
        val context = holder.itemView.context
        val endPos = min(position + 5, itemCount - 1)
        for (nextPos in (position + 1)..endPos) {
            preloadImage(context, getItem(nextPos).url)
        }
    }

    private fun preloadImage(context: Context, url: String) {
        if (url.isBlank()) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .precision(Precision.INEXACT)
            .build()
        context.imageLoader.enqueue(request)
    }

    inner class CatViewHolder(private val binding: ItemCatFeedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val gestureDetector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        triggerPawAnimation()
                        if (!item.isLiked) {
                            onLikeClicked(item)
                        }
                    }
                    return true
                }
            }
        )

        init {
            binding.cardContainer.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }

            binding.btnLike.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLikeClicked(getItem(position))
                }
            }

            binding.btnShare.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onShareClicked(getItem(position))
                }
            }

            binding.btnDownload.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDownloadClicked(getItem(position))
                }
            }
        }

        fun bind(item: CatItem, position: Int) {
            val context = binding.root.context

            // Badge
            if (item.isGif) {
                binding.mediaTypeBadge.text = context.getString(R.string.gif_badge)
                binding.mediaTypeBadge.setBackgroundResource(R.drawable.bg_kid_badge_gif)
            } else {
                binding.mediaTypeBadge.text = context.getString(R.string.photo_badge)
                binding.mediaTypeBadge.setBackgroundResource(R.drawable.bg_kid_badge_photo)
            }

            // Kid-friendly name based on position (deterministic, changes per scroll)
            binding.catTagsText.text = KID_CAT_NAMES[position % KID_CAT_NAMES.size]

            // Tags as subtitle
            val formattedTags = if (item.tags.isNotEmpty()) {
                item.tags.joinToString(" ") { "#${it.trim()}" }
            } else {
                "#cat #purr"
            }
            binding.catIdText.text = formattedTags

            // Like state
            updateLikeUi(item.isLiked)

            // Ambient blurred backdrop
            binding.ambientBackdropView.load(item.url) {
                size(120, 120)
                scale(Scale.FILL)
                precision(Precision.INEXACT)
                crossfade(150)
            }

            // Main cat image / GIF
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.catImageView.load(item.url) {
                allowHardware(true)
                crossfade(200)
                listener(
                    onSuccess = { _, _ ->
                        binding.loadingIndicator.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        binding.loadingIndicator.visibility = View.GONE
                    }
                )
            }
        }

        private fun updateLikeUi(isLiked: Boolean) {
            val context = binding.root.context
            if (isLiked) {
                binding.btnLike.setImageResource(R.drawable.ic_cat_paw)
                binding.btnLike.setColorFilter(
                    ContextCompat.getColor(context, R.color.primary)
                )
            } else {
                binding.btnLike.setImageResource(R.drawable.ic_cat_paw_outline)
                binding.btnLike.clearColorFilter()
            }
        }

        private fun triggerPawAnimation() {
            val paw = binding.doubleTapHeart
            paw.visibility = View.VISIBLE
            paw.alpha = 1f
            paw.scaleX = 0.3f
            paw.scaleY = 0.3f

            val scaleX = ObjectAnimator.ofFloat(paw, View.SCALE_X, 0.3f, 1.3f, 1f)
            val scaleY = ObjectAnimator.ofFloat(paw, View.SCALE_Y, 0.3f, 1.3f, 1f)
            val fade = ObjectAnimator.ofFloat(paw, View.ALPHA, 1f, 0f).apply {
                startDelay = 500
                duration = 350
            }

            val anim = AnimatorSet().apply {
                interpolator = OvershootInterpolator(1.5f)
                duration = 420
                playTogether(scaleX, scaleY)
            }

            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { fade.start() }
            })
            fade.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { paw.visibility = View.GONE }
            })

            anim.start()
        }
    }
}

class CatDiffCallback : DiffUtil.ItemCallback<CatItem>() {
    override fun areItemsTheSame(oldItem: CatItem, newItem: CatItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: CatItem, newItem: CatItem) = oldItem == newItem
}
