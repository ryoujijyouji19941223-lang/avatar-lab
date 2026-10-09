package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/**
 * Black Rabbit Chat v0.1
 *
 * Crow lesson applied from the start:
 * - body and head are separate groups
 * - blink is an eyelid occlusion of the existing eye, not a black bar
 * - left/right eyes are independent
 * - mouth is split into fixed upper muzzle, mouth interior, moving lower jaw
 * - opening the mouth never swaps the whole face
 */
class AvatarView(context: Context) : View(context) {
    private val layers = AvatarAssets.load(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val furPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(54, 51, 61) }
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
        val poseK = 0.22f
        val expressionK = 0.31f
        yaw += (targetYaw - yaw) * poseK
        pitch += (targetPitch - pitch) * poseK
        roll += (targetRoll - roll) * poseK
        jaw += (targetJaw - jaw) * expressionK
        blinkLeft += (targetBlinkLeft - blinkLeft) * expressionK
        blinkRight += (targetBlinkRight - blinkRight) * expressionK

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Portrait framing: full ears + shoulders, close enough to inspect eyes and mouth.
        val modelW = minOf(w * 0.96f, h * 0.72f)
        val scale = modelW / 480f
        val originX = (w - 480f * scale) * 0.5f
        val originY = h * 0.025f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(scale, scale)

        drawBody(canvas)
        drawHeadGroup(canvas)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawBody(canvas: Canvas) {
        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
        val breathe = 1f + sin(seconds * 1.45).toFloat() * 0.004f

        // Use only the upper half of the body source so the stream layout is shoulders-up.
        val src = Rect(0, 0, layers.body.width, (layers.body.height * 0.52f).toInt())
        val dst = RectF(48f, 285f, 432f, 720f)

        canvas.save()
        canvas.scale(1f, breathe, 240f, 650f)
        canvas.drawBitmap(layers.body, src, dst, paint)
        canvas.restore()
    }

    private fun drawHeadGroup(canvas: Canvas) {
        val head = RectF(100f, 8f, 380f, 387f)
        val pivotX = 240f
        val pivotY = 245f

        canvas.save()
        matrix.reset()
        camera.save()
        camera.rotateY(yaw * 15.5f)
        camera.rotateX(-pitch * 10.0f)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-pivotX, -pivotY)
        matrix.postTranslate(
            pivotX + yaw * 6.0f,
            pivotY + pitch * 5.0f
        )
        canvas.concat(matrix)
        canvas.rotate(roll * 9.0f, pivotX, pivotY)

        canvas.drawBitmap(layers.head, null, head, paint)

        // Real eye-shaped occlusion. The original neutral eye stays underneath.
        drawBlink(canvas, head, viewerLeft = true, blink = blinkRight)
        drawBlink(canvas, head, viewerLeft = false, blink = blinkLeft)

        drawMouth(canvas, head, jaw)

        canvas.restore()
    }

    private fun drawBlink(canvas: Canvas, head: RectF, viewerLeft: Boolean, blink: Float) {
        // Real human blinks frequently peak around ~0.5-0.7 in MediaPipe.
        val p = smooth01(((blink - 0.07f) / 0.55f).coerceIn(0f, 1f))
        if (p <= 0.001f) return

        val eye = if (viewerLeft) {
            RectF(
                head.left + head.width() * 0.245f,
                head.top + head.height() * 0.555f,
                head.left + head.width() * 0.445f,
                head.top + head.height() * 0.675f
            )
        } else {
            RectF(
                head.left + head.width() * 0.555f,
                head.top + head.height() * 0.555f,
                head.left + head.width() * 0.755f,
                head.top + head.height() * 0.675f
            )
        }

        val seam = eye.centerY() + eye.height() * 0.08f
        val path = eyePath(eye)

        // Upper lid comes down and lower lid rises slightly.
        val upperBottom = eye.top + (seam - eye.top) * p
        val lowerTop = eye.bottom - (eye.bottom - seam) * p

        canvas.save()
        canvas.clipPath(path)
        canvas.clipRect(eye.left, eye.top, eye.right, upperBottom)
        canvas.drawColor(furPaint.color)
        canvas.restore()

        canvas.save()
        canvas.clipPath(path)
        canvas.clipRect(eye.left, lowerTop, eye.right, eye.bottom)
        canvas.drawColor(furPaint.color)
        canvas.restore()

        // Use the generated rabbit's own eyelid shapes for the seam/detail.
        val upperLid = if (viewerLeft) layers.upperLidLeft else layers.upperLidRight
        val closedLid = if (viewerLeft) layers.closedLidLeft else layers.closedLidRight

        val lidDst = RectF(
            eye.left - eye.width() * 0.08f,
            eye.top + eye.height() * (0.05f + 0.38f * p),
            eye.right + eye.width() * 0.08f,
            eye.top + eye.height() * (0.48f + 0.38f * p)
        )
        paint.alpha = (255 * p).toInt().coerceIn(0, 255)
        canvas.drawBitmap(upperLid, null, lidDst, paint)

        if (p > 0.72f) {
            val q = ((p - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val closedDst = RectF(
                eye.left - eye.width() * 0.02f,
                seam - eye.height() * 0.18f,
                eye.right + eye.width() * 0.02f,
                seam + eye.height() * 0.18f
            )
            paint.alpha = (255 * q).toInt()
            canvas.drawBitmap(closedLid, null, closedDst, paint)
        }
        paint.alpha = 255

        // Guarantee full anatomical closure: the aperture itself vanishes.
        if (p >= 0.96f) {
            canvas.save()
            canvas.clipPath(path)
            canvas.drawColor(furPaint.color)
            canvas.restore()
            val closedDst = RectF(
                eye.left - eye.width() * 0.02f,
                seam - eye.height() * 0.18f,
                eye.right + eye.width() * 0.02f,
                seam + eye.height() * 0.18f
            )
            canvas.drawBitmap(closedLid, null, closedDst, paint)
        }
    }

    private fun drawMouth(canvas: Canvas, head: RectF, rawJaw: Float) {
        val p = smooth01(((rawJaw - 0.04f) / 0.72f).coerceIn(0f, 1f))

        // This rectangle replaces only the muzzle/mouth module, never the whole face.
        val mouth = RectF(
            head.left + head.width() * 0.305f,
            head.top + head.height() * 0.665f,
            head.left + head.width() * 0.695f,
            head.top + head.height() * 0.865f
        )
        val splitY = mouth.top + mouth.height() * 0.48f

        // Deepest layer: mouth interior is revealed only by opening.
        if (p > 0.01f) {
            val inside = RectF(
                mouth.left + mouth.width() * 0.24f,
                splitY - 2f,
                mouth.right - mouth.width() * 0.24f,
                splitY + mouth.height() * (0.12f + 0.43f * p)
            )
            paint.alpha = (210 + 45 * p).toInt()
            canvas.drawBitmap(layers.mouthInside, null, inside, paint)
            paint.alpha = 255
        }

        val src = layers.mouthNeutral
        val splitSrc = (src.height * 0.48f).toInt()

        // Fixed upper muzzle + nose.
        canvas.drawBitmap(
            src,
            Rect(0, 0, src.width, splitSrc),
            RectF(mouth.left, mouth.top, mouth.right, splitY),
            paint
        )

        // Lower jaw only. Hinge is at the mouth corner line.
        canvas.save()
        val hingeX = mouth.centerX()
        val hingeY = splitY
        canvas.translate(0f, mouth.height() * 0.16f * p)
        canvas.scale(1f + 0.015f * p, 1f + 0.23f * p, hingeX, hingeY)
        canvas.drawBitmap(
            src,
            Rect(0, splitSrc, src.width, src.height),
            RectF(mouth.left, splitY, mouth.right, mouth.bottom),
            paint
        )
        canvas.restore()
    }

    private fun eyePath(r: RectF): Path = Path().apply {
        moveTo(r.left, r.centerY())
        cubicTo(
            r.left + r.width() * 0.22f, r.top,
            r.left + r.width() * 0.75f, r.top,
            r.right, r.centerY()
        )
        cubicTo(
            r.left + r.width() * 0.78f, r.bottom,
            r.left + r.width() * 0.22f, r.bottom,
            r.left, r.centerY()
        )
        close()
    }

    private fun smooth01(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
