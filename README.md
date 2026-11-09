# Baracata

Aplicativo Android para **preparar anúncios de livros usados**. Você aponta a câmera para o código de barras (ISBN), e o app preenche título, autor, editora, ano, páginas, sinopse e capa. Depois organiza as fotos, gera o texto do anúncio e ajuda a controlar em quais lojas cada livro já foi cadastrado.

Projeto pessoal, feito para uso real: eu vendo livros usados e queria parar de digitar tudo à mão em cada site. A publicação nas lojas continua **manual** (OLX, Mercado Livre, Amazon, Estante Virtual, Marketplace do Facebook etc. não têm API aberta para isso). O app só deixa tudo pronto para copiar e anexar.

## O que ele faz

- **Leitura de ISBN pela câmera** (ZXing) ou digitado, com validação (ISBN-10/13, dígito verificador).
- **Busca automática dos dados do livro** em várias fontes, sem exigir conta:
  - [BrasilAPI](https://brasilapi.com.br) (bases brasileiras) como fonte principal;
  - [Open Library](https://openlibrary.org) como reserva e para sinopse;
  - [Google Books](https://developers.google.com/books) opcional, com chave de API do próprio usuário, para completar sinopse, capa e campos vazios.
- **Busca pelo título** quando não há ISBN legível (ex.: boxes): lista candidatos para a pessoa escolher.
- **Nunca sobrescreve o que o usuário digitou**: só preenche campos vazios, e guarda de onde veio cada dado (fonte, URL, data da consulta).
- **Capas pelo ISBN**: mostra as capas encontradas para escolher a do exemplar (uma só entra sozinha como capa).
- **Fotos**: da galeria ou tiradas pelo app, reordenáveis, com exportação numerada (01, 02, …) para anexar nos sites.
- **Texto do anúncio** pronto para copiar (título e descrição).
- **Controle de lojas**: marca onde cada livro já foi cadastrado, com filtros "Faltam cadastrar" e "Já cadastrados".
- **Lista com busca e ordenação**, aviso de ISBN já cadastrado e confirmação ao sair sem salvar.
- **Backup e restauração** em um arquivo `.zip` (livros, fotos, lojas). A chave de API não entra no backup.
- Funciona **offline** para tudo, menos a busca de dados. Tema escuro (padrão) e claro.

## Tecnologias

| Área | Escolha |
|---|---|
| Linguagem / UI | Kotlin 2.4, Jetpack Compose (Compose Multiplatform 1.12, Material 3) |
| Banco local | SQLite com [SQLDelight](https://sqldelight.github.io/sqldelight/) 2.4, migrações versionadas |
| Rede | `HttpURLConnection` + kotlinx.serialization (sem biblioteca de HTTP externa) |
| Concorrência | Coroutines |
| Câmera / código de barras | ZXing Android Embedded, `TakePicture` + `FileProvider` |
| Build | Gradle 9.6, AGP 9.5, JDK 21, minSdk 26 (Android 8) |
| Testes | kotlin.test, 56 testes rodando na JVM (SQLite em memória) |

## Arquitetura

```
shared/      regras e telas (Kotlin Multiplatform, alvo Android + alvo JVM só para testes)
  dominio/     Item, Estado, Loja, validação de ISBN, composição do texto do anúncio
  dados/       RepositorioDeItens (SQLDelight), BuscaDeLivro (fontes externas),
               ServicoDeFotos, ServicoDeBackup
  ui/          Compose: lista, formulário, configurações
Mobile/      app Android (Activity, seletores de arquivo, câmera, leitor de código)
assinatura/  chave de assinatura do APK (NÃO vai para o repositório)
```

Decisões de projeto que valem destaque:

- **Separação por plataforma com interfaces.** O que depende do Android (seletor de fotos, câmera, arquivos de backup, leitor de código) fica atrás de interfaces (`PlataformaDeFotos`, `PlataformaDeBackup`) implementadas só no módulo Android. Isso permite testar toda a lógica de dados e busca na JVM, sem emulador.
- **Fontes externas tolerantes a falha.** Cada fonte pode falhar, estar fora do ar ou não conhecer o ISBN sem derrubar a busca; os dados de várias fontes são combinados, sem apagar o que o usuário já corrigiu, e a procedência fica registrada.
- **Segredos fora do código.** A chave do Google Books é digitada no app, fica só no banco do aparelho, não vai para o backup nem para a URL guardada junto do livro.
- **Fotos fora do banco.** Arquivos em pasta privada do app, gravados de forma atômica (arquivo temporário + renomeação), com limpeza de órfãos na abertura. O banco guarda só o registro (ordem, nome, tamanho).
- **Restauração segura.** O backup só acrescenta: livros que já existem não são sobrescritos, e arquivos que não são do app são recusados sem mexer no banco.
- **Dados de terceiros tratados como entrada não confiável.** Respostas HTTP quebradas, HTML nas sinopses, imagens falsas (checagem dos primeiros bytes) e nomes de arquivo com caminhos são tratados explicitamente.

## Como rodar

Requisitos: JDK 21 e Android SDK (aponte `sdk.dir` no `local.properties`).

```bash
# testes (JVM, não precisa de celular)
./gradlew :shared:desktopTest

# APK de teste (assinado com a chave de depuração)
./gradlew :androidApp:assembleDebug
# saída: Mobile/build/outputs/apk/debug/
```

Para gerar um APK de distribuição, crie uma chave e o arquivo `assinatura/keystore.properties` (`senha=...`) e rode `./gradlew :androidApp:assembleRelease`. Sem esse arquivo, o build de release sai sem assinatura.

> O alvo JVM dentro de `shared` se chama `desktop` por motivos históricos; ele existe só para executar os testes no computador. O app é exclusivamente Android.

## Limitações conhecidas

- A qualidade dos dados depende das bases gratuitas: alguns ISBNs (principalmente boxes e edições raras) não existem em nenhuma delas.
- Sinopses são textos de terceiros, muitas vezes em inglês; o app só sugere, e a pessoa confere.
- A orientação EXIF das fotos só é corrigida na miniatura; a foto exportada é a original.
- Exportar fotos para a galeria exige Android 10+.
- O backup é manual e não há sincronização entre aparelhos (cada celular tem seus dados).
- Sem sugestão automática de preço: OLX e Mercado Livre bloqueiam consultas automáticas, e fazer *scraping* iria contra os termos deles.

## Documentos do projeto

- [`Mobile/PLANO.md`](Mobile/PLANO.md): decisões, escopo e pendências.
- [`GUIA-INSTALAR-NO-CELULAR.md`](GUIA-INSTALAR-NO-CELULAR.md): como instalar o APK em outro celular.

## Créditos e dados

Dados de livros por BrasilAPI, Open Library e Google Books; use-os conforme os termos de cada serviço. O código de barras é lido com [ZXing](https://github.com/zxing/zxing). O logotipo é do autor do projeto.

## Licença

Defina a licença antes de publicar (por exemplo MIT). Sem uma licença, o código fica com todos os direitos reservados.
