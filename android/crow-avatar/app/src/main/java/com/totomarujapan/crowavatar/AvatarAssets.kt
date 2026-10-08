package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.*
import android.util.Base64
import org.json.JSONObject

/** Masks use the original portrait's 480px space, never expression frames. */
object RigGeometry {
    const val HINGE_X = 251f
    const val HINGE_Y = 244f
    fun head() = Path().apply {
        moveTo(0f, 0f); lineTo(480f, 0f); lineTo(480f, 309f)
        lineTo(357f, 314f); lineTo(344f, 328f); lineTo(328f, 320f)
        lineTo(316f, 337f); lineTo(307f, 327f); lineTo(288f, 350f)
        lineTo(280f, 340f); lineTo(252f, 365f); lineTo(233f, 349f)
        lineTo(222f, 356f); lineTo(199f, 335f); lineTo(191f, 344f)
        lineTo(170f, 320f); lineTo(155f, 328f); lineTo(135f, 314f)
        lineTo(0f, 309f); close()
    }
    fun upperBeak() = Path().apply {
        moveTo(197f, 242f)
        cubicTo(216f, 232f, 237f, 218f, 248f, 207f)
        cubicTo(267f, 224f, 286f, 234f, 304f, 242f)
        cubicTo(280f, 259f, 266f, 279f, 251f, 291f)
        cubicTo(232f, 269f, 215f, 255f, 197f, 242f); close()
    }
    fun lowerBeak() = Path().apply {
        moveTo(230f, 288f); quadTo(249f, 299f, 274f, 285f)
        lineTo(251f, 315f); close()
    }
    // Anatomical LEFT is on the viewer's right. Camera input is not mirrored.
    val leftEye = EyeContour(
        floatArrayOf(278f, 214f, 285f, 183f, 311f, 169f, 337f, 178f),
        floatArrayOf(278f, 214f, 306f, 218f, 330f, 211f, 337f, 178f),
        floatArrayOf(278f, 214f, 302f, 218f, 326f, 211f, 337f, 178f),
        Rect(282, 220, 325, 238))
    val rightEye = EyeContour(
        floatArrayOf(165f, 179f, 183f, 170f, 208f, 176f, 220f, 215f),
        floatArrayOf(165f, 179f, 173f, 214f, 201f, 219f, 220f, 215f),
        floatArrayOf(165f, 179f, 175f, 211f, 199f, 219f, 220f, 215f),
        Rect(174, 220, 217, 238))
}
class EyeContour(val top: FloatArray, val bottom: FloatArray, val seam: FloatArray, val texture: Rect) {
    val bounds = RectF(minOf(top[0], top[6]) - 2f, 169f, maxOf(top[0], top[6]) + 2f, 221f)
    fun curve(from: FloatArray, closure: Float) = FloatArray(8) { i -> from[i] + (seam[i] - from[i]) * closure }
    fun area(a: FloatArray, b: FloatArray) = Path().apply {
        moveTo(a[0], a[1]); cubicTo(a[2], a[3], a[4], a[5], a[6], a[7])
        cubicTo(b[4], b[5], b[2], b[3], b[0], b[1]); close()
    }
    fun aperture() = area(top, bottom)
    fun line(c: FloatArray) = Path().apply {
        moveTo(c[0], c[1]); cubicTo(c[2], c[3], c[4], c[5], c[6], c[7])
    }
}
data class EyeLayers(val eyeball: Bitmap, val upperLid: Bitmap, val lowerLid: Bitmap, val contour: EyeContour)
data class AvatarLayers(val body: Bitmap, val head: Bitmap, val leftEye: EyeLayers,
    val rightEye: EyeLayers, val lowerBeak: Bitmap, val upperBeak: Bitmap)

object AvatarAssets {
    fun load(context: Context): AvatarLayers {
        val obj = JSONObject(context.assets.open("avatar_images.json").bufferedReader().use { it.readText() })
        val bytes = Base64.decode(obj.getString("neutral").substringAfter(","), Base64.DEFAULT)
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        require(source.width == 480 && source.height == 480)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun layer(path: Path) = bitmap { c -> c.clipPath(path); c.drawBitmap(source, 0f, 0f, paint) }
        fun eye(contour: EyeContour): EyeLayers {
            fun lid(a: FloatArray, b: FloatArray) = bitmap { c ->
                c.clipPath(contour.area(a, b)); c.drawColor(Color.rgb(64, 59, 65))
                c.drawBitmap(source, contour.texture, contour.bounds, paint)
            }
            return EyeLayers(layer(contour.aperture()), lid(contour.top, contour.seam),
                lid(contour.seam, contour.bottom), contour)
        }
        val body = bitmap { c ->
            c.clipRect(0f, 310f, 480f, 480f)
            // Feather backing under the overlapping head/neck prevents a transparent slit.
            c.drawBitmap(source, Rect(183, 365, 318, 424), RectF(168f, 306f, 333f, 382f), paint)
            c.clipOutPath(RigGeometry.head())
            c.drawBitmap(source, 0f, 0f, paint)
        }
        val head = layer(RigGeometry.head())
        Canvas(head).apply {
            val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            drawPath(RigGeometry.leftEye.aperture(), erase); drawPath(RigGeometry.rightEye.aperture(), erase)
            drawPath(RigGeometry.upperBeak(), erase); drawPath(RigGeometry.lowerBeak(), erase)
        }
        val result = AvatarLayers(body, head, eye(RigGeometry.leftEye), eye(RigGeometry.rightEye),
            layer(RigGeometry.lowerBeak()), layer(RigGeometry.upperBeak()))
        source.recycle()
        return result
    }
    private fun bitmap(draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
}
