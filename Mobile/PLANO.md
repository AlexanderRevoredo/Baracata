# Plano do aplicativo Baracata (Android)

## Decisões

- O app é **só Android**, para uso pessoal de uma pessoa. A versão para Windows foi descartada (09/11/2026).
- **Sem backend e sem sincronização.** O banco externo (Supabase) existiria só para integrar PC e celular; sem o PC, não há função. Tudo fica no aparelho. Foram removidos do código a revisão confirmada pelo servidor e a limpeza automática de itens "já enviados".
- Kotlin com Compose (Kotlin Multiplatform só como organização do projeto: um alvo JVM existe apenas para rodar testes no computador). Kotlin em vez de Flutter para aproveitar o JDK; Java puro não é viável porque a interface Compose exige Kotlin.
- **Só livros** (09/11/2026). Eletrônicos, móveis e outros foram removidos: sem código de barras para automatizar o preenchimento, o cadastro manual não compensa. Também saíram os campos defeitos/observações, preço e descrição. O nome do app é **Baracata**.
- Publicação nos marketplaces é manual: o app prepara texto e fotos; a pessoa copia e anexa na OLX/Facebook Marketplace.

## O que o app faz

Cada livro (uma unidade) tem título, estado (Novo/Seminovo/Usado), dados do livro (ISBN, autor, editora, ano da edição, páginas, sinopse) e fotos ordenadas (a primeira é a capa, mostrada na lista). O texto do anúncio é composto por regras simples a partir desses dados; nada é inventado.

- **Onde já foi cadastrado:** cada livro na lista tem um botão ✓ que abre as lojas (Amazon, Mercado Livre, OLX, Estante Virtual, Marketplace do Facebook, Shopee, Enjoei, Magazine Luiza) para marcar onde o livro já foi anunciado. A lista mostra "Em: …" ou "Ainda não cadastrado" e tem filtros Todos / Faltam cadastrar / Já cadastrados. A marcação não altera os outros dados nem a ordem da lista.

- **Livros:** ISBN digitado ou lido pela câmera (leitor do Google Play Services). A busca preenche só campos vazios (título, autor, editora, ano da edição, páginas, sinopse) e guarda fonte, URL e data da consulta; correções manuais nunca são sobrescritas. Fontes: BrasilAPI, Open Library (sinopse e outros títulos) e, se a pessoa configurar uma chave de API em ⚙, Google Books. Capas encontradas viram foto 1 (uma só, automática) ou opções para escolher. Nem toda fonte tem sinopse ou capa; a pessoa confere a edição.
- **Fotos:** escolhidas pelo seletor do sistema (sem permissão de galeria), copiadas para a pasta privada do app, reordenáveis, removíveis; "Exportar fotos" salva numeradas (01, 02…) em Imagens/AppDeVendas/<título> para anexar manualmente.
- **Textos:** copiar título e descrição separadamente, inclusive offline.
- **Dados locais:** SQLite (SQLDelight) com migrações; "Apagar dados" na lista (com confirmação) e exclusão item a item. Tema escuro por padrão, claro opcional.

## Pendências e limites conhecidos

- **Feito em 09/11/2026:** backup (⚙ → Salvar backup / Restaurar, em .zip, sem a chave do Google); botão voltar do Android com confirmação ao sair sem salvar; aviso de ISBN já cadastrado; busca e ordenação na lista; tirar foto pelo app; APK assinado com chave própria (pasta `assinatura`, com LEIA-ME); guia de instalação em outro celular (`GUIA-INSTALAR-NO-CELULAR.md`); interface reorganizada (identificar → dados → fotos → texto do anúncio).
- Orientação EXIF só é corrigida na miniatura; a foto exportada é a original.
- Fotos de uma escolha ou da câmera se perdem se o Android encerrar o app com o seletor/câmera aberto.
- Sinopse vem de texto de terceiros (ex.: catálogo em inglês): conferir, reescrever se quiser; só usar o que for permitido reutilizar.
- Livros especiais (estojos, box): o ISBN descreve o estojo; o título certo pode estar em "outros títulos" da fonte. Alguns boxes não existem em nenhuma base (busca manual).
- Exportar fotos para a galeria exige Android 10 ou mais novo.
- Backup é manual: o app não lembra de fazer. Guardar o arquivo fora do celular.
- **A chave de assinatura (`assinatura/baracata.jks` + `keystore.properties`) precisa de cópia em pelo menos 2 lugares fora do computador.** Perdê-la impede atualizar o app sem desinstalar (e apagar os dados).
- Validar termos de uso de BrasilAPI e Open Library; mostrar mais de um candidato quando houver ISBN ambíguo (feito por título; por ISBN, só um).

## Verificações antes de considerar pronto

- Fotos: várias imagens, ordem, cancelamento do seletor, pouco espaço, exclusão do original da galeria depois de importar.
- ISBN: livro físico, reflexo, código ilegível (sempre com digitação manual), ISBN inexistente, ausência de sinopse/capa.
- Modo avião: cadastrar com fotos, fechar/reabrir e recuperar tudo; consultas de rede falham sem bloquear o cadastro.
- Atualizar o APK preservando banco e fotos (migração).
- Preparar e anexar manualmente anúncios reais (livro, PS4, sofá) na OLX e no Facebook, conferindo acentos ao colar e ajustes exigidos pelas plataformas.

## Fontes consultadas

- Google Books API: uso e pesquisa (https://developers.google.com/books/docs/v1/using), consultada em 29/10/2026. Sem chave, a cota anônima costuma estar esgotada (HTTP 429); a Books API em si não tem custo de consulta, mas exige projeto no Google Cloud.
- BrasilAPI (https://brasilapi.com.br) e Open Library (https://openlibrary.org/developers/api), testadas na prática em 08–09/11/2026. A documentação de ML Kit, Compose e SQLDelight não foi lida a fundo; versões em uso estão nos arquivos Gradle.
