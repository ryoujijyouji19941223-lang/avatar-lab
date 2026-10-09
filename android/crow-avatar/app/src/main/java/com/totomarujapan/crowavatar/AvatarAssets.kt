package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64

data class RabbitLayers(
    val body: Bitmap,
    val head: Bitmap,
    val eyeLeft: Bitmap,
    val eyeRight: Bitmap,
    val upperLidLeft: Bitmap,
    val upperLidRight: Bitmap,
    val closedLidLeft: Bitmap,
    val closedLidRight: Bitmap,
    val mouthNeutral: Bitmap,
    val mouthSmall: Bitmap,
    val mouthOpen: Bitmap,
    val mouthInside: Bitmap
)

object AvatarAssets {
    // The generated rabbit sheet was repacked into one compact atlas.
    // Source rects below are in the 320 x 320 atlas coordinate system.
    fun load(context: Context): RabbitLayers {
        val encoded = buildString {
            for (i in 1..3) {
                append(
                    context.assets.open("rabbit_atlas.b64.$i")
                        .bufferedReader()
                        .use { it.readText().trim() }
                )
            }
        }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val atlas = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Rabbit atlas could not be decoded"
        }
        require(atlas.width == 320 && atlas.height == 320) {
            "Unexpected rabbit atlas size: ${atlas.width}x${atlas.height}"
        }

        fun crop(x: Int, y: Int, w: Int, h: Int): Bitmap =
            Bitmap.createBitmap(atlas, x, y, w, h)

        return RabbitLayers(
            body = crop(0, 50, 120, 265),
            head = crop(129, 0, 105, 142),
            eyeLeft = crop(129, 146, 33, 23),
            eyeRight = crop(168, 146, 34, 23),
            upperLidLeft = crop(206, 146, 48, 20),
            upperLidRight = crop(259, 146, 50, 20),
            closedLidLeft = crop(129, 188, 41, 11),
            closedLidRight = crop(175, 188, 42, 11),
            mouthNeutral = crop(222, 188, 46, 30),
            mouthSmall = crop(129, 229, 47, 31),
            mouthOpen = crop(181, 229, 46, 31),
            mouthInside = crop(232, 229, 28, 20)
        ).also {
            atlas.recycle()
        }
    }
}
