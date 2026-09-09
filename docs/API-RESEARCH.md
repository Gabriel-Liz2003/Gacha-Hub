# Pesquisa de integrações — 2026-09-08

## Documentação efetivamente consultada

1. https://github.com/EnkaNetwork/API-docs/blob/master/api.md
   Documenta vitrine, User-Agent próprio, rate limits dinâmicos, TTL e códigos
   400/404/424/429/500/503. Não permite enumeração massiva de UIDs.
2. https://github.com/EnkaNetwork/API-docs/blob/master/docs/zzz/api.md
   Campos de AvatarList, Weapon, EquippedList e requisitos de dados externos para
   atributos. Não se deve tratar base de equipamento como atributo final do agente.
3. https://github.com/seriaati/enka-py/blob/main/enka/constants/common.py
   Confirma URLs de Genshin, HSR e ZZZ usadas no adaptador. SHA consultado:
   49368dfa05c72b76dcbbf22ccd4927f40d4beeb5.
4. https://github.com/seriaati/enka-py/blob/main/enka/models/hsr/response.py
   Confirma `detailInfo.avatarDetailList`. Não confundir com lista no root.
5. https://github.com/seriaati/enka-py/blob/main/enka/models/hsr/character.py
   Confirma avatarId, rank, equipment, relicList, skillTreeList e cálculo posterior
   de atributos através de assets.
6. https://github.com/seriaati/enka-py/blob/main/enka/models/zzz/character.py
   Confirma PromotionLevel, UpgradeLevel, SkillLevelList, Index, Equipment.
7. https://enka.network/ e https://status.enka.network/
   Páginas consultadas; não substituem teste de todos os endpoints por UID real.
8. https://enka.network/api/uid/618285856
   A URL de exemplo documentada respondeu como JSON na ferramenta web. Não houve
   execução do cliente Android. Nenhum UID foi varrido/enumerado.

## Endpoints implementados

- GET `https://enka.network/api/uid/{uid}`
- GET `https://enka.network/api/hsr/uid/{uid}`
- GET `https://enka.network/api/zzz/uid/{uid}`

São endpoints comunitários, não oficiais. Nenhuma autenticação é enviada. HSR e
ZZZ estão ancorados em documentação/código do mantenedor, mas o funcionamento ao
vivo nesta sessão não foi confirmado com contas reais. O parser mantém dados brutos
para evolução futura e não inventa atributos finais.

## HoYoLAB / Battle Chronicle

https://github.com/seriaati/genshin.py foi consultado como wrapper comunitário.
Não foi validada documentação de uma autorização oficial de terceiros apropriada
para este aplicativo sem credenciais de sessão. Portanto, não foi implementado
login HoYoLAB, extração de cookies, tokens privados ou coleta de senha. Isso é uma
limitação de integração, não uma afirmação de inexistência de qualquer API oficial.

## WuWa / Kuro

Foi consultado https://wutheringwaves.kurogames.com/ e procurada documentação de
serviços públicos comunitários de UID. A pesquisa não estabeleceu endpoint, termos,
escopo e autorização suficientes para integrar com segurança. Alguns links de
projetos/documentação não puderam ser carregados. Não foi feita inferência de que
esses serviços não existem; a integração ficou em JSON/manual.

## Fontes de build e custo consultadas

- https://www.prydwen.gg/zenless/characters/ellen
- https://www.prydwen.gg/star-rail/characters/tingyun
- https://keqingmains.com/q/bennett-quickguide/
- https://library.keqingmains.com/characters/pyro/bennett
- https://www.prydwen.gg/wuthering-waves/characters/encore

Não foi encontrada uma API editorial de builds garantida para reutilização desses
sites. O app usa pacote curado e links de atribuição, não scraping em tempo real.
O conjunto inicial não constitui comparação abrangente entre múltiplas fontes.
Datas e patches da própria fonte são exibidos sem substituí-los pela versão atual.

## Build Android

https://developer.android.com/build/releases/agp-8-9-0-release-notes confirma AGP
8.9.x, Gradle 8.11.1, JDK 17 e SDK máximo 35. Dependências foram fixadas no projeto;
a resolução completa pelo Gradle ainda precisa ocorrer no CI.
