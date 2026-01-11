/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.String

class HandLandmarkerHelper(
    val context : Context,
    val lsnr    : LandmarkerListener? = null
) {
    val res = context.resources
    val alpha = res.getFloat(R.dimen.landmark_detect_confidence)
    val scale = res.getFloat(R.dimen.landmark_post_scale)
    val hands = res.getInteger(R.integer.landmark_num_hands)
    val gpu   = res.getInteger(R.integer.landmark_delegate_gpu)
    val opt   = String.format(
                    "%s %dH A=%4.2f ",
                    if (gpu == 1) "GPU" else "CPU", hands, alpha
                )

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var marker: HandLandmarker? = null

    init {
        setup()
    }

    fun clear() {
        marker?.close()
        marker = null
    }

    // Return running status of HandLandmarkerHelper
    fun isClose(): Boolean {
        return marker == null
    }

    fun normalizeColor(v: Float): Int {
        var c : Int = ((1f + v*2f) * 128f).toInt()
        if (c < 0) return Color.BLACK
        if (c > 255) return Color.WHITE
        return Color.rgb(c, c, c) /// monochrome
    }

    fun calcCtrlColors(
        result : HandLandmarkerResult
    ) : Array<Int> {
        var x : Float = 0f
        var y : Float = 0f
        var i : Int = 0

        for (landmark in result.landmarks()) {
            for (pt in landmark) {
                if (i == 8) { x += pt.x(); y += pt.y(); }
                if (i == 5) { x -= pt.x(); y -= pt.y(); }
                i++
            }
        }
        val r : Int = normalizeColor(x)
        val u : Int = normalizeColor(y)

        return arrayOf(r, u)
    }
    // Initialize the Hand landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setup() {
        Log.d(TAG,
            "setupHandLandmarker")

        // Set general hand landmarker options
        val baseOptionBuilder = BaseOptions.builder()
        baseOptionBuilder
            .setModelAssetPath(MP_HAND_LANDMARKER_TASK)
            .setDelegate(if (gpu == 1) Delegate.GPU else Delegate.CPU)

        // Check if runningMode is consistent with handLandmarkerHelperListener
        if (lsnr == null) {
            throw IllegalStateException(
                "handLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
            )
        }

        try {
            val baseOptions = baseOptionBuilder.build()

            // Create an option builder with base options and specific
            // options only use for Hand Landmarker.
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(alpha)
                    .setMinTrackingConfidence(alpha)
                    .setMinHandPresenceConfidence(alpha)
                    .setNumHands(hands)
                    .setRunningMode(RunningMode.LIVE_STREAM)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            optionsBuilder
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)

            val options = optionsBuilder.build()
            marker =
                HandLandmarker.createFromOptions(context, options)

            lsnr.onHelperCreated(opt)
        } catch (e: IllegalStateException) {
            lsnr?.onError(
                "Hand Landmarker failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "MediaPipe failed to load the task with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            lsnr?.onError(
                "Hand Landmarker failed to initialize. See error logs for details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to HandlandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -scale,
                    scale,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run hand hand landmark using MediaPipe Hand Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        marker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }
    // Return the landmark result to this HandLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        lsnr?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    // Return errors thrown during detection to this HandLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        lsnr?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "trace HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val time   : Long,
        val height : Int,
        val width  : Int,
    )

    interface LandmarkerListener {
        fun onHelperCreated(opt: String)
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
