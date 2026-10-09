# Palavreiro — plano de design do app de celular

Em 08/10/2026 o Daniel decidiu as escolhas principais e depois passou as demais decisões de design para o Claude.

## Decidido

| Item | Decisão |
|---|---|
| Nome do app | **Palavreiro** |
| Estilo | Orgânico e arredondado (formas suaves, cantos bem redondos, sem bordas duras) |
| Paleta | **Noite suave** — fundo azul-escuro/roxo, tons aveludados |
| Cores das letras | Verde (lugar certo) e amarelo (fora do lugar), o padrão do Termo |
| Fonte | Geométrica |
| Tela inicial | Menu com cartões, um por jogo (Termo, Dueto, Quarteto e outros) |
| Perfil | Ícone de pessoa no canto superior direito; abre Estatísticas, Conquistas e Configurações |
| Rodapé | Apenas os créditos: foto redonda + **"criado por: @dmwnezes"**, com link para o Instagram |
| Abertura | Nome do app + créditos, **sem frase** embaixo do nome |
| Dedicatória | Nenhuma (igual ao Sintonia) |
| Ícone do app | **Cinco quadradinhos**: uma fileira de 5 quadrados arredondados, alguns verdes e um amarelo |
| Reações | Vibração (ao errar e ao acertar), sons curtos (ao digitar e revelar) e confete na vitória |

## Detalhes definidos pelo Claude

| Item | Decisão |
|---|---|
| Tons da Noite suave | Fundo em degradê `#2A2058` → `#120E2B`; superfícies `#231C48` / `#2F275C`; texto `#F4F1FF`; destaque lilás `#9B8CFF` |
| Verde e amarelo | `#5FB873` (lugar certo) e `#E6C14F` (fora do lugar, com letra escura para ler bem) |
| Fonte | Outfit (geométrica, arredondada, gratuita) |
| Cantos | Quadrados 14 dp, teclas 12 dp, cartões 28 dp, janelas 32 dp |
| Cartões | Termo (com selo Novo / Feito hoje), Infinito, Dueto e Quarteto (em breve); cada um com mini tabuleiro |
| Conquistas | 8: primeira palavra, de primeira, em duas, sequências de 3/7/30, 25 no Infinito, 100 no total |
| Configurações | Sons e vibração podem ser desligados; Como jogar; Buscar atualização |
| Ícone | Fundo roxo em degradê com 5 quadradinhos: verde, verde, amarelo, verde, verde |
| Formato | App Android nativo (Kotlin + Compose), como o Sintonia; site com os mesmos jogos e visual |
| Atualização | Dentro do app: verifica ao abrir e em Perfil > Buscar atualização; baixa o APK das Releases do GitHub e abre o instalador |

## App Android

- Código em `app/`; a lista de palavras é a mesma do site (`shared/words.js`), então a palavra do dia é igual nos dois.
- Cada envio ao GitHub que muda o app gera um APK novo em Releases (`Palavreiro.apk`).

## Já funcionando na versão web

- Letra em qualquer posição: toque num quadrado da linha para escolher onde a letra entra.
- Modos Diário e Infinito; Dueto e Quarteto ainda "em breve".
- Créditos na abertura e na janela "Como jogar".

## Jogos (app Android)

| Jogo | Como funciona |
|---|---|
| Termo | Uma palavra por dia, 6 tentativas |
| Infinito | Palavras sorteadas sem limite |
| Dueto | 2 palavras ao mesmo tempo, 7 tentativas; desafio do dia e depois partidas livres |
| Quarteto | 4 palavras ao mesmo tempo, 9 tentativas; teclas divididas em 4 cores |
| Conexões | 16 palavras em 4 grupos (amarelo, verde, azul, roxo), 4 erros; 30 desafios em `app/src/main/assets/conexoes.txt` |
| Caça-Palavras | Grade 10×10 do tema do dia, arrastar o dedo para marcar, cronômetro; 30 temas em `caca.txt` |
| Reverso | O app tenta adivinhar a palavra que a pessoa pensou; ela marca as cores |
| Qual é a Palavra? | Definição escrita à mão, 3 chances, cada erro revela uma letra; `definicoes.txt` |
| Sinônimos | 4 opções e 10 s por palavra, cadeia até errar; `sinonimos.txt` (linhas com `=` evitam alternativas ambíguas) |

Para acrescentar conteúdo, basta editar os arquivos `.txt` em `shared/` (usados pelo app e pelo site).

## Recursos extras (app e site)

| Recurso | Onde |
|---|---|
| Modo difícil | Perfil > Configurações; verdes no lugar e amarelos obrigatórios (Termo e Infinito) |
| Dicas 💡 | Lâmpada no topo do Termo/Dueto/Quarteto; até 2 por partida, aparecem no compartilhamento |
| O que significa | Ao fim da partida, significado escrito para cada uma das 1000 palavras (`shared/significados.txt`) |
| Calendário | Perfil; cor mais forte = mais jogos no dia; pontinho verde/vermelho = Termo do dia |
| Cartão para Stories | Imagem 1080×1920 com o resultado, no fim do Termo, Dueto, Quarteto, Conexões e Caça-Palavras |
| Desafio por link | "Desafiar um amigo": gera `dmwnezes.github.io/termo/?d=...`, que abre no site ou no app |
| Widget (app) | Sequência e status dos 5 jogos do dia; toque abre o jogo |
| Lembrete diário (app) | Perfil > Configurações; notificação no horário escolhido, só se o Termo do dia não foi jogado |
| Atalhos do ícone (app) | Segurar o ícone: Termo, Conexões, Infinito, Caça-Palavras |

