package br.app.vendas.dados

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.app.vendas.db.BancoDeVendas
import br.app.vendas.dominio.Foto
import br.app.vendas.dominio.Item
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FotosTest {

    private class PlataformaFalsa(val escolhas: List<ImagemEscolhida> = emptyList()) : PlataformaDeFotos {
        var exportado: Pair<String, List<ImagemParaExportar>>? = null
        override suspend fun escolher() = escolhas
        override suspend fun exportar(nomeDaPasta: String, imagens: List<ImagemParaExportar>): String {
            exportado = nomeDaPasta to imagens
            return "ok"
        }
    }

    private fun pasta() = Files.createTempDirectory("fotos-teste").toFile().also { it.deleteOnExit() }

    private var contador = 0
    private fun servico(plataforma: PlataformaDeFotos, arquivos: ArquivosDeFotos = ArquivosDeFotos(pasta().absolutePath)) =
        ServicoDeFotos(arquivos, plataforma) { "id${++contador}" } to arquivos

    private fun repositorio() = RepositorioDeItens(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = BancoDeVendas.Schema),
    )

    @Test
    fun fotosFicamNaOrdemSalvaEPodemSerReordenadas() = runBlocking {
        val repo = repositorio()
        val a = Foto("a", "a.jpg", 1)
        val b = Foto("b", "b.png", 2)
        val c = Foto("c", "c.jpg", 3)
        repo.salvar(Item("1", "Item", fotos = listOf(a, b, c)))
        assertEquals(listOf(a, b, c), repo.listar().single().fotos)

        repo.salvar(Item("1", "Item", fotos = listOf(c, a)))
        assertEquals(listOf(c, a), repo.listar().single().fotos)
    }

    @Test
    fun fotosNaoVazamEntreItensEExcluirRemoveOsRegistros() = runBlocking {
        val repo = repositorio()
        repo.salvar(Item("1", "A", fotos = listOf(Foto("a", "a.jpg", 1))))
        repo.salvar(Item("2", "B", fotos = listOf(Foto("b", "b.jpg", 1))))
        repo.excluir("1")
        val itens = repo.listar()
        assertEquals(listOf("2"), itens.map { it.id })
        assertEquals(listOf("b"), itens.single().fotos.map { it.id })

        repo.apagarTudo()
        assertTrue(repo.listar().isEmpty())
    }

    @Test
    fun adicionarCopiaOsBytesParaAPastaPrivadaComNomeSeguro() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val (servico, arquivos) = servico(PlataformaFalsa(listOf(ImagemEscolhida(bytes, "JPEG"), ImagemEscolhida(bytes, "../x"))))
        val novas = servico.adicionar()

        assertEquals(2, novas.size)
        assertTrue(novas[0].arquivo.endsWith(".jpg"))
        assertTrue(novas[1].arquivo.endsWith(".jpg"), "extensão estranha vira jpg")
        assertContentEquals(bytes, servico.ler(novas[0]))
        assertEquals(4L, novas[0].tamanho)
        assertEquals(2, arquivos.nomes().size)
    }

    @Test
    fun descartarApagaOArquivoELimpezaRemoveSoOsOrfaos() = runBlocking {
        val bytes = byteArrayOf(9)
        val (servico, arquivos) = servico(PlataformaFalsa(List(3) { ImagemEscolhida(bytes, "jpg") }))
        val (f1, f2, f3) = servico.adicionar()

        servico.descartar(listOf(f1))
        assertNull(servico.ler(f1))

        servico.limparOrfaos(setOf(f2.arquivo))
        assertEquals(listOf(f2.arquivo), arquivos.nomes())
        assertNull(servico.ler(f3))

        servico.apagarTudo()
        assertTrue(arquivos.nomes().isEmpty())
    }

    @Test
    fun exportarNumeraNaOrdemDoItemEPulaArquivoAusente() = runBlocking {
        val plataforma = PlataformaFalsa(listOf(ImagemEscolhida(byteArrayOf(1), "png"), ImagemEscolhida(byteArrayOf(2), "jpg")))
        val (servico, arquivos) = servico(plataforma)
        val (a, b) = servico.adicionar()
        val fantasma = Foto("z", "z.jpg", 1)

        servico.exportar("O poço: e/o pêndulo?", listOf(b, fantasma, a))

        val (pasta, imagens) = plataforma.exportado!!
        assertEquals("O poço e o pêndulo", pasta)
        assertEquals(listOf("01.jpg", "02.png"), imagens.map { it.nome })
        assertContentEquals(byteArrayOf(2), imagens[0].bytes)
        assertEquals(2, arquivos.nomes().size)
    }

    @Test
    fun nomeDePastaSempreValido() {
        assertEquals("item", ServicoDeFotos.nomeDaPasta("  ???  "))
        assertEquals("a b", ServicoDeFotos.nomeDaPasta("a\\b"))
        assertEquals(60, ServicoDeFotos.nomeDaPasta("x".repeat(100)).length)
    }
}
