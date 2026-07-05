# Impl 09 — Day 9: Resiliency (pod-kill zero-loss) + Kafka durable log ⭐

**Outcome:** kill a pod holding live sockets → clients reconnect to the survivor → resync via the
seq cursor → **zero message loss**; plus the durable Kafka backbone, graceful drain, and
backoff+jitter reconnect. **Status:** ✅ shipped & verified. Completes **Phase 2**.

## Prerequisites
- Day 8 (multi-pod + Redis fan-out) and Day 6 (history/sync cursor).

## Build order (backend)
1. **Kafka** — `pom`: `spring-kafka`; `application.yml`: `spring.kafka.*` (bootstrap-servers,
   String (de)serializers, group `linkup-events`, `listener.missing-topics-fatal: false`).
2. **`events.MessageEventPublisher`** — `KafkaTemplate` → topic `message.created`, key =
   conversationId; publish **after** persist + fan-out in `MessageService.send`. Publish failure is
   logged, never fails the send (message is already durable in Postgres).
3. **`events.MessageEventConsumer`** — `@KafkaListener` logging the event (Day-9 placeholder; real
   push/search/AI consumers grow off the same topic, own groups).
4. **Graceful drain** — `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase`.

## Build order (frontend)
- **`SocketService`** reconnect: replace the fixed delay with **exponential backoff + jitter**
  (`min(base·2^n, 30s) + random(0..1s)`), reset on connect. Kills the thundering-herd on a mass
  reconnect (scenario #8).

## Infra
- `docker-compose`: **Kafka** (KRaft single-node, `apache/kafka:3.9.0`) with a `kafka-topics --list`
  healthcheck; both backend pods get `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092` and
  `depends_on: kafka (healthy)`.

## Verify
- **Kafka pipeline:** send a message → `Kafka[message.created] … seq=N` in a pod's logs.
- **Pod-kill zero-loss (chaos):** `e2e/npm run demo:podkill` — alice on pod-1 sends; `docker kill`
  bob's pod-2; alice sends 3 during the outage; bob reconnects via the gateway to pod-1 and resyncs
  `?after=<seq>` → **4/4 recovered, zero loss**. Restores the pod after.

## Why (one line each)
Durable persist before ack → the message survives any socket death. seq cursor + reconnect/resync →
"give me everything after N" recovers the outage window. Backoff **+ jitter** → a mass reconnect
doesn't stampede the survivors. Graceful drain → planned churn (deploys) is invisible. Kafka log →
durable async backbone decoupled from live delivery; publish failure ≠ send failure (outbox is the
next hardening). One consumer group → each event processed once across pods.

## Decisions referenced
- Hard scenarios #5 (pod kill), #8 (reconnect storm). Redis (sync) vs Kafka (async).
  Interview deep-dive: doc 09. War-story #7.
