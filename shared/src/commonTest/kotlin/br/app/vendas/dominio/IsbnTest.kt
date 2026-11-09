package br.app.vendas.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IsbnTest {
    @Test
    fun isbn13ValidoComEHifens() {
        assertTrue(Isbn.isValido("978-0-306-40615-7"))
        assertTrue(Isbn.isValido("9780306406157"))
    }

    @Test
    fun isbn13ComDigitoErradoEInvalido() {
        assertFalse(Isbn.isValido("9780306406158"))
    }

    @Test
    fun isbn13ExigePrefixo978Ou979() {
        // Mesmo com dígito verificador correto, 123... não é ISBN.
        assertFalse(Isbn.isValido("1234567890128"))
    }

    @Test
    fun isbn10ValidoInclusiveComX() {
        assertTrue(Isbn.isValido("0-306-40615-2"))
        assertTrue(Isbn.isValido("080442957X"))
        assertTrue(Isbn.isValido("080442957x"))
    }

    @Test
    fun isbn10ComXForaDoFimEInvalido() {
        assertFalse(Isbn.isValido("0X06406152"))
    }

    @Test
    fun tamanhoErradoEInvalido() {
        assertFalse(Isbn.isValido(""))
        assertFalse(Isbn.isValido("12345"))
    }

    @Test
    fun converteIsbn10ParaIsbn13() {
        assertEquals("9780306406157", Isbn.paraIsbn13("0306406152"))
        assertEquals("9780306406157", Isbn.paraIsbn13("978-0-306-40615-7"))
        assertNull(Isbn.paraIsbn13("0306406153"))
    }
}
