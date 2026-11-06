# Contrato da coleção `matches`

Banco `yoursummoner_collector`, coleção `matches`. Fonte da verdade: `CollectedMatch` (domínio) e `MatchDocument` (Mongo). Versão atual: `schemaVersion: 1`.

## Documento

Um documento por partida. `_id` é igual a `matchId`. Datas são `Date` (UTC) do Mongo; enums são strings. Campos nulos não são gravados (ficam ausentes).

| Campo | Tipo | Descrição |
|---|---|---|
| `_id` | string | Igual a `matchId`. |
| `schemaVersion` | int | `1`. Muda só com quebra de contrato. |
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
| `events` | array | Timeline ordenada por `t`. Ver abaixo. |
| `collectedAt` | Date | Quando o coletor gravou. Chave de leitura do processor. |

### `participants[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `slot` | int | 1 a 10 (`participantId` da Riot). Liga participante, eventos e situação. |
| `team` | int | `100` ou `200`. |
| `championId` | int | |
| `position` | string | Como a Riot informa (`TOP`, `JUNGLE`, `MIDDLE`, `BOTTOM`, `UTILITY`); pode vir vazio. |
| `win` | boolean | |
| `items` | int[] | Itens finais, sem zeros (até 7). |
| `summonerSpells` | int[] | Os dois feitiços. |
| `runes` | objeto | `primaryStyle` int, `subStyle` int, `perks` int[]. |
| `kills`, `deaths`, `assists` | int | |
| `physicalDamage`, `magicDamage`, `trueDamage` | int | Dano a campeões, por tipo. |
| `damageTaken` | int | |
| `goldEarned` | int | |
| `cs` | int | Tropas mais monstros da selva. |
| `champLevel` | int | |
| `visionScore` | int | |

### `events[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `t` | int | Segundos desde o início. |
| `slot` | int | Quem fez. |
| `type` | string | `BUY`, `SELL`, `UNDO` ou `SKILL`. |
| `itemId` | int | Nos eventos de item (`BUY`, `SELL`, `UNDO`). |
| `skillSlot` | int | Só em `SKILL`: 1 a 4 (Q, W, E, R). |
| `situation` | objeto | Só em `BUY` e `SKILL`. Estado do frame anterior ao evento (frames são por minuto). |

`situation`: `blueKills` e `redKills` (int, abates por time até o evento) e `slots[]` com 10 entradas `{ slot, gold, totalGold, level, xp, cs }` (`gold` é o ouro atual). Quem saiu da partida tem a entrada zerada.

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
