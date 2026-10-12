package com.catfeed.stream

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class CatFeedApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val httpCacheDirectory = File(cacheDir, "http_cache")
        val okHttpClient = OkHttpClient.Builder()
            .cache(Cache(httpCacheDirectory, 100L * 1024L * 1024L)) // 100MB HTTP disk cache
            .connectionPool(ConnectionPool(15, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // 30% of app RAM for decoded bitmaps
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("purr_image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L) // 150MB disk cache
                    .build()
            }
            .allowHardware(true) // Direct GPU memory rendering
            .crossfade(180)
            .build()
    }
}
