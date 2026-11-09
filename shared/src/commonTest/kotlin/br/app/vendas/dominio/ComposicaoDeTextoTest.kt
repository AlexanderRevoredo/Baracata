package br.app.vendas.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposicaoDeTextoTest {
    @Test
    fun omiteCamposVazios() {
        val item = Item(id = "1", titulo = "  O poço e o pêndulo ")
        assertEquals("O poço e o pêndulo", ComposicaoDeTexto.titulo(item))
        assertEquals("", ComposicaoDeTexto.descricao(item))
    }

    @Test
    fun comporDescricaoUsaSomenteOQueFoiInformado() {
        val item = Item(
            id = "2",
            titulo = "O poço e o pêndulo",
            estado = Estado.USADO,
            atributos = linkedMapOf("Autor" to "Edgar Allan Poe", "Editora" to "", "Páginas" to " 160 "),
        )
        val esperado = """
            Autor: Edgar Allan Poe
            Páginas: 160
            Estado: Usado
        """.trimIndent()
        assertEquals(esperado, ComposicaoDeTexto.descricao(item))
    }

    @Test
    fun atributosInternosNaoEntramNoTextoDoAnuncio() {
        val item = Item(
            id = "3",
            titulo = "O poço e o pêndulo",
            atributos = linkedMapOf("Autor" to "Edgar Allan Poe", "_fonte" to "BrasilAPI · consultado em 2026-11-08"),
        )
        assertEquals("Autor: Edgar Allan Poe", ComposicaoDeTexto.descricao(item))
    }
}
