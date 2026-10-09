package com.totomarujapan.crowavatar

import kotlin.math.*

/** Normalized x and z share image-width units; y must first be scaled by h/w. */
data class EyePoint(val x: Float, val y: Float, val z: Float) {
    operator fun minus(other: EyePoint) = EyePoint(x-other.x, y-other.y, z-other.z)
    fun dot(other: EyePoint) = x*other.x + y*other.y + z*other.z
    fun length() = sqrt(dot(this))
}

object EyeInputGeometry {
    // MediaPipe anatomical left/right eyelid topology; not screen-side labels.
    val LEFT = intArrayOf(263,362,386,374,385,380,387,373)
    val RIGHT = intArrayOf(33,133,159,145,158,153,160,144)

    /** 3D perpendicular lid gap / 3D corner width. Translation, scale and rigid
     * head yaw/pitch/roll cancel, unlike screen-space eye height/width.
     * This remains a model estimate, not a camera measurement of actual 3D depth.
     */
    fun aperture(points: List<EyePoint>, indices: IntArray): Float? {
        if (points.size <= indices.maxOrNull()!!) return null
        val horizontal = points[indices[1]] - points[indices[0]]
        val widthSquared = horizontal.dot(horizontal)
        if (!widthSquared.isFinite() || widthSquared < 1e-8f) return null
        var gap = 0f
        for (i in 2 until indices.size step 2) {
            val vertical = points[indices[i]] - points[indices[i+1]]
            val projection = vertical.dot(horizontal)
            gap += sqrt(max(0f, vertical.dot(vertical) - projection*projection/widthSquared))
        }
        val ratio = gap / 3f / sqrt(widthSquared)
        return ratio.takeIf { it.isFinite() && it in 0f..0.8f }
    }
}

/** Separate state for each eye. Blendshape remains the expression input;
 * geometry corroborates it and rejects a pose-related false blink.
 */
class EyeBlinkInput {
    private var openReference: Float? = null
    fun reset(openAperture: Float? = null) {
        // Explicit open-eye calibration at start/recentre; reject fully closed
        // or malformed geometry rather than teaching a held wink as open.
        openReference = openAperture?.takeIf { it.isFinite() && it in 0.12f..0.8f }
    }

    fun update(rawBlink: Float, aperture: Float?, poseReliable: Boolean = true,
        allowCalibration: Boolean = true): Float {
        if (!poseReliable || aperture == null || !aperture.isFinite()) return 0f
        val raw = rawBlink.coerceIn(0f,1f)
        val previous = openReference
        // Learn from confidently open frames only. A blink/held wink can never
        // slowly become the new open-eye reference, even during recalibration.
        if (allowCalibration && aperture >= 0.12f && raw < 0.20f) {
            if (previous == null) openReference = aperture
            else if (aperture/previous in 0.90f..1.10f)
                openReference = previous * 0.98f + aperture * 0.02f
        }
        val reference = openReference ?: 0.26f
        val openness = aperture / reference
        // Tolerate ~16% landmark noise without visible half-closing. Gradually
        // admit real partial closure; preserve a full blink in either eye.
        val evidence = ((0.84f-openness)/0.30f).coerceIn(0f,1f)
        val supported = if (openness < 0.35f && raw >= 0.55f) max(raw,0.72f) else raw
        return supported * evidence
    }
}
