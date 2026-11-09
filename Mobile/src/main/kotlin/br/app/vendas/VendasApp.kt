package br.app.vendas

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.app.vendas.dados.BuscaDeLivro
import br.app.vendas.dados.RepositorioDeItens
import br.app.vendas.db.BancoDeVendas

/** Uma única conexão com o banco por processo (evita abrir outra a cada rotação de tela). */
class VendasApp : Application() {
    val repositorio: RepositorioDeItens by lazy {
        RepositorioDeItens(AndroidSqliteDriver(BancoDeVendas.Schema, this, "vendas.db"))
    }
    val busca: BuscaDeLivro by lazy { BuscaDeLivro() }
}
