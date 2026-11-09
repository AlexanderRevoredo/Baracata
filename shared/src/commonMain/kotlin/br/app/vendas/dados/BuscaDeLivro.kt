package br.app.vendas.dados

import br.app.vendas.dominio.Isbn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

data class RespostaHttp(val codigo: Int, val corpo: String)

class RespostaBinaria(val codigo: Int, val corpo: ByteArray)

interface ClienteHttp {
    /** Faz um GET. Lança exceção de E/S quando não há rede. */
    suspend fun obter(url: String): RespostaHttp

    /** GET de um arquivo (imagem). Limite de 8 MB por segurança. */
    suspend fun obterBytes(url: String): RespostaBinaria = RespostaBinaria(404, ByteArray(0))
}

/** Um livro sugerido por uma busca por título; a pessoa escolhe o que corresponde ao seu exemplar. */
class CandidatoDeLivro(
    val titulo: String,
    val autores: List<String>,
    val editora: String?,
    val anoEdicao: Int?,
    val paginas: Int?,
    val sinopse: String?,
    val capaUrl: String?,
    val fonte: String,
)

/** Uma capa candidata, já baixada. [extensao] sem ponto. */
class CapaCandidata(val fonte: String, val bytes: ByteArray, val extensao: String)

/** Cliente HTTP da plataforma (Android e desktop usam a JVM). */
expect fun criarClienteHttp(): ClienteHttp

/**
 * Dados de um livro vindos de uma fonte externa, com proveniência.
 * Ano da edição NÃO é o ano de lançamento original da obra; este fica desconhecido.
 */
data class DadosDoLivro(
    val isbn: String,
    val titulo: String?,
    val subtitulo: String?,
    val autores: List<String>,
    val editora: String?,
    val anoEdicao: Int?,
    val paginas: Int?,
    val sinopse: String?,
    val fonte: String,
    val urlConsulta: String,
    val consultadoEm: String,
    val capaUrl: String? = null,
    /** Chave da edição no Open Library (ex.: "/books/OL1M"), usada para buscar a sinopse. */
    val chaveEdicao: String? = null,
    /** Avisos da fonte que a pessoa decide se usa (créditos, outro título). Não entram no anúncio. */
    val observacoes: List<String> = emptyList(),
    /** Outros títulos da mesma obra na fonte (ex.: "Ship of Theseus" para o ISBN do estojo "S."). */
    val outrosTitulos: List<String> = emptyList(),
    /** Capa encontrada no Google Books (só com chave configurada). */
    val capaGoogle: String? = null,
    /** De onde veio a sinopse (pode ser diferente da fonte principal). */
    val fonteSinopse: String? = null,
    /** A capa do Google veio de uma busca por título (pode ser de outra edição). */
    val capaGoogleAproximada: Boolean = false,
    /** Formato segundo a base (ex.: "PHYSICAL", "DIGITAL"). */
    val formato: String? = null,
    /** Campos que vieram de outras fontes porque a principal não tinha (ex.: "Google Books (autor, páginas)"). */
    val complementos: List<String> = emptyList(),
) {
    /** Texto curto de proveniência, guardado junto do item. */
    fun descricaoDaFonte(): String =
        "$fonte · $urlConsulta · consultado em $consultadoEm" +
            (fonteSinopse?.let { " · sinopse: $it" } ?: "") +
            (if (complementos.isEmpty()) "" else " · completado com: " + complementos.joinToString("; "))
}

sealed interface ResultadoBusca {
    data class Encontrado(val dados: DadosDoLivro) : ResultadoBusca
    data object NaoEncontrado : ResultadoBusca
    data class SemConexao(val detalhe: String) : ResultadoBusca
}

/**
 * Busca dados do livro pelo ISBN, sem chave de API:
 * 1. BrasilAPI (agrega CBL e outras bases brasileiras);
 * 2. Open Library, como reserva para edições estrangeiras.
 * Um ISBN encontrado não garante que seja a edição do exemplar: a pessoa confere.
 */
class BuscaDeLivro(
    private val http: ClienteHttp = criarClienteHttp(),
    private val hoje: () -> String = { Clock.System.now().toString().take(10) },
) {
    private class Fonte(
        val url: (isbn13: String) -> String,
        val interpretar: (corpo: String, isbn13: String, url: String) -> DadosDoLivro?,
    )

    private val fontes = listOf(
        Fonte(
            url = { "https://brasilapi.com.br/api/isbn/v1/$it" },
            interpretar = { corpo, isbn, url -> interpretarBrasilApi(corpo, isbn, url) },
        ),
        Fonte(
            url = { "https://openlibrary.org/api/books?bibkeys=ISBN:$it&format=json&jscmd=data" },
            interpretar = { corpo, isbn, url -> interpretarOpenLibrary(corpo, isbn, url) },
        ),
    )

    /**
     * Chave de API do Google Books, digitada pela pessoa no app (opcional). Sem ela o Google não é consultado.
     * Fica só no banco local do aparelho; nunca entra no código nem na URL guardada junto do item.
     */
    var chaveGoogle: String? = null

    private class RespostaGoogle(val dados: DadosDoLivro?, val aviso: String?)

    private suspend fun consultarGoogle(isbn: String): RespostaGoogle {
        val chave = chaveGoogle?.trim().orEmpty()
        if (chave.isEmpty()) return RespostaGoogle(null, null)
        val publica = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"
        return try {
            val r = http.obter("$publica&maxResults=1&key=$chave")
            when (r.codigo) {
                200 -> RespostaGoogle(interpretarGoogle(r.corpo, isbn, publica)?.copy(consultadoEm = hoje()), null)
                400, 403 -> RespostaGoogle(null, "Google Books recusou a chave (HTTP ${r.codigo}). Confira a chave e se a Books API está ativada.")
                429 -> RespostaGoogle(null, "Google Books: limite de consultas atingido (HTTP 429).")
                else -> RespostaGoogle(null, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RespostaGoogle(null, null)
        }
    }

    suspend fun buscar(isbnBruto: String): ResultadoBusca {
        val isbn = Isbn.paraIsbn13(isbnBruto) ?: return ResultadoBusca.NaoEncontrado
        var falha: String? = null
        for (fonte in fontes) {
            val url = fonte.url(isbn)
            try {
                val resposta = http.obter(url)
                when (resposta.codigo) {
                    200 -> fonte.interpretar(resposta.corpo, isbn, url)?.let {
                        return ResultadoBusca.Encontrado(complementar(it.copy(consultadoEm = hoje())))
                    }
                    400, 404 -> Unit // a fonte não conhece este ISBN: tenta a próxima
                    else -> falha = "HTTP ${resposta.codigo}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                falha = e.message ?: e::class.simpleName ?: "erro de rede"
            }
        }
        // Nenhuma fonte gratuita conhece o livro: tenta o Google Books, se a pessoa configurou uma chave.
        val google = consultarGoogle(isbn)
        if (google.dados != null) return ResultadoBusca.Encontrado(google.dados.copy(observacoes = listOfNotNull(google.aviso)))
        return if (falha != null) ResultadoBusca.SemConexao(falha) else ResultadoBusca.NaoEncontrado
    }

    /**
     * Completa os dados: Google Books (com chave) para sinopse e capa, depois Open Library para sinopse e
     * "outros títulos". Qualquer falha aqui só deixa os dados como estavam. A sinopse vem de texto de
     * terceiros: a pessoa confere e reescreve se quiser. [DadosDoLivro.fonteSinopse] diz de onde veio.
     */
    private suspend fun complementar(original: DadosDoLivro): DadosDoLivro {
        var d = original
        val temChave = !chaveGoogle.isNullOrBlank()
        val google = consultarGoogle(d.isbn)
        google.dados?.let { d = preencherVazios(d, it, "Google Books") }
        if (google.dados != null || google.aviso != null) {
            val doGoogle = if (d.sinopse == null) google.dados?.sinopse else null
            d = d.copy(
                sinopse = d.sinopse ?: doGoogle,
                fonteSinopse = if (doGoogle != null) "Google Books" else d.fonteSinopse,
                capaGoogle = google.dados?.capaGoogle,
                observacoes = d.observacoes + listOfNotNull(google.aviso),
            )
        }
        // O Google pode não ter o ISBN exato mas ter o mesmo livro em outra edição: procura pelo título.
        if ((d.sinopse == null || d.capaGoogle == null || d.autores.isEmpty()) && temChave && google.aviso == null) {
            consultarGooglePorTitulo(d)?.let { achado ->
                if (d.sinopse == null && achado.sinopse != null) {
                    d = d.copy(sinopse = achado.sinopse, fonteSinopse = "Google Books (busca por título; confira a edição)")
                }
                if (d.autores.isEmpty() && achado.autores.isNotEmpty()) {
                    d = d.copy(
                        autores = achado.autores,
                        complementos = d.complementos + "Google Books por título (autor; confira a edição)",
                    )
                }
                if (d.capaGoogle == null && achado.capa != null) {
                    d = d.copy(capaGoogle = achado.capa, capaGoogleAproximada = true)
                }
            }
        }
        d = completarComOpenLibrary(d)
        if (d.sinopse == null && temChave && google.aviso == null) {
            d = d.copy(observacoes = d.observacoes + "Sinopse não encontrada: nem o Google Books nem o Open Library têm descrição deste livro.")
        }
        return d
    }

    private class AchadoGoogle(val sinopse: String?, val capa: String?, val autores: List<String>)

    /** Busca no Google por título e autor; só usa volumes com exatamente o mesmo título. */
    private suspend fun consultarGooglePorTitulo(d: DadosDoLivro): AchadoGoogle? {
        val chave = chaveGoogle?.trim().orEmpty()
        val titulo = d.titulo ?: return null
        if (chave.isEmpty()) return null
        val autor = d.autores.firstOrNull()?.let { "+inauthor:${codificarUrl(it)}" }.orEmpty()
        return try {
            val r = http.obter(
                "https://www.googleapis.com/books/v1/volumes?q=intitle:${codificarUrl(titulo)}$autor&maxResults=5&langRestrict=pt&key=$chave",
            )
            if (r.codigo != 200) return null
            val alvo = titulo.trim().lowercase()
            val iguais = ((json.parseToJsonElement(r.corpo).jsonObject["items"] as? JsonArray).orEmpty())
                .mapNotNull { (it as? JsonObject)?.get("volumeInfo") as? JsonObject }
                .filter { it.texto("title")?.trim()?.lowercase() == alvo }
            val sinopse = iguais.firstNotNullOfOrNull { limparHtml(it.texto("description")) }
            val capa = iguais.firstNotNullOfOrNull { v -> capaDe(v) }
            val autores = iguais.firstNotNullOfOrNull { v -> (v["authors"] as? JsonArray).textos().takeIf { it.isNotEmpty() } }.orEmpty()
            if (sinopse == null && capa == null && autores.isEmpty()) null else AchadoGoogle(sinopse, capa, autores)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Preenche só o que a fonte principal deixou vazio; anota de onde veio cada complemento. */
    private fun preencherVazios(d: DadosDoLivro, outro: DadosDoLivro, nome: String): DadosDoLivro {
        val campos = mutableListOf<String>()
        val autores = if (d.autores.isEmpty() && outro.autores.isNotEmpty()) outro.autores.also { campos += "autor" } else d.autores
        val editora = d.editora ?: outro.editora?.also { campos += "editora" }
        val ano = d.anoEdicao ?: outro.anoEdicao?.also { campos += "ano" }
        val paginas = d.paginas ?: outro.paginas?.also { campos += "páginas" }
        if (campos.isEmpty()) return d
        return d.copy(
            autores = autores, editora = editora, anoEdicao = ano, paginas = paginas,
            complementos = d.complementos + "$nome (${campos.joinToString(", ")})",
        )
    }

    private suspend fun completarComOpenLibrary(d: DadosDoLivro): DadosDoLivro {
        var atual = d
        try {
            var chave = d.chaveEdicao
            if (chave == null) {
                val url = "https://openlibrary.org/api/books?bibkeys=ISBN:${d.isbn}&format=json&jscmd=data"
                val r = http.obter(url)
                if (r.codigo == 200) {
                    val ol = interpretarOpenLibrary(r.corpo, d.isbn, url)
                    chave = ol?.chaveEdicao
                    if (ol != null) atual = preencherVazios(d, ol, "Open Library")
                }
            }
            if (chave == null || !chave.startsWith("/books/")) return atual
            val resp = http.obter("https://openlibrary.org$chave.json")
            if (resp.codigo != 200) return atual
            val edicao = json.parseToJsonElement(resp.corpo).jsonObject
            var sinopse = limparSinopse(descricaoDe(edicao["description"]))
            if (sinopse == null) {
                val obra = ((edicao["works"] as? JsonArray)?.firstOrNull() as? JsonObject)?.texto("key")
                if (obra != null && obra.startsWith("/works/")) {
                    val ro = http.obter("https://openlibrary.org$obra.json")
                    if (ro.codigo == 200) {
                        sinopse = limparSinopse(descricaoDe(json.parseToJsonElement(ro.corpo).jsonObject["description"]))
                    }
                }
            }
            val outrosTitulos = (edicao["other_titles"] as? JsonArray).textos()
                .filter { !it.equals(atual.titulo, ignoreCase = true) }.take(3)
            val usaOpenLibrary = atual.sinopse == null && sinopse != null
            return atual.copy(
                sinopse = atual.sinopse ?: sinopse,
                fonteSinopse = if (usaOpenLibrary) "Open Library" else atual.fonteSinopse,
                outrosTitulos = outrosTitulos,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return atual
        }
    }

    /**
     * Busca livros pelo título (para boxes, livros sem ISBN legível ou ISBN que nenhuma base conhece).
     * Usa o Google Books (se houver chave) e o Open Library. Devolve até ~16 sugestões, sem repetir.
     */
    suspend fun buscarPorTitulo(tituloBruto: String): List<CandidatoDeLivro> {
        val titulo = tituloBruto.trim()
        if (titulo.length < 3) return emptyList()
        // Título copiado da capa costuma ter ruído ("edição de colecionador", "volumes 1 a 5"): tenta também sem ele.
        val simples = titulo.lowercase()
            .replace(Regex("edi[cç][aã]o (de colecionador|especial|comemorativa)|cole[cç][aã]o"), " ")
            .replace(Regex("volumes?\\s*[\\d, ea]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val variantes = listOf(titulo, simples).filter { it.length >= 3 }.distinctBy { it.lowercase() }
        val todos = variantes.flatMap { buscarPorTituloExato(it) }
        return todos.distinctBy { it.titulo.lowercase() + "|" + it.autores.firstOrNull().orEmpty().lowercase() + "|" + it.fonte }
    }

    private suspend fun buscarPorTituloExato(titulo: String): List<CandidatoDeLivro> {
        val achados = mutableListOf<CandidatoDeLivro>()
        val chave = chaveGoogle?.trim().orEmpty()
        if (chave.isNotEmpty()) {
            try {
                val r = http.obter("https://www.googleapis.com/books/v1/volumes?q=${codificarUrl(titulo)}&maxResults=8&key=$chave")
                if (r.codigo == 200) {
                    ((json.parseToJsonElement(r.corpo).jsonObject["items"] as? JsonArray).orEmpty())
                        .mapNotNull { (it as? JsonObject)?.get("volumeInfo") as? JsonObject }
                        .forEach { v ->
                            val t = v.texto("title") ?: return@forEach
                            achados += CandidatoDeLivro(
                                titulo = listOfNotNull(t, v.texto("subtitle")).joinToString(": "),
                                autores = (v["authors"] as? JsonArray).textos(),
                                editora = v.texto("publisher"),
                                anoEdicao = anoDe(v.texto("publishedDate")),
                                paginas = v.inteiro("pageCount")?.takeIf { it > 0 },
                                sinopse = limparHtml(v.texto("description")),
                                capaUrl = capaDe(v),
                                fonte = "Google Books",
                            )
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // segue para a próxima fonte
            }
        }
        try {
            val r = http.obter(
                "https://openlibrary.org/search.json?title=${codificarUrl(titulo)}&limit=8" +
                    "&fields=title,author_name,publisher,number_of_pages_median,cover_i",
            )
            if (r.codigo == 200) {
                ((json.parseToJsonElement(r.corpo).jsonObject["docs"] as? JsonArray).orEmpty())
                    .mapNotNull { it as? JsonObject }
                    .forEach { d ->
                        val t = d.texto("title") ?: return@forEach
                        achados += CandidatoDeLivro(
                            titulo = t,
                            autores = (d["author_name"] as? JsonArray).textos(),
                            editora = (d["publisher"] as? JsonArray).textos().firstOrNull(),
                            anoEdicao = null, // o Open Library só informa o ano da 1ª publicação da obra, não o da edição
                            paginas = d.inteiro("number_of_pages_median")?.takeIf { it > 0 },
                            sinopse = null,
                            capaUrl = d.inteiro("cover_i")?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                            fonte = "Open Library",
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sem esta fonte
        }
        return achados.distinctBy { it.titulo.lowercase() + "|" + it.autores.firstOrNull().orEmpty().lowercase() + "|" + it.fonte }
    }

    /** Baixa a capa de uma sugestão (null se não for uma imagem válida). */
    suspend fun baixarCapa(url: String, fonte: String): CapaCandidata? {
        if (!url.startsWith("https://")) return null
        return try {
            val r = http.obterBytes(url)
            val ext = if (r.codigo == 200 && r.corpo.size > 2_000) extensaoDaImagem(r.corpo) else null
            if (ext == null) null else CapaCandidata(fonte, r.corpo, ext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reúne as capas que as fontes têm para este ISBN (sem repetir imagens idênticas), para a pessoa
     * escolher a do seu exemplar. A capa de um ISBN costuma ser uma só; mais de uma só aparece se
     * as fontes divergirem. Quem usa a capa como foto do anúncio é responsável pelo uso da imagem.
     */
    suspend fun buscarCapas(isbnBruto: String, dados: DadosDoLivro?): List<CapaCandidata> {
        val isbn = Isbn.paraIsbn13(isbnBruto) ?: return emptyList()
        val candidatas = listOfNotNull(
            dados?.capaUrl?.takeIf { it.startsWith("https://") }?.let { "BrasilAPI" to it },
            dados?.capaGoogle?.takeIf { it.startsWith("https://") }?.let {
                (if (dados.capaGoogleAproximada) "Google Books (mesmo título, pode ser outra edição)" else "Google Books") to it
            },
            "Open Library" to "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false",
        )
        val vistas = mutableListOf<CapaCandidata>()
        for ((fonte, url) in candidatas) {
            try {
                val r = http.obterBytes(url)
                val ext = if (r.codigo == 200 && r.corpo.size > 2_000) extensaoDaImagem(r.corpo) else null
                if (ext != null && vistas.none { it.bytes.contentEquals(r.corpo) }) {
                    vistas += CapaCandidata(fonte, r.corpo, ext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem capa desta fonte; segue para a próxima
            }
        }
        return vistas
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reconhece JPEG, PNG e WebP pelos primeiros bytes (descarta páginas de erro e imagens vazias). */
        internal fun extensaoDaImagem(b: ByteArray): String? = when {
            b.size > 12 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
            b.size > 12 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
            b.size > 12 && b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() -> "webp"
            else -> null
        }

        private fun JsonObject.texto(chave: String): String? =
            (this[chave] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject.inteiro(chave: String): Int? =
            (this[chave] as? JsonPrimitive)?.intOrNull

        private fun anoDe(texto: String?): Int? =
            texto?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }

        internal fun interpretarBrasilApi(corpo: String, isbn13: String, url: String): DadosDoLivro? = try {
            val raiz = json.parseToJsonElement(corpo).jsonObject
            val titulo = raiz.texto("title")
            if (titulo == null) null else DadosDoLivro(
                isbn = isbn13,
                titulo = titulo,
                subtitulo = raiz.texto("subtitle"),
                autores = (raiz["authors"] as? JsonArray).textos(),
                editora = raiz.texto("publisher"),
                anoEdicao = raiz.inteiro("year")?.takeIf { it > 0 },
                paginas = raiz.inteiro("page_count")?.takeIf { it > 0 },
                sinopse = raiz.texto("synopsis"),
                fonte = "BrasilAPI" + (raiz.texto("provider")?.let { " (base: $it)" } ?: ""),
                urlConsulta = url,
                consultadoEm = "",
                capaUrl = raiz.texto("cover_url"),
                formato = raiz.texto("format"),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        internal fun interpretarOpenLibrary(corpo: String, isbn13: String, url: String): DadosDoLivro? = try {
            val raiz = json.parseToJsonElement(corpo).jsonObject
            val livro = raiz["ISBN:$isbn13"] as? JsonObject
            val titulo = livro?.texto("title")
            if (livro == null || titulo == null) null else DadosDoLivro(
                isbn = isbn13,
                titulo = titulo,
                subtitulo = livro.texto("subtitle"),
                autores = (livro["authors"] as? JsonArray).nomes(),
                editora = (livro["publishers"] as? JsonArray).nomes().firstOrNull(),
                anoEdicao = anoDe(livro.texto("publish_date")),
                paginas = livro.inteiro("number_of_pages")?.takeIf { it > 0 },
                sinopse = null,
                fonte = "Open Library",
                urlConsulta = url,
                consultadoEm = "",
                chaveEdicao = livro.texto("key"),
                observacoes = listOfNotNull(livro.texto("by_statement")?.let { "Créditos na fonte: $it" }),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        internal fun interpretarGoogle(corpo: String, isbn13: String, url: String): DadosDoLivro? = try {
            val volume = ((json.parseToJsonElement(corpo).jsonObject["items"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("volumeInfo") as? JsonObject
            val titulo = volume?.texto("title")
            if (volume == null || titulo == null) null else DadosDoLivro(
                isbn = isbn13,
                titulo = titulo,
                subtitulo = volume.texto("subtitle"),
                autores = (volume["authors"] as? JsonArray).textos(),
                editora = volume.texto("publisher"),
                anoEdicao = anoDe(volume.texto("publishedDate")),
                paginas = volume.inteiro("pageCount")?.takeIf { it > 0 },
                sinopse = limparHtml(volume.texto("description")),
                fonte = "Google Books",
                urlConsulta = url,
                consultadoEm = "",
                capaGoogle = capaDe(volume),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        private fun capaDe(volume: JsonObject): String? =
            ((volume["imageLinks"] as? JsonObject)?.let { it.texto("thumbnail") ?: it.texto("smallThumbnail") })
                ?.replace("http://", "https://")?.replace("&edge=curl", "")

        /** Codifica texto para uso em URL (percent-encoding de UTF-8). */
        internal fun codificarUrl(texto: String): String = buildString {
            for (b in texto.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-._~") append(ch)
                else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }

        /** A descrição do Google pode vir com <br>, <p>, <b> e entidades HTML. */
        internal fun limparHtml(texto: String?): String? = texto
            ?.replace(Regex("(?i)<br\\s*/?>|</?p(\\s[^>]*)?>"), "\n")
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")
            ?.replace("&lt;", "<")?.replace("&gt;", ">")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()?.takeIf { it.isNotEmpty() }

        /** O Open Library guarda a descrição como texto simples ou como {"value": "..."}. */
        private fun descricaoDe(e: JsonElement?): String? = when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> e.texto("value")
            else -> null
        }

        /** Tira aspas de catálogo e a assinatura final tipo `--Slipcase.` ou `--publisher.`. */
        internal fun limparSinopse(texto: String?): String? =
            texto?.trim()?.removeSurrounding("\"")?.replace(Regex("\"?\\s*--[A-Za-z .]{2,40}\\.?\\s*$"), "")
                ?.trim()?.trim('"')?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonArray?.textos(): List<String> =
            this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }.orEmpty()

        private fun JsonArray?.nomes(): List<String> =
            this?.mapNotNull { (it as? JsonObject)?.texto("name") }.orEmpty()
    }
}
