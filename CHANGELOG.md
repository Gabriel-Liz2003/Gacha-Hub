# Changelog

## 0.3.0 — catálogo e evolução Genshin

- Pacote v2: 120 personagens Genshin, ascensões por etapa e talentos 1–10 para 118.
- Dias e locais de farm dos materiais, fontes fixadas por commit e licença MIT.
- Gerador reproduzível e testes de todas as quantidades, etapas parciais e triple crown.
- Dados novos embutidos atualizam o banco ao abrir; projetos antigos são preservados.
- Parsing do catálogo fora da thread de interface e cache do conteúdo decodificado.
- Room v3: catálogo dividido em registros menores; migration recupera JSON legado acima de 2 MB.
- Schema de conteúdo 2 impede APKs antigos de aceitarem pacotes grandes incompatíveis.
- EXP, armas, talentos do Viajante e expansão dos outros três jogos ainda pendentes.


## 0.2.0 — gerenciamento de projetos e times

- Campos de objetivos por trilha, sem exigir JSON ou presumir a etapa atual.
- Atualização de conteúdo por botão, usando o pacote publicado na main; reconsulta idempotente.
- Edição e exclusão de times; edição, prioridade e exclusão de projetos.
- Visão global com ordenação por jogo, personagem, prioridade e progresso.
- Reservas recalculadas sem gastar inventário; projetos concluídos mantêm custos históricos.
- Testes Android de edição, exclusão, isolamento por conta e persistência.
- Cobertura de conteúdo permanece parcial.


## 0.1.0 — APK de desenvolvimento

- Estrutura Android nativa e persistência local transacional.
- Contas multijogo, vitrine Enka e fallback JSON/manual.
- Conteúdo curado parcial, comparador, planejador de transições e inventário reservado.
- Times, favoritos, calendário e backup explícito.
- Workflow de build/testes/APK e prerelease de desenvolvimento.
- Testes locais de domínio e conteúdo executados.
- Publicado no PR #1, compilado e testado em Android 15 no GitHub Actions.
- Corrigido conflito do tipo Target com anotação Kotlin.
- Pendentes: QA ampliado e cobertura de conteúdo.
