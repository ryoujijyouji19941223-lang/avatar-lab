package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.*

/** Column-major canonical face rotation supplied by MediaPipe, not landmark ratios. */
object FaceRotation {
    fun radians(m: FloatArray): FloatArray {
        require(m.size == 16)
        val scale = sqrt(m[0]*m[0] + m[1]*m[1] + m[2]*m[2]).coerceAtLeast(0.0001f)
        return floatArrayOf(asin((-m[2]/scale).coerceIn(-1f, 1f)), atan2(m[6], m[10]), atan2(m[1], m[0]))
    }
}
class FaceTracker(context: Context, private val onPose: (FacePose?) -> Unit) : AutoCloseable {
    private val landmarker = FaceLandmarker.createFromOptions(context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            // Serial video inference bounds memory: every MPImage is closed before the next frame.
            .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f).setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f).setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true).build())
    private var closed = false
    private var lastTimestamp = -1L
    private var centre: FloatArray? = null
    private val leftBlink = EyeBlinkInput()
    private val rightBlink = EyeBlinkInput()
    @Volatile private var recenterRequested = false
    fun recenter() { recenterRequested = true }

    @Synchronized fun detect(proxy: ImageProxy) {
        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            val timestamp = SystemClock.uptimeMillis()
            if (closed || timestamp - lastTimestamp < 40L) return
            lastTimestamp = timestamp
            val rotation = proxy.imageInfo.rotationDegrees
            // Copy full padded rows, then crop padding. Do not assume rowStride == width*4.
            val plane = proxy.planes[0]
            require(plane.pixelStride == 4)
            val paddedWidth = plane.rowStride / plane.pixelStride
            bitmap = Bitmap.createBitmap(paddedWidth, proxy.height, Bitmap.Config.ARGB_8888)
            val buffer = plane.buffer.duplicate().apply { rewind() }
            // Some devices omit trailing padding on the last row.
            val packed = java.nio.ByteBuffer.allocate(plane.rowStride * proxy.height)
            packed.put(buffer); packed.rewind(); bitmap.copyPixelsFromBuffer(packed)
            val transform = Matrix().apply { postRotate(rotation.toFloat()) }
            // No horizontal mirroring: eyeBlinkLeft/Right remain anatomical sides.
            rotated = Bitmap.createBitmap(bitmap, 0, 0, proxy.width, proxy.height, transform, true)
            val frameBitmap = requireNotNull(rotated)
            BitmapImageBuilder(frameBitmap).build().use { input ->
                val result = landmarker.detectForVideo(input, timestamp)
                val m = result.facialTransformationMatrixes().orElse(emptyList()).firstOrNull()
                if (m == null || result.faceLandmarks().isEmpty()) { onPose(null); return }
                val angles = FaceRotation.radians(m)
                // Use eye geometry in width units after portrait rotation. 3D
                // distances reject foreshortening from head yaw/pitch/roll.
                val rotatedAspect = frameBitmap.height.toFloat() / frameBitmap.width
                val points = result.faceLandmarks().first().map {
                    EyePoint(it.x(), it.y()*rotatedAspect, it.z())
                }
                val leftAperture = EyeInputGeometry.aperture(points, EyeInputGeometry.LEFT)
                val rightAperture = EyeInputGeometry.aperture(points, EyeInputGeometry.RIGHT)
                // At an extreme angle the hidden eye cannot be reliably inferred.
                // Return open rather than retain a false partial blink.
                val reliable = abs(angles[0]) < 0.95f && abs(angles[1]) < 0.75f
                if (centre == null || recenterRequested) {
                    centre = angles.copyOf(); recenterRequested = false
                    leftBlink.reset(leftAperture); rightBlink.reset(rightAperture)
                }
                val c = requireNotNull(centre)
                val calibrate = abs(angles[0]-c[0]) < 0.18f && abs(angles[1]-c[1]) < 0.18f
                val blends = result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty()
                fun score(name: String) = blends.firstOrNull { it.categoryName() == name }?.score() ?: 0f
                onPose(FacePose(
                    ((angles[0]-c[0]) / 0.60f).coerceIn(-1f,1f),
                    ((angles[1]-c[1]) / 0.45f).coerceIn(-1f,1f),
                    ((angles[2]-c[2]) / 0.40f).coerceIn(-1f,1f),
                    score("jawOpen"),
                    leftBlink.update(score("eyeBlinkLeft"), leftAperture, reliable, calibrate),
                    rightBlink.update(score("eyeBlinkRight"), rightAperture, reliable, calibrate)))
            }
        } finally {
            proxy.close()
            if (rotated !== bitmap) rotated?.recycle()
            bitmap?.recycle()
        }
    }
    @Synchronized override fun close() { if (!closed) { closed = true; landmarker.close() } }
}
