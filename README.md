# Gacha Hub — Android

**Estado: APK de desenvolvimento compilado e testado no GitHub Actions.**
A cobertura funcional e de conteúdo ainda é parcial; os critérios do app completo
não estão todos atendidos. Veja as limitações abaixo.

Build validado: https://github.com/Gabriel-Liz2003/Gacha-Hub/actions/runs/34313485400

APK (artifact ZIP, login GitHub necessário):
https://github.com/Gabriel-Liz2003/Gacha-Hub/actions/runs/34313485400/artifacts/10089306797


App nativo para Zenless Zone Zero, Honkai: Star Rail, Genshin Impact e Wuthering
Waves. Kotlin, Jetpack Compose, Material 3, Room, Coroutines/Flow, OkHttp e Coil.
Android 8+ (API 26), target/compile SDK 35. Sem WebView, Electron ou Chromium.

## O que o código já implementa

- Jogos e contas separados; várias contas por jogo.
- Cadastro manual de personagens, filtros textuais e de raridade/posse/favoritos.
- Importação JSON normalizada para os quatro jogos.
- Adaptadores Enka de vitrine pública para Genshin, HSR e ZZZ.
- Nível, ascensão, duplicatas, habilidades, arma, equipamentos, atributos e favorito.
- Editor de atributos por campos; editor avançado JSON para habilidades e equipamentos.
- Builds específicas, alternativas, slots, prioridades e benchmarks com fonte/data/patch.
- Comparador por benchmark, com contexto e faixas editoriais explícitas.
- Planejador de trilhas de custos exatos, com rejeição de lacunas.
- Checklist manual com campos de quantidades e cadastro de materiais próprios.
- Reserva de inventário compartilhado por prioridade e consumo explícito ao concluir.
- Times manuais com edição, exclusão e seleção de alternativas da fonte que o usuário possui.
- Objetivos por campos Atual/Objetivo, edição de planos e prioridades, exclusão sem alterar inventário.
- Visão global dos projetos, separando contas e ordenando por jogo, personagem, prioridade ou progresso.
- Calendário por fuso fixo e horário de reset do servidor.
- Persistência Room transacional, cache Enka com TTL e espera após HTTP 429.
- Exportação/restauração por seletor de documentos Android, sem permissão de armazenamento.
- Pacotes de conteúdo JSON locais ou por HTTPS. Conteúdo salvo fica disponível offline.

O build, lint, testes Kotlin e quatro testes de instrumentação passaram em
emulador Android 15. Isso não cobre todos os aparelhos nem importação real por UID.

## Conteúdo inicial — cobertura real

O pacote embutido tem **14 personagens, 4 builds, 3 guias de times, 14 materiais e
2 tabelas totais de ascensão**. A contagem é do catálogo local, nunca uma alegação
de roster completo. Os personagens adicionais podem ser cadastrados ou importados.

Builds: Ellen, Tingyun, Bennett e Encore. Somente os trechos resumidos e referenciados
foram incluídos; o app não espelha guias inteiros nem faz scraping periódico.
O patch da recomendação pode ser anterior ao patch atual, mesmo quando a página
foi consultada recentemente. Datas de consulta e atualização são distintas.

Custos: ascensão total de Tingyun e Encore, **sem EXP de nível**, arma ou habilidades.
Uma tabela de ascensão total 0→6 não pode calcular 2→6 por divisão proporcional.
O motor aceita custos por nível, habilidades e armas através de novas trilhas,
mas essas tabelas ainda precisam de curadoria e validação para cobertura completa.
Não há rendimentos/energia de farm nem banners documentados no pacote inicial.

## Como compilar e baixar pelo celular

Código publicado na branch `main`:
https://github.com/Gabriel-Liz2003/Gacha-Hub

PR de desenvolvimento: https://github.com/Gabriel-Liz2003/Gacha-Hub/pull/1

O workflow está na branch padrão. O botão manual **Run workflow** está disponível
na página Actions; pushes e pull requests também iniciam a compilação.

Após a publicação do código:

1. Abra o repositório no navegador do celular e entre em **Actions → Android APK**.
2. Use **Run workflow**, selecione a branch e confirme. Push e PR também disparam.
3. Aguarde os jobs `build` e `device-tests`. Um APK no artifact, sozinho, não
   significa que os testes de dispositivo passaram.
4. Abra a execução e baixe **GachaHub-debug-N** em Artifacts, com a conta logada.
5. Extraia o ZIP com o gerenciador de arquivos do Android e abra `app-debug.apk`.
6. Autorize instalação para o navegador/gerenciador usado, se o Android solicitar.

O artifact dura 30 dias. A assinatura é debug, criada pelo runner; builds de runners
diferentes podem não atualizar a instalação anterior. **Exporte o backup antes de
reinstalar**, pois desinstalar apaga o banco. Uma chave de assinatura persistente
para distribuição real ainda precisa ser configurada em GitHub Secrets.

Tags `v*` geram uma **prerelease de desenvolvimento**, apenas quando ambos os jobs
passam. O APK da prerelease também é debug. A pipeline verifica a assinatura com
`apksigner`; ela não apresenta APK debug como release de produção.

Não precisa Android Studio nem PC. Gradle 8.11.1 e JDK 17 são instalados pelo Actions.
Não há Gradle Wrapper binário no pacote; a pipeline usa `gradle/actions/setup-gradle`
com versão fixa e executa `gradle` diretamente.

## Importação por jogo

| Jogo | Método no código | Limitações |
|---|---|---|
| Genshin | Enka UID, JSON, manual | Vitrine; atributos finais disponíveis em `fightPropMap`; nomes de equipamentos não resolvidos |
| HSR | Enka UID, JSON, manual | Vitrine; atributos finais requerem JSON normalizado/manual; IDs e dados brutos preservados |
| ZZZ | Enka UID, JSON, manual | Vitrine; atributos finais e Potential não calculados; dados brutos preservados |
| WuWa | JSON e manual | Nenhum endpoint seguro de UID foi verificado; nenhuma integração fictícia |

Enka não é API oficial HoYoverse e não fornece o inventário completo de materiais.
Importar novamente não remove personagens que deixaram de ser exibidos. Favoritos,
notas e build escolhida são preservados; stats manuais sobrevivem quando a resposta
não fornece stats. Confira sua atualização manual após trocar equipamentos.
IDs desconhecidos recebem nome provisório e preservam o JSON original. Podem ser
resolvidos em um próximo pacote ou em importação JSON com o mesmo ID.

HoYoLAB/Battle Chronicle não foi integrado: não foi validado um fluxo oficial de
autorização de terceiros sem uso de credenciais privadas. O projeto não pede senha,
cookies, tokens de sessão, login em WebView ou interceptação de tráfego.

### JSON de importação

Selecione uma conta e use **Conta → Importar personagens por JSON**.
Exemplo pronto: `content/import-example.json`.

```json
{
  "schemaVersion": 1,
  "game": "GENSHIN",
  "owned": [{
    "characterId": "gi:bennett",
    "level": 40,
    "copies": 1,
    "ascension": 1,
    "stats": {"Energy Recharge": 180.0},
    "skills": {"Burst": 4, "Skill": 4, "Normal Attack": 1}
  }]
}
```

Personagens novos entram no campo `characters` (id, game, name, rarity 4/5,
element, specialty, role, image HTTPS e providerId opcional). Seus IDs devem ser
únicos e estáveis. Todos devem pertencer ao jogo da conta selecionada.

Porcentagens são números de tela (`70.0`, não `0.7`). Use os nomes exatos dos
benchmarks. `weapon` e `equipment` seguem `Gear` em `data/Models.kt`; `equipment`
é uma lista com slots únicos. Habilidades são um mapa de nome/ID → nível base.
Arquivo máximo de 8 MiB. Importação inválida é revertida integralmente.

## Atualizar conteúdo sem novo APK

Edite uma cópia de `content/starter.json`, mantenha `schemaVersion: 1`, aumente
`version` e informe `publishedAt`, cobertura e proveniência de cada build/custo.
Use **Importar pacote de conteúdo**, ou publique um JSON em HTTPS e informe sua
URL na tela inicial. Links devem retornar JSON diretamente (não páginas HTML,
redirecionamentos ou páginas GitHub `blob`). Não há serviço de hospedagem provisionado
nem manutenção editorial automática nesta entrega.

Os pacotes são atualizações editoriais completas; registros antigos de materiais e
personagens removidos são preservados para referências de contas/projetos. Projetos
guardam o snapshot de custos da criação; uma atualização não muda um projeto ativo.
Novos projetos usam a nova tabela. Não são aceitos downgrades nem mudanças de jogo
para um ID já registrado. Nomes/URLs de fontes não são prova de qualidade: pacotes
externos só devem ser importados de curadores em quem o usuário confia.

`CostStep` usa `characterId`, `track`, `from`, `to`, `costs` e `source`.
Uma trilha poderia ser `level`, `skill:burst`, `weapon:sapwood:level` ou `ascension`.
Cada ligação cobre EXATAMENTE uma transição documentada, sem interpolação. Os IDs
de materiais devem existir e pertencer ao jogo do personagem. A UI ainda tem
editor JSON avançado de objetivos para trilhas múltiplas.

Comparador: cada benchmark define `low`, `adequate`, `excellent`, unidade e contexto.
As fronteiras abaixo da faixa recomendada são classificações editoriais do app,
não recomendações atribuídas à fonte. No pacote inicial `low` = 80% do mínimo
recomendado. Ultrapassar benchmark não significa ganhar DPS. Não há cálculo de
buffs, breakpoints condicionais, dano ou otimização automática de equipamentos.

## Arquitetura e testes

Leia `docs/ARCHITECTURE.md`, `docs/API-RESEARCH.md`, `docs/ACCEPTANCE.md` e
`docs/VALIDATION.md`. O banco não usa migrations destrutivas.

Execução com JDK 17 e ferramentas de build disponíveis:

```sh
bash scripts/test-core.sh
python3 -m unittest discover -s tests -v
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
gradle :app:connectedDebugAndroidTest
```

Todos os comandos passaram no GitHub Actions, incluindo testes em emulador Android 15.
Não há resultados simulados nem selo de CI verde sem uma execução real.

## Privacidade e atribuição

Permissão única: INTERNET. Backup do Android desativado. Backup exportado é JSON
em texto simples e pode conter UID, personagens e configurações da conta. Não
exporta cache bruto de consultas, mas inclui os dados brutos de cada personagem
importado. Não compartilhe sem revisar. Imagens são carregadas pelos provedores;
Coil pode manter cache sujeito à limpeza do Android. Sem garantia de todas as
imagens offline. Os dados estruturados ficam em Room.

Nomes, personagens e imagens pertencem aos titulares dos jogos e provedores
indicados. Projeto não oficial, sem afiliação com HoYoverse, Kuro, Enka, KQM ou
Prydwen. As recomendações são sínteses com links para as análises originais.
