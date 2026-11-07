# yoursummoner_collector

Microsserviço Spring Boot 4 / Java 25, hexagonal (travada por `ArchitectureTest`), que coleta partidas ranqueadas solo de jogadores Mestre+ de todos os servidores da Riot e guarda uma versão reduzida e anônima para o `yoursummoner_processor`.

Design: [docs/superpowers/specs/2026-11-06-collector-design.md](docs/superpowers/specs/2026-11-06-collector-design.md). Plano: [docs/superpowers/plans/2026-11-06-collector.md](docs/superpowers/plans/2026-11-06-collector.md).

Stack: OpenFeign, Resilience4j, MongoDB, MapStruct/Lombok, JUnit/Mockito/WireMock/JaCoCo. `RIOT_API_KEY`, `YOURSUMMONER_MASTER_KEY` e `MONGODB_URI` só por variável de ambiente. Rode `mvn spring-boot:run`, `mvn test` ou `mvn verify` com o JDK 25 de `D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1`.

## Regras

- Nunca guardar PUUID, Riot ID, nome de invocador ou summonerId na coleção `matches`. PUUID só na fila temporária `player_queue` (TTL 24 h).
- Seguir sempre as políticas da Riot. A coleta é a de menor prioridade da cota (orçamento de 60% por região).
- Nunca commite chaves, `.env` ou segredos. A chave entra só por `RIOT_API_KEY` (propriedade `riot.api.token`, nunca `riot.api.key`) ou pelo cofre da YourSummoner-API.
- A sessão principal planeja e revisa; implementação vai para os delegados (Codex, Gemini via FreeLLMAPI, Ollama local, subagentes), com spec fechada e revisão do diff.
- Testes no Windows: o surefire passa `-Djdk.net.unixdomain.tmpdir=${project.build.directory}`; sem isso o `HttpClient` do JDK falha com "Unable to establish loopback connection".

## Development method: superpowers

Development here follows the [superpowers](https://github.com/obra/superpowers) skills. Before acting on a task, invoke the skill that fits it:

| When | Skill |
|---|---|
| A new feature, a change of behavior or a design decision | `superpowers:brainstorming`, then `superpowers:writing-plans` |
| Running a written plan | `superpowers:executing-plans`, or `superpowers:subagent-driven-development` for independent tasks |
| Any feature or bug fix | `superpowers:test-driven-development` |
| A bug, a failing test or behavior you did not expect | `superpowers:systematic-debugging` |
| Two or more tasks that share no state | `superpowers:dispatching-parallel-agents` |
| Work that needs its own workspace | `superpowers:using-git-worktrees` |
| Before saying the work is done, passes or is ready to commit | `superpowers:verification-before-completion` |
| After a task or before a merge, and when review comments arrive | `superpowers:requesting-code-review`, `superpowers:receiving-code-review` |
| The branch is complete | `superpowers:finishing-a-development-branch` |

## Ferramentas do workspace

Scripts de rotina em `D:\Workspace\Scripts\yoursummoner` (ver `README.md` lá): `verify.ps1 -Repo <repo>` (build e testes com resumo), `stack.ps1 up|down|status` (Mongo, API, coletor, processor), `collector-check.ps1`, `board.ps1` (quadro). JDK 25 em `D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1`, Maven em `D:\Workspace\Languages-Frameworkspache-maven-3.10.0`. Agente delegado não roda git.
