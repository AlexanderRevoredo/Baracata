package br.app.vendas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun ManipularVoltar(ativo: Boolean, aoVoltar: () -> Unit) = BackHandler(enabled = ativo, onBack = aoVoltar)
