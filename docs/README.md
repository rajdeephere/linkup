# LinkUp — Engineering Docs

How LinkUp is **designed, planned, and built**. These docs ship *with the code* so the design, the
phased plan, and the runbooks all version alongside what they describe — one self-contained source of truth.

---

## Design — how it works

| Doc | What it is |
|-----|-----------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | The map — components, the send/sync paths, sync vs async, and the four invariants you defend with your life |
| [data-model.md](./data-model.md) | Domain model + database schema (Day-1 tables + planned migrations) |
| [wire-protocol.md](./wire-protocol.md) | The contract — REST endpoints + WebSocket/STOMP frames, the `clientMsgId`/`seq` rules |
| [DEPLOYMENT-ARCHITECTURE.md](./DEPLOYMENT-ARCHITECTURE.md) | Deploy strategy — two-tier rollout, the **stateful WS-tier graceful drain**, CI/CD, scaling, multi-region, DR |
| [adr/](./adr/) | **12 Architecture Decision Records** — every load-bearing decision with its trade-off |

## Plan & build — what/why + how

| Doc | What it is |
|-----|-----------|
| [requirement-execution-plan/](./requirement-execution-plan/) | The phased build plan — what/why per phase (Goal · Scope · Done-when) |
| [step-by-step-implementation/](./step-by-step-implementation/) | Granular runbooks, split into `implementation/` (build) + `deployment/` (run/ship) |

## How they fit together

```
   requirement-execution-plan/   →  the plan: phases, each a demoable milestone
        │
        ▼
   step-by-step-implementation/  →  runbooks: the exact build + deploy steps
        │   ▲
        ▼   │ each decision links to…
   adr/ · ARCHITECTURE · data-model · wire-protocol · DEPLOYMENT-ARCHITECTURE
                                  →  the design & the defensible trade-offs
```

Start at [ARCHITECTURE.md](./ARCHITECTURE.md) to understand the system, then
[requirement-execution-plan/](./requirement-execution-plan/) for the build order.
