# yoursummoner_collector

Serviço Spring Boot 4 / Java 25 que coleta partidas ranqueadas solo/duo de elo alto (Mestre, Grão-Mestre e Desafiante) da Riot API, reduz cada uma a um documento anônimo e a guarda no MongoDB para o `yoursummoner_processor` consumir.

## O que guarda

Coleção `matches` (banco `yoursummoner_collector`): partida reduzida, imutável, com `schemaVersion: 1`. Tem os 10 participantes (campeão, posição, itens finais, feitiços, runas, KDA, dano, ouro, CS, nível, visão) e a timeline de compras, vendas, desfazimentos e skills, cada compra/skill com a situação da partida naquele momento (ouro, nível, XP, CS e abates). Schema completo em [docs/contrato-matches.md](docs/contrato-matches.md).

Mantém só as partidas dos últimos `collector.retention-patches` patches (padrão 3); o resto é apagado pela rotina diária de retenção.

## O que NUNCA guarda

Nenhum identificador de jogador: nem PUUID, nem Riot ID (`gameName`/`tagLine`), nem nome de invocador, nem `summonerId`. Os jogadores semente ficam só numa fila temporária (expira em 24 h) para dirigir a coleta e nunca entram no documento da partida.

## Cota e chave

- Cada região (`americas`, `europe`, `asia`, `sea`) tem seu limitador local, ajustado pelos headers `X-App-Rate-Limit` e `X-Method-Rate-Limit`, respeitando `Retry-After` em 429. O coletor usa no máximo `collector.budget-fraction` (60%) da cota; o resto fica para a YourSummoner-API.
- A chave da Riot é lida do cofre criptografado da YourSummoner-API (coleção `secrets` do banco `yoursummoner`, AES-256-GCM), com fallback para `RIOT_API_KEY`. Trocar a chave na tela de admin vale sem reiniciar.
- 401/403 pausa a coleta inteira (estado `PAUSED_KEY`) até a chave do cofre mudar.

## Variáveis de ambiente

| Variável | Para quê |
|---|---|
| `RIOT_API_KEY` | Chave da Riot (fallback quando o cofre não tem uma). Nunca commitar. |
| `YOURSUMMONER_MASTER_KEY` | Chave mestra que abre o cofre da YourSummoner-API. |
| `MONGODB_URI` | MongoDB (padrão `mongodb://127.0.0.1:27017/yoursummoner_collector`). |

## Como rodar

```
set JAVA_HOME=D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1
set PATH=%JAVA_HOME%\bin;%PATH%
mvn spring-boot:run
```

Sobe na porta 8081, context path `/collector`. Plataformas: `collector.platforms` (ex.: `BR1`). Desliga a coleta com `collector.enabled=false`. Testes: `mvn verify`.

## Operação

`GET http://localhost:8081/collector/status`:

```json
{ "state": "RUNNING",
  "regions": [ { "region": "AMERICAS", "pendingPlayers": 120, "matchesLastHour": 35, "matchesLastDay": 640,
                 "lastOutcome": "SAVED", "budgetUsed": { "americas": "31/60", "br1": "4/60" } } ] }
```

`state` é `RUNNING`, `PAUSED_KEY` (chave recusada) ou `DISABLED`. `budgetUsed` mostra, por host, as chamadas usadas sobre o teto na maior janela. A resposta não traz dados de jogador.

Métricas em `/collector/actuator/metrics`: `collector.matches{region,outcome}`, `collector.queue.pending{region}` e `collector.paused` (0/1).

## Documentos

- Design: [docs/superpowers/specs/2026-11-06-collector-design.md](docs/superpowers/specs/2026-11-06-collector-design.md)
- Plano: [docs/superpowers/plans/2026-11-06-collector.md](docs/superpowers/plans/2026-11-06-collector.md)
- Contrato da coleção `matches`: [docs/contrato-matches.md](docs/contrato-matches.md)
