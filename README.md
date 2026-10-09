# Palavreiro

Jogos de palavras em português: Termo, Infinito, Dueto, Quarteto, Conexões, Caça-Palavras, Reverso, Qual é a Palavra? e Sinônimos. O site tem o Termo; o app Android tem todos.

- **Site:** https://dmwnezes.github.io/termo/
- **App Android:** baixe o `Palavreiro.apk` mais recente em Releases. O app avisa sozinho quando há versão nova.

Jogo estilo Termo, com palavras de 5 letras.

Modos:
- Diário: uma palavra por dia, igual para todos.
- Infinito: quantas palavras quiser, com estatísticas separadas.

Vocabulário: 1000 palavras possíveis como resposta e cerca de 14.000 aceitas como tentativa. Os acentos aparecem sozinhos.

Regras das respostas: sem pronomes, sem verbos conjugados (só infinitivo), sem plurais, palavras com masculino e feminino sempre no masculino e sem nomes de pessoas. Nomes de pessoas também não são aceitos como tentativa.

Para testar: abra o arquivo index.html no navegador.

Estrutura:
- index.html, style.css: interface
- shared/words.js: ANSWERS (respostas) e VALID (tentativas extras aceitas), usado pelo site e pelo app
- app/: app Android (Kotlin + Compose)
- DESIGN.md: decisões de design
- game.js: regras do jogo e estatísticas

Próximos modos previstos: Dueto e Quarteto.

Publicar uma atualização: aumente o número `?v=` dos arquivos no index.html, para o navegador baixar a versão nova.
