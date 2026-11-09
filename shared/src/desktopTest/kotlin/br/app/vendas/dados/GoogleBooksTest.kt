package br.app.vendas.dados

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleBooksTest {

    private val google = """
        {"items":[{"volumeInfo":{"title":"O poço e o pêndulo","authors":["Edgar Allan Poe"],
         "publisher":"Lafonte","publishedDate":"2020-03-01","pageCount":160,
         "description":"Um conto.<br>Terror &amp; suspense.<p>Fim.</p>",
         "imageLinks":{"thumbnail":"http://books.google.com/capa.jpg?id=1&edge=curl"}}}]}
    """.trimIndent()

    private val brasilApi = """{"title":"O poço e o pêndulo","authors":["Poe"],"publisher":"Lafonte","year":2020,"page_count":160,"provider":"cbl"}"""

    private class HttpFalso(val respostas: Map<String, RespostaHttp>) : ClienteHttp {
        val pedidos = mutableListOf<String>()
        override suspend fun obter(url: String): RespostaHttp {
            pedidos += url
            return respostas.entries.firstOrNull { url.startsWith(it.key) }?.value ?: RespostaHttp(404, "")
        }
    }

    @Test
    fun interpretaGoogleELimpaHtmlDaSinopse() {
        val d = BuscaDeLivro.interpretarGoogle(google, "9786586096767", "u")!!
        assertEquals("Lafonte", d.editora)
        assertEquals(2020, d.anoEdicao)
        assertEquals(160, d.paginas)
        assertEquals("Um conto.\nTerror & suspense.\nFim.", d.sinopse)
        assertEquals("https://books.google.com/capa.jpg?id=1", d.capaGoogle)
    }

    @Test
    fun comChaveCompletaASinopseSemVazarAChaveNaFonte() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, brasilApi),
                "https://www.googleapis.com" to RespostaHttp(200, google),
            ),
        )
        val busca = BuscaDeLivro(http) { "2026-11-09" }.apply { chaveGoogle = "SEGREDO" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9786586096767")).dados
        assertTrue(dados.sinopse!!.startsWith("Um conto."))
        assertEquals("https://books.google.com/capa.jpg?id=1", dados.capaGoogle)
        assertTrue("SEGREDO" !in dados.descricaoDaFonte(), "a chave não pode ir para o texto guardado")
        assertTrue(http.pedidos.any { "key=SEGREDO" in it })
    }

    @Test
    fun seOIsbnNaoTemDescricaoProcuraPeloTituloEDizDeOndeVeio() = runBlocking {
        val semItens = """{"totalItems":0}"""
        val porTitulo = """{"items":[
            {"volumeInfo":{"title":"Outro livro","description":"Errado"}},
            {"volumeInfo":{"title":"O Poço e o Pêndulo","description":"Conto de Poe."}}]}"""
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, brasilApi),
                "https://www.googleapis.com/books/v1/volumes?q=isbn:" to RespostaHttp(200, semItens),
                "https://www.googleapis.com/books/v1/volumes?q=intitle:" to RespostaHttp(200, porTitulo),
            ),
        )
        val busca = BuscaDeLivro(http) { "2026-11-09" }.apply { chaveGoogle = "k" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9786586096767")).dados
        assertEquals("Conto de Poe.", dados.sinopse)
        assertTrue(dados.descricaoDaFonte().contains("busca por título"))
        assertTrue(http.pedidos.any { "intitle:O%20po%C3%A7o%20e%20o%20p%C3%AAndulo" in it })
    }

    @Test
    fun capaVemDaBuscaPorTituloQuandoOIsbnNaoTemEViraCandidataAproximada() = runBlocking {
        val porTitulo = """{"items":[{"volumeInfo":{"title":"O poço e o pêndulo",
            "imageLinks":{"thumbnail":"http://books.google.com/capa2.jpg?id=2"}}}]}"""
        val jpeg = ByteArray(3000) { 1 }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        val http = object : ClienteHttp {
            override suspend fun obter(url: String) = when {
                url.startsWith("https://brasilapi.com.br") -> RespostaHttp(200, brasilApi)
                url.contains("q=isbn:") -> RespostaHttp(200, """{"totalItems":0}""")
                url.contains("q=intitle:") -> RespostaHttp(200, porTitulo)
                else -> RespostaHttp(404, "")
            }
            override suspend fun obterBytes(url: String) =
                if (url.startsWith("https://books.google.com/capa2.jpg")) RespostaBinaria(200, jpeg) else RespostaBinaria(404, ByteArray(0))
        }
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "k" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9786586096767")).dados
        val capas = busca.buscarCapas("9786586096767", dados)
        assertEquals(1, capas.size)
        assertTrue(capas.single().fonte.contains("pode ser outra edição"))
    }

    @Test
    fun semSinopseEmNenhumaFonteAvisaEmVezDeFicarCalado() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, brasilApi),
                "https://www.googleapis.com" to RespostaHttp(200, """{"totalItems":0}"""),
            ),
        )
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "k" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9786586096767")).dados
        assertNull(dados.sinopse)
        assertTrue(dados.observacoes.any { "Sinopse não encontrada" in it })
    }

    @Test
    fun semChaveOGoogleNemEConsultado() = runBlocking {
        val http = HttpFalso(mapOf("https://brasilapi.com.br" to RespostaHttp(200, brasilApi)))
        val dados = assertIs<ResultadoBusca.Encontrado>(BuscaDeLivro(http) { "x" }.buscar("9786586096767")).dados
        assertNull(dados.sinopse)
        assertTrue(http.pedidos.none { "googleapis" in it })
    }

    @Test
    fun chaveRecusadaViraAvisoEOsDadosPrincipaisSobrevivem() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, brasilApi),
                "https://www.googleapis.com" to RespostaHttp(403, "{}"),
            ),
        )
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "ruim" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9786586096767")).dados
        assertEquals("O poço e o pêndulo", dados.titulo)
        assertTrue(dados.observacoes.any { "recusou a chave" in it })
    }
}

class CompletarDadosTest {
    private class HttpFalso(val respostas: Map<String, RespostaHttp>) : ClienteHttp {
        override suspend fun obter(url: String): RespostaHttp =
            respostas.entries.firstOrNull { url.startsWith(it.key) }?.value ?: RespostaHttp(404, "")
    }

    private val pobre = """{"title":"Paralelos","authors":[],"publisher":"Geração Editorial","page_count":0,"format":"DIGITAL","provider":"cbl"}"""

    @Test
    fun googleCompletaAutorAnoEPaginasQueABaseNacionalNaoTem() = runBlocking {
        val google = """{"items":[{"volumeInfo":{"title":"Paralelos","authors":["Fulano de Tal"],"publishedDate":"2013","pageCount":220}}]}"""
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, pobre),
                "https://www.googleapis.com" to RespostaHttp(200, google),
            ),
        )
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "k" }
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9788581301082")).dados
        assertEquals(listOf("Fulano de Tal"), dados.autores)
        assertEquals(2013, dados.anoEdicao)
        assertEquals(220, dados.paginas)
        assertEquals("Geração Editorial", dados.editora)
        assertTrue(dados.descricaoDaFonte().contains("completado com: Google Books (autor, ano, páginas)"))
    }


    @Test
    fun openLibraryTambemCompletaOQueFalta() = runBlocking {
        val ol = """{"ISBN:9788581301082": {"title": "Paralelos", "authors": [{"name": "Fulano"}], "number_of_pages": 99, "key": "/books/OL5M"}}"""
        val http = HttpFalso(
            mapOf(
                "https://brasilapi.com.br" to RespostaHttp(200, pobre),
                "https://openlibrary.org/api/books" to RespostaHttp(200, ol),
            ),
        )
        val dados = assertIs<ResultadoBusca.Encontrado>(BuscaDeLivro(http) { "x" }.buscar("9788581301082")).dados
        assertEquals(listOf("Fulano"), dados.autores)
        assertEquals(99, dados.paginas)
    }
}

class BuscaPorTituloTest {
    private class HttpFalso(val respostas: Map<String, RespostaHttp>) : ClienteHttp {
        val pedidos = mutableListOf<String>()
        override suspend fun obter(url: String): RespostaHttp {
            pedidos += url
            return respostas.entries.firstOrNull { url.startsWith(it.key) }?.value ?: RespostaHttp(404, "")
        }
    }

    private val google = """{"items":[{"volumeInfo":{"title":"As Crônicas de Gelo e Fogo","subtitle":"Box","authors":["George R. R. Martin"],
        "publisher":"Leya","publishedDate":"2019-05-01","pageCount":4000,"description":"<p>Cinco volumes.</p>",
        "imageLinks":{"thumbnail":"http://books.google.com/c.jpg"}}}]}"""
    private val ol = """{"docs":[{"title":"A Game of Thrones","author_name":["George R. R. Martin"],"publisher":["Bantam"],
        "number_of_pages_median":694,"cover_i":123}]}"""

    @Test
    fun juntaSugestoesDoGoogleEDoOpenLibrary() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://www.googleapis.com" to RespostaHttp(200, google),
                "https://openlibrary.org/search.json" to RespostaHttp(200, ol),
            ),
        )
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "k" }
        val c = busca.buscarPorTitulo("cronicas de gelo e fogo box")
        assertEquals(listOf("Google Books", "Open Library"), c.map { it.fonte })
        assertEquals("As Crônicas de Gelo e Fogo: Box", c[0].titulo)
        assertEquals("Cinco volumes.", c[0].sinopse)
        assertEquals(2019, c[0].anoEdicao)
        assertNull(c[1].anoEdicao)
        assertEquals("https://covers.openlibrary.org/b/id/123-L.jpg", c[1].capaUrl)
    }

    @Test
    fun semChaveSoConsultaOOpenLibraryETituloCurtoNaoConsultaNada() = runBlocking {
        val http = HttpFalso(mapOf("https://openlibrary.org/search.json" to RespostaHttp(200, ol)))
        val busca = BuscaDeLivro(http) { "x" }
        assertEquals(1, busca.buscarPorTitulo("game of thrones").size)
        assertTrue(http.pedidos.none { "googleapis" in it })
        http.pedidos.clear()
        assertTrue(busca.buscarPorTitulo("ab").isEmpty())
        assertTrue(http.pedidos.isEmpty())
    }

    @Test
    fun falhaEmUmaFonteNaoImpedeAOutra() = runBlocking {
        val http = HttpFalso(mapOf("https://www.googleapis.com" to RespostaHttp(500, ""), "https://openlibrary.org/search.json" to RespostaHttp(200, ol)))
        val busca = BuscaDeLivro(http) { "x" }.apply { chaveGoogle = "k" }
        assertEquals(listOf("Open Library"), busca.buscarPorTitulo("game of thrones").map { it.fonte })
    }

    @Test
    fun tituloCopiadoDaCapaTambemETentadoSemORuido() = runBlocking {
        val http = HttpFalso(mapOf("https://openlibrary.org/search.json" to RespostaHttp(200, ol)))
        val busca = BuscaDeLivro(http) { "x" }
        busca.buscarPorTitulo("edição de colecionador as cronicas de gelo e fogo volumes 1, 2, 3, 4 e 5")
        val consultas = http.pedidos.map { it.substringAfter("title=").substringBefore("&") }
        assertEquals(2, consultas.size)
        assertTrue(consultas.any { "colecionador" in it })
        assertTrue(consultas.any { "colecionador" !in it && "volumes" !in it && "cronicas" in it })
    }
}
