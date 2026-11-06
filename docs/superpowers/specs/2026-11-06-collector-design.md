# Design — yoursummoner_collector: coleta de partidas de elo alto

- Data: 2026-11-06
- Repositório: `D:\Workspace\yoursummoner_collector` (GitHub `allanmaslopes/yoursummoner_collector`)
- Relacionados: `YourSummoner-API` (cofre de segredos, catálogo de patches), `yoursummoner_processor` (consome o que este serviço guarda), `live-builds` e `live-builds-engine` (usam o conhecimento final)

## 1. Intenção

O objetivo final é um motor de recomendação de itens e skills próprio, que aprenda com jogadores que tomam boas decisões rápidas conforme a situação da partida e repasse isso a jogadores casuais. Para isso, o primeiro passo é ter **partidas de jogadores de elo alto, com a linha do tempo**, guardadas de forma enxuta e sem identificar ninguém.

Este microsserviço só **coleta e guarda**. Quem transforma isso em conhecimento é o `yoursummoner_processor` (outro serviço, outro design), para manter cada lógica consistente e separada.

## 2. Objetivos

- Coletar partidas da **ranqueada solo/duo (fila 420)** de jogadores **Mestre, Grão-Mestre e Desafiante** de **todos os servidores** da Riot.
- Guardar de cada partida uma versão **reduzida** com o que o motor precisa: o resultado, os 10 participantes e, da timeline, cada compra/venda de item e cada ponto de skill **com a situação naquele minuto**.
- **Não guardar PUUID, Riot ID nem nome de invocador** nas partidas guardadas.
- Funcionar com a **chave de desenvolvimento** da Riot (vence a cada 24 h, 100 chamadas/2 min e 20/s por região) e ficar pronto para a chave pessoal só trocando configuração.
- Dividir a cota com a YourSummoner-API sem atrapalhar quem usa o site.
- Seguir sempre as políticas da Riot.

## 3. Não-objetivos

- Calcular agregados (taxa de vitória por item, "situação → compra"): é o `yoursummoner_processor`.
- Arena, ARAM, flex e outras filas (depois).
- Elo abaixo de Mestre.
- UI própria. Status por endpoint e métricas bastam.
- Rate limiter central compartilhado entre serviços (é o futuro `yoursummoner_riot_gateway`; aqui só um limitador local com orçamento).

## 4. Anti-objetivos

- Guardar qualquer identificador de jogador junto com a partida, ou permitir reconstruir quem jogou.
- Deixar a YourSummoner-API sem cota (a coleta é sempre a de menor prioridade).
- Insistir quando a Riot responde 401/403/429: a coleta pausa e respeita `Retry-After`.
- Guardar o JSON cru da timeline (500 KB–1 MB por partida).

## 5. Fluxo

1. **Semente de jogadores** (a cada 6 h, por plataforma: BR1, NA1, LA1, LA2, EUW1, EUN1, TR1, RU, ME1, KR, JP1, OC1, SG2, TW2, VN2): `league-v4` `challengerleagues`, `grandmasterleagues` e `masterleagues` `/by-queue/RANKED_SOLO_5x5` → lista de PUUIDs.
   Os PUUIDs vão para uma **fila de trabalho temporária** (coleção própria com TTL de 24 h), nunca para as partidas guardadas.
2. **IDs de partida** por jogador: `match-v5` `/by-puuid/{puuid}/ids?queue=420&type=ranked&startTime=<início do patch anterior>&count=20`, no cluster regional da plataforma (americas, europe, asia, sea).
3. **Deduplicação** pelo `matchId` (que não identifica jogador): uma partida vista por 10 jogadores Mestre+ é baixada uma vez.
4. **Detalhe + timeline** (`/matches/{id}` e `/matches/{id}/timeline`): descarta se a fila não for 420 ou se o patch (`gameVersion`) não for o atual nem o anterior (vindos do catálogo de patches da YourSummoner-API).
5. **Redução e gravação**: monta o documento reduzido (seção 6), grava, e descarta o JSON cru.
6. **Retenção**: um job diário apaga partidas de patches fora dos **3 últimos** (configurável).

## 6. O que se guarda (contrato com o processor)

Coleção `matches` no banco `yoursummoner_collector` (MongoDB). Documento **imutável** com `schemaVersion: 1` e `collectedAt`. O processor lê pela ordem de `collectedAt` e mantém o próprio ponto de leitura; o coletor nunca marca nada como "processado".

- **Partida:** `matchId`, `platform`, `region`, `patch` (`16.22`), `gameVersion`, `queueId`, `gameDurationSec`, `gameStart` (truncado à hora), `winningTeam`, `tier` (o elo mínimo dos jogadores semente que a trouxeram: MASTER/GRANDMASTER/CHALLENGER).
- **Participantes (10):** `slot` (1–10, o `participantId` da Riot), `team`, `championId`, `position`, `win`, `items` finais, `summonerSpells`, runas (`primaryStyle`, `subStyle`, `perks`), `kills/deaths/assists`, `totalDamageDealtToChampions` (físico/mágico/verdadeiro), `damageTaken`, `goldEarned`, `cs`, `champLevel`, `visionScore`. **Nada de PUUID, Riot ID ou nome.**
- **Eventos da timeline:** lista ordenada de `{ t (s), slot, type: BUY | SELL | UNDO | SKILL, itemId? , skillSlot? }`.
- **Situação a cada evento de BUY/SKILL:** do frame do minuto do evento, para os 10 slots: `gold` atual, `totalGold`, `level`, `xp`, `cs`; e o placar de abates por time até ali. (Frames são por minuto; usar o frame anterior ao evento.)
- Estimativa: 20–40 KB por partida.

## 7. Cota e chave

- **Limitador local por região** (plataforma e cluster regional são limites separados na Riot), lendo os headers `X-App-Rate-Limit` / `X-App-Rate-Limit-Count` / `X-Method-Rate-Limit` para se ajustar, e respeitando `Retry-After`.
- **Orçamento:** o coletor usa no máximo 60% da cota de cada região (configurável), deixando o resto para a YourSummoner-API. Shortcut conhecido: sem um limitador central, os dois serviços não sabem do consumo um do outro; o orçamento é a mitigação até existir o `yoursummoner_riot_gateway`.
- **Chave:** lida do mesmo cofre criptografado da YourSummoner-API (coleção `secrets` do banco `yoursummoner`, AES-256-GCM, chave mestra `YOURSUMMONER_MASTER_KEY`), com fallback para `RIOT_API_KEY`. Trocar a chave na tela de admin vale para o coletor sem reiniciar.
- **401/403:** pausa a coleta inteira (a chave venceu), loga um WARN, tenta de novo a cada 5 min. **429:** espera o `Retry-After` só naquela região.

## 8. Operação

- `GET /status`: por região, partidas coletadas na última hora/dia, fila de jogadores pendentes, uso da cota, estado (rodando, pausado por chave, pausado por limite).
- Liga/desliga por configuração (`collector.enabled`) e por região (`collector.regions`).
- Métricas Micrometer (contadores por região e por resultado: guardada, descartada por patch, descartada por fila, repetida).

## 9. Decisões do usuário

- Microsserviço próprio, separado do processor.
- Mestre+ de todos os servidores.
- Começar com a chave de desenvolvimento; a pessoal virá depois.
- Guardar partidas de elo alto sem identificar jogadores; partidas de elo baixo não entram.
- Seguir as políticas da Riot.

## 10. Decisões do arquiteto (revise)

1. Stack igual à YourSummoner-API: Spring Boot 4.1, Java 25, hexagonal travada por ArchitectureTest, OpenFeign + Resilience4j, MongoDB, virtual threads, JUnit/Mockito/WireMock/JaCoCo.
2. Só fila 420 e só patch atual + anterior.
3. Retenção dos 3 últimos patches (o usuário não decidiu; padrão configurável).
4. Documento reduzido em vez do JSON cru (seção 6).
5. PUUID só na fila temporária com TTL de 24 h.
6. Orçamento de 60% da cota por região.
7. `gameStart` truncado à hora (o horário exato ajuda pouco ao motor e aumenta a chance de reidentificação).
8. Um worker por região em virtual thread; regiões independentes.
9. Patches atual e anterior lidos de `GET /your-summoner/patches` da YourSummoner-API (Feign), com cache de 1 h e mantendo o último valor bom se a API estiver fora. Não ler o banco da API diretamente (evita acoplar os serviços pelo schema).
10. O orçamento de 60% é calculado sobre o limite que a Riot informa nos headers `X-App-Rate-Limit` (e `X-Method-Rate-Limit` por endpoint) da última resposta; até a primeira resposta, usa os limites da chave de desenvolvimento (20/1 s e 100/120 s). Trocar para a chave pessoal ajusta sozinho.

## 11. O que fica com o builder

Nomes de classes e pacotes, formato exato dos índices, algoritmo do limitador (token bucket ou janela deslizante), cadência fina dos jobs, ordem de prioridade entre semente, IDs e detalhes dentro da cota.
