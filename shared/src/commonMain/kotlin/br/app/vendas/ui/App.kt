package br.app.vendas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.app.vendas.dados.BuscaDeLivro
import br.app.vendas.dados.PlataformaDeBackup
import br.app.vendas.dados.RepositorioDeItens
import br.app.vendas.dados.ServicoDeBackup
import br.app.vendas.dados.ServicoDeFotos
import br.app.vendas.dominio.Isbn
import br.app.vendas.dominio.Item
import kotlinx.coroutines.launch

private sealed interface Tela {
    data object Lista : Tela
    data class Formulario(val item: Item?) : Tela
}

private const val CHAVE_TEMA = "tema"
private const val CHAVE_GOOGLE_BOOKS = "chave_google_books"

/**
 * @param aoMudarTema avisa a plataforma quando o tema muda (a barra de status do Android precisa disso).
 * @param lerIsbn lê o código de barras pela câmera e devolve o número (null se cancelou). Null = sem leitor.
 * @param backup e [plataformaDeBackup]: exportar e restaurar tudo em um arquivo. Nulos = sem backup.
 */
@Composable
fun App(
    repositorio: RepositorioDeItens,
    busca: BuscaDeLivro,
    fotos: ServicoDeFotos,
    backup: ServicoDeBackup? = null,
    plataformaDeBackup: PlataformaDeBackup? = null,
    aoMudarTema: (escuro: Boolean) -> Unit = {},
    lerIsbn: (suspend () -> String?)? = null,
) {
    val escopo = rememberCoroutineScope()
    val avisos = remember { SnackbarHostState() }
    fun avisar(texto: String) {
        escopo.launch {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar(texto)
        }
    }

    var escuro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { escuro = repositorio.lerConfiguracao(CHAVE_TEMA) != "claro" }
    LaunchedEffect(escuro) { aoMudarTema(escuro) }
    var chaveGoogle by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        chaveGoogle = repositorio.lerConfiguracao(CHAVE_GOOGLE_BOOKS).orEmpty()
        busca.chaveGoogle = chaveGoogle.ifBlank { null }
    }

    MaterialTheme(colorScheme = if (escuro) Paleta.escuro else Paleta.claro) {
        Surface(Modifier.fillMaxSize()) {
            var tela by remember { mutableStateOf<Tela>(Tela.Lista) }
            var itens by remember { mutableStateOf<List<Item>>(emptyList()) }
            var configurando by remember { mutableStateOf(false) }

            fun recarregar() {
                escopo.launch { itens = repositorio.listar() }
            }
            LaunchedEffect(Unit) {
                val todos = repositorio.listar()
                itens = todos
                // Sobras de edição interrompida (foto adicionada e o livro nunca salvo).
                fotos.limparOrfaos(todos.flatMap { it.fotos }.map { it.arquivo }.toSet())
            }

            when (val atual = tela) {
                Tela.Lista -> TelaDaLista(
                    itens = itens,
                    fotos = fotos,
                    avisos = avisos,
                    escuro = escuro,
                    onAlternarTema = {
                        escuro = !escuro
                        escopo.launch { repositorio.salvarConfiguracao(CHAVE_TEMA, if (escuro) "escuro" else "claro") }
                    },
                    onConfiguracoes = { configurando = true },
                    onDefinirLojas = { item, lojas ->
                        escopo.launch {
                            repositorio.definirLojas(item.id, lojas)
                            recarregar()
                        }
                    },
                    onNovo = { tela = Tela.Formulario(null) },
                    onAbrir = { tela = Tela.Formulario(it) },
                )

                is Tela.Formulario -> TelaDoFormulario(
                    inicial = atual.item,
                    busca = busca,
                    fotos = fotos,
                    avisos = avisos,
                    lerIsbn = lerIsbn,
                    // ISBN -> título dos OUTROS livros, para avisar de repetição.
                    outrosIsbns = itens.filter { it.id != atual.item?.id }
                        .mapNotNull { i -> Isbn.paraIsbn13(i.atributos["ISBN"].orEmpty())?.let { it to i.titulo } }
                        .toMap(),
                    onAvisar = ::avisar,
                    onSalvar = { item ->
                        escopo.launch {
                            repositorio.salvar(item)
                            // Só apaga o arquivo depois que o banco já não aponta para ele.
                            fotos.descartar(atual.item?.fotos.orEmpty().filter { it !in item.fotos })
                            recarregar()
                            tela = Tela.Lista
                            avisar("Livro salvo.")
                        }
                    },
                    onExcluir = { id ->
                        escopo.launch {
                            repositorio.excluir(id)
                            fotos.descartar(atual.item?.fotos.orEmpty())
                            recarregar()
                            tela = Tela.Lista
                            avisar("Livro excluído.")
                        }
                    },
                    onVoltar = { tela = Tela.Lista },
                    novoId = repositorio::novoId,
                )
            }

            if (configurando) {
                Configuracoes(
                    chaveGoogle = chaveGoogle,
                    totalDeLivros = itens.size,
                    podeFazerBackup = backup != null && plataformaDeBackup != null,
                    onFechar = { configurando = false },
                    onSalvarChave = { nova ->
                        chaveGoogle = nova.trim()
                        busca.chaveGoogle = chaveGoogle.ifBlank { null }
                        escopo.launch { repositorio.salvarConfiguracao(CHAVE_GOOGLE_BOOKS, chaveGoogle) }
                        avisar(if (chaveGoogle.isBlank()) "Chave do Google removida." else "Chave do Google salva.")
                    },
                    onExportar = {
                        configurando = false
                        escopo.launch {
                            val escritor = plataformaDeBackup?.criarArquivo(backup!!.nomeSugerido()) ?: return@launch
                            try {
                                val r = backup!!.exportar(escritor)
                                avisar("Backup salvo: ${r.livros} livro(s) e ${r.fotos} foto(s).")
                            } catch (e: Exception) {
                                avisar("Não foi possível salvar o backup: ${e.message ?: "erro desconhecido"}")
                            }
                        }
                    },
                    onRestaurar = {
                        configurando = false
                        escopo.launch {
                            val leitor = plataformaDeBackup?.abrirArquivo() ?: return@launch
                            try {
                                val r = backup!!.restaurar(leitor)
                                recarregar()
                                avisar(
                                    "Restaurado: ${r.adicionados} livro(s) adicionado(s)" +
                                        (if (r.jaExistiam > 0) ", ${r.jaExistiam} já existiam" else "") +
                                        (if (r.fotosAusentes > 0) ", ${r.fotosAusentes} foto(s) faltando no arquivo" else "") + ".",
                                )
                            } catch (e: IllegalArgumentException) {
                                avisar(e.message ?: "Arquivo de backup inválido.")
                            } catch (e: Exception) {
                                avisar("Não foi possível restaurar: ${e.message ?: "erro desconhecido"}")
                            }
                        }
                    },
                    onApagarTudo = {
                        configurando = false
                        escopo.launch {
                            repositorio.apagarTudo()
                            fotos.apagarTudo()
                            recarregar()
                            avisar("Todos os dados foram apagados.")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Configuracoes(
    chaveGoogle: String,
    totalDeLivros: Int,
    podeFazerBackup: Boolean,
    onFechar: () -> Unit,
    onSalvarChave: (String) -> Unit,
    onExportar: () -> Unit,
    onRestaurar: () -> Unit,
    onApagarTudo: () -> Unit,
) {
    var texto by remember { mutableStateOf(chaveGoogle) }
    var confirmandoApagar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Configurações") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (podeFazerBackup) {
                    Text("Backup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Guarda todos os livros e fotos em um arquivo, para você não perder nada se trocar de celular ou " +
                            "desinstalar o app. Guarde o arquivo fora do celular (nuvem, computador).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onExportar, modifier = Modifier.fillMaxWidth(), enabled = totalDeLivros > 0) {
                        Text("Salvar backup")
                    }
                    OutlinedButton(onClick = onRestaurar, modifier = Modifier.fillMaxWidth()) { Text("Restaurar de um backup") }
                    HorizontalDivider()
                }

                Text("Google Books", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Opcional. Com uma chave de API, a busca por ISBN e por título também consulta o Google Books " +
                        "(sinopses, capas, autores). A chave fica só neste aparelho e não vai para o backup.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it },
                    label = { Text("Chave de API") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onSalvarChave(texto) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar chave") }
                HorizontalDivider()

                Text("Dados", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { confirmandoApagar = true }, enabled = totalDeLivros > 0) {
                    Text("Apagar todos os dados…", color = if (totalDeLivros > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
    )

    if (confirmandoApagar) {
        AlertDialog(
            onDismissRequest = { confirmandoApagar = false },
            title = { Text("Apagar todos os dados?") },
            text = {
                Text(
                    "Isso remove os $totalDeLivros livro(s) e as fotos deste aparelho, inclusive a marcação de onde foram " +
                        "cadastrados. Não dá para desfazer. Se não tiver um backup, faça-o antes.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoApagar = false
                    onApagarTudo()
                }) { Text("Apagar tudo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmandoApagar = false }) { Text("Cancelar") } },
        )
    }
}
