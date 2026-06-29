# Implementation Track — build runbooks

How to **write** each feature, in order. Each runbook: **Prerequisites · Build order · Key files ·
Why it's done this way · Verify.** Code lives in the app repo (`linkup/backend`, `linkup/frontend`).

| # | Day | Topic | Status |
|---|-----|-------|--------|
| [01](./01-day1-auth-and-domain.md) | 1 | Auth + `User`/`Device` domain + `/me` + Angular login/home + SocketService stub | ✅ full |
| [02](./02-day2-conversations.md) | 2 | Conversations + participants (REST) + conversation-list view | ✅ full |
| [03](./03-day3-websocket.md) | 3 | WebSocket/STOMP transport + auth handshake + live SocketService | ✅ full |
| 04 | 4 | Send/receive, server-assigned `seq`, `seq`-ordered render | ⬜ outline |
| 05 | 5 | Idempotent send (`clientMsgId`) + dedup + optimistic UI | ⬜ outline |
| 06 | 6 | History (cursor pagination) + reconnect/sync | ⬜ outline |
| 07+ | 7+ | receipts/presence/typing → fan-out → media → push → flagship | ⬜ per phase |

Outlined runbooks get written in full the moment their feature ships.
