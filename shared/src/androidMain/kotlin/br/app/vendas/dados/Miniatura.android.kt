package br.app.vendas.dados

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream

actual fun decodificarMiniatura(bytes: ByteArray, ladoMax: Int): ImageBitmap? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

    var amostra = 1
    while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMax) amostra *= 2
    val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes) ?: return null

    // O BitmapFactory ignora a orientação gravada pela câmera (EXIF); aplica aqui.
    val graus = try {
        when (ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (graus == 0f) return bitmap.asImageBitmap()
    val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(graus) }, true)
    return girado.asImageBitmap()
}
