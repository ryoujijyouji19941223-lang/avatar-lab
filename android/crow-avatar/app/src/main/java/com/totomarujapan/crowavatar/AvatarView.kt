package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

class AvatarView(context: Context) : View(context) {
    private val assets = AvatarAssets.load(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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

        val k = 0.22f
        yaw += (targetYaw - yaw) * k
        pitch += (targetPitch - pitch) * k
        roll += (targetRoll - roll) * k
        jaw += (targetJaw - jaw) * k
        blinkLeft += (targetBlinkLeft - blinkLeft) * k
        blinkRight += (targetBlinkRight - blinkRight) * k

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val drawSize = minOf(w * 1.06f, h * 0.77f)
        val left = (w - drawSize) / 2f
        val top = h * 0.08f
        val dst = RectF(left, top, left + drawSize, top + drawSize)
        val cx = dst.centerX()
        val headPivotY = dst.top + dst.height() * 0.46f

        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
        val breathe = 1f + (sin(seconds * 1.45).toFloat() * 0.0045f)

        // BODY: shoulder / jacket / chest only. No camera-driven movement.
        canvas.save()
        val bodyTop = dst.top + dst.height() * 0.655f
        canvas.clipRect(0f, bodyTop, w, h)
        canvas.scale(1.0015f, breathe, cx, dst.bottom)
        canvas.drawBitmap(assets.neutral, null, dst, paint)
        canvas.restore()

        // HEAD GROUP: user-defined face/head area only. Shoulders are excluded by the path.
        canvas.save()

        matrix.reset()
        camera.save()
        camera.rotateY(yaw * 9.5f)
        camera.rotateX(-pitch * 6.0f)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-cx, -headPivotY)
        matrix.postTranslate(
            cx + yaw * w * 0.009f,
            headPivotY + pitch * h * 0.007f
        )
        canvas.concat(matrix)
        canvas.rotate(roll * 7f, cx, headPivotY)

        val headPath = buildHeadPath(dst)
        canvas.clipPath(headPath)

        // Neutral head is the stable base.
        canvas.drawBitmap(assets.neutral, null, dst, paint)

        // Expression layers are clipped to local regions only.
        if (blinkLeft > 0.52f) {
            canvas.save()
            canvas.clipRect(region(dst, 0.285f, 0.335f, 0.475f, 0.475f))
            canvas.drawBitmap(assets.blink, null, dst, paint)
            canvas.restore()
        }

        if (blinkRight > 0.52f) {
            canvas.save()
            canvas.clipRect(region(dst, 0.545f, 0.335f, 0.755f, 0.475f))
            canvas.drawBitmap(assets.blink, null, dst, paint)
            canvas.restore()
        }

        if (jaw > 0.18f) {
            canvas.save()
            canvas.clipRect(region(dst, 0.385f, 0.455f, 0.650f, 0.685f))
            canvas.drawBitmap(assets.talk, null, dst, paint)
            canvas.restore()
        }

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun region(
        dst: RectF,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): RectF = RectF(
        dst.left + dst.width() * x0,
        dst.top + dst.height() * y0,
        dst.left + dst.width() * x1,
        dst.top + dst.height() * y1
    )

    private fun buildHeadPath(dst: RectF): Path {
        fun x(v: Float) = dst.left + dst.width() * v
        fun y(v: Float) = dst.top + dst.height() * v

        // Approximation of the user's outlined movable area:
        // hair + face + small neck ruff, explicitly excluding both shoulders.
        return Path().apply {
            moveTo(x(0.50f), y(0.015f))
            cubicTo(x(0.30f), y(0.015f), x(0.13f), y(0.08f), x(0.085f), y(0.24f))
            cubicTo(x(0.045f), y(0.38f), x(0.11f), y(0.54f), x(0.245f), y(0.595f))
            cubicTo(x(0.30f), y(0.62f), x(0.34f), y(0.61f), x(0.39f), y(0.64f))
            cubicTo(x(0.43f), y(0.67f), x(0.47f), y(0.685f), x(0.50f), y(0.69f))
            cubicTo(x(0.54f), y(0.685f), x(0.58f), y(0.665f), x(0.62f), y(0.635f))
            cubicTo(x(0.67f), y(0.605f), x(0.71f), y(0.615f), x(0.76f), y(0.59f))
            cubicTo(x(0.90f), y(0.52f), x(0.965f), y(0.36f), x(0.915f), y(0.22f))
            cubicTo(x(0.86f), y(0.075f), x(0.70f), y(0.015f), x(0.50f), y(0.015f))
            close()
        }
    }
}
