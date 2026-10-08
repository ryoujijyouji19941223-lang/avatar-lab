package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.atan2
import kotlin.math.max

data class FacePose(
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val jaw: Float,
    val blinkLeft: Float,
    val blinkRight: Float
)

class FaceTracker(
    context: Context,
    private val onPose: (FacePose) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {

    private val landmarker: FaceLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .setResultListener(this::onResult)
            .setErrorListener { error -> onError(error.message ?: error.toString()) }
            .build()

        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy) {
        val timestamp = SystemClock.uptimeMillis()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } finally {
            imageProxy.close()
        }

        val transform = Matrix().apply {
            postRotate(rotation.toFloat())
            postScale(-1f, 1f, width / 2f, height / 2f)
        }

        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, transform, true
        )
        if (rotated !== bitmap) bitmap.recycle()

        val mpImage = BitmapImageBuilder(rotated).build()
        landmarker.detectAsync(mpImage, timestamp)
    }

    private fun onResult(
        result: FaceLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: MPImage
    ) {
        val face = result.faceLandmarks().firstOrNull() ?: return

        fun x(i: Int) = face[i].x()
        fun y(i: Int) = face[i].y()

        val noseX = x(1)
        val noseY = y(1)
        val chinY = y(152)
        val leftFaceX = x(234)
        val rightFaceX = x(454)
        val leftEyeX = x(33)
        val leftEyeY = y(33)
        val rightEyeX = x(263)
        val rightEyeY = y(263)

        val faceWidth = max(0.001f, kotlin.math.abs(rightFaceX - leftFaceX))
        val faceMidX = (leftFaceX + rightFaceX) * 0.5f
        val eyeMidY = (leftEyeY + rightEyeY) * 0.5f
        val eyeToChin = max(0.001f, chinY - eyeMidY)

        val yaw = ((faceMidX - noseX) / faceWidth * 4.8f).coerceIn(-1f, 1f)
        val noseRatio = (noseY - eyeMidY) / eyeToChin
        val pitch = ((noseRatio - 0.43f) * 4.5f).coerceIn(-1f, 1f)
        val roll = (
            atan2(
                (rightEyeY - leftEyeY).toDouble(),
                (rightEyeX - leftEyeX).toDouble()
            ).toFloat() * 1.75f
        ).coerceIn(-0.8f, 0.8f)

        val blend = result.faceBlendshapes()
            .orElse(emptyList())
            .firstOrNull()
            .orEmpty()

        fun score(name: String): Float =
            blend.firstOrNull { it.categoryName() == name }?.score() ?: 0f

        val jaw = (score("jawOpen") * 1.35f).coerceIn(0f, 1f)
        val blinkLeft = (score("eyeBlinkLeft") * 1.25f).coerceIn(0f, 1f)
        val blinkRight = (score("eyeBlinkRight") * 1.25f).coerceIn(0f, 1f)

        onPose(FacePose(yaw, pitch, roll, jaw, blinkLeft, blinkRight))
    }

    override fun close() {
        landmarker.close()
    }
}
