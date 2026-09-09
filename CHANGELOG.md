# Changelog

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
