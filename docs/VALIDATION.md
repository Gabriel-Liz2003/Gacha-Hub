# Verificação executada — 2026-09-08

## Resultados observados

- `bash scripts/test-core.sh`: PASS, **2.036 asserções** sobre o código de produção
  `ResourceMath.java`. Inclui 1.000 cenários determinísticos de alocação de inventário
  com duas verificações cada. Não são 2.036 testes Android.
- `python3 -m unittest discover -s tests -v`: **9 testes passaram**. Validam pacote,
  referências, separação dos jogos, fontes, quantidades verificadas, contagens,
  cópia idêntica do asset e permissão única INTERNET.
- Java 17 disponível. O launcher `javac` não estava no PATH, mas o módulo
  `jdk.compiler` estava presente; compilação foi executada com
  `java -m jdk.compiler/com.sun.tools.javac.Main`.

## Testes escritos, NÃO executados nesta sessão

- JUnit Kotlin: custos exatos, lacunas, downgrade, trilhas duplicadas, schema,
  custos cruzados entre jogos, fontes, parsing Genshin/HSR/ZZZ, vitrine vazia,
  WuWa rejeitado, níveis inválidos, combinações de time e leitura limitada.
- Instrumentação Room: conta → vitrine → build → projeto → inventário → time →
  fechar banco → reabrir → comparação integral → backup/restauração.
- Migration de banco v1 real para v2, preservando uma conta existente.
- Rollback de importação inválida, atualização e rejeição de downgrade de conteúdo.
- Compose: abrir ZZZ, criar conta e acessar importação.

O teste de reabertura de banco cobre persistência, mas não é um teste de encerramento
forçado do processo Android. O fluxo SAF de importação/exportação precisa de teste
manual no dispositivo, assim como teclado, acessibilidade, tamanhos de tela e imagens.

## Ambiente e CI

Gradle, Android SDK e emulador não estavam instalados. A tentativa de acesso para
baixar dependências foi bloqueada/cancelada antes da decisão de rede; não houve
escalada alternativa nem resolução de dependências Android. A documentação pública
foi consultada por ferramentas disponíveis para pesquisa.

**GitHub Actions: NÃO EXECUTADO.** Sem URL de run, job ou logs remotos para reportar.
**APK: NÃO GERADO.** Não existe arquivo instalável nesta entrega.
**Instalação: NÃO TESTADA.**
**Compilação Kotlin/Compose/Room: NÃO VERIFICADA.** Pode conter erros a corrigir no CI.

Não se deve interpretar testes de conteúdo e domínio como verificação de compilação
Android, interface, integração real com conta ou satisfação de todos os requisitos.
