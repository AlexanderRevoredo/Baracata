package br.app.vendas.dados

import br.app.vendas.dominio.Estado
import br.app.vendas.dominio.Foto
import br.app.vendas.dominio.Item
import br.app.vendas.dominio.Loja
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/** Grava o arquivo de backup (um .zip). Cada entrada é gravada e liberada, sem juntar tudo na memória. */
interface EscritorDeBackup {
    fun adicionar(nome: String, bytes: ByteArray)
    fun fechar()
}

/** Lê um arquivo de backup. */
interface LeitorDeBackup {
    fun ler(nome: String): ByteArray?
    fun fechar()
}

/** Pede à pessoa onde guardar ou de onde abrir o backup (seletor de arquivos do sistema). */
interface PlataformaDeBackup {
    /** Abre o seletor de destino. Null se a pessoa cancelou. */
    suspend fun criarArquivo(nomeSugerido: String): EscritorDeBackup?

    /** Abre o seletor de arquivo. Null se a pessoa cancelou. */
    suspend fun abrirArquivo(): LeitorDeBackup?
}

class ResumoDeBackup(val livros: Int, val fotos: Int)

class ResultadoDeRestauracao(val adicionados: Int, val jaExistiam: Int, val fotosAusentes: Int)

/**
 * Exporta todos os livros e fotos para um arquivo e restaura a partir dele. A restauração só acrescenta:
 * livros que já existem no aparelho (mesmo identificador) ficam como estão. A chave do Google Books não
 * entra no backup, por ser um segredo.
 */
class ServicoDeBackup(
    private val repositorio: RepositorioDeItens,
    private val arquivos: ArquivosDeFotos,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun nomeSugerido(): String = "baracata-backup-" + Clock.System.now().toString().take(10) + ".zip"

    suspend fun exportar(escritor: EscritorDeBackup): ResumoDeBackup = withContext(Dispatchers.IO) {
        try {
            val itens = repositorio.listar()
            val dados = buildJsonObject {
                put("app", "Baracata")
                put("versao", 1)
                put("criadoEm", Clock.System.now().toString())
                putJsonArray("itens") {
                    itens.forEach { item ->
                        add(
                            buildJsonObject {
                                put("id", item.id)
                                put("titulo", item.titulo)
                                put("estado", item.estado?.name.orEmpty())
                                putJsonObject("atributos") { item.atributos.forEach { (k, v) -> put(k, v) } }
                                putJsonArray("fotos") {
                                    item.fotos.forEach { f ->
                                        add(
                                            buildJsonObject {
                                                put("id", f.id)
                                                put("arquivo", f.arquivo)
                                                put("tamanho", f.tamanho)
                                            },
                                        )
                                    }
                                }
                                putJsonArray("lojas") { item.lojas.forEach { add(it.name) } }
                            },
                        )
                    }
                }
            }
            escritor.adicionar(ARQUIVO_DE_DADOS, dados.toString().encodeToByteArray())
            var fotos = 0
            itens.flatMap { it.fotos }.forEach { f ->
                arquivos.ler(f.arquivo)?.let {
                    escritor.adicionar("$PASTA_DE_FOTOS${f.arquivo}", it)
                    fotos++
                }
            }
            ResumoDeBackup(itens.size, fotos)
        } finally {
            escritor.fechar()
        }
    }

    /** Lança [IllegalArgumentException] se o arquivo não for um backup do Baracata. */
    suspend fun restaurar(leitor: LeitorDeBackup): ResultadoDeRestauracao = withContext(Dispatchers.IO) {
        try {
            val bruto = leitor.ler(ARQUIVO_DE_DADOS)
                ?: throw IllegalArgumentException("Este arquivo não é um backup do Baracata.")
            val raiz = try {
                json.parseToJsonElement(bruto.decodeToString()).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Este arquivo não é um backup do Baracata.")
            }
            if (raiz.texto("app") != "Baracata") throw IllegalArgumentException("Este arquivo não é um backup do Baracata.")
            if ((raiz["versao"] as? JsonPrimitive)?.longOrNull.let { it == null || it > 1 }) {
                throw IllegalArgumentException("Backup de uma versão mais nova do app. Atualize o Baracata.")
            }
            val existentes = repositorio.listar().map { it.id }.toSet()
            var adicionados = 0
            var jaExistiam = 0
            var ausentes = 0
            // O backup lista do mais recente para o mais antigo; restaura ao contrário para manter essa ordem.
            ((raiz["itens"] as? JsonArray).orEmpty()).mapNotNull { it as? JsonObject }.reversed().forEach { o ->
                val id = o.texto("id") ?: return@forEach
                if (id in existentes) {
                    jaExistiam++
                    return@forEach
                }
                val fotos = ((o["fotos"] as? JsonArray).orEmpty()).mapNotNull { it as? JsonObject }.mapNotNull { f ->
                    val arquivo = f.texto("arquivo") ?: return@mapNotNull null
                    val bytes = leitor.ler("$PASTA_DE_FOTOS$arquivo")
                    if (bytes == null) {
                        ausentes++
                        null
                    } else {
                        arquivos.gravar(arquivo, bytes)
                        Foto(f.texto("id") ?: arquivo, arquivo, bytes.size.toLong())
                    }
                }
                val lojas = ((o["lojas"] as? JsonArray).orEmpty())
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .mapNotNull { n -> Loja.entries.firstOrNull { it.name == n } }.toSet()
                repositorio.salvar(
                    Item(
                        id = id,
                        titulo = o.texto("titulo").orEmpty(),
                        estado = Estado.entries.firstOrNull { it.name == o.texto("estado") },
                        atributos = (o["atributos"] as? JsonObject)?.mapNotNull { (k, v) ->
                            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                        }.orEmpty().toMap(),
                        fotos = fotos,
                    ),
                )
                if (lojas.isNotEmpty()) repositorio.definirLojas(id, lojas)
                adicionados++
            }
            ResultadoDeRestauracao(adicionados, jaExistiam, ausentes)
        } finally {
            leitor.fechar()
        }
    }

    private fun JsonObject.texto(chave: String): String? = (this[chave] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val ARQUIVO_DE_DADOS = "dados.json"
        const val PASTA_DE_FOTOS = "fotos/"
    }
}
