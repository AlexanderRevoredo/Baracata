package br.app.vendas.dados

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.app.vendas.db.BancoDeVendas
import br.app.vendas.dominio.Estado
import br.app.vendas.dominio.Foto
import br.app.vendas.dominio.Item
import br.app.vendas.dominio.Loja
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {

    private fun repositorio() = RepositorioDeItens(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = BancoDeVendas.Schema))
    private fun pasta() = Files.createTempDirectory("backup-teste").toFile().also { it.deleteOnExit() }

    private class Aparelho {
        val repo = RepositorioDeItens(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = BancoDeVendas.Schema))
        val arquivos = ArquivosDeFotos(Files.createTempDirectory("fotos-backup").toFile().absolutePath)
        val backup = ServicoDeBackup(repo, arquivos)
    }

    private fun zipDe(escrever: (ZipEscritor) -> Unit): File {
        val arquivo = File(pasta(), "b.zip")
        ZipEscritor(arquivo.outputStream()).also(escrever)
        return arquivo
    }

    @Test
    fun exportarERestaurarEmOutroAparelhoPreservaTudo() = runBlocking {
        val origem = Aparelho()
        origem.arquivos.gravar("f1.jpg", byteArrayOf(1, 2, 3))
        origem.arquivos.gravar("f2.png", byteArrayOf(9, 8))
        origem.repo.salvar(
            Item(
                "a", "O poço e o pêndulo", Estado.USADO,
                mapOf("Autor" to "Poe", "_fonte" to "BrasilAPI"),
                listOf(Foto("f1", "f1.jpg", 3), Foto("f2", "f2.png", 2)),
            ),
        )
        origem.repo.definirLojas("a", setOf(Loja.OLX, Loja.AMAZON))
        origem.repo.salvar(Item("b", "Outro livro"))

        val arquivo = File(pasta(), "backup.zip")
        val resumo = origem.backup.exportar(ZipEscritor(arquivo.outputStream()))
        assertEquals(2, resumo.livros)
        assertEquals(2, resumo.fotos)

        val destino = Aparelho()
        val r = destino.backup.restaurar(ZipLeitor(arquivo))
        assertEquals(2, r.adicionados)
        assertEquals(0, r.jaExistiam)

        val itens = destino.repo.listar().associateBy { it.id }
        val a = itens.getValue("a")
        assertEquals("O poço e o pêndulo", a.titulo)
        assertEquals(Estado.USADO, a.estado)
        assertEquals(mapOf("Autor" to "Poe", "_fonte" to "BrasilAPI"), a.atributos)
        assertEquals(listOf("f1", "f2"), a.fotos.map { it.id })
        assertEquals(setOf(Loja.OLX, Loja.AMAZON), a.lojas)
        assertContentEquals(byteArrayOf(1, 2, 3), destino.arquivos.ler("f1.jpg"))
        assertEquals(listOf("b", "a"), destino.repo.listar().map { it.id }, "mantém a ordem: o mais recente primeiro")
    }

    @Test
    fun restaurarNaoSobrescreveLivroQueJaExiste() = runBlocking {
        val origem = Aparelho()
        origem.repo.salvar(Item("a", "Título do backup"))
        val arquivo = File(pasta(), "backup.zip")
        origem.backup.exportar(ZipEscritor(arquivo.outputStream()))

        val destino = Aparelho()
        destino.repo.salvar(Item("a", "Título editado depois"))
        val r = destino.backup.restaurar(ZipLeitor(arquivo))

        assertEquals(0, r.adicionados)
        assertEquals(1, r.jaExistiam)
        assertEquals("Título editado depois", destino.repo.listar().single().titulo)
    }

    @Test
    fun fotoAusenteNoArquivoEContadaESemFotoOLivroEntra() = runBlocking {
        val origem = Aparelho()
        origem.repo.salvar(Item("a", "Livro", fotos = listOf(Foto("f1", "f1.jpg", 3))))
        // não existe o arquivo da foto no aparelho de origem
        val arquivo = File(pasta(), "backup.zip")
        origem.backup.exportar(ZipEscritor(arquivo.outputStream()))

        val destino = Aparelho()
        val r = destino.backup.restaurar(ZipLeitor(arquivo))
        assertEquals(1, r.adicionados)
        assertEquals(1, r.fotosAusentes)
        assertTrue(destino.repo.listar().single().fotos.isEmpty())
    }

    @Test
    fun arquivoQueNaoEBackupDoBaracataEhRecusadoSemMexerNoBanco() = runBlocking {
        val destino = Aparelho()
        destino.repo.salvar(Item("a", "Existente"))

        val semDados = zipDe { it.adicionar("qualquer.txt", byteArrayOf(1)); it.fechar() }
        assertFailsWith<IllegalArgumentException> { destino.backup.restaurar(ZipLeitor(semDados)) }

        val outroApp = zipDe { it.adicionar("dados.json", """{"app":"Outro","versao":1,"itens":[]}""".toByteArray()); it.fechar() }
        assertFailsWith<IllegalArgumentException> { destino.backup.restaurar(ZipLeitor(outroApp)) }

        val futuro = zipDe { it.adicionar("dados.json", """{"app":"Baracata","versao":99,"itens":[]}""".toByteArray()); it.fechar() }
        assertFailsWith<IllegalArgumentException> { destino.backup.restaurar(ZipLeitor(futuro)) }

        assertEquals(listOf("a"), destino.repo.listar().map { it.id })
    }

    @Test
    fun chaveDoGoogleNaoVaiParaOBackup() = runBlocking {
        val origem = Aparelho()
        origem.repo.salvarConfiguracao("chave_google_books", "SEGREDO123")
        origem.repo.salvar(Item("a", "Livro"))
        val arquivo = File(pasta(), "backup.zip")
        origem.backup.exportar(ZipEscritor(arquivo.outputStream()))
        assertTrue(!arquivo.readBytes().decodeToString(throwOnInvalidSequence = false).contains("SEGREDO123"))
    }
}
