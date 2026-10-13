package com.catfeed.stream.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Lightweight playful sound effects for kids (meow on like, pop on download, chime on share).
 *
 * Sounds are resolved dynamically from res/raw by name so the app compiles and runs even
 * before audio files are added. Drop any of the following WAV/OGG files into res/raw/ to
 * activate them (no code change needed):
 *   - meow.wav   (played when a kitty is liked)
 *   - pop.wav    (played when a kitty is saved)
 *   - chime.wav  (played when a kitty is shared)
 */
class SoundFxPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val meowId = loadSound(context, "meow")
    private val popId = loadSound(context, "pop")
    private val chimeId = loadSound(context, "chime")

    private fun loadSound(context: Context, name: String): Int {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (resId != 0) soundPool.load(context, resId, 1) else 0
    }

    private fun play(id: Int) {
        if (id != 0) {
            soundPool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playLike() = play(meowId)

    fun playDownload() = play(popId)

    fun playShare() = play(chimeId)

    fun release() {
        soundPool.release()
    }
}
