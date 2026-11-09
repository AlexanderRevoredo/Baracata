package br.app.vendas

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.app.vendas.dados.ArquivosDeFotos
import br.app.vendas.dados.ServicoDeBackup
import br.app.vendas.dados.ServicoDeFotos
import br.app.vendas.ui.App
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vendas = application as VendasApp
        val arquivosDeFotos = ArquivosDeFotos(File(filesDir, "fotos").absolutePath)
        val fotos = ServicoDeFotos(
            arquivosDeFotos,
            PlataformaDeFotosAndroid(this),
            vendas.repositorio::novoId,
        )
        val leitor = LeitorDeCodigo(this)
        val plataformaDeBackup = PlataformaDeBackupAndroid(this)
        val backup = ServicoDeBackup(vendas.repositorio, arquivosDeFotos)
        setContent {
            App(
                repositorio = vendas.repositorio,
                busca = vendas.busca,
                fotos = fotos,
                backup = backup,
                plataformaDeBackup = plataformaDeBackup,
                lerIsbn = { leitor.ler() },
                // Ícones da barra de status/navegação acompanham o tema escolhido no app.
                aoMudarTema = { escuro ->
                    val estilo = if (escuro) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                },
            )
        }
    }
}
