package com.totomarujapan.crowavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject

data class AvatarBitmaps(
    val neutral: Bitmap,
    val blink: Bitmap,
    val talk: Bitmap
)

object AvatarAssets {
    fun load(context: Context): AvatarBitmaps {
        val json = context.assets.open("avatar_images.json")
            .bufferedReader()
            .use { it.readText() }
        val obj = JSONObject(json)

        return AvatarBitmaps(
            neutral = decode(obj.getString("neutral")),
            blink = decode(obj.getString("blink")),
            talk = decode(obj.getString("talk"))
        )
    }

    private fun decode(dataUri: String): Bitmap {
        val base64 = dataUri.substringAfter(",")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Avatar image could not be decoded"
        }
    }
}
