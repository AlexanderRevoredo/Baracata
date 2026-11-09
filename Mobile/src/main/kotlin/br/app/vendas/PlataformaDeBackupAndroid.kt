package br.app.vendas

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import br.app.vendas.dados.EscritorDeBackup
import br.app.vendas.dados.LeitorDeBackup
import br.app.vendas.dados.PlataformaDeBackup
import br.app.vendas.dados.ZipEscritor
import br.app.vendas.dados.ZipLeitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup pelos seletores de arquivo do sistema (a pessoa escolhe a pasta ou o Drive onde guardar).
 * Precisa ser criada em onCreate, antes de a tela iniciar.
 */
class PlataformaDeBackupAndroid(private val activity: ComponentActivity) : PlataformaDeBackup {

    private var destino: CompletableDeferred<Uri?>? = null
    private var origem: CompletableDeferred<Uri?>? = null

    private val criar = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        destino?.complete(uri)
        destino = null
    }

    private val abrir = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        origem?.complete(uri)
        origem = null
    }

    override suspend fun criarArquivo(nomeSugerido: String): EscritorDeBackup? {
        val espera = CompletableDeferred<Uri?>()
        destino = espera
        criar.launch(nomeSugerido)
        val uri = espera.await() ?: return null
        return withContext(Dispatchers.IO) {
            val saida = activity.contentResolver.openOutputStream(uri, "wt") ?: return@withContext null
            ZipEscritor(saida)
        }
    }

    override suspend fun abrirArquivo(): LeitorDeBackup? {
        val espera = CompletableDeferred<Uri?>()
        origem = espera
        abrir.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        val uri = espera.await() ?: return null
        return withContext(Dispatchers.IO) {
            // O zip precisa de acesso aleatório: copia para o cache do app e abre de lá.
            val copia = File(activity.cacheDir, "restaurar.zip")
            activity.contentResolver.openInputStream(uri)?.use { entrada -> copia.outputStream().use { entrada.copyTo(it) } }
                ?: return@withContext null
            object : LeitorDeBackup {
                private val zip = ZipLeitor(copia)
                override fun ler(nome: String) = zip.ler(nome)
                override fun fechar() {
                    zip.fechar()
                    copia.delete()
                }
            }
        }
    }
}
