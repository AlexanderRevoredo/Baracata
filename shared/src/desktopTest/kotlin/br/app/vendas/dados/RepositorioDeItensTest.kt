package br.app.vendas.dados

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.app.vendas.db.BancoDeVendas
import br.app.vendas.dominio.Estado
import br.app.vendas.dominio.Item
import br.app.vendas.dominio.Loja
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositorioDeItensTest {

    private fun repositorio(relogio: () -> Long = { 1000L }): RepositorioDeItens {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = BancoDeVendas.Schema)
        return RepositorioDeItens(driver, relogio)
    }

    private fun item(id: String, titulo: String = "PS4") = Item(
        id = id,
        titulo = titulo,
        estado = Estado.SEMINOVO,
        atributos = mapOf("Autor" to "Poe", "Editora" to "Lafonte"),
    )

    @Test
    fun salvaELeDeVolta() = runBlocking {
        val repo = repositorio()
        repo.salvar(item("a"))
        val lidos = repo.listar()
        assertEquals(1, lidos.size)
        assertEquals(item("a"), lidos.single())
    }

    @Test
    fun editarAtualizaSemDuplicarEAtributosAntigosSaem() = runBlocking {
        val repo = repositorio()
        repo.salvar(item("a"))
        repo.salvar(item("a", "PS4 Slim").copy(atributos = mapOf("Autor" to "Outro")))
        val lidos = repo.listar()
        assertEquals(1, lidos.size)
        assertEquals("PS4 Slim", lidos.single().titulo)
        assertEquals(mapOf("Autor" to "Outro"), lidos.single().atributos)
    }

    @Test
    fun revisaoSempreAumentaMesmoComRelogioParado() = runBlocking {
        val repo = repositorio(relogio = { 1000L })
        val r1 = repo.salvar(item("a"))
        val r2 = repo.salvar(item("a", "Outro título"))
        assertTrue(r2 > r1)
    }

    @Test
    fun excluirRemoveItemEAtributos() = runBlocking {
        val repo = repositorio()
        repo.salvar(item("a"))
        repo.salvar(item("b"))
        repo.excluir("a")
        assertEquals(listOf("b"), repo.listar().map { it.id })
    }

    @Test
    fun apagarTudoEsvaziaOBanco() = runBlocking {
        val repo = repositorio()
        repo.salvar(item("a"))
        repo.salvar(item("b"))
        repo.apagarTudo()
        assertEquals(0L, repo.contar())
        assertTrue(repo.listar().isEmpty())
    }



    @Test
    fun itemSemEstadoVoltaSemEstado() = runBlocking {
        val repo = repositorio()
        repo.salvar(item("a").copy(estado = null))
        assertEquals(null, repo.listar().single().estado)
    }

    @Test
    fun configuracaoSobreviveAApagarTudo() = runBlocking {
        val repo = repositorio()
        assertEquals(null, repo.lerConfiguracao("tema"))
        repo.salvarConfiguracao("tema", "claro")
        repo.salvar(item("a"))
        repo.apagarTudo()
        assertEquals("claro", repo.lerConfiguracao("tema"))
    }

    @Test
    fun migracaoDaVersao1PreservaOsDadosEAdicionaEstado() = runBlocking {
        // Banco como o da versão 1 do app, já com um item cadastrado.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """CREATE TABLE item (
                id TEXT NOT NULL PRIMARY KEY, categoria TEXT NOT NULL, titulo TEXT NOT NULL,
                conservacao TEXT NOT NULL DEFAULT '', preco_centavos INTEGER,
                descricao TEXT NOT NULL DEFAULT '', criado_em INTEGER NOT NULL,
                atualizado_em INTEGER NOT NULL, sincronizado_revisao INTEGER)""",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE item_atributo (item_id TEXT NOT NULL, nome TEXT NOT NULL, valor TEXT NOT NULL, PRIMARY KEY (item_id, nome))",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO item (id, categoria, titulo, conservacao, preco_centavos, descricao, criado_em, atualizado_em) " +
                "VALUES ('velho', 'LIVRO', 'Livro antigo', 'Capa riscada', 2500, 'Texto', 1, 1)",
            0,
        )

        BancoDeVendas.Schema.migrate(driver, 1, BancoDeVendas.Schema.version)

        val repo = RepositorioDeItens(driver)
        val lido = repo.listar().single()
        assertEquals("Livro antigo", lido.titulo)
        assertEquals(null, lido.estado)
        repo.salvar(lido.copy(estado = Estado.NOVO))
        assertEquals(Estado.NOVO, repo.listar().single().estado)
        repo.salvarConfiguracao("tema", "escuro")
        assertEquals("escuro", repo.lerConfiguracao("tema"))
    }


    @Test
    fun lojasSaoGuardadasSeparadasENaoMudamAOrdemNemOsDados() = runBlocking {
        val repo = repositorio()
        val rev = repo.salvar(item("a"))
        repo.definirLojas("a", setOf(Loja.OLX, Loja.AMAZON))
        assertEquals(setOf(Loja.OLX, Loja.AMAZON), repo.listar().single().lojas)

        repo.definirLojas("a", setOf(Loja.OLX))
        assertEquals(setOf(Loja.OLX), repo.listar().single().lojas)

        // editar o livro não apaga em quais lojas ele foi anunciado
        repo.salvar(item("a", "Título novo"))
        assertEquals(setOf(Loja.OLX), repo.listar().single().lojas)
        assertTrue(rev > 0)

        repo.excluir("a")
        repo.salvar(item("a"))
        assertTrue(repo.listar().single().lojas.isEmpty(), "excluir também apaga as lojas marcadas")
    }
}
