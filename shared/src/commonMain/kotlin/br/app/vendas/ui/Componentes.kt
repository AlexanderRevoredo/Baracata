package br.app.vendas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.vendas.dados.ServicoDeFotos
import br.app.vendas.dados.decodificarMiniatura
import br.app.vendas.dominio.Foto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Guarda as últimas miniaturas já decodificadas para a lista não decodificar de novo a cada rolagem. */
private object CacheDeMiniaturas {
    // Só é usado pela thread de interface (o produceState grava depois de voltar do withContext).
    private val mapa = LinkedHashMap<String, ImageBitmap>()

    operator fun get(id: String): ImageBitmap? = mapa[id]
    operator fun set(id: String, imagem: ImageBitmap) {
        if (mapa.size >= 60) mapa.remove(mapa.keys.first())
        mapa[id] = imagem
    }
}

@Composable
internal fun MiniaturaDeFoto(
    foto: Foto,
    fotos: ServicoDeFotos,
    largura: Dp = 72.dp,
    altura: Dp = 72.dp,
    escala: ContentScale = ContentScale.Crop,
) {
    val imagem by produceState<ImageBitmap?>(CacheDeMiniaturas[foto.id], foto.id) {
        value = CacheDeMiniaturas[foto.id] ?: withContext(Dispatchers.Default) {
            fotos.ler(foto)?.let { decodificarMiniatura(it, 320) }
        }?.also { CacheDeMiniaturas[foto.id] = it }
    }
    Box(
        Modifier.size(largura, altura).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val pronta = imagem
        if (pronta != null) {
            Image(bitmap = pronta, contentDescription = "Foto do livro", contentScale = escala, modifier = Modifier.fillMaxSize())
        } else {
            Text("…")
        }
    }
}

@Composable
internal fun MiniaturaDeBytes(bytes: ByteArray) {
    val imagem by produceState<ImageBitmap?>(null, bytes) {
        value = withContext(Dispatchers.Default) { decodificarMiniatura(bytes, 400) }
    }
    Box(
        Modifier.size(width = 100.dp, height = 150.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val pronta = imagem
        if (pronta != null) {
            Image(bitmap = pronta, contentDescription = "Capa encontrada", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text("…")
        }
    }
}

/** Bloco de formulário com título, para agrupar campos que têm a ver entre si. */
@Composable
internal fun Secao(titulo: String, subtitulo: String? = null, conteudo: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            conteudo()
        }
    }
}
