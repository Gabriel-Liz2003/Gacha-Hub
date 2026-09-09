# Arquitetura

MVVM, Kotlin/Compose/Material 3. `HubViewModel` expõe StateFlow e serializa ações do
usuário. `Repository` valida operações e usa transações Room. Não é um backend;
o usuário controla dados locais e fontes de conteúdo.

- `Models.kt`: tipos serializáveis para jogos, catálogo, conta, owned, gear, fonte,
  build, benchmark, custos, materiais, projeto, time, banner e backup.
- `Database.kt`: registros Room com chave `(kind,id)` e payload tipado JSON;
  tabela de cache separada. Schema v3; migrations 1→2 (cache) e 2→3 (catálogo em registros pequenos).
- `Repository.kt`: persistência, importação com merge, snapshots de projetos,
  inventário, backup e atualização atômica.
- `Importers.kt`: interface extensível AccountImporter, Enka, parsing defensivo,
  HTTP com timeout, HTTPS, tamanho limitado, User-Agent, TTL e espera 429.
- `Planner.kt`: encadeamento exato de custos e combinação de times por preferência
  da fonte, respeitando posse e sem repetir personagem em duas vagas.
- `core/ResourceMath.java`: domínio JVM independente do Android, com contas
  inteiras, overflow verificado, alocação, progresso, filtros, cache e reset.
  Java permite testes de produção sem Gradle; UI e restante da arquitetura são Kotlin.
- `ui/`: navegação interna por estado Compose e editores. Não usa navegador embutido.

Tradeoff: payloads JSON tipados em Room mantêm flexibilidade de jogos sem quatro
schemas divergentes. Não há foreign keys SQL internas aos payloads; invariantes
são validados no Repository. Uma alteração em Owned exige serializar sua conta;
adequado para coleções pessoais pequenas, não para milhões de registros.

Dados são separados por jogo e por conta. Inventário de duas contas não se mistura.
Reservas são derivadas dos projetos ativos ordenados por prioridade e ID. Progresso
é a média das frações preenchidas por material; moeda não domina visualmente bosses.
Concluir um projeto consome os materiais em transação. Exclusão de projetos e
edição completa de times/projetos existentes ainda não têm interface dedicada.

Adicionar jogo exige enum/configuração, campos específicos se necessários, catálogo,
fontes de custos, adaptador e testes de contratos reais. As abstrações não afirmam
que todo jogo usa automaticamente as mesmas regras de ascensão/equipamentos.

## Catálogo grande (0.3)

Room v3 mantém os metadados em `content/current` e cada personagem, build, material,
custo, time-guia e banner em registros `catalog_*` separados, com limite de 512 KB
por registro. Listas são reconstruídas por índice estável. A atualização é transacional.

A migration 2→3 lê `substr(payload)` do antigo registro de conteúdo em blocos de
32.768 caracteres, permitindo recuperar inclusive bancos que já tenham um registro
maior que o CursorWindow. Contas e projetos não são reescritos. Não se altera o limite
interno do Android por reflexão. O cache de decodificação é invalidado pelos registros
de catálogo completos; parsing e reconstrução ficam fora da thread da interface.

Pacotes grandes usam schemaVersion 2. Leitores 0.1/0.2 aceitam somente schema 1 e
rejeitam o pacote antes da escrita, preservando o catálogo anterior.
