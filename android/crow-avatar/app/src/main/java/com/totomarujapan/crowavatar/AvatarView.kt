package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

data class FacePose(val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
    val jaw: Float = 0f, val blinkLeft: Float = 0f, val blinkRight: Float = 0f)
object RigMotion {
    fun closure(score: Float): Float {
        val x = ((score - 0.08f) / 0.64f).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
    fun opening(score: Float) = ((score - 0.05f) / 0.70f).coerceIn(0f, 1f)
    fun smooth(old: Float, target: Float, dt: Float, tau: Float): Float =
        old + (target - old) * (1f - exp(-dt / tau))
}
class CrowRenderer(private val layers: AvatarLayers) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(48, 29, 53) }
    private val mouthFloorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(164, 108, 125) }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(33, 29, 39); style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
    }
    private val camera = Camera()
    private val matrix = Matrix()
    private val restInverse = beakProjection(0f).apply { invert(this) }
    /** Local 480px space; the composed moving head is not cropped again. */
    fun draw(canvas: Canvas, pose: FacePose, seconds: Double) {
        canvas.save()
        canvas.scale(1f, 1f + sin(seconds * 1.45).toFloat() * 0.003f, 240f, 480f)
        canvas.drawBitmap(layers.body, 0f, 0f, paint); canvas.restore()
        canvas.save()
        camera.save()
        camera.rotateY(pose.yaw.coerceIn(-1f, 1f) * 17f) // 13 -> 17 degrees (1.31x)
        camera.rotateX(-pose.pitch.coerceIn(-1f, 1f) * 10.1f) // 8.4 -> 10.1 degrees (1.20x)
        camera.getMatrix(matrix); camera.restore()
        matrix.preTranslate(-251f, -317f)
        matrix.postTranslate(251f + pose.yaw * 5f, 317f + pose.pitch * 3f)
        canvas.concat(matrix)
        canvas.rotate(pose.roll.coerceIn(-1f, 1f) * 8.6f, 251f, 317f)
        canvas.drawBitmap(layers.head, 0f, 0f, paint)
        drawEye(canvas, layers.leftEye, RigMotion.closure(pose.blinkLeft))
        drawEye(canvas, layers.rightEye, RigMotion.closure(pose.blinkRight))
        drawMouth(canvas, RigMotion.opening(pose.jaw)); canvas.restore()
    }
    private fun drawEye(canvas: Canvas, eye: EyeLayers, closure: Float) {
        val contour = eye.contour
        // Both opaque lids are the backing; only the remaining aperture reveals the eyeball.
        canvas.drawBitmap(eye.upperLid, 0f, 0f, paint); canvas.drawBitmap(eye.lowerLid, 0f, 0f, paint)
        val upper = contour.curve(contour.top, closure)
        val lower = contour.curve(contour.bottom, closure)
        if (closure < 0.999f) {
            canvas.save(); canvas.clipPath(contour.area(upper, lower))
            canvas.drawBitmap(eye.eyeball, 0f, 0f, paint); canvas.restore()
        }
        if (closure > 0.02f) {
            lipPaint.alpha = (255 * closure).toInt()
            canvas.drawPath(contour.line(upper), lipPaint); lipPaint.alpha = 255
        }
    }
    /** Actual X-axis hinge rotation, normalized to an identity transform at rest.
     * No asymmetric translation or Z rotation: mouth centre stays at x=251.
     */
    internal fun beakProjection(opening: Float): Matrix {
        val c = Camera(); val m = Matrix()
        c.save(); c.rotateX(42f - opening * 30f); c.getMatrix(m); c.restore()
        m.preTranslate(-RigGeometry.HINGE_X, -RigGeometry.HINGE_Y)
        m.postTranslate(RigGeometry.HINGE_X, RigGeometry.HINGE_Y + opening * 2f)
        return m
    }
    internal fun beakTransform(opening: Float) = Matrix().apply { setConcat(beakProjection(opening), restInverse) }
    private fun drawMouth(canvas: Canvas, opening: Float) {
        val beak = beakTransform(opening)
        val tip = floatArrayOf(251f, 315f); beak.mapPoints(tip)
        val inside = Path().apply {
            moveTo(199f, 242f); quadTo(251f, 272f, 302f, 242f)
            quadTo(286f, tip[1] - 16f, 251f, tip[1] + 2f)
            quadTo(221f, tip[1] - 15f, 199f, 242f); close()
        }
        canvas.drawPath(inside, mouthPaint) // deepest mouth layer
        // Keep all new shading below the fixed upper beak tip. Nothing above
        // y=292 changes with jaw, including the source's antialiased upper rim.
        if (opening > 0.02f) {
            canvas.save(); canvas.clipPath(inside); canvas.clipRect(197f, 292f, 305f, tip[1]+3f)
            mouthFloorPaint.shader = LinearGradient(251f,292f,251f,tip[1]+3f,
                Color.rgb(48,29,53),Color.rgb(113,72,91),Shader.TileMode.CLAMP)
            canvas.drawPath(inside,mouthFloorPaint)
            val tongue = Path().apply {
                moveTo(237f,tip[1]-5f)
                quadTo(251f,tip[1]-14f,265f,tip[1]-5f)
                quadTo(251f,tip[1]+1f,237f,tip[1]-5f); close()
            }
            canvas.drawPath(tongue,tonguePaint)
            canvas.restore()
        }
        canvas.save(); canvas.concat(beak); canvas.drawBitmap(layers.lowerBeak, 0f, 0f, paint); canvas.restore()
        canvas.drawBitmap(layers.upperBeak, 0f, 0f, paint) // fixed, frontmost; never receives jaw
    }
}
class AvatarView(context: Context) : View(context) {
    private val renderer = CrowRenderer(AvatarAssets.load(context))
    private var target = FacePose(); private var current = FacePose()
    private var tracking = false; private var lastInput = 0L; private var lastFrame = 0L
    private val startedAt = SystemClock.uptimeMillis()
    fun updateFace(pose: FacePose) { target = pose; lastInput = SystemClock.uptimeMillis() }
    fun setTracking(enabled: Boolean) { tracking = enabled; if (!enabled) target = FacePose() }
    fun preview(pose: FacePose) { tracking = false; target = pose }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.1f)
        lastFrame = now
        if (tracking && now - lastInput > 550L) target = FacePose()
        fun h(a: Float, b: Float) = RigMotion.smooth(a, b, dt, 0.070f)
        fun e(a: Float, b: Float) = RigMotion.smooth(a, b, dt, if (b > a) 0.018f else 0.045f)
        current = FacePose(h(current.yaw, target.yaw), h(current.pitch, target.pitch), h(current.roll, target.roll),
            h(current.jaw, target.jaw), e(current.blinkLeft, target.blinkLeft), e(current.blinkRight, target.blinkRight))
        val size = minOf(width * 1.02f, height * 0.77f)
        canvas.save(); canvas.translate((width - size) / 2f, height * 0.075f); canvas.scale(size / 480f, size / 480f)
        renderer.draw(canvas, current, (now - startedAt) / 1000.0); canvas.restore()
        if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) { lastFrame = 0L; postInvalidateOnAnimation() }
    }
}
