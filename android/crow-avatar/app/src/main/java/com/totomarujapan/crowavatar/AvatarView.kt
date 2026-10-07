package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
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
    private var targetBlink = 0f

    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f
    private var jaw = 0f
    private var blink = 0f

    private val startedAt = System.nanoTime()

    fun updateFace(yaw: Float, pitch: Float, roll: Float, jaw: Float, blink: Float) {
        targetYaw = yaw.coerceIn(-1f, 1f)
        targetPitch = pitch.coerceIn(-1f, 1f)
        targetRoll = roll.coerceIn(-1f, 1f)
        targetJaw = jaw.coerceIn(0f, 1f)
        targetBlink = blink.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setTracking(enabled: Boolean) {
        if (!enabled) {
            targetYaw = 0f
            targetPitch = 0f
            targetRoll = 0f
            targetJaw = 0f
            targetBlink = 0f
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
        blink += (targetBlink - blink) * k

        val bitmap = when {
            blink > 0.58f -> assets.blink
            jaw > 0.22f -> assets.talk
            else -> assets.neutral
        }

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val drawSize = minOf(w * 1.06f, h * 0.77f)
        val left = (w - drawSize) / 2f
        val top = h * 0.08f
        val dst = RectF(left, top, left + drawSize, top + drawSize)
        val cx = dst.centerX()
        val cy = dst.top + dst.height() * 0.49f

        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
        val breathe = 1f + (sin(seconds * 1.45).toFloat() * 0.0045f)

        // Body: nearly fixed, only tiny breathing.
        canvas.save()
        canvas.clipRect(0f, dst.top + dst.height() * 0.53f, w, h)
        canvas.scale(1.0015f, breathe, cx, dst.bottom)
        canvas.drawBitmap(assets.neutral, null, dst, paint)
        canvas.restore()

        // Head: yaw / pitch / roll only affect the upper area.
        canvas.save()
        canvas.clipRect(0f, 0f, w, dst.top + dst.height() * 0.74f)

        matrix.reset()
        camera.save()
        camera.rotateY(yaw * 10f)
        camera.rotateX(-pitch * 6.5f)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-cx, -cy)
        matrix.postTranslate(cx + yaw * w * 0.010f, cy + pitch * h * 0.008f)
        canvas.concat(matrix)
        canvas.rotate(roll * 7f, cx, cy)

        val sx = 1f - abs(yaw) * 0.025f
        canvas.scale(sx, 1f, cx, cy)
        canvas.drawBitmap(bitmap, null, dst, paint)
        canvas.restore()

        postInvalidateOnAnimation()
    }
}
