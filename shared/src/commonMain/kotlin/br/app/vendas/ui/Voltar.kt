package br.app.vendas.ui

import androidx.compose.runtime.Composable

/** Trata o botão/gesto "voltar" do sistema enquanto [ativo]. No Android chama [aoVoltar] em vez de fechar o app. */
@Composable
expect fun ManipularVoltar(ativo: Boolean, aoVoltar: () -> Unit)
