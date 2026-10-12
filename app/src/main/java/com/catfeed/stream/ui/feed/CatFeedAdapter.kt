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
import com.catfeed.stream.R
import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.databinding.ItemCatFeedBinding

class CatFeedAdapter(
    private val onLikeClicked: (CatItem) -> Unit,
    private val onShareClicked: (CatItem) -> Unit,
    private val onCopyClicked: (CatItem) -> Unit
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
        holder.bind(item)

        // Pre-fetch the next item into Coil image cache for instant, zero-delay rendering
        val nextPos = position + 1
        if (nextPos < itemCount) {
            preloadImage(holder.itemView.context, getItem(nextPos).url)
        }
    }

    private fun preloadImage(context: Context, url: String) {
        if (url.isBlank()) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
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
                        triggerDoubleTapHeartAnimation()
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

            binding.btnCopy.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCopyClicked(getItem(position))
                }
            }
        }

        fun bind(item: CatItem) {
            val context = binding.root.context

            // Badge text & color
            if (item.isGif) {
                binding.mediaTypeBadge.text = context.getString(R.string.gif_badge)
                binding.mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_gif)
            } else {
                binding.mediaTypeBadge.text = context.getString(R.string.photo_badge)
                binding.mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_photo)
            }

            // Tags
            val formattedTags = if (item.tags.isNotEmpty()) {
                item.tags.joinToString(" ") { "#${it.trim()}" }
            } else {
                "#cat #purr"
            }
            binding.catTagsText.text = formattedTags
            binding.catIdText.text = "ID: ${item.id.take(12)}"

            // Like state
            updateLikeUi(item.isLiked)

            // Image loading with progress indicator
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.catImageView.load(item.url) {
                crossfade(true)
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
                binding.btnLike.setImageResource(R.drawable.ic_heart_filled)
                binding.btnLike.setColorFilter(ContextCompat.getColor(context, R.color.like_active))
                binding.likeCountText.text = "1"
                binding.likeCountText.setTextColor(ContextCompat.getColor(context, R.color.like_active))
            } else {
                binding.btnLike.setImageResource(R.drawable.ic_heart_outline)
                binding.btnLike.setColorFilter(ContextCompat.getColor(context, R.color.like_inactive))
                binding.likeCountText.text = "0"
                binding.likeCountText.setTextColor(ContextCompat.getColor(context, R.color.onBackground))
            }
        }

        private fun triggerDoubleTapHeartAnimation() {
            val heart = binding.doubleTapHeart
            heart.visibility = View.VISIBLE
            heart.alpha = 1f
            heart.scaleX = 0.3f
            heart.scaleY = 0.3f

            val scaleXAnim = ObjectAnimator.ofFloat(heart, View.SCALE_X, 0.3f, 1.25f, 1f)
            val scaleYAnim = ObjectAnimator.ofFloat(heart, View.SCALE_Y, 0.3f, 1.25f, 1f)
            val alphaFade = ObjectAnimator.ofFloat(heart, View.ALPHA, 1f, 0f).apply {
                startDelay = 600
                duration = 300
            }

            val animSet = AnimatorSet().apply {
                interpolator = OvershootInterpolator(1.4f)
                duration = 450
                playTogether(scaleXAnim, scaleYAnim)
            }

            animSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    alphaFade.start()
                }
            })

            alphaFade.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heart.visibility = View.GONE
                }
            })

            animSet.start()
        }
    }
}

class CatDiffCallback : DiffUtil.ItemCallback<CatItem>() {
    override fun areItemsTheSame(oldItem: CatItem, newItem: CatItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CatItem, newItem: CatItem): Boolean {
        return oldItem == newItem
    }
}
