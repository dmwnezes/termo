# Palavreiro

Jogos em português: 11 de palavras (Termo, Dueto, Quarteto, Conexões, Caça-Palavras, Palavra Cruzada, Intruso, Certo ou Errado, Qual é a Palavra?, Sinônimo ou Antônimo e Jogo da Memória), 5 de lógica (Mestre Mandou, Matiz, Quadrante, Xeque e Batalha Naval) e 3 de conhecimento (Trivia, Pódio e Ampulheta), mais partidas ao vivo com amigos. O site e o app Android têm a mesma palavra do dia.

- **Site:** https://dmwnezes.com/termo/ (funciona sem internet depois da primeira visita, menos as partidas com amigos)
- **App Android:** https://github.com/dmwnezes/termo/releases/latest/download/Palavreiro.apk (o app avisa sozinho quando há versão nova)

## Palavras

1227 palavras possíveis como resposta, cada uma com significado, e cerca de 6.000 aceitas como tentativa. Os acentos aparecem sozinhos.

Regras das respostas: sem pronomes, sem verbos conjugados (só infinitivo), sem plurais, palavras com masculino e feminino sempre no masculino e sem nomes de pessoas. Nomes de pessoas também não são aceitos como tentativa.

## Estrutura

- `shared/`: palavras (`words.js`), significados, desafios do Conexões, temas do Caça-Palavras, definições e sinônimos, usados pelo site e pelo app
- `index.html`, `style.css`, `js/`, `sw.js`, `manifest.webmanifest`: site
- `tests/`: testes do site; `scripts/gerar-cruzada.js`: gera `shared/cruzada.txt`
- `app/`: app Android (Kotlin + Compose)
- `DESIGN.md`: decisões de design e recursos

## Publicar

- Site: é só enviar para o `main`. Os arquivos não têm `?v=`: o `sw.js` busca sempre a versão nova. Arquivo novo em `js/`, `shared/` ou `img/` precisa entrar na lista `FILES` do `sw.js` (o teste avisa).
- App: cada envio que muda `app/` ou `shared/` gera um APK novo em Releases.
- Conteúdo diário: acrescente sempre no fim dos arquivos. `P.dailyCount` (site) e `dailyIndex` (app) mantêm os dias anteriores a 03/11/2026 como estavam, por causa do Arquivo.

## Testes

- Site: `npm i playwright && npx playwright install chromium && node --test tests/site.test.js` (rodam no GitHub a cada envio).
- App: `./gradlew testDebugUnitTest`.
