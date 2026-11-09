package br.app.vendas.dados

import androidx.compose.ui.graphics.ImageBitmap
import br.app.vendas.dominio.Foto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pasta privada do app onde ficam os arquivos das fotos (implementada em cada plataforma). */
expect class ArquivosDeFotos(pasta: String) {
    /** Grava de forma atômica: ou o arquivo inteiro existe, ou nada. */
    fun gravar(nome: String, bytes: ByteArray)
    fun ler(nome: String): ByteArray?
    fun apagar(nome: String)
    fun nomes(): List<String>
}

/** Imagem escolhida pela pessoa no seletor do sistema. [extensao] sem ponto, em minúsculas. */
class ImagemEscolhida(val bytes: ByteArray, val extensao: String)

/** Imagem pronta para sair do app. [nome] já vem numerado pela ordem, ex.: "01.jpg". */
class ImagemParaExportar(val nome: String, val bytes: ByteArray)

/** O que muda de uma plataforma para outra: seletor de imagens e entrega dos arquivos. */
interface PlataformaDeFotos {
    /** Abre o seletor. Lista vazia se a pessoa cancelou. */
    suspend fun escolher(): List<ImagemEscolhida>

    /** Coloca as imagens onde a pessoa consegue anexá-las no anúncio. Devolve o aviso a mostrar. */
    suspend fun exportar(nomeDaPasta: String, imagens: List<ImagemParaExportar>): String

    /** Este aparelho consegue tirar foto pelo app? */
    val podeTirarFoto: Boolean get() = false

    /** Abre a câmera e devolve a foto tirada (null se cancelou ou a câmera foi negada). */
    suspend fun tirarFoto(): ImagemEscolhida? = null
}

/** Decodifica uma miniatura (lado maior limitado a [ladoMax] px), respeitando a orientação quando possível. */
expect fun decodificarMiniatura(bytes: ByteArray, ladoMax: Int): ImageBitmap?

class ServicoDeFotos(
    private val arquivos: ArquivosDeFotos,
    private val plataforma: PlataformaDeFotos,
    private val novoId: () -> String,
) {
    /** Abre o seletor e copia cada imagem para a pasta privada. As fotos só entram no item quando ele for salvo. */
    suspend fun adicionar(): List<Foto> {
        val escolhidas = plataforma.escolher()
        return withContext(Dispatchers.IO) {
            escolhidas.map { img ->
                val id = novoId()
                val nome = "$id.${extensaoSegura(img.extensao)}"
                arquivos.gravar(nome, img.bytes)
                Foto(id, nome, img.bytes.size.toLong())
            }
        }
    }

    val podeTirarFoto: Boolean get() = plataforma.podeTirarFoto

    /** Abre a câmera, guarda a foto na pasta privada e devolve o registro (null se cancelou). */
    suspend fun tirar(): Foto? {
        val img = plataforma.tirarFoto() ?: return null
        return adicionarBytes(img.bytes, img.extensao)
    }

    /** Guarda uma imagem que já está na memória (ex.: capa baixada pelo ISBN). */
    suspend fun adicionarBytes(bytes: ByteArray, extensao: String): Foto = withContext(Dispatchers.IO) {
        val id = novoId()
        val nome = "$id.${extensaoSegura(extensao)}"
        arquivos.gravar(nome, bytes)
        Foto(id, nome, bytes.size.toLong())
    }

    suspend fun ler(foto: Foto): ByteArray? = withContext(Dispatchers.IO) { arquivos.ler(foto.arquivo) }

    suspend fun descartar(fotos: List<Foto>) = withContext(Dispatchers.IO) {
        fotos.forEach { arquivos.apagar(it.arquivo) }
    }

    /** Exporta na ordem do item. Devolve o aviso para a pessoa (ou o motivo da falha). */
    suspend fun exportar(titulo: String, fotos: List<Foto>): String = withContext(Dispatchers.IO) {
        // Numeração contínua (01, 02, ...) mesmo se alguma foto estiver sem arquivo neste aparelho.
        val lidas = fotos.mapNotNull { f -> arquivos.ler(f.arquivo)?.let { f to it } }
        val imagens = lidas.mapIndexed { i, (f, bytes) -> ImagemParaExportar(nomeNumerado(i + 1, f.arquivo), bytes) }
        if (imagens.isEmpty()) return@withContext "Nenhuma foto disponível neste aparelho para exportar."
        val aviso = plataforma.exportar(nomeDaPasta(titulo), imagens)
        if (imagens.size < fotos.size) "$aviso (${fotos.size - imagens.size} sem arquivo neste aparelho)" else aviso
    }

    /** Apaga arquivos que nenhum item usa mais (sobras de edição interrompida, itens removidos). */
    suspend fun limparOrfaos(emUso: Set<String>) = withContext(Dispatchers.IO) {
        arquivos.nomes().filter { it !in emUso }.forEach { arquivos.apagar(it) }
    }

    suspend fun apagarTudo() = withContext(Dispatchers.IO) {
        arquivos.nomes().forEach { arquivos.apagar(it) }
    }

    companion object {
        private val EXTENSOES = setOf("jpg", "png", "webp", "heic", "gif")

        fun extensaoSegura(extensao: String): String {
            val e = extensao.lowercase().removePrefix(".").let { if (it == "jpeg") "jpg" else it }
            return if (e in EXTENSOES) e else "jpg"
        }

        fun nomeNumerado(posicao: Int, arquivo: String): String =
            posicao.toString().padStart(2, '0') + "." + extensaoSegura(arquivo.substringAfterLast('.', "jpg"))

        /** Nome de pasta aceito pelo Windows e pelo Android. */
        fun nomeDaPasta(titulo: String): String {
            val limpo = titulo.map { if (it in "\\/:*?\"<>|" || it.isISOControl()) ' ' else it }
                .joinToString("").trim().trimEnd('.').replace(Regex("\\s+"), " ").take(60).trim()
            return limpo.ifEmpty { "item" }
        }
    }
}
