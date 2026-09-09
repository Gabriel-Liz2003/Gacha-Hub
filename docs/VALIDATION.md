# Verificação — 2026-09-09

## GitHub Actions confirmado

- Run: https://github.com/Gabriel-Liz2003/Gacha-Hub/actions/runs/34313485400
- Commit: `6c401a5ec4297e336bd39942258979a95be9a68d`
- `build`: success (Gradle, testes Kotlin, lint, assembleDebug, apksigner).
- `device-tests`: success, quatro testes em emulador Android 15.
- `release`: skipped, esperado porque o evento não é uma tag.
- APK: artifact `GachaHub-debug-4`, ID `10089306797`, ZIP de 11.350.922 bytes.
- Expiração do APK artifact: 2026-10-09.

O primeiro build falhou por conflito de importação de `Target` com a anotação Kotlin.
Importações explícitas do tipo do planejador corrigiram a compilação. A nova execução
acima concluiu ambos os jobs com sucesso.

## Cobertura validada

2.036 asserções JVM de domínio e nove testes Python de conteúdo passaram localmente
e no Actions. A suíte JUnit Kotlin passou no job de build. Inclui parsing dos três
adaptadores Enka, rejeição de WuWa sem endpoint, custos exatos, validação e times.

Quatro testes de instrumentação passaram:

1. Conta → importação de fixture → build → plano → inventário → time → fechar banco
   → reabrir → comparar dados → backup e restauração.
2. Migration real v1→v2 preservando conta e criando cache.
3. Rollback de importação inválida, atualização e rejeição de downgrade de conteúdo.
4. Compose: abrir jogo, criar conta e acessar a tela de UID.

Os testes de importação usam fixtures, não contas reais. Reabrir o banco não equivale
a encerrar à força o processo Android. SAF, servidores reais, outros tamanhos de tela,
acessibilidade e imagens ainda precisam de cobertura adicional.

## Limitações remanescentes

Catálogo, custos de evolução, calendários e builds são parciais. CI verde não implica
cumprimento dos 28 itens do pedido. A auditoria está em ACCEPTANCE.md. O APK usa chave
debug do runner e pode exigir backup/reinstalação ao trocar de build.

O SDK/Gradle local continuam indisponíveis; a compilação Android foi feita remotamente.
