# Implementation Track — build runbooks

How to **write** each feature, in order. Each runbook: **Prerequisites · Build order · Key files ·
Why it's done this way · Verify.** Code lives in the app repo (`linkup/backend`, `linkup/frontend`).

| # | Day | Topic | Status |
|---|-----|-------|--------|
| [01](./01-day1-auth-and-domain.md) | 1 | Auth + `User`/`Device` domain + `/me` + Angular login/home + SocketService stub | ✅ full |
| [02](./02-day2-conversations.md) | 2 | Conversations + participants (REST) + conversation-list view | ✅ full |
| [03](./03-day3-websocket.md) | 3 | WebSocket/STOMP transport + auth handshake + live SocketService | ✅ full |
| [04](./04-day4-messaging.md) | 4 | Send/receive, server-assigned `seq`, `seq`-ordered render | ✅ full |
| [05](./05-day5-optimistic-send.md) | 5 | Optimistic send (render pending → reconcile on echo) + retry | ✅ full |
| [06](./06-day6-history-and-sync.md) | 6 | History (cursor pagination) + reconnect/sync — **Phase 0 done** | ✅ full |
| [07](./07-day7-presence-typing-receipts.md) | 7 | Presence + typing (Redis) + read receipts + unread | ✅ full |
| 08+ | 8+ | Redis Pub/Sub fan-out → resiliency → media → push → flagship | ⬜ per phase |

Outlined runbooks get written in full the moment their feature ships.
