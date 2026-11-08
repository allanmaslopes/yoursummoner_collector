# Contrato da coleção `matches`

Banco `yoursummoner_collector`, coleção `matches`. Fonte da verdade: `CollectedMatch` (domínio) e `MatchDocument` (Mongo). Versão atual: `schemaVersion: 2`. O coletor produz somente v2; documentos v1 já existentes não são convertidos nem produzidos novamente.

## Documento

Um documento por partida. `_id` é igual a `matchId`. Datas são `Date` (UTC) do Mongo; enums são strings. Campos nulos não são gravados (ficam ausentes).

| Campo | Tipo | Descrição |
|---|---|---|
| `_id` | string | Igual a `matchId`. |
| `schemaVersion` | int | `2`. Muda só com quebra de contrato. |
| `matchId` | string | Ex.: `BR1_3012345678`. Único. |
| `platform` | string | `BR1`, `NA1`, `EUW1`, etc. |
| `region` | string | `AMERICAS`, `EUROPE`, `ASIA` ou `SEA`. |
| `patch` | string | `major.minor`, ex.: `16.22`. |
| `patchMajor`, `patchMinor` | int | O patch em números (comparação e retenção). |
| `gameVersion` | string | Versão completa do jogo informada pela Riot. |
| `queueId` | int | Sempre `420` (ranqueada solo/duo). |
| `gameDurationSec` | int | Duração em segundos. |
| `gameStartHour` | Date | Início da partida truncado à hora (UTC). |
| `winningTeam` | int | `100` ou `200`. |
| `tier` | string | `MASTER`, `GRANDMASTER` ou `CHALLENGER`: elo do jogador semente que trouxe a partida. |
| `participants` | array (10) | Ver abaixo. |
| `frames` | array | Um estado compacto por frame da timeline da Riot. Ver abaixo. |
| `events` | array | Timeline ordenada por `t`. Ver abaixo. |
| `collectedAt` | Date | Quando o coletor gravou. Chave de leitura do processor. |

### `participants[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `slot` | int | 1 a 10 (`participantId` da Riot). Liga participante, eventos e frames. |
| `team` | int | `100` ou `200`. |
| `championId` | int | |
| `position` | string | Como a Riot informa (`TOP`, `JUNGLE`, `MIDDLE`, `BOTTOM`, `UTILITY`); pode vir vazio. |
| `win` | boolean | |
| `items` | int[] | Itens finais, sem zeros (até 7). |
| `summonerSpells` | int[] | Os dois feitiços. |
| `runes` | objeto | `primaryStyle` int, `subStyle` int, `perks` int[] e `statPerks` objeto `{ offense, flex, defense }` (ints). `statPerks` estÃ¡ ausente em partidas anteriores a 2026-11-07. |
| `kills`, `deaths`, `assists` | int | |
| `physicalDamage`, `magicDamage`, `trueDamage` | int | Dano a campeões, por tipo. |
| `damageTaken` | int | |
| `goldEarned` | int | |
| `cs` | int | Tropas mais monstros da selva. |
| `champLevel` | int | |
| `visionScore` | int | |
| `timeCCingOthers` | int | Tempo de controle aplicado a outros, em segundos. Ausente na Riot vira 0; campo aditivo, 0 em partidas anteriores a 2026-11-08. |
| `totalHeal` | int | Cura total do participante (em si e aliados). Ausente na Riot vira 0; aditivo como o anterior. |

### `frames[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `minute` | int | Índice ordinal do frame na timeline (`0`, `1`, `2`, ...), independentemente do timestamp. |
| `slots` | array (10) | Estado dos slots 1 a 10 no frame. Slot ausente na resposta da Riot é gravado com valores zero. |
| `blueKills` | int | Abates do time 100 com `CHAMPION_KILL.timestamp` menor ou igual ao timestamp do frame. |
| `redKills` | int | Abates do time 200 com `CHAMPION_KILL.timestamp` menor ou igual ao timestamp do frame. |

Cada entrada de `slots` contém `{ slot, gold, totalGold, level, xp, cs }`; `gold` é o ouro atual e `cs` soma tropas e monstros da selva. `frames` preserva a ordem da timeline, e seu índice é a referência usada pelos eventos.

### `events[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `t` | int | Segundos desde o início. |
| `slot` | int | Quem fez. |
| `type` | string | `BUY`, `SELL`, `UNDO` ou `SKILL`. |
| `itemId` | int | Nos eventos de item (`BUY`, `SELL`, `UNDO`). |
| `skillSlot` | int | Só em `SKILL`: 1 a 4 (Q, W, E, R). |
| `frame` | int | Só em `BUY` e `SKILL`: índice em `frames`, calculado por `floor(timestamp / frameInterval)`, limitado entre zero e o último frame. Ausente nos demais eventos. |

## Índices

- `matchId` único (a partida entra uma vez só).
- `collectedAt` crescente (leitura do processor).
- `patch` (retenção e consultas por patch).

## Imutabilidade

O coletor só insere (`saveIfAbsent`); nunca atualiza um documento. A única remoção é a retenção por patch. Um documento, depois de gravado, não muda.

## Como o processor deve ler

- Somente leitura: nunca escrever nesta coleção, nem marcar "processado".
- Ler em ordem crescente de `collectedAt`, guardando o próprio ponto de leitura (último `collectedAt` e `_id` lidos) em outro banco ou coleção.
- `collectedAt` pode repetir: desempatar por `_id` e não reprocessar o último lido.
- `schemaVersion` desconhecido: ignorar o documento e avisar, sem adivinhar o formato.
- Tolerar campos novos e campos ausentes (nulos não são gravados).

## Retenção

Ficam as partidas dos últimos `collector.retention-patches` patches (padrão 3); as de patch menor são apagadas pela rotina diária das 04:30. O processor deve ler o que precisa antes disso ou guardar o que derivar.

## Sem identificadores

Nenhum campo identifica um jogador: não há PUUID, Riot ID (`gameName`/`tagLine`), nome de invocador nem `summonerId`. O `slot` só vale dentro da partida. Não cruzar partidas para reconstruir identidade. Se algum identificador aparecer num documento, é defeito do coletor: pare de ler e avise.
