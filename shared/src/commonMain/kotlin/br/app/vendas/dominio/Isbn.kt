package br.app.vendas.dominio

/**
 * Validação e normalização de ISBN-10 e ISBN-13.
 * Funciona offline e não consulta nenhuma fonte externa.
 */
object Isbn {

    /** Remove hífens, espaços e qualquer caractere que não seja dígito ou X. */
    fun normalizar(bruto: String): String =
        bruto.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    fun isValido(bruto: String): Boolean {
        val isbn = normalizar(bruto)
        return when (isbn.length) {
            10 -> valido10(isbn)
            13 -> valido13(isbn)
            else -> false
        }
    }

    /** Converte um ISBN-10 válido em ISBN-13. Devolve o ISBN-13 normalizado se já for válido, ou null. */
    fun paraIsbn13(bruto: String): String? {
        val isbn = normalizar(bruto)
        return when {
            isbn.length == 13 && valido13(isbn) -> isbn
            isbn.length == 10 && valido10(isbn) -> {
                val base = "978" + isbn.substring(0, 9)
                base + digitoVerificador13(base)
            }
            else -> null
        }
    }

    private fun valido10(isbn: String): Boolean {
        var soma = 0
        for (i in 0 until 10) {
            val c = isbn[i]
            val valor = when {
                c.isDigit() -> c - '0'
                c == 'X' && i == 9 -> 10
                else -> return false
            }
            soma += valor * (10 - i)
        }
        return soma % 11 == 0
    }

    private fun valido13(isbn: String): Boolean {
        if (!isbn.all { it.isDigit() }) return false
        if (!(isbn.startsWith("978") || isbn.startsWith("979"))) return false
        return digitoVerificador13(isbn.substring(0, 12)) == isbn[12]
    }

    private fun digitoVerificador13(primeiros12: String): Char {
        var soma = 0
        for (i in 0 until 12) {
            val d = primeiros12[i] - '0'
            soma += if (i % 2 == 0) d else d * 3
        }
        return ('0' + (10 - soma % 10) % 10)
    }
}
