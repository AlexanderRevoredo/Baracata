package br.app.vendas.dados

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual class ArquivosDeFotos actual constructor(pasta: String) {
    private val raiz = File(pasta).also { it.mkdirs() }

    private fun arquivo(nome: String): File {
        require(nome.isNotEmpty() && '/' !in nome && '\\' !in nome && nome != "." && nome != "..") { "Nome inválido: $nome" }
        return File(raiz, nome)
    }

    actual fun gravar(nome: String, bytes: ByteArray) {
        val destino = arquivo(nome)
        val temporario = File(raiz, "$nome.tmp")
        temporario.writeBytes(bytes)
        Files.move(temporario.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    actual fun ler(nome: String): ByteArray? = arquivo(nome).takeIf { it.isFile }?.readBytes()

    actual fun apagar(nome: String) {
        arquivo(nome).delete()
    }

    actual fun nomes(): List<String> = raiz.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
}
