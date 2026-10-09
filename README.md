# Palavreiro

Jogos de palavras em português: Termo, Infinito, Dueto, Quarteto, Conexões, Caça-Palavras, Reverso, Qual é a Palavra? e Sinônimos. O site e o app Android têm todos os jogos, com a mesma palavra do dia.

- **Site:** https://dmwnezes.github.io/termo/
- **App Android:** https://github.com/dmwnezes/termo/releases/latest/download/Palavreiro.apk (o app avisa sozinho quando há versão nova)

## Palavras

1000 palavras possíveis como resposta, cada uma com significado, e cerca de 13.000 aceitas como tentativa. Os acentos aparecem sozinhos.

Regras das respostas: sem pronomes, sem verbos conjugados (só infinitivo), sem plurais, palavras com masculino e feminino sempre no masculino e sem nomes de pessoas. Nomes de pessoas também não são aceitos como tentativa.

## Estrutura

- `shared/`: palavras (`words.js`), significados, desafios do Conexões, temas do Caça-Palavras, definições e sinônimos, usados pelo site e pelo app
- `index.html`, `style.css`, `js/`: site
- `app/`: app Android (Kotlin + Compose)
- `DESIGN.md`: decisões de design e recursos

## Publicar

- Site: aumente o número `?v=` dos arquivos no `index.html` (e a constante `V` em `js/core.js`), para o navegador baixar a versão nova.
- App: cada envio que muda `app/` ou `shared/` gera um APK novo em Releases.
