# Auditoria do pedido — versão 0.4.0

Legenda: **código** = existe implementação, mas falta validação Android;
**parcial** = parte do requisito está ausente; **bloqueado** = depende de acesso/ambiente.
Nenhuma linha desta matriz equivale a afirmar que o app está pronto para produção.

| Item original | Estado | Evidência / lacuna |
|---|---|---|
| 1. Plataforma/build pelo GitHub | Validado | Run 34734934446: build e testes Android passaram; APK artifact disponível |
| 2. Stack nativa | Código | Kotlin, Compose, Material 3, Room, Coroutines, OkHttp |
| 3. Organização por jogo | Código | Home e nove seções; várias contas por jogo |
| 4. Importação | Parcial | Enka Genshin/HSR/ZZZ; JSON/manual todos; ZZZ resolve 58 Provider IDs e mantém fallback desconhecido; sem conta completa ou Kuro UID |
| 5. Personagens | Parcial | Modelo/editor com facção e rank; catálogo de 184, sendo 120 Genshin e 59 ZZZ live; Claret ainda sem Provider ID/imagem/facção |
| 6. Builds | Parcial | 59 builds ZZZ e quatro legadas, 65 W-Engines e 27 sets; recomendações sem benchmarks inventados; não cobre dados futuros |
| 7. Fontes | Parcial | Optimizer MIT por commit, HoYoverse e Prydwen; fatos sem licença foram transcritos apenas como referência manual, com limitações registradas |
| 8. Atualizações | Código | Pacote local/HTTPS versionado, validado e salvo sem novo APK; fonte padrão publicada no próprio repositório e consulta por botão |
| 9. Comparador | Parcial | Benchmarks individuais; sem cálculo de buffs/estado de combate, limites condicionais avançados |
| 10. Planejador completo | Parcial | Motor de transições exatas; ZZZ tem EXP em pontos, promoção e cinco habilidades para 58 agentes e Core para 26; Denny de aplicação e algumas trilhas permanecem ausentes |
| 11. Checklist | Código | Quantidades, faltantes, progresso, reserva de inventário e consumo |
| 12. Calendário | Parcial | Regras por material e reset do servidor; dias de livros Genshin importados; limites semanais e outros jogos incompletos |
| 13. Times | Parcial | Montagem/salvamento e quatro guias ZZZ com substituições; editor e exclusão disponíveis |
| 14. Baseado na conta | Parcial | Seleção de membros possuídos em slots das fontes; não calcula melhor DPS global |
| 15. Vários projetos | Código | Visão global por jogo/conta e ordenação por prioridade, progresso e personagem |
| 16. Busca/filtros | Código | Nome, atributo, especialidade, função e facção; rank S/A, posse e favorito |
| 17. Offline | Código | Room e pacote embutido; imagens em cache sujeito a expulsão |
| 18. Banco | Código | Room, transações, migrations 1→2→3, backup; catálogo em registros menores com JSON tipado |
| 19. Privacidade | Código | INTERNET apenas, sem senhas/trackers; backups explícitos |
| 20. Interface | Parcial | Compose dark, cards, cores e navegação; sem QA visual em dispositivo; editores avançados JSON |
| 21. Dashboard | Código | Totais locais explícitos, projetos, favoritos, atributos pendentes e farm |
| 22. Arquitetura multijogo | Código | Tipos compartilhados e adaptador de importação extensível |
| 23. Pesquisa | Parcial | API Enka documentada e snapshot ZZZ fixado; UID real, atualização automática e tabelas Core ausentes ainda não foram validados |
| 24. GitHub | Validado | Branch `feature/zzz-progression-data`, PR #5 e Actions verde; proteção de main não configurada |
| 25. Autonomia | Executado | Arquivos, pesquisa e testes locais sem exigir decisões técnicas ao usuário |
| 26. Processo | Validado | Build, lint, APK assinado e instrumentação no emulador Android 15 passaram no run 34734934446 |
| 27. Testes | Parcial | 25 testes Python, 2.036 asserções Java e Actions passaram; faltam integração com UID real e aparelhos adicionais |
| 28. Sucesso final | Não atingido | APK e CI verde obtidos; cobertura completa e QA ampliado ainda pendentes |

## GitHub desbloqueado

O usuário forneceu `Gabriel-Liz2003/Gacha-Hub`. O código foi publicado na branch
`feature/zzz-progression-data` e o PR #5 está aberto contra `main`. A compilação e os testes
Android passaram no run 34734934446. Os resultados estão em VALIDATION.md.

## Trabalho de produto que ainda continua necessário

Claret ainda aguarda Provider ID, imagem, facção e custos estruturados verificáveis.
Core Skill só está disponível para 26 agentes e EXP não escolhe a combinação de logs/Denny.
Um build verde não resolve essas lacunas de fonte; a entrega registra-as no pacote em vez
de inserir valores estimados.
