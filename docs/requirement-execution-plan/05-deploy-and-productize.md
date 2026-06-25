# Phase 05 — Deploy, observe, productize (+ multi-region stretch)

**Status:** ⬜ · **Roadmap:** Days 14–15

## Goal
The app **live at a public URL**, **observable** (trace one message across pods), **load-tested**, and
**documented** with the war-stories. Stretch: a second region with global routing.

## Scope
**In:** Dockerize backend + Angular; Helm charts; deploy to k8s (kind→cloud or managed); GitHub Actions
build→test→push→deploy; OpenTelemetry tracing a message end-to-end (send → seq → fan-out → deliver →
ack) into Grafana/Tempo; rate limiting + abuse guard; load test (k6/Gatling); the README + ADRs + STAR
write-ups. **Stretch (Phase 5 spec):** geo-routed WS tiers, cross-region message replication, presence
federation. **Out:** anything that isn't a deployed, observable, documented system.

## Architecture delta
```
   CI/CD: GitHub Actions → build/test → image → deploy (Argo CD GitOps)
   Observability: OTel → Prometheus / Grafana / Tempo / Loki   (one message = one trace across pods)
   Rollout: rolling everywhere; canary (Istio weight) for the delivery path; stateful WS-tier drain
   Stretch: region-B WS tier + global LB (latency routing) + cross-region replication
```
> **Full strategy:** [`linkup/docs/DEPLOYMENT-ARCHITECTURE.md`](../DEPLOYMENT-ARCHITECTURE.md)
> — the two-tier deploy semantics, the stateful WS-tier graceful drain + reconnect/resync, scaling
> (HPA/KEDA/Karpenter), multi-region, and rollback/DR. ([ADR-0012](../adr/0012-stateful-ws-tier-rolling-drain.md))

## Done when
- [ ] App reachable at a public URL; Angular client talks to it.
- [ ] A Grafana/Tempo trace shows one message crossing pods (send → seq → fan-out → deliver → ack).
- [ ] Load test reports fan-out p99 + reconnect-storm behavior; rate limits hold under an abuse blast.
- [ ] README + per-decision ADRs + STAR war-stories written (cross-pod fan-out, pod-kill zero-loss,
      exactly-once display).

## Maps to
- The SLOs you defend: delivery p99 same-region, reconnect-resync time, zero-loss on pod kill.
- Hard scenarios: all of §9 demonstrable; abuse blast (#13).
