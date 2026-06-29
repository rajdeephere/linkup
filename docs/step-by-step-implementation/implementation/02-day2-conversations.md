# Impl 02 — Day 2: Conversations + Participants

**Outcome:** create direct/group conversations, list mine, fetch one, add members — all
membership-authorized; a supporting `GET /v1/users`; Angular conversation-list + create view.
**Status:** ✅ shipped & verified (12/12 backend scenarios).

## Prerequisites
- Day 1 done; infra + backend running ([deployment/01](../deployment/01-local-docker-dev.md)).

## Build order (backend) — `com.linkup.conversation`
1. **Migration** `V2__conversations_and_participants.sql` — `conversations` (type, title,
   last_message_at) + `participants` (role, joined_at, **last_read_seq**, muted_until) with a
   **unique (conversation_id, user_id)** and indexes on both FKs.
2. **Enums** `ConversationType` (DIRECT/GROUP), `ParticipantRole` (MEMBER/ADMIN) — stored as STRING.
3. **Entities** `Conversation`, `Participant` (both relations `LAZY`; unique constraint declared).
   → *`Participant` is a join **entity**, not a `@ManyToMany`, because it carries `lastReadSeq` etc.*
4. **Repos** `ConversationRepository`; `ParticipantRepository` with: `findByUserId`,
   `existsByConversation_IdAndUser_Id` (the authz check), `findByConversationIdFetchUser` /
   `findByConversationIdsFetchUser` (**`join fetch p.user`** to kill N+1), and
   `findDirectConversationBetween` (the dedup query: `group by … having count = 2`).
5. **DTOs** `CreateConversationRequest` (type, title?, memberUserIds), `AddMembersRequest`,
   `ParticipantSummary`, `ConversationResponse`.
6. **Exceptions** `NotFoundException`(404) / `ForbiddenException`(403) / `BadRequestException`(400)
   + handlers in `GlobalExceptionHandler` (uniform `ApiError`).
7. **Service** `ConversationService` — create (direct dedup + exactly-2 rule; group needs title,
   creator=ADMIN), `listMine` (3-query fetch-join, sorted by recency), `getOne`, `addMembers`
   (idempotent). Every read/write calls `assertMember` first.
8. **Controller** `ConversationController` — `POST /v1/conversations`, `GET /v1/conversations`,
   `GET /v1/conversations/{id}`, `POST /v1/conversations/{id}/members`; caller id from
   `@AuthenticationPrincipal` (never the body).
9. **Support** `GET /v1/users` (UserController + `findByIdNot`) → picker source.

## Build order (frontend)
1. `core/conversations/conversation.models.ts` — wire types mirroring the DTOs.
2. `core/conversations/conversation.service.ts` — `list()`, `create()`, `listUsers()` (token
   attached by the interceptor).
3. Rebuild `features/home` — conversation list (direct shows the *other* participant; group shows
   title + member count) + a "new conversation" panel (type toggle, title for group, member picker).

## Verify (backend)
```bash
# register alice/bob/carol/dave; capture tokens + userIds, then:
# create direct → 201 (2 members); create direct again → SAME id (dedup)
# create group  → 201 (creator ADMIN); list (alice/bob) → 2 each
# non-member GET → 403; add member → 200 then GET → 200
# add-to-direct → 400; direct-with-2-others → 400; group-no-title → 400; no token → 403
```
Full matrix passed 12/12 (see Day-2 test). `GET /v1/users` returns others only (excludes self).

## Why (one line each)
Join **entity** (not @ManyToMany) → membership carries `lastReadSeq`/role. One `type` column →
direct & group share structure; invariants live in the service. **Dedup query** → no duplicate 1:1
threads. **Membership = authz** (id from token) → can't access others' chats. **Fetch-join** → 3
queries, not 1+N. **Idempotent add-members** → safe retries. `GET /v1/users` → makes the create UX
real (known directory-leak trade-off; replace with search later).

## Decisions referenced
- The conversation/membership model + invariants map onto [data-model.md](../../data-model.md)
  and [wire-protocol.md](../../wire-protocol.md). Interview deep-dive: doc 03.
