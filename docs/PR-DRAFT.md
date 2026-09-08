# feat: establish native multigame Gacha Hub

Centraliza coleções, projetos e referências de quatro jogos mantendo contas e
inventários separados. Acrescenta persistência Room, importação de vitrine pública,
fallback JSON/manual, curadoria parcial de builds e motor de custos que rejeita
transições sem documentação.

Arquivos principais: modelos/Repository/Importers/Planner, UI Compose, pacote
starter, ResourceMath e workflow Android. Conteúdo é atualizável sem novo APK.

Validação executada: 2.036 asserções JVM e 9 testes Python do conteúdo. Testes Kotlin,
instrumentação, migration e Compose estão escritos e aguardam CI. Sem screenshots
porque a interface não foi renderizada em dispositivo.

Este PR deve continuar em draft: falta validar compilação/APK, integrar um feed
editorial, expandir tabelas/catálogos e completar recursos listados em ACCEPTANCE.md.
