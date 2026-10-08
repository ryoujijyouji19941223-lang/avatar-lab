package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/**
 * Chat A/B implementation v0.4.
 *
 * The three source portraits (neutral / blink / talk) are treated as an aligned texture atlas.
 * We never swap the whole head for an expression.
 *
 * Render order inside the moving HEAD group:
 *   mouth interior (back)
 *   lower beak (movable)
 *   neutral head excluding the lower-beak zone (fixed upper beak remains)
 *   left/right eyelids (independent)
 *
 * BODY is a separate fixed group with breathing only.
 */
class AvatarView(context: Context) : View(context) {
    private val assets = AvatarAssets.load(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val camera = android.graphics.Camera()
    private val matrix = Matrix()

    private var targetYaw = 0f
    private var targetPitch = 0f
    private var targetRoll = 0f
    private var targetJaw = 0f
    private var targetBlinkLeft = 0f
    private var targetBlinkRight = 0f

    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f
    private var jaw = 0f
    private var blinkLeft = 0f
    private var blinkRight = 0f

    private val startedAt = System.nanoTime()

    fun updateFace(
        yaw: Float,
        pitch: Float,
        roll: Float,
        jaw: Float,
        blinkLeft: Float,
        blinkRight: Float
    ) {
        targetYaw = yaw.coerceIn(-1f, 1f)
        targetPitch = pitch.coerceIn(-1f, 1f)
        targetRoll = roll.coerceIn(-1f, 1f)
        targetJaw = jaw.coerceIn(0f, 1f)
        targetBlinkLeft = blinkLeft.coerceIn(0f, 1f)
        targetBlinkRight = blinkRight.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setTracking(enabled: Boolean) {
        if (!enabled) {
            targetYaw = 0f
            targetPitch = 0f
            targetRoll = 0f
            targetJaw = 0f
            targetBlinkLeft = 0f
            targetBlinkRight = 0f
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Head stays responsive; jaw and lids smooth enough to avoid FaceLandmarker jitter.
        val headK = 0.24f
        val expressionK = 0.30f
        yaw += (targetYaw - yaw) * headK
        pitch += (targetPitch - pitch) * headK
        roll += (targetRoll - roll) * headK
        jaw += (targetJaw - jaw) * expressionK
        blinkLeft += (targetBlinkLeft - blinkLeft) * expressionK
        blinkRight += (targetBlinkRight - blinkRight) * expressionK

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val drawSize = minOf(w * 1.06f, h * 0.77f)
        val left = (w - drawSize) / 2f
        val top = h * 0.08f
        val dst = RectF(left, top, left + drawSize, top + drawSize)
        val cx = dst.centerX()
        val headPivotY = dst.top + dst.height() * 0.46f

        // BODY: never follows the face. Only tiny breathing remains.
        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
        val breathe = 1f + (sin(seconds * 1.45).toFloat() * 0.0045f)
        canvas.save()
        val bodyTop = dst.top + dst.height() * 0.655f
        canvas.clipRect(0f, bodyTop, w, h)
        canvas.scale(1.0015f, breathe, cx, dst.bottom)
        canvas.drawBitmap(assets.neutral, null, dst, paint)
        canvas.restore()

        // HEAD transform. Slightly wider than v0.3, but not enough to expose the shoulders.
        canvas.save()
        matrix.reset()
        camera.save()
        camera.rotateY(yaw * 14.5f)
        camera.rotateX(-pitch * 9.5f)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-cx, -headPivotY)
        matrix.postTranslate(
            cx + yaw * w * 0.013f,
            headPivotY + pitch * h * 0.011f
        )
        canvas.concat(matrix)
        canvas.rotate(roll * 8.8f, cx, headPivotY)

        val headPath = buildHeadPath(dst)
        canvas.clipPath(headPath)

        val upperBeakPath = buildUpperBeakPath(dst)
        val jawOuterPath = buildJawOuterPath(dst)
        val lowerJawPath = Path(jawOuterPath).apply {
            // Protect the entire fixed upper beak from the movable lower-beak zone.
            op(upperBeakPath, Path.Op.DIFFERENCE)
        }
        val headWithoutJaw = Path(headPath).apply {
            op(lowerJawPath, Path.Op.DIFFERENCE)
        }

        // 1) MOUTH INTERIOR — deepest layer.
        if (jaw > 0.015f) {
            val p = smooth01((jaw - 0.015f) / 0.985f)
            val hingeY = y(dst, 0.548f)
            canvas.save()
            canvas.clipPath(buildMouthInteriorPath(dst))
            canvas.scale(1f, 0.08f + 0.92f * p, x(dst, 0.518f), hingeY)
            layerPaint.alpha = (160 + 95 * p).toInt().coerceIn(0, 255)
            canvas.drawBitmap(assets.talk, null, dst, layerPaint)
            layerPaint.alpha = 255
            canvas.restore()
        }

        // 2) LOWER BEAK — this is the only beak layer driven by jawOpen.
        // In a front view, jaw opening reads more naturally as vertical hinge expansion
        // than as an in-plane clockwise rotation.
        run {
            val p = smooth01(jaw)
            val pivotX = x(dst, 0.518f)
            val pivotY = y(dst, 0.515f)
            canvas.save()
            canvas.translate(0f, dst.height() * 0.030f * p)
            canvas.scale(
                1f + 0.018f * p,
                1f + 0.205f * p,
                pivotX,
                pivotY
            )
            canvas.clipPath(lowerJawPath)
            canvas.drawBitmap(assets.neutral, null, dst, paint)
            canvas.restore()
        }

        // 3) FIXED HEAD / FACE / UPPER BEAK.
        // The neutral image is an atlas source; the lower-jaw region is cut out here.
        canvas.save()
        canvas.clipPath(headWithoutJaw)
        canvas.drawBitmap(assets.neutral, null, dst, paint)
        canvas.restore()

        // 4) EYELIDS. Each eye is independent. The closed-eye texture is from the
        // aligned blink portrait, split into upper/lower halves and moved toward the seam.
        drawBlink(canvas, dst, isLeft = true, amount = blinkLeft)
        drawBlink(canvas, dst, isLeft = false, amount = blinkRight)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawBlink(canvas: Canvas, dst: RectF, isLeft: Boolean, amount: Float) {
        // Face Landmarker often peaks around 0.5–0.7 on a real full blink.
        // Map that range aggressively so a real closed eye actually reaches 100% closure.
        val p = smooth01(((amount - 0.07f) / 0.52f).coerceIn(0f, 1f))
        if (p <= 0.001f) return

        val eyePath = buildEyePath(dst, isLeft)
        val bounds = eyeBounds(dst, isLeft)
        val seamY = bounds.top + bounds.height() * 0.57f

        // IMPORTANT:
        // The neutral eye remains underneath, but the lid texture is OPAQUE and progressively
        // covers the *entire* eye socket. We do not draw a new black bar or a different eye.
        //
        // Upper lid: reveal the already-aligned blink texture from the top down.
        val upperBottom = bounds.top + (seamY - bounds.top) * p
        if (upperBottom > bounds.top) {
            canvas.save()
            canvas.clipPath(eyePath)
            canvas.clipRect(bounds.left, bounds.top, bounds.right, upperBottom)
            layerPaint.alpha = 255
            canvas.drawBitmap(assets.blink, null, dst, layerPaint)
            canvas.restore()
        }

        // Lower lid: reveal the aligned blink texture from the bottom up.
        val lowerTop = bounds.bottom - (bounds.bottom - seamY) * p
        if (lowerTop < bounds.bottom) {
            canvas.save()
            canvas.clipPath(eyePath)
            canvas.clipRect(bounds.left, lowerTop, bounds.right, bounds.bottom)
            layerPaint.alpha = 255
            canvas.drawBitmap(assets.blink, null, dst, layerPaint)
            canvas.restore()
        }

        // Once the tracker says "closed", stamp the exact closed-eye atlas region once.
        // This is the missing inner-lid/occlusion step: no sclera or iris can leak through.
        if (p >= 0.88f) {
            canvas.save()
            canvas.clipPath(eyePath)
            layerPaint.alpha = 255
            canvas.drawBitmap(assets.blink, null, dst, layerPaint)
            canvas.restore()
        }
    }

    private fun eyeBounds(dst: RectF, isLeft: Boolean): RectF =
        if (isLeft) {
            RectF(
                x(dst, 0.278f), y(dst, 0.338f),
                x(dst, 0.476f), y(dst, 0.472f)
            )
        } else {
            RectF(
                x(dst, 0.558f), y(dst, 0.338f),
                x(dst, 0.752f), y(dst, 0.472f)
            )
        }

    private fun buildEyePath(dst: RectF, isLeft: Boolean): Path {
        // These paths follow the original neutral eye sockets and are deliberately a few
        // pixels wider than the visible white/iris so the lid can completely occlude them.
        return if (isLeft) {
            Path().apply {
                moveTo(x(dst, 0.286f), y(dst, 0.382f))
                cubicTo(x(dst, 0.315f), y(dst, 0.338f), x(dst, 0.405f), y(dst, 0.337f), x(dst, 0.468f), y(dst, 0.382f))
                cubicTo(x(dst, 0.472f), y(dst, 0.416f), x(dst, 0.446f), y(dst, 0.459f), x(dst, 0.392f), y(dst, 0.468f))
                cubicTo(x(dst, 0.337f), y(dst, 0.466f), x(dst, 0.296f), y(dst, 0.439f), x(dst, 0.286f), y(dst, 0.382f))
                close()
            }
        } else {
            Path().apply {
                moveTo(x(dst, 0.562f), y(dst, 0.382f))
                cubicTo(x(dst, 0.625f), y(dst, 0.337f), x(dst, 0.715f), y(dst, 0.338f), x(dst, 0.744f), y(dst, 0.382f))
                cubicTo(x(dst, 0.734f), y(dst, 0.439f), x(dst, 0.693f), y(dst, 0.466f), x(dst, 0.638f), y(dst, 0.468f))
                cubicTo(x(dst, 0.584f), y(dst, 0.459f), x(dst, 0.558f), y(dst, 0.416f), x(dst, 0.562f), y(dst, 0.382f))
                close()
            }
        }
    }

    private fun buildUpperBeakPath(dst: RectF): Path = Path().apply {
        moveTo(x(dst, 0.415f), y(dst, 0.502f))
        lineTo(x(dst, 0.518f), y(dst, 0.431f))
        lineTo(x(dst, 0.626f), y(dst, 0.502f))
        lineTo(x(dst, 0.518f), y(dst, 0.602f))
        close()
    }

    private fun buildJawOuterPath(dst: RectF): Path = Path().apply {
        moveTo(x(dst, 0.419f), y(dst, 0.502f))
        lineTo(x(dst, 0.518f), y(dst, 0.658f))
        lineTo(x(dst, 0.626f), y(dst, 0.502f))
        close()
    }

    private fun buildMouthInteriorPath(dst: RectF): Path = Path().apply {
        moveTo(x(dst, 0.463f), y(dst, 0.558f))
        lineTo(x(dst, 0.518f), y(dst, 0.626f))
        lineTo(x(dst, 0.574f), y(dst, 0.558f))
        close()
    }

    private fun buildHeadPath(dst: RectF): Path {
        // User-defined movable region: hair + face + small neck ruff, shoulders excluded.
        return Path().apply {
            moveTo(x(dst, 0.50f), y(dst, 0.015f))
            cubicTo(x(dst, 0.30f), y(dst, 0.015f), x(dst, 0.13f), y(dst, 0.08f), x(dst, 0.085f), y(dst, 0.24f))
            cubicTo(x(dst, 0.045f), y(dst, 0.38f), x(dst, 0.11f), y(dst, 0.54f), x(dst, 0.245f), y(dst, 0.595f))
            cubicTo(x(dst, 0.30f), y(dst, 0.62f), x(dst, 0.34f), y(dst, 0.61f), x(dst, 0.39f), y(dst, 0.64f))
            cubicTo(x(dst, 0.43f), y(dst, 0.67f), x(dst, 0.47f), y(dst, 0.685f), x(dst, 0.50f), y(dst, 0.69f))
            cubicTo(x(dst, 0.54f), y(dst, 0.685f), x(dst, 0.58f), y(dst, 0.665f), x(dst, 0.62f), y(dst, 0.635f))
            cubicTo(x(dst, 0.67f), y(dst, 0.605f), x(dst, 0.71f), y(dst, 0.615f), x(dst, 0.76f), y(dst, 0.59f))
            cubicTo(x(dst, 0.90f), y(dst, 0.52f), x(dst, 0.965f), y(dst, 0.36f), x(dst, 0.915f), y(dst, 0.22f))
            cubicTo(x(dst, 0.86f), y(dst, 0.075f), x(dst, 0.70f), y(dst, 0.015f), x(dst, 0.50f), y(dst, 0.015f))
            close()
        }
    }

    private fun x(dst: RectF, v: Float): Float = dst.left + dst.width() * v
    private fun y(dst: RectF, v: Float): Float = dst.top + dst.height() * v

    private fun smooth01(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
