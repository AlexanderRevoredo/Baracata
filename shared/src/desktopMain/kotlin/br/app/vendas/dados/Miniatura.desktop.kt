package br.app.vendas.dados

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam

actual fun decodificarMiniatura(bytes: ByteArray, ladoMax: Int): ImageBitmap? {
    // ImageIO lê já reduzida (subamostragem), sem carregar a foto inteira na memória.
    try {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { entrada ->
            val leitor = ImageIO.getImageReaders(entrada).asSequence().firstOrNull()
            if (leitor != null) {
                try {
                    leitor.input = entrada
                    val maior = maxOf(leitor.getWidth(0), leitor.getHeight(0))
                    val passo = (maior / ladoMax).coerceAtLeast(1)
                    val param: ImageReadParam = leitor.defaultReadParam.apply { setSourceSubsampling(passo, passo, 0, 0) }
                    return leitor.read(0, param).toComposeImageBitmap()
                } finally {
                    leitor.dispose()
                }
            }
        }
    } catch (e: Exception) {
        // cai para o Skia abaixo (ex.: WebP)
    }
    return try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
