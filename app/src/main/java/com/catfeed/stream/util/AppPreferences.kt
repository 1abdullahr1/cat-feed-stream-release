package com.catfeed.stream.util

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("purr_feed_prefs", Context.MODE_PRIVATE)

    var hasSeenSwipeHint: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_SWIPE_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_SWIPE_HINT, value).apply()

    companion object {
        private const val KEY_HAS_SEEN_SWIPE_HINT = "key_has_seen_swipe_hint"
    }
}
