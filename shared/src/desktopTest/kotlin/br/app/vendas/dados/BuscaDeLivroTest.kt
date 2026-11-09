package br.app.vendas.dados

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuscaDeLivroTest {

    private class HttpFalso(private val respostas: Map<String, RespostaHttp>, private val cai: Boolean = false) : ClienteHttp {
        val pedidos = mutableListOf<String>()
        override suspend fun obter(url: String): RespostaHttp {
            pedidos += url
            if (cai) throw IOException("sem rede")
            return respostas.entries.firstOrNull { url.startsWith(it.key) }?.value ?: RespostaHttp(404, "")
        }
    }

    private val brasilApi = """
        {"isbn":"9786586096767","title":"O poço e o pêndulo","subtitle":null,
         "authors":["Edgar Allan Poe","Adriana Buzzetti"],"publisher":"Lafonte","synopsis":null,
         "dimensions":{"width":14,"height":21,"unit":"CENTIMETER"},"year":2020,"format":"PHYSICAL",
         "page_count":160,"subjects":["terror"],"location":"São Paulo, SP","retail_price":null,
         "cover_url":null,"provider":"cbl"}
    """.trimIndent()

    private val openLibrary = """
        {"ISBN:9780306406157": {"title": "Error-correction coding for digital communications",
         "authors": [{"name": "George C. Clark"}, {"name": "J. Bibb Cain"}],
         "number_of_pages": 422, "publishers": [{"name": "Plenum Press"}], "publish_date": "March 3, 1981"}}
    """.trimIndent()

    private fun busca(http: ClienteHttp) = BuscaDeLivro(http) { "2026-11-08" }

    @Test
    fun brasilApiPreencheOsCamposEGuardaAFonte() = runBlocking {
        val http = HttpFalso(mapOf("https://brasilapi.com.br" to RespostaHttp(200, brasilApi)))
        val r = busca(http).buscar("978-65-86096-76-7")

        val dados = assertIs<ResultadoBusca.Encontrado>(r).dados
        assertEquals("O poço e o pêndulo", dados.titulo)
        assertEquals(listOf("Edgar Allan Poe", "Adriana Buzzetti"), dados.autores)
        assertEquals("Lafonte", dados.editora)
        assertEquals(2020, dados.anoEdicao)
        assertEquals(160, dados.paginas)
        assertNull(dados.sinopse)
        assertEquals("BrasilAPI (base: cbl)", dados.fonte)
        assertEquals("2026-11-08", dados.consultadoEm)
        assertTrue(dados.descricaoDaFonte().contains("consultado em 2026-11-08"))
        assertTrue(http.pedidos.first().startsWith("https://brasilapi.com.br"))
    }

    @Test
    fun sinopseVemDoOpenLibraryQuandoAFontePrincipalNaoTem() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, brasilApi),
                "https://openlibrary.org/api/books" to RespostaHttp(
                    200, """{"ISBN:9786586096767": {"title": "x", "key": "/books/OL1M"}}""",
                ),
                "https://openlibrary.org/books/OL1M.json" to RespostaHttp(
                    200, """{"works": [{"key": "/works/OL9W"}]}""",
                ),
                "https://openlibrary.org/works/OL9W.json" to RespostaHttp(
                    200, """{"description": {"type": "/type/text", "value": "\"Um conto de terror.\"--Slipcase."}}""",
                ),
            ),
        )
        val dados = assertIs<ResultadoBusca.Encontrado>(busca(http).buscar("9786586096767")).dados
        assertEquals("Um conto de terror.", dados.sinopse)
        assertEquals("BrasilAPI (base: cbl)", dados.fonte)
    }

    @Test
    fun falhaAoCompletarNaoEstragaOsDadosPrincipais() = runBlocking {
        val http = HttpFalso(mapOf("https://brasilapi.com.br" to RespostaHttp(200, brasilApi)))
        val dados = assertIs<ResultadoBusca.Encontrado>(busca(http).buscar("9786586096767")).dados
        assertEquals("O poço e o pêndulo", dados.titulo)
        assertNull(dados.sinopse)
    }

    @Test
    fun limpaAssinaturaDeCatalogoDaSinopse() {
        assertEquals("Texto bom.", BuscaDeLivro.limparSinopse("\"Texto bom.\"--Publisher."))
        assertNull(BuscaDeLivro.limparSinopse("   "))
    }

    @Test
    fun usaOpenLibraryQuandoABrasilApiNaoConheceOIsbn() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(400, """{"message":"ISBN inválido"}"""),
                "https://openlibrary.org" to RespostaHttp(200, openLibrary),
            ),
        )
        val dados = assertIs<ResultadoBusca.Encontrado>(busca(http).buscar("0306406152")).dados
        assertEquals("Open Library", dados.fonte)
        assertEquals(1981, dados.anoEdicao)
        assertEquals("Plenum Press", dados.editora)
        assertTrue(http.pedidos.size >= 2, "BrasilAPI e Open Library foram consultadas")
    }

    @Test
    fun naoEncontradoQuandoNenhumaFonteConhece() = runBlocking {
        val http = HttpFalso(mapOf("https://openlibrary.org" to RespostaHttp(200, "{}")))
        assertEquals(ResultadoBusca.NaoEncontrado, busca(http).buscar("9786586096767"))
    }

    @Test
    fun semRedeNaoViraNaoEncontrado() = runBlocking {
        val r = busca(HttpFalso(emptyMap(), cai = true)).buscar("9786586096767")
        assertIs<ResultadoBusca.SemConexao>(r)
        Unit
    }

    @Test
    fun isbnInvalidoNemConsultaAInternet() = runBlocking {
        val http = HttpFalso(emptyMap())
        assertEquals(ResultadoBusca.NaoEncontrado, busca(http).buscar("123"))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test
    fun respostaQuebradaNaoDerrubaABusca() = runBlocking {
        val http = HttpFalso(mapOf("https://brasilapi.com.br" to RespostaHttp(200, "<html>erro</html>")))
        assertEquals(ResultadoBusca.NaoEncontrado, busca(http).buscar("9786586096767"))
    }
}
