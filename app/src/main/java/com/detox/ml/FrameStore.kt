package com.detox.ml

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Dataset collection (developer mode). Saves model-input frames to app-specific storage:
 *   Android/data/com.detox/files/frames/<real|ai>/<reelHash>__<timestamp>.png
 * Frames never leave the device unless you pull them yourself:
 *   adb pull /sdcard/Android/data/com.detox/files/frames ml-training/raw/reels
 * The "<reelHash>__" prefix lets prepare_data.py keep all frames of one reel in one split.
 */
class FrameStore(private val context: Context) {
    fun save(bitmap: Bitmap, label: String, reelHash: Int): Boolean {
        val dir = File(context.getExternalFilesDir("frames"), label).apply { mkdirs() }
        val file = File(dir, "${Integer.toHexString(reelHash)}__${System.currentTimeMillis()}.png")
        return runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
    }
}
