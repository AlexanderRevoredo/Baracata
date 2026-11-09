package br.app.vendas.dominio

enum class Estado(val rotulo: String) {
    NOVO("Novo"),
    SEMINOVO("Seminovo"),
    USADO("Usado"),
}

/** Lojas e sites onde um livro pode ser anunciado. A pessoa marca em quais já anunciou. */
enum class Loja(val rotulo: String) {
    AMAZON("Amazon"),
    MERCADO_LIVRE("Mercado Livre"),
    OLX("OLX"),
    ESTANTE_VIRTUAL("Estante Virtual"),
    FACEBOOK("Marketplace do Facebook"),
    SHOPEE("Shopee"),
    ENJOEI("Enjoei"),
    MAGALU("Magazine Luiza"),
}

/**
 * Livro à venda (uma unidade). Os dados do livro (ISBN, autor, editora...) ficam em [atributos].
 * Nomes de atributo que começam com "_" são internos (ex.: "_fonte", de onde vieram os dados)
 * e não entram no texto do anúncio. [lojas] são os sites onde o livro já foi anunciado.
 */
data class Item(
    val id: String,
    val titulo: String,
    val estado: Estado? = null,
    val atributos: Map<String, String> = emptyMap(),
    /** Em ordem de exibição; a primeira é a capa. */
    val fotos: List<Foto> = emptyList(),
    val lojas: Set<Loja> = emptySet(),
)

/** Registro de uma foto. A imagem fica em arquivo ([arquivo]) na pasta privada do app, não no banco. */
data class Foto(val id: String, val arquivo: String, val tamanho: Long)
