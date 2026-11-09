package br.app.vendas.dados

import app.cash.sqldelight.db.SqlDriver
import br.app.vendas.db.BancoDeVendas
import br.app.vendas.dominio.Estado
import br.app.vendas.dominio.Foto
import br.app.vendas.dominio.Item
import br.app.vendas.dominio.Loja
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Acesso ao banco local (SQLite). Todas as operações rodam fora da thread de interface.
 */
/** Coluna herdada do tempo em que havia outras categorias; hoje todo item é livro. */
private const val CATEGORIA_LIVRO = "LIVRO"

class RepositorioDeItens(
    private val driver: SqlDriver,
    private val relogio: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val banco = BancoDeVendas(driver)
    private val q get() = banco.itemQueries

    suspend fun listar(): List<Item> = withContext(Dispatchers.IO) {
        val atributos = q.listarAtributos().executeAsList()
            .groupBy({ it.item_id }, { it.nome to it.valor })
        val fotos = q.listarFotos().executeAsList()
            .groupBy({ it.item_id }, { Foto(it.id, it.arquivo, it.tamanho) })
        val lojas = q.listarLojas().executeAsList()
            .groupBy({ it.item_id }, { l -> Loja.entries.firstOrNull { it.name == l.loja } })
        q.listar().executeAsList().map { linha ->
            Item(
                id = linha.id,
                estado = Estado.entries.firstOrNull { it.name == linha.estado },
                titulo = linha.titulo,
                atributos = atributos[linha.id].orEmpty().toMap(),
                fotos = fotos[linha.id].orEmpty(),
                lojas = lojas[linha.id].orEmpty().filterNotNull().toSet(),
            )
        }
    }

    /** Cria ou atualiza o item. Devolve a revisão gravada. */
    suspend fun salvar(item: Item): Long = withContext(Dispatchers.IO) {
        banco.transactionWithResult {
            val anterior = q.buscarRevisao(item.id).executeAsOneOrNull()
            val agora = relogio()
            val revisao = if (anterior == null) agora else maxOf(agora, anterior + 1)
            if (anterior == null) {
                q.inserir(
                    item.id, CATEGORIA_LIVRO, item.estado?.name.orEmpty(), item.titulo, "",
                    null, "", agora, revisao,
                )
            } else {
                q.atualizar(
                    CATEGORIA_LIVRO, item.estado?.name.orEmpty(), item.titulo, "",
                    null, "", revisao, item.id,
                )
            }
            q.excluirAtributosDoItem(item.id)
            item.atributos.forEach { (nome, valor) ->
                if (nome.isNotBlank() && valor.isNotBlank()) q.inserirAtributo(item.id, nome, valor)
            }
            q.excluirFotosDoItem(item.id)
            item.fotos.forEachIndexed { posicao, f ->
                q.inserirFoto(f.id, item.id, posicao.toLong(), f.arquivo, f.tamanho)
            }
            revisao
        }
    }

    suspend fun excluir(id: String) = withContext(Dispatchers.IO) {
        banco.transaction {
            q.excluirFotosDoItem(id)
            q.excluirLojasDoItem(id)
            q.excluirAtributosDoItem(id)
            q.excluirItem(id)
        }
    }

    /** Marca em quais lojas o livro foi anunciado. Não mexe nos outros dados nem na ordem da lista. */
    suspend fun definirLojas(id: String, lojas: Set<Loja>) = withContext(Dispatchers.IO) {
        banco.transaction {
            q.excluirLojasDoItem(id)
            lojas.forEach { q.inserirLoja(id, it.name) }
        }
    }

    suspend fun contar(): Long = withContext(Dispatchers.IO) { q.contar().executeAsOne() }

    /**
     * Apaga TODOS os dados locais e compacta o arquivo do banco.
     * Não mexe no servidor. Quem chama deve pedir confirmação antes.
     */
    suspend fun apagarTudo() = withContext(Dispatchers.IO) {
        banco.transaction {
            q.excluirTodasFotos()
            q.excluirTodasLojas()
            q.excluirTodosAtributos()
            q.excluirTodosItens()
        }
        // VACUUM não pode rodar dentro de transação.
        driver.execute(null, "VACUUM", 0)
        Unit
    }

    /** Preferências do aparelho (tema etc.). Sobrevivem a [apagarTudo]. */
    suspend fun lerConfiguracao(chave: String): String? = withContext(Dispatchers.IO) {
        q.lerConfiguracao(chave).executeAsOneOrNull()
    }

    suspend fun salvarConfiguracao(chave: String, valor: String) = withContext(Dispatchers.IO) {
        q.salvarConfiguracao(chave, valor)
        Unit
    }

    @OptIn(ExperimentalUuidApi::class)
    fun novoId(): String = Uuid.random().toString()
}
