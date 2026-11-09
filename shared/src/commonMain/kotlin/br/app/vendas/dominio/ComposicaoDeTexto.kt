package br.app.vendas.dominio

/**
 * Compõe o texto do anúncio por regras simples, usando apenas o que a pessoa informou.
 * Campos vazios são omitidos; nada é inventado.
 */
object ComposicaoDeTexto {

    fun titulo(item: Item): String = item.titulo.trim()

    fun descricao(item: Item): String {
        val linhas = mutableListOf<String>()
        item.atributos.forEach { (nome, valor) ->
            val v = valor.trim()
            if (nome.isNotBlank() && !nome.startsWith("_") && v.isNotEmpty()) linhas += "${nome.trim()}: $v"
        }
        item.estado?.let { linhas += "Estado: ${it.rotulo}" }
        return linhas.joinToString("\n")
    }
}
