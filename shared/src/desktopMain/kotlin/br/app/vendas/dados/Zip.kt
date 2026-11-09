package br.app.vendas.dados

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Backup em .zip: grava entrada por entrada. */
class ZipEscritor(saida: OutputStream) : EscritorDeBackup {
    private val zip = ZipOutputStream(saida.buffered())

    override fun adicionar(nome: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(nome))
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun fechar() = zip.close()
}

/** Lê um backup .zip (precisa de um arquivo no disco). */
class ZipLeitor(arquivo: File) : LeitorDeBackup {
    private val zip = ZipFile(arquivo)

    override fun ler(nome: String): ByteArray? =
        zip.getEntry(nome)?.let { entrada -> zip.getInputStream(entrada).use { it.readBytes() } }

    override fun fechar() = zip.close()
}
