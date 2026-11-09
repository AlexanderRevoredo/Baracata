package br.app.vendas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.app.vendas.dados.ServicoDeFotos
import br.app.vendas.dominio.Item
import br.app.vendas.dominio.Loja

private enum class Filtro(val rotulo: String) { TODOS("Todos"), FALTAM("Faltam cadastrar"), CADASTRADOS("Já cadastrados") }

private enum class Ordem(val rotulo: String) { RECENTES("Recentes"), TITULO("A–Z") }

/** Texto que a busca da lista procura: título, autor, editora e ISBN. */
private fun Item.textoDeBusca(): String =
    (listOf(titulo) + listOf("Autor", "Editora", "ISBN").mapNotNull { atributos[it] }).joinToString(" ").lowercase()

@Composable
internal fun TelaDaLista(
    itens: List<Item>,
    fotos: ServicoDeFotos,
    avisos: SnackbarHostState,
    escuro: Boolean,
    onAlternarTema: () -> Unit,
    onConfiguracoes: () -> Unit,
    onDefinirLojas: (Item, Set<Loja>) -> Unit,
    onNovo: () -> Unit,
    onAbrir: (Item) -> Unit,
) {
    var filtro by remember { mutableStateOf(Filtro.TODOS) }
    var ordem by remember { mutableStateOf(Ordem.RECENTES) }
    var consulta by remember { mutableStateOf("") }
    var lojasDoId by remember { mutableStateOf<String?>(null) }

    val termo = consulta.trim().lowercase()
    val visiveis = itens
        .filter {
            when (filtro) {
                Filtro.TODOS -> true
                Filtro.FALTAM -> it.lojas.isEmpty()
                Filtro.CADASTRADOS -> it.lojas.isNotEmpty()
            }
        }
        .filter { termo.isEmpty() || termo in it.textoDeBusca() }
        .let { lista -> if (ordem == Ordem.TITULO) lista.sortedBy { it.titulo.lowercase() } else lista }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNovo) { Text("＋  Novo livro", style = MaterialTheme.typography.titleSmall) }
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Baracata", style = MaterialTheme.typography.headlineMedium)
                    if (itens.isNotEmpty()) {
                        val cadastrados = itens.count { it.lojas.isNotEmpty() }
                        Text(
                            "${itens.size} livro(s) · $cadastrados já cadastrado(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // ☀ muda para o tema claro; ☾ muda para o escuro.
                TextButton(onClick = onAlternarTema) { Text(if (escuro) "☀" else "☾", style = MaterialTheme.typography.headlineSmall) }
                TextButton(onClick = onConfiguracoes) { Text("⚙", style = MaterialTheme.typography.headlineSmall) }
            }

            if (itens.isNotEmpty()) {
                OutlinedTextField(
                    value = consulta,
                    onValueChange = { consulta = it },
                    placeholder = { Text("Buscar por título, autor ou ISBN") },
                    singleLine = true,
                    trailingIcon = if (consulta.isNotEmpty()) {
                        { TextButton(onClick = { consulta = "" }) { Text("✕") } }
                    } else null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Filtro.entries.forEach { f ->
                        val quantos = when (f) {
                            Filtro.TODOS -> itens.size
                            Filtro.FALTAM -> itens.count { it.lojas.isEmpty() }
                            Filtro.CADASTRADOS -> itens.count { it.lojas.isNotEmpty() }
                        }
                        FilterChip(selected = f == filtro, onClick = { filtro = f }, label = { Text("${f.rotulo} ($quantos)") })
                    }
                    OutlinedButton(
                        onClick = { ordem = if (ordem == Ordem.RECENTES) Ordem.TITULO else Ordem.RECENTES },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text("Ordem: ${ordem.rotulo}") }
                }
            }

            when {
                itens.isEmpty() -> EstadoVazio(onNovo)
                visiveis.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        if (termo.isNotEmpty()) "Nenhum livro encontrado para \"$consulta\"." else "Nenhum livro neste filtro.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // espaço extra no fim para o botão flutuante não cobrir o último livro
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(visiveis, key = { it.id }) { item ->
                        CartaoDoLivro(item, fotos, onAbrir = { onAbrir(item) }, onLojas = { lojasDoId = item.id })
                    }
                }
            }
        }
    }

    itens.firstOrNull { it.id == lojasDoId }?.let { item ->
        AlertDialog(
            onDismissRequest = { lojasDoId = null },
            title = { Text("Onde este livro foi cadastrado?") },
            text = {
                Column {
                    Text(item.titulo, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Loja.entries.forEach { loja ->
                        val marcada = loja in item.lojas
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onDefinirLojas(item, if (marcada) item.lojas - loja else item.lojas + loja)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = marcada, onCheckedChange = null)
                            Text(loja.rotulo, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { lojasDoId = null }) { Text("Pronto") } },
        )
    }
}

@Composable
private fun CartaoDoLivro(item: Item, fotos: ServicoDeFotos, onAbrir: () -> Unit, onLojas: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onAbrir)) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A capa é a primeira foto do livro.
            val capa = item.fotos.firstOrNull()
            if (capa != null) {
                MiniaturaDeFoto(capa, fotos, largura = 64.dp, altura = 96.dp, escala = ContentScale.Fit)
            } else {
                Box(
                    Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("📖", style = MaterialTheme.typography.headlineSmall) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.titulo.ifBlank { "(sem título)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detalhes = listOfNotNull(
                    item.atributos["Autor"],
                    item.estado?.rotulo,
                    item.fotos.size.takeIf { it > 0 }?.let { "$it foto(s)" },
                ).joinToString(" · ")
                if (detalhes.isNotEmpty()) {
                    Text(
                        detalhes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (item.lojas.isEmpty()) "Ainda não cadastrado" else "Em: " + item.lojas.sortedBy { it.ordinal }.joinToString(", ") { it.rotulo },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.lojas.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Botão de check: marca em quais lojas o livro já foi cadastrado.
            if (item.lojas.isEmpty()) {
                OutlinedButton(onClick = onLojas, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("✓") }
            } else {
                Button(onClick = onLojas, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("✓ ${item.lojas.size}") }
            }
        }
    }
}

@Composable
private fun EstadoVazio(onNovo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📚", style = MaterialTheme.typography.displayMedium)
        Text("Nenhum livro ainda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Toque em \"Novo livro\" e aponte a câmera para o código de barras: o Baracata preenche título, autor, " +
                "sinopse e capa para você.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onNovo) { Text("Cadastrar o primeiro livro") }
    }
}
