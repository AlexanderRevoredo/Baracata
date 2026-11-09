package br.app.vendas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.app.vendas.dados.BuscaDeLivro
import br.app.vendas.dados.CandidatoDeLivro
import br.app.vendas.dados.CapaCandidata
import br.app.vendas.dados.DadosDoLivro
import br.app.vendas.dados.ResultadoBusca
import br.app.vendas.dados.ServicoDeFotos
import br.app.vendas.dominio.ComposicaoDeTexto
import br.app.vendas.dominio.Estado
import br.app.vendas.dominio.Foto
import br.app.vendas.dominio.Isbn
import br.app.vendas.dominio.Item
import kotlinx.coroutines.launch

private const val ATRIBUTO_FONTE = "_fonte"

/** Dados do livro que aparecem no formulário (além do título). */
private val CAMPOS_DO_LIVRO = listOf("Autor", "Editora", "Ano da edição", "Páginas", "Sinopse")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TelaDoFormulario(
    inicial: Item?,
    busca: BuscaDeLivro,
    fotos: ServicoDeFotos,
    avisos: SnackbarHostState,
    lerIsbn: (suspend () -> String?)?,
    outrosIsbns: Map<String, String>,
    onAvisar: (String) -> Unit,
    onSalvar: (Item) -> Unit,
    onExcluir: (String) -> Unit,
    onVoltar: () -> Unit,
    novoId: () -> String,
) {
    val escopo = rememberCoroutineScope()
    val area = LocalClipboardManager.current

    // ---- estado do formulário ----
    val fotosDoItem = remember { mutableStateListOf<Foto>().apply { inicial?.fotos?.let { addAll(it) } } }
    val fotosSalvas = inicial?.fotos.orEmpty()
    var titulo by remember { mutableStateOf(inicial?.titulo.orEmpty()) }
    var estado by remember { mutableStateOf(inicial?.estado) }
    val atributos = remember { mutableStateMapOf<String, String>().apply { inicial?.atributos?.let { putAll(it) } } }

    var ocupadoComFotos by remember { mutableStateOf(false) }
    var confirmandoExclusao by remember { mutableStateOf(false) }
    var confirmandoSaida by remember { mutableStateOf(false) }

    // ---- estado da busca de dados ----
    var capas by remember { mutableStateOf<List<CapaCandidata>>(emptyList()) }
    var candidatos by remember { mutableStateOf<List<CandidatoDeLivro>>(emptyList()) }
    var statusTitulo by remember { mutableStateOf("") }
    var tituloDaFonte by remember { mutableStateOf<String?>(null) }
    var avisosDaFonte by remember { mutableStateOf<List<String>>(emptyList()) }
    var outrosTitulos by remember { mutableStateOf<List<String>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var statusBusca by remember { mutableStateOf("") }

    val isbnDigitado = atributos["ISBN"].orEmpty()
    val isbn13 = Isbn.paraIsbn13(isbnDigitado)
    // Ao abrir um livro que já tem ISBN, não consulta de novo: só consulta quando o ISBN muda.
    var isbnJaConsultado by remember { mutableStateOf(Isbn.paraIsbn13(inicial?.atributos?.get("ISBN").orEmpty())) }

    fun montar(id: String): Item = Item(
        id = id,
        titulo = titulo.trim(),
        estado = estado,
        // Na ordem dos campos do formulário; "_fonte" é interno (de onde vieram os dados).
        atributos = (listOf("ISBN") + CAMPOS_DO_LIVRO + ATRIBUTO_FONTE)
            .mapNotNull { nome -> atributos[nome]?.trim()?.takeIf { it.isNotEmpty() }?.let { nome to it } }
            .toMap(),
        fotos = fotosDoItem.toList(),
        lojas = inicial?.lojas.orEmpty(),
    )

    val previa = montar(inicial?.id ?: "previa")
    val alterado = if (inicial == null) {
        previa.titulo.isNotEmpty() || previa.estado != null || previa.fotos.isNotEmpty() ||
            previa.atributos.keys.any { it != ATRIBUTO_FONTE }
    } else {
        previa != inicial
    }

    fun voltar() {
        escopo.launch {
            // Fotos adicionadas e não salvas são descartadas junto com a edição.
            fotos.descartar(fotosDoItem.filter { it !in fotosSalvas })
            onVoltar()
        }
    }

    fun tentarVoltar() {
        if (alterado) confirmandoSaida = true else voltar()
    }

    /** Preenche só os campos vazios: nunca sobrescreve o que a pessoa digitou ou corrigiu. */
    fun preencher(nome: String, valor: String?) {
        if (!valor.isNullOrBlank() && atributos[nome].isNullOrBlank()) atributos[nome] = valor
    }

    fun aplicar(d: DadosDoLivro) {
        val tituloDaBusca = listOfNotNull(d.titulo, d.subtitulo).joinToString(": ")
        if (titulo.isBlank()) titulo = tituloDaBusca
        tituloDaFonte = tituloDaBusca
        avisosDaFonte = d.observacoes
        outrosTitulos = d.outrosTitulos
        preencher("Autor", d.autores.joinToString(", "))
        preencher("Editora", d.editora)
        preencher("Ano da edição", d.anoEdicao?.toString())
        preencher("Páginas", d.paginas?.toString())
        preencher("Sinopse", d.sinopse)
        atributos[ATRIBUTO_FONTE] = d.descricaoDaFonte()
    }

    fun consultar(isbn: String) {
        escopo.launch {
            buscando = true
            capas = emptyList()
            candidatos = emptyList()
            statusTitulo = ""
            avisosDaFonte = emptyList()
            outrosTitulos = emptyList()
            statusBusca = "Buscando dados do livro…"
            val r = busca.buscar(isbn)
            statusBusca = when (r) {
                is ResultadoBusca.Encontrado -> {
                    aplicar(r.dados)
                    "Dados preenchidos (${r.dados.fonte}). Confira se é a edição do seu exemplar."
                }
                ResultadoBusca.NaoEncontrado ->
                    "Nenhuma fonte conhece este ISBN. Digite o título e toque em \"Buscar pelo título\", ou preencha à mão."
                is ResultadoBusca.SemConexao -> "Sem conexão com a fonte de dados. Preencha à mão ou tente de novo."
            }
            if (r !is ResultadoBusca.SemConexao) {
                val achadas = busca.buscarCapas(isbn, (r as? ResultadoBusca.Encontrado)?.dados)
                if (achadas.size == 1 && fotosDoItem.isEmpty()) {
                    // Uma só capa e o livro ainda sem foto: já entra como capa (dá para remover).
                    val c = achadas.first()
                    fotosDoItem.add(0, fotos.adicionarBytes(c.bytes, c.extensao))
                    onAvisar("Capa adicionada como foto 1 (${c.fonte}).")
                } else {
                    capas = achadas
                }
            }
            buscando = false
        }
    }

    fun buscarPeloTitulo() {
        escopo.launch {
            buscando = true
            candidatos = emptyList()
            statusTitulo = "Buscando pelo título…"
            val achados = busca.buscarPorTitulo(titulo)
            candidatos = achados
            statusTitulo = if (achados.isEmpty()) {
                "Nada encontrado para esse título. Tente menos palavras ou preencha à mão."
            } else {
                "Toque no livro que corresponde ao seu exemplar:"
            }
            buscando = false
        }
    }

    fun usarCandidato(c: CandidatoDeLivro) {
        escopo.launch {
            titulo = c.titulo
            preencher("Autor", c.autores.joinToString(", "))
            preencher("Editora", c.editora)
            preencher("Ano da edição", c.anoEdicao?.toString())
            preencher("Páginas", c.paginas?.toString())
            preencher("Sinopse", c.sinopse)
            atributos[ATRIBUTO_FONTE] = "${c.fonte} · escolhido numa busca por título (confira a edição)"
            candidatos = emptyList()
            statusTitulo = "Dados preenchidos a partir de \"${c.titulo}\" (${c.fonte}). Confira se é a edição do seu exemplar."
            capas = listOfNotNull(c.capaUrl?.let { busca.baixarCapa(it, c.fonte) })
        }
    }

    fun buscarPorIsbn() {
        if (isbn13 != null) consultar(isbn13) else statusBusca = "Digite ou leia um ISBN válido primeiro."
    }

    LaunchedEffect(isbn13) {
        if (isbn13 == null) {
            // ISBN apagado ou incompleto: digitar o mesmo ISBN de novo deve consultar outra vez.
            isbnJaConsultado = null
        } else if (isbn13 != isbnJaConsultado) {
            isbnJaConsultado = isbn13
            consultar(isbn13)
        }
    }

    // Botão/gesto "voltar" do sistema: pergunta antes de perder alterações.
    ManipularVoltar(ativo = true) { tentarVoltar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            Row(
                // statusBarsPadding: sem isso a barra fica por baixo do relógio e da bateria do celular.
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { tentarVoltar() }) { Text("← Voltar") }
                Text(
                    if (inicial == null) "Novo livro" else "Editar livro",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Button(
                    onClick = { onSalvar(montar(inicial?.id ?: novoId())) },
                    enabled = titulo.isNotBlank(),
                ) { Text("Salvar") }
            }
        },
    ) { margens ->
        Column(
            Modifier.fillMaxSize().padding(margens).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------------- 1. Identificar ----------------
            Secao("Identificar o livro", "Leia o código de barras (ISBN) e o app preenche o resto.") {
                if (lerIsbn != null) {
                    Button(
                        enabled = !buscando,
                        onClick = {
                            escopo.launch {
                                val lido = lerIsbn()
                                if (lido != null) atributos["ISBN"] = lido
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("📷  Ler código de barras", style = MaterialTheme.typography.titleSmall) }
                }
                OutlinedTextField(
                    value = isbnDigitado,
                    onValueChange = { atributos["ISBN"] = it },
                    label = { Text("ISBN (ou digite aqui)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscarPorIsbn() }),
                    isError = isbnDigitado.isNotBlank() && isbn13 == null,
                    supportingText = if (isbnDigitado.isNotBlank()) {
                        {
                            val digitos = isbnDigitado.filter { it.isDigit() }
                            Text(
                                when {
                                    isbn13 != null -> "ISBN válido (ISBN-13: $isbn13)"
                                    digitos.length == 13 && digitos.startsWith("789") ->
                                        "Este código ($digitos) é um código de barras comum, não um ISBN (que começa com 978). " +
                                            "Boxes e kits costumam ter código próprio. Procure o ISBN na contracapa ou preencha à mão."
                                    else -> "ISBN incompleto ou inválido, confira os dígitos"
                                },
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                outrosIsbns[isbn13]?.let { existente ->
                    Text(
                        "Atenção: você já cadastrou este ISBN (\"$existente\"). Confira para não duplicar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(enabled = !buscando, onClick = { buscarPorIsbn() }) { Text("Buscar por ISBN") }
                    OutlinedButton(
                        enabled = !buscando,
                        onClick = {
                            if (titulo.trim().length >= 3) buscarPeloTitulo()
                            else statusTitulo = "Digite o título do livro (no campo \"Título\", abaixo) para buscar."
                        },
                    ) { Text("Buscar pelo título") }
                }
                if (buscando) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (statusBusca.isNotEmpty()) Text(statusBusca, style = MaterialTheme.typography.bodySmall)
                if (statusTitulo.isNotEmpty()) Text(statusTitulo, style = MaterialTheme.typography.bodySmall)
                avisosDaFonte.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                outrosTitulos.filter { it != titulo.trim() }.forEach { outro ->
                    TextButton(onClick = { titulo = outro }) { Text("Usar o título: $outro") }
                }
                tituloDaFonte?.takeIf { it.isNotBlank() && it != titulo.trim() }?.let { sugerido ->
                    TextButton(onClick = { titulo = sugerido }) { Text("Usar o título da fonte: $sugerido") }
                }
                candidatos.forEach { c ->
                    Card(Modifier.fillMaxWidth().clickable { usarCandidato(c) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.titulo, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    c.autores.joinToString(", ").ifBlank { null },
                                    c.editora,
                                    c.anoEdicao?.toString(),
                                    c.paginas?.let { "$it págs." },
                                    c.fonte,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (capas.isNotEmpty()) {
                    Text(
                        if (capas.size == 1) "Capa encontrada. Toque para usar como foto 1:" else "Capas encontradas. Toque na do seu exemplar:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        capas.forEach { capa ->
                            Column(
                                Modifier.clickable {
                                    escopo.launch {
                                        fotosDoItem.add(0, fotos.adicionarBytes(capa.bytes, capa.extensao))
                                        capas = emptyList()
                                        onAvisar("Capa adicionada como foto 1.")
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MiniaturaDeBytes(capa.bytes)
                                Text(capa.fonte, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(110.dp))
                            }
                        }
                    }
                }
                atributos[ATRIBUTO_FONTE]?.takeIf { it.isNotBlank() }?.let {
                    Text("Fonte: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---------------- 2. Dados ----------------
            Secao("Dados do livro") {
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it },
                    label = { Text("Título *") },
                    isError = titulo.isBlank() && alterado,
                    supportingText = if (titulo.isBlank() && alterado) {
                        { Text("O título é obrigatório para salvar") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                CAMPOS_DO_LIVRO.forEach { nome ->
                    val longo = nome == "Sinopse"
                    OutlinedTextField(
                        value = atributos[nome].orEmpty(),
                        onValueChange = { atributos[nome] = it },
                        label = { Text(nome) },
                        singleLine = !longo,
                        minLines = if (longo) 4 else 1,
                        maxLines = if (longo) 10 else 1,
                        keyboardOptions = if (nome == "Ano da edição" || nome == "Páginas") {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Estado do exemplar", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Estado.entries.forEach { e ->
                        FilterChip(
                            selected = e == estado,
                            onClick = { estado = if (estado == e) null else e },
                            label = { Text(e.rotulo) },
                        )
                    }
                }
            }

            // ---------------- 3. Fotos ----------------
            Secao("Fotos (${fotosDoItem.size})", "A primeira é a capa mostrada na lista.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (fotos.podeTirarFoto) {
                        Button(
                            enabled = !ocupadoComFotos,
                            onClick = {
                                escopo.launch {
                                    ocupadoComFotos = true
                                    try {
                                        fotos.tirar()?.let {
                                            fotosDoItem.add(it)
                                            onAvisar("Foto adicionada. Salve o livro para guardá-la.")
                                        }
                                    } catch (e: Exception) {
                                        onAvisar("Não foi possível tirar a foto: ${e.message ?: "erro desconhecido"}")
                                    }
                                    ocupadoComFotos = false
                                }
                            },
                        ) { Text("📷  Tirar foto") }
                    }
                    OutlinedButton(
                        enabled = !ocupadoComFotos,
                        onClick = {
                            escopo.launch {
                                ocupadoComFotos = true
                                try {
                                    val novas = fotos.adicionar()
                                    fotosDoItem.addAll(novas)
                                    if (novas.isNotEmpty()) onAvisar("${novas.size} foto(s) adicionada(s). Salve o livro para guardá-las.")
                                } catch (e: Exception) {
                                    onAvisar("Não foi possível importar as fotos: ${e.message ?: "erro desconhecido"}")
                                }
                                ocupadoComFotos = false
                            }
                        },
                    ) { Text("Da galeria") }
                    OutlinedButton(
                        enabled = fotosDoItem.isNotEmpty() && !ocupadoComFotos,
                        onClick = {
                            escopo.launch {
                                ocupadoComFotos = true
                                onAvisar(
                                    try {
                                        fotos.exportar(titulo, fotosDoItem.toList())
                                    } catch (e: Exception) {
                                        "Não foi possível exportar: ${e.message ?: "erro desconhecido"}"
                                    },
                                )
                                ocupadoComFotos = false
                            }
                        },
                    ) { Text("Exportar para anunciar") }
                }
                if (fotosDoItem.isEmpty()) {
                    Text("Nenhuma foto ainda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fotosDoItem.forEachIndexed { i, foto ->
                            key(foto.id) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiniaturaDeFoto(foto, fotos, largura = 112.dp, altura = 160.dp, escala = ContentScale.Fit)
                                    Text(
                                        if (i == 0) "Capa" else "Foto ${i + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (i == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row {
                                        val tocar = PaddingValues(0.dp)
                                        TextButton(
                                            enabled = i > 0, contentPadding = tocar, modifier = Modifier.size(40.dp),
                                            onClick = { fotosDoItem.add(i - 1, fotosDoItem.removeAt(i)) },
                                        ) { Text("◀") }
                                        TextButton(
                                            enabled = i < fotosDoItem.lastIndex, contentPadding = tocar, modifier = Modifier.size(40.dp),
                                            onClick = { fotosDoItem.add(i + 1, fotosDoItem.removeAt(i)) },
                                        ) { Text("▶") }
                                        TextButton(
                                            contentPadding = tocar, modifier = Modifier.size(40.dp),
                                            onClick = {
                                                fotosDoItem.removeAt(i)
                                                // Foto ainda não salva: ninguém mais aponta para o arquivo, pode apagar já.
                                                if (foto !in fotosSalvas) escopo.launch { fotos.descartar(listOf(foto)) }
                                            },
                                        ) { Text("✕", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- 4. Texto do anúncio ----------------
            Secao("Texto do anúncio", "Pronto para copiar e colar na OLX, Mercado Livre e outras lojas.") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            ComposicaoDeTexto.titulo(previa).ifBlank { "(o título aparece aqui)" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ComposicaoDeTexto.descricao(previa).ifBlank { "(a descrição aparece aqui)" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 12, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        enabled = previa.titulo.isNotEmpty(),
                        onClick = {
                            area.setText(AnnotatedString(ComposicaoDeTexto.titulo(previa)))
                            onAvisar("Título copiado.")
                        },
                    ) { Text("Copiar título") }
                    OutlinedButton(
                        enabled = ComposicaoDeTexto.descricao(previa).isNotEmpty(),
                        onClick = {
                            area.setText(AnnotatedString(ComposicaoDeTexto.descricao(previa)))
                            onAvisar("Descrição copiada.")
                        },
                    ) { Text("Copiar descrição") }
                }
            }

            if (inicial != null) {
                TextButton(onClick = { confirmandoExclusao = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Excluir este livro", color = MaterialTheme.colorScheme.error)
                }
            }
            Text("", Modifier.height(24.dp))
        }
    }

    if (confirmandoSaida) {
        AlertDialog(
            onDismissRequest = { confirmandoSaida = false },
            title = { Text("Sair sem salvar?") },
            text = { Text("Você tem alterações que ainda não foram salvas.") },
            confirmButton = {
                TextButton(
                    enabled = titulo.isNotBlank(),
                    onClick = {
                        confirmandoSaida = false
                        onSalvar(montar(inicial?.id ?: novoId()))
                    },
                ) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmandoSaida = false }) { Text("Continuar editando") }
                    TextButton(onClick = {
                        confirmandoSaida = false
                        voltar()
                    }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    if (confirmandoExclusao && inicial != null) {
        AlertDialog(
            onDismissRequest = { confirmandoExclusao = false },
            title = { Text("Excluir este livro?") },
            text = { Text("O livro e as fotos dele serão removidos deste aparelho. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoExclusao = false
                    onExcluir(inicial.id)
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmandoExclusao = false }) { Text("Cancelar") } },
        )
    }
}
