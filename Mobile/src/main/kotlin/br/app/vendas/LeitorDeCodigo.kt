package br.app.vendas

import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CompletableDeferred

/**
 * Leitor de código de barras que abre uma tela da própria câmera deste app (ZXing). Roda no mesmo
 * processo do app: não depende de outro app responder, o que evita travar quando o sistema
 * economiza bateria. O Android pede a permissão da câmera na primeira leitura.
 * Precisa ser criado em onCreate, antes de a tela iniciar.
 */
class LeitorDeCodigo(activity: ComponentActivity) {
    private var pendente: CompletableDeferred<String?>? = null

    private val leitor = activity.registerForActivityResult(ScanContract()) { resultado ->
        pendente?.complete(resultado.contents)
        pendente = null
    }

    /** Devolve o número lido, ou null se a pessoa cancelou. */
    suspend fun ler(): String? {
        val espera = CompletableDeferred<String?>()
        pendente = espera
        leitor.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(listOf(BarcodeFormat.EAN_13.name, BarcodeFormat.EAN_8.name, BarcodeFormat.UPC_A.name))
                .setPrompt("Aponte para o código de barras (ISBN) do livro")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
        return espera.await()
    }
}
