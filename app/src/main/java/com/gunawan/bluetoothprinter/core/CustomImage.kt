package com.gunawan.bluetoothprinter.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

class CustomImage {

    suspend fun getImageBitmap(url: String, context: Context): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()

        return when (val result = loader.execute(request)) {
            is SuccessResult -> (result.drawable as BitmapDrawable).bitmap
            else -> null
        }
    }

}