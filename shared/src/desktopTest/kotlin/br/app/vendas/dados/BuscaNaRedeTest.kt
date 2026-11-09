package br.app.vendas.dados

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Consulta as fontes de verdade pela internet. Só roda com a variável de ambiente TESTE_REDE=1. */
class BuscaNaRedeTest {
    private val ligado = System.getenv("TESTE_REDE") == "1"

    @Test
    fun estojoSTemSinopseCapaEOutroTitulo() = runBlocking {
        if (!ligado) return@runBlocking
        val busca = BuscaDeLivro()
        val dados = assertIs<ResultadoBusca.Encontrado>(busca.buscar("9780316201643")).dados
        println("SINOPSE: ${dados.sinopse}")
        println("OUTROS: ${dados.outrosTitulos} CREDITOS: ${dados.observacoes}")
        assertNotNull(dados.sinopse)
        assertTrue("Ship of Theseus" in dados.outrosTitulos)
        val capas = busca.buscarCapas("9780316201643", dados)
        println("CAPAS: ${capas.map { it.fonte to it.bytes.size }}")
        assertEquals(1, capas.size)
    }
}
