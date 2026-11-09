package br.app.vendas.dados

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val LIMITE_BYTES = 8 * 1024 * 1024

private fun abrir(url: String, aceita: String): HttpURLConnection {
    val conexao = URI(url).toURL().openConnection() as HttpURLConnection
    conexao.connectTimeout = 10_000
    conexao.readTimeout = 15_000
    conexao.setRequestProperty("User-Agent", "AppDeVendas/0.1 (uso pessoal)")
    conexao.setRequestProperty("Accept", aceita)
    return conexao
}

actual fun criarClienteHttp(): ClienteHttp = object : ClienteHttp {
    override suspend fun obter(url: String): RespostaHttp = withContext(Dispatchers.IO) {
        val conexao = abrir(url, "application/json")
        try {
            val codigo = conexao.responseCode
            val fluxo = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
            val corpo = fluxo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            RespostaHttp(codigo, corpo)
        } finally {
            conexao.disconnect()
        }
    }

    override suspend fun obterBytes(url: String): RespostaBinaria = withContext(Dispatchers.IO) {
        val conexao = abrir(url, "image/*")
        try {
            val codigo = conexao.responseCode
            if (codigo !in 200..299) return@withContext RespostaBinaria(codigo, ByteArray(0))
            val bytes = conexao.inputStream.use { it.readNBytes(LIMITE_BYTES + 1) }
            if (bytes.size > LIMITE_BYTES) RespostaBinaria(413, ByteArray(0)) else RespostaBinaria(codigo, bytes)
        } finally {
            conexao.disconnect()
        }
    }
}
