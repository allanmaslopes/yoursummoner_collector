# yoursummoner_collector — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Microsserviço Spring Boot que coleta partidas ranqueadas solo de jogadores Mestre+ de todos os servidores da Riot e guarda uma versão reduzida e anônima, com a situação de cada compra e ponto de skill, para o `yoursummoner_processor`.

**Architecture:** Hexagonal, igual à YourSummoner-API. Um job semeia jogadores por plataforma; um worker por região (virtual thread) consome a fila, busca IDs, detalhe e timeline sob um limitador com orçamento, reduz e grava no MongoDB. A chave da Riot vem do cofre da YourSummoner-API.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (OpenFeign), Resilience4j, Spring Data MongoDB, MapStruct, Lombok (só fora do domínio), JUnit 5, Mockito, WireMock, ArchUnit, JaCoCo, flapdoodle (Mongo embarcado).

**Spec:** `docs/superpowers/specs/2026-11-06-collector-design.md`

**Referência para reaproveitar (somente leitura):** `D:\Workspace\YourSummoner-API` — `pom.xml`, `ArchitectureTest`, `infrastructure/adapter/out/riot/{RiotFeignConfig,RiotErrorDecoder,RiotResilience}`, `infrastructure/security/{AesGcmCipher,MasterKey}`, `infrastructure/adapter/out/mongo/{MongoSecretStore,SecretDocument}`, `domain/model/{Platform,Region}`, `domain/exception/*`, `application/port/out/RiotCredentials`. Copie e adapte; não crie dependência de código entre os repositórios.

## Global Constraints

- Pacote base `com.your.summoner.collector`; artifactId `yoursummoner-collector`; porta `8081`; context path `/collector`.
- Mesmas versões e plugins do `pom.xml` da YourSummoner-API (incluindo o `argLine` com `-Djdk.net.unixdomain.tmpdir=${project.build.directory}` no surefire e o `jvmArguments` igual no `spring-boot-maven-plugin`: sem isso o Windows falha com "Unable to establish loopback connection"). Sem Spring Security nem springdoc.
- Hexagonal travada por `ArchitectureTest` (mesma regra endurecida da API: `application` só pode usar `org.springframework.stereotype..` do Spring).
- Mongo: `spring.mongodb.uri: ${MONGODB_URI:mongodb://127.0.0.1:27017/yoursummoner_collector}`. Cofre: banco `yoursummoner` (propriedade `collector.vault.database`, padrão `yoursummoner`), coleção `secrets`, só leitura.
- Chave: cofre (`RIOT_API_KEY`, decifrado com `YOURSUMMONER_MASTER_KEY`) e, se vazio/indisponível, `riot.api.token: ${RIOT_API_KEY:}`. Nunca o nome `riot.api.key`. Testes usam `test-key`.
- Nenhum PUUID, Riot ID, `summonerName`, `summonerId` ou `riotIdGameName/TagLine` em `matches`. Só na fila temporária `player_queue` (TTL 24 h).
- Só fila 420; só patch atual e anterior; retenção dos 3 últimos patches (`collector.retention-patches: 3`).
- Orçamento `collector.budget-fraction: 0.6` do limite informado pela Riot por região.
- Plataformas padrão (`collector.platforms`): BR1, NA1, LA1, LA2, EUW1, EUN1, TR1, RU, ME1, KR, JP1, OC1, SG2, TW2, VN2. Regiões: AMERICAS (BR1, NA1, LA1, LA2), EUROPE (EUW1, EUN1, TR1, RU, ME1), ASIA (KR, JP1), SEA (OC1, SG2, TW2, VN2).
- Perfil `test`: `collector.enabled: false` (nenhum job sobe), Mongo embarcado, WireMock.
- Commits locais na branch `master`, sem push, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Chave vence no meio da coleta (401/403):** todas as regiões pausam, nenhuma partida é perdida (o jogador volta à fila), e a coleta retoma sozinha quando a chave muda no cofre. Teste na Task 4 (`unauthorizedPausesAllRegionsAndResumesOnNewKey`).
2. **Partida de patch antigo ou de outra fila vinda pelos IDs:** descartada sem gravar e sem ser baixada de novo depois. Teste na Task 7 (`oldPatchAndOtherQueueAreDiscardedOnce`).
3. **A mesma partida chega por vários jogadores ao mesmo tempo (duas regiões não, mas vários workers sim):** uma gravação só, sem exceção. Teste na Task 6 (`concurrentSaveOfSameMatchKeepsOne`).
4. **Timeline sem frame no minuto exato do evento ou com participante que saiu (AFK):** situação vem do frame anterior; não há pânico nem NPE. Teste na Task 5 (`eventBeforeFirstFrameUsesFrameZero`, `leaverHasZeroedState`).
5. **Nenhum identificador vaza para `matches`:** o JSON gravado não contém `puuid`, `riotId`, `summonerName`, `summonerId`, `gameName` nem `tagLine`. Teste na Task 5 (`reducedMatchHasNoPlayerIdentifiers`) e na Task 6 (`storedDocumentHasNoPlayerIdentifiers`).

---

### Task 1: Bootstrap

**Files:** `pom.xml`, `src/main/java/com/your/summoner/collector/CollectorApplication.java` (`@SpringBootApplication @EnableFeignClients @EnableScheduling @ConfigurationPropertiesScan`), `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, `src/test/java/.../ArchitectureTest.java`, `src/test/java/.../ApplicationSmokeTest.java` (`/collector/actuator/health` 200), `CLAUDE.md` e `AGENTS.md` (mesmo modelo da YourSummoner-API, com a tabela do superpowers), `.gitignore` (`.env`, `target/`, `.embedmongo/`), `.env.example` (`RIOT_API_KEY`, `YOURSUMMONER_MASTER_KEY`, `MONGODB_URI`).

- [ ] **Step 1:** Escrever `ArchitectureTest` e `ApplicationSmokeTest`.
- [ ] **Step 2:** `mvn -q verify` → FAIL (sem app).
- [ ] **Step 3:** Criar pom, classe principal e configs.
- [ ] **Step 4:** `mvn -q verify` → PASS.
- [ ] **Step 5:** Commit `build: inicia o yoursummoner_collector em Spring Boot 4`.

### Task 2: Domínio

**Files:** `domain/model/{Platform,Region,Tier,CollectedMatch,PatchVersion}.java` e testes em `domain/model/`.

**Interfaces:**
- Produces:
  ```java
  enum Tier { MASTER, GRANDMASTER, CHALLENGER }                    // ordem crescente
  record PatchVersion(int major, int minor) implements Comparable<PatchVersion> {
      static Optional<PatchVersion> fromGameVersion(String gameVersion); // "16.22.712.1234" → 16.22
      static Optional<PatchVersion> parse(String patch);                 // "16.22" ou "16.22.1" → 16.22
      String toString();                                                 // "16.22"
  }
  record CollectedMatch(int schemaVersion, String matchId, Platform platform, Region region, String patch, String gameVersion,
                        int queueId, int gameDurationSec, Instant gameStartHour, int winningTeam, Tier tier,
                        List<Participant> participants, List<TimelineEvent> events, Instant collectedAt) {
      record Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items,
                         List<Integer> summonerSpells, Runes runes, int kills, int deaths, int assists,
                         int physicalDamage, int magicDamage, int trueDamage, int damageTaken,
                         int goldEarned, int cs, int champLevel, int visionScore) {}
      record Runes(int primaryStyle, int subStyle, List<Integer> perks) {}
      enum EventType { BUY, SELL, UNDO, SKILL }
      record TimelineEvent(int t, int slot, EventType type, Integer itemId, Integer skillSlot, Situation situation) {}
      record Situation(List<SlotState> slots, int blueKills, int redKills) {}   // situation só em BUY e SKILL
      record SlotState(int slot, int gold, int totalGold, int level, int xp, int cs) {}
  }
  ```
  Listas copiadas com `List.copyOf` (null → vazio). `Platform` e `Region` copiados da YourSummoner-API, mais `Region.forPlatform` cobrindo as 15 plataformas do Global Constraints.

- [ ] **Step 1:** Testes: `patchFromGameVersion` (`"16.22.712.1234"` → `16.22`), `patchCompare` (`16.9 < 16.22`), `invalidPatchIsEmpty`, `listsAreImmutable`, `everyPlatformHasARegion`.
- [ ] **Step 2–4:** FAIL → implementar → PASS (`mvn -q test -Dtest=...`).
- [ ] **Step 5:** Commit `feat: domínio do coletor`.

### Task 3: Adapters da Riot e chave do cofre

**Files:**
- `infrastructure/adapter/out/riot/api/{RiotLeagueApi,RiotMatchApi}.java` (Feign, `URI baseUri` como primeiro parâmetro, como na API).
  - `GET /lol/league/v4/{challenger|grandmaster|master}leagues/by-queue/RANKED_SOLO_5x5` → `entries[].puuid`.
  - `GET /lol/match/v5/matches/by-puuid/{puuid}/ids?queue=420&type=ranked&startTime=&count=20`.
  - `GET /lol/match/v5/matches/{id}` e `GET /lol/match/v5/matches/{id}/timeline`.
- `.../riot/dto/*` (records só com os campos que o redutor usa; `@JsonIgnoreProperties(ignoreUnknown = true)`).
- `.../riot/{RiotFeignConfig,RiotErrorDecoder}.java` copiados e adaptados; o decoder também expõe os headers `X-App-Rate-Limit`, `X-App-Rate-Limit-Count`, `X-Method-Rate-Limit`, `X-Method-Rate-Limit-Count` e `Retry-After` para a Task 4 (via um `RateLimitObserver` chamado a cada resposta, com a região).
- `application/port/out/{RiotCredentials,LeagueSource,MatchSource}.java`:
  ```java
  interface LeagueSource { List<String> masterPlusPuuids(Platform p, Tier tier); }
  interface MatchSource { List<String> rankedMatchIds(Region r, String puuid, Instant since); Optional<RiotMatch> match(Region r, String id); Optional<RiotTimeline> timeline(Region r, String id); }
  ```
  `RiotMatch` e `RiotTimeline` são os DTOs; ficam em `application/port/out/riot` como records simples para não vazar Feign.
- `infrastructure/security/{AesGcmCipher,MasterKey}.java` copiados; `infrastructure/adapter/out/vault/VaultRiotCredentials.java` lê `secrets/RIOT_API_KEY` do banco do cofre com cache de 30 s e cai no `riot.api.token`.

- [ ] **Step 1:** Testes WireMock: `leagueReturnsPuuids` (3 tiers), `matchIdsSendsQueueTypeAndStartTime`, `matchAndTimelineDecode` (fixtures em `src/test/resources/riot/`), `notFoundIsEmpty`, `requestsCarryTestKeyOnly`, `observerSeesRateLimitHeaders`. Teste do cofre (Mongo embarcado + `AesGcmCipher` com chave gerada no teste): `vaultKeyWinsOverEnv`, `fallsBackToEnvWhenVaultEmpty`, `vaultChangeVisibleAfterCache`.
- [ ] **Step 2–4:** FAIL → implementar → PASS.
- [ ] **Step 5:** Commit `feat: adapters da Riot (league, match, timeline) e chave do cofre`.

Fixtures: `match-v5` e `timeline` sintéticos montados a partir da documentação oficial (`https://developer.riotgames.com/apis#match-v5`), marcados com `"_fixture_note": "sintético"`; serão trocados por reais na Task 8.

### Task 4: Limitador por região com orçamento

**Files:** `application/port/out/RequestBudget.java`, `infrastructure/ratelimit/{RegionRateLimiter,RateLimitHeaders,CollectorPause}.java`.

**Interfaces:**
- ```java
  interface RequestBudget { void acquire(RouteKey key) throws InterruptedException; }   // bloqueia até haver orçamento
  record RouteKey(String host /* "br1" ou "americas" */, String method /* "league" | "match-ids" | "match" | "timeline" */) {}
  ```
- `RateLimitHeaders.parse("20:1,100:120")` → lista de `(limite, janelaSeg)`.
- Para cada host, janela deslizante por limite com teto `floor(limite × budgetFraction)` (mín. 1); vale também o `X-Method-Rate-Limit` por método. Até a primeira resposta: `20:1,100:120`.
- `429` → bloqueia aquele host por `Retry-After` (ou 10 s sem header).
- `401/403` → `CollectorPause` global ligado; `acquire` bloqueia todas as regiões; a cada 5 min o pause testa se a chave do cofre mudou (hash) e, se mudou, libera.

- [ ] **Step 1:** Testes com relógio injetável (sem dormir de verdade): `parsesHeaders`, `respectsBudgetFraction` (limite 100/120 s e 0,6 → 61ª chamada bloqueia), `methodLimitAlsoApplies`, `retryAfterBlocksOnlyThatHost`, `unauthorizedPausesAllRegionsAndResumesOnNewKey`.
- [ ] **Step 2–4:** FAIL → implementar → PASS.
- [ ] **Step 5:** Commit `feat: limitador de cota por região com orçamento e pausa por chave`.

### Task 5: Redutor (função pura)

**Files:** `application/service/MatchReducer.java` e teste.

**Interfaces:**
- `CollectedMatch reduce(RiotMatch m, RiotTimeline tl, Platform p, Tier tier, Instant collectedAt)`.
- Regras:
  - `gameStartHour` = `gameStartTimestamp` truncado à hora (UTC).
  - `winningTeam` = `teamId` (100/200) do time com `win == true`.
  - `items` = `item0..item6` sem zeros.
  - Eventos: `ITEM_PURCHASED` → BUY, `ITEM_SOLD` → SELL, `ITEM_UNDO` → UNDO, `SKILL_LEVEL_UP` → SKILL (`skillSlot` 1–4). Ordenados por `t` (s, de `timestamp` ms).
  - `situation` (só BUY e SKILL): frame de índice `floor(timestamp / frameInterval)` (o frame anterior ao evento); `participantFrames` dos 10 slots (`currentGold`, `totalGold`, `level`, `xp`, `minionsKilled + jungleMinionsKilled`); abates por time = `CHAMPION_KILL` com `timestamp ≤` evento, contados pelo time do `killerId`.
  - Slot sem `participantFrame` (saiu) → `SlotState` zerado com o slot.
  - Nenhum campo de identificação é lido para o resultado.

- [ ] **Step 1:** Testes: `reducesParticipantsAndWinner`, `itemsWithoutZeros`, `buyHasSituationFromPreviousFrame`, `skillEventsHaveSlot`, `sellAndUndoHaveNoSituation`, `killsCountedUntilEvent`, `eventBeforeFirstFrameUsesFrameZero`, `leaverHasZeroedState`, `gameStartTruncatedToHour`, `reducedMatchHasNoPlayerIdentifiers` (serializa com Jackson e verifica que o JSON não contém `puuid`, `riotId`, `summonerName`, `summonerId`, `gameName`, `tagLine`, nem o valor de nenhum PUUID do fixture).
- [ ] **Step 2–4:** FAIL → implementar → PASS.
- [ ] **Step 5:** Commit `feat: redutor de partida com situação por evento`.

### Task 6: Persistência

**Files:** `application/port/out/{MatchStore,SeenMatches,PlayerQueue}.java`; `infrastructure/adapter/out/mongo/{MatchDocument,MongoMatchStore,MongoSeenMatches,MongoPlayerQueue,CollectorIndexes}.java`.

**Interfaces:**
- ```java
  interface MatchStore { boolean saveIfAbsent(CollectedMatch m); long deleteOlderThan(PatchVersion oldestKept); long countSince(Instant since, Region r); }
  interface SeenMatches { boolean markIfNew(String matchId); }        // true na primeira vez; TTL 30 dias
  interface PlayerQueue { void offer(Platform p, Tier tier, List<String> puuids); Optional<QueuedPlayer> poll(Region r); int pending(Region r); }
  record QueuedPlayer(Platform platform, Tier tier, String puuid) {}  // em application/port/out
  ```
- Coleções: `matches` (índice único `matchId`, índice `collectedAt`, índice `patch`), `seen_matches` (`_id` = matchId, TTL 30 dias), `player_queue` (`{platform, puuid}` único, TTL 24 h em `enqueuedAt`, `poll` atômico com `findAndModify` + remove, preferindo o maior `tier`).
- `MatchDocument` mapeado do domínio por MapStruct; `schemaVersion = 1`.

- [ ] **Step 1:** Testes (Mongo embarcado): `saveIfAbsentIsIdempotent`, `concurrentSaveOfSameMatchKeepsOne` (20 threads), `deleteOlderThanKeepsRecentPatches`, `seenMatchesMarksOnce`, `queueDeduplicatesAndPrefersHigherTier`, `queueEntriesExpire` (TTL configurado no índice), `storedDocumentHasNoPlayerIdentifiers`.
- [ ] **Step 2–4:** FAIL → implementar → PASS.
- [ ] **Step 5:** Commit `feat: persistência de partidas, partidas vistas e fila de jogadores`.

### Task 7: Pipeline e jobs

**Files:** `application/port/out/PatchCatalog.java` (`List<PatchVersion> currentAndPrevious()`); `infrastructure/adapter/out/api/YourSummonerPatchCatalog.java` (Feign para `${collector.yoursummoner-api-url:http://localhost:8080/your-summoner}/patches`, cache 1 h, último valor bom em falha); `application/service/{SeedService,CollectService,RetentionService}.java`; `infrastructure/adapter/in/scheduler/{SeedScheduler,RegionWorkers,RetentionScheduler}.java`.

**Interfaces / regras:**
- `SeedService.seed(Platform)`: para os 3 tiers, `LeagueSource` → `PlayerQueue.offer`.
- `CollectService.collectOne(Region) : CollectOutcome` (enum `SAVED, DUPLICATE, DISCARDED_PATCH, DISCARDED_QUEUE, NOT_FOUND, IDLE`): `poll` → `rankedMatchIds(since = início do patch anterior)` → para cada id, `SeenMatches.markIfNew` → `match` → filtro fila 420 e patch ∈ `currentAndPrevious` → `timeline` → `reduce` → `saveIfAbsent`. Toda chamada à Riot passa por `RequestBudget.acquire`. Se a coleta pausar (chave) no meio, o jogador volta para a fila. O início do patch anterior vem de `PatchCatalog` (data da primeira partida vista desse patch ou, na falta, agora − 28 dias).
- `RegionWorkers`: um laço por região numa virtual thread, enquanto `collector.enabled`; `IDLE` → dorme 30 s.
- `SeedScheduler`: a cada 6 h (cron `collector.seed-cron: "0 0 */6 * * *"`) e uma vez na subida.
- `RetentionScheduler`: diário, mantém os `retention-patches` mais novos.

- [ ] **Step 1:** Testes (Mockito/fakes): `savesNewRankedMatchOfRecentPatch`, `oldPatchAndOtherQueueAreDiscardedOnce` (segunda vez o id não é baixado), `duplicateIdIsSkippedWithoutDownload`, `everyRiotCallAcquiresBudget`, `pauseMidwayReturnsPlayerToQueue`, `seedOffersAllTiers`, `retentionKeepsThreePatches`, `patchCatalogKeepsLastGoodValue`.
- [ ] **Step 2–4:** FAIL → implementar → PASS.
- [ ] **Step 5:** Commit `feat: pipeline de coleta por região, semente e retenção`.

### Task 8: Status, métricas e verificação real

**Files:** `infrastructure/adapter/in/rest/StatusController.java` (`GET /collector/status`), métricas Micrometer (`collector.matches{region,outcome}`, `collector.queue.pending{region}`, `collector.paused`), `README.md`, `docs/contrato-matches.md` (schema da coleção `matches` para o processor), `.claude/launch.json` (entrada `collector`).

- [ ] **Step 1:** Testes: `statusShowsPerRegionCountsAndState`, `metricsCountOutcomes`.
- [ ] **Step 2–4:** FAIL → implementar → PASS; `mvn verify` completo.
- [ ] **Step 5 (com o usuário, quando houver chave de desenvolvimento válida):** subir com `collector.platforms: BR1` por 15 min; conferir `/collector/status`, contar documentos em `matches`, abrir um documento e verificar que não há identificadores; medir consumo de cota (nenhum 429 para a YourSummoner-API em paralelo). Trocar os fixtures sintéticos por 1 partida e 1 timeline reais **anonimizados**.
- [ ] **Step 6:** Commit `feat: status e métricas do coletor; contrato da coleção matches`.
