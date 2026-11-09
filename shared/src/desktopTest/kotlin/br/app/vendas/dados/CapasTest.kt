package br.app.vendas.dados

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapasTest {

    private fun jpeg(marca: Byte, tamanho: Int = 3000) =
        ByteArray(tamanho) { 1 }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = marca }

    private class HttpFalso(val imagens: Map<String, RespostaBinaria>) : ClienteHttp {
        override suspend fun obter(url: String) = RespostaHttp(404, "")
        override suspend fun obterBytes(url: String) =
            imagens.entries.firstOrNull { url.startsWith(it.key) }?.value ?: RespostaBinaria(404, ByteArray(0))
    }

    private val dados = DadosDoLivro(
        "9786586096767", "T", null, emptyList(), null, null, null, null, "BrasilAPI", "u", "2026-11-09",
        capaUrl = "https://exemplo.com/capa.jpg",
    )

    @Test
    fun juntaCapasDiferentesDeTodasAsFontes() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "https://exemplo.com" to RespostaBinaria(200, jpeg(1)),
                "https://covers.openlibrary.org" to RespostaBinaria(200, jpeg(2)),
            ),
        )
        val capas = BuscaDeLivro(http) { "x" }.buscarCapas("9786586096767", dados)
        assertEquals(listOf("BrasilAPI", "Open Library"), capas.map { it.fonte })
        assertEquals("jpg", capas[0].extensao)
    }

    @Test
    fun imagensIdenticasViramUmaSoEErrosSaoIgnorados() = runBlocking {
        val igual = jpeg(7)
        val http = HttpFalso(
            mapOf(
                "https://exemplo.com" to RespostaBinaria(200, igual),
                "https://covers.openlibrary.org" to RespostaBinaria(200, igual.copyOf()),
            ),
        )
        assertEquals(1, BuscaDeLivro(http) { "x" }.buscarCapas("9786586096767", dados).size)

        val semNada = HttpFalso(mapOf("https://covers.openlibrary.org" to RespostaBinaria(200, "<html>erro</html>".toByteArray())))
        assertTrue(BuscaDeLivro(semNada) { "x" }.buscarCapas("9786586096767", null).isEmpty())
    }

    @Test
    fun imagemMinuscuaOuLinkNaoHttpsNaoViramCapa() = runBlocking {
        val http = HttpFalso(
            mapOf(
                "http://inseguro" to RespostaBinaria(200, jpeg(1)),
                "https://covers.openlibrary.org" to RespostaBinaria(200, jpeg(2, tamanho = 43)),
            ),
        )
        val d = dados.copy(capaUrl = "http://inseguro/capa.jpg")
        assertTrue(BuscaDeLivro(http) { "x" }.buscarCapas("9786586096767", d).isEmpty())
    }
}
