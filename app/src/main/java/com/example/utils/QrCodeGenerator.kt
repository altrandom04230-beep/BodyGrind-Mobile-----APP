package com.example.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = android.graphics.Color.BLACK,
        lightColor: Int = android.graphics.Color.WHITE
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) darkColor else lightColor)
            }
        }
        return bitmap
    }

    fun generateQrImageBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = android.graphics.Color.BLACK,
        lightColor: Int = android.graphics.Color.WHITE
    ): ImageBitmap {
        return generateQrBitmap(content, sizePx, darkColor, lightColor).asImageBitmap()
    }
}
