package br.app.vendas

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import br.app.vendas.dados.ImagemEscolhida
import br.app.vendas.dados.ImagemParaExportar
import br.app.vendas.dados.PlataformaDeFotos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Seletor de fotos do sistema (não pede permissão de acesso à galeria) e exportação para
 * Imagens/Baracata/<título> na galeria. Precisa ser criada em onCreate, antes de a tela iniciar.
 */
class PlataformaDeFotosAndroid(private val activity: ComponentActivity) : PlataformaDeFotos {

    private var pendente: CompletableDeferred<List<Uri>>? = null

    private val seletor = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        pendente?.complete(uris)
        pendente = null
    }

    private var permissao: CompletableDeferred<Boolean>? = null
    private var tirada: CompletableDeferred<Boolean>? = null

    private val pedirPermissao = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permissao?.complete(ok)
        permissao = null
    }

    private val camera = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        tirada?.complete(ok)
        tirada = null
    }

    override val podeTirarFoto: Boolean
        get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /**
     * Abre a câmera do aparelho. A foto vai para um arquivo temporário do app (nunca para a galeria) e é
     * lida e apagada logo depois. Se o Android encerrar o app com a câmera aberta, a foto se perde.
     */
    override suspend fun tirarFoto(): ImagemEscolhida? {
        // O app declara a permissão de câmera (por causa do leitor de ISBN); sem ela concedida, a câmera do sistema falha.
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val espera = CompletableDeferred<Boolean>()
            permissao = espera
            pedirPermissao.launch(Manifest.permission.CAMERA)
            if (!espera.await()) return null
        }
        val arquivo = withContext(Dispatchers.IO) {
            File(activity.cacheDir, "camera").apply { mkdirs() }.let { File(it, "foto.jpg").also { f -> f.delete() } }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.arquivos", arquivo)
        val espera = CompletableDeferred<Boolean>()
        tirada = espera
        camera.launch(uri)
        if (!espera.await()) return null
        return withContext(Dispatchers.IO) {
            if (!arquivo.exists() || arquivo.length() == 0L) return@withContext null
            ImagemEscolhida(arquivo.readBytes(), "jpg").also { arquivo.delete() }
        }
    }

    override suspend fun escolher(): List<ImagemEscolhida> {
        val espera = CompletableDeferred<List<Uri>>()
        pendente = espera
        seletor.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val uris = espera.await()
        val resolver = activity.contentResolver
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
                val extensao = when (resolver.getType(uri)) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/heic", "image/heif" -> "heic"
                    else -> "jpg"
                }
                ImagemEscolhida(bytes, extensao)
            }
        }
    }

    override suspend fun exportar(nomeDaPasta: String, imagens: List<ImagemParaExportar>): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "A exportação para a galeria exige Android 10 ou mais novo."
        }
        return withContext(Dispatchers.IO) {
            val resolver = activity.contentResolver
            val relativo = "Pictures/Baracata/$nomeDaPasta"
            // Remove exportações anteriores deste mesmo item para não sobrar foto velha.
            resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf("$relativo/"),
            )
            var gravadas = 0
            imagens.forEach { img ->
                val valores = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, img.nome)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime(img.nome))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativo)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
                if (uri != null && resolver.openOutputStream(uri)?.use { it.write(img.bytes); true } == true) gravadas++
            }
            "$gravadas foto(s) salvas na galeria, pasta Baracata/$nomeDaPasta. Anexe na ordem 01, 02, ..."
        }
    }

    private fun mime(nome: String) = when (nome.substringAfterLast('.')) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }
}
