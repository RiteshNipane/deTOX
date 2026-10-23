package com.detox.ml

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Screenshot -> square bitmap of [inputSize]. Needs android:canTakeScreenshot="true" and API 30+.
 *
 * The squash-resize here is the same one the collected training frames go through, so the
 * model sees at inference exactly what it saw in training. Do not change one without the other.
 * The system rate-limits takeScreenshot(); callers sample every ~1.5 s, well above the floor.
 */
class FrameGrabber(private val inputSize: Int) {
    private val executor = Executors.newSingleThreadExecutor()

    /** Returns null on failure (rate limit, secure window, etc.). Caller owns the bitmap. */
    suspend fun grab(service: AccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val out = try {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { hw ->
                            val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            soft?.let {
                                val scaled = Bitmap.createScaledBitmap(it, inputSize, inputSize, true)
                                if (scaled !== it) it.recycle()
                                scaled
                            }
                        }
                    } catch (_: Throwable) {
                        null
                    } finally {
                        buffer.close()
                    }
                    if (cont.isActive) cont.resume(out)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            },
            )
        } catch (_: Exception) {
            // e.g. screenshot capability not granted yet; degrade to "no frame".
            if (cont.isActive) cont.resume(null)
        }
    }
}
