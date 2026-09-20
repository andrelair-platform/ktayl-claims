# Sprint Plan — Claims v1 (the legacy-wrap thin slice)

> **BMAD artefact — SPRINT PLAN + READINESS GATE.** Decomposes the [Brief](./brief.md) thin slice into
> implementable stories with ACs, and states the readiness verdict.
> **Status: DRAFT for review.** These become `bmad/stories/claims/clm-v1-slice/` files (synced to board
> **#11**) **only after you validate this plan** — nothing is on the board yet, and **no Oracle is pulled**
> until this + the SA set are validated.

## Slice goal

One LOB (**Property**, aligned with Underwriting #12), one claim type: take a claim **FNOL → reserve →
settle → closed entirely through the ACL** over a real **legacy Oracle core**, with a coverage check
against the legacy policy, an immutable audit trail, and legacy changes surfacing as **NATS events via CDC**.

## Story breakdown

Each: parent epic · priority · estimate · acceptance criteria (happy + failure) · DoD.

### S001 — Legacy Oracle core: schema + seed  · [CLM-01 / legacy] · P1 · 5
Evolve **GlobalCore** (`globalcore-legacy`) to **Oracle Free** + the **Claims** schema (claims/reserves/payments + refs), seeded; keep it SOAP/batch/frozen, outside k8s.
- **AC** ✓ Oracle Free runs (controller/node, `:1521`, `FREEPDB1`); ✓ `CUSTOMER/POLICY/CLAIM/CLAIM_RESERVE/PAYMENT` created; ✓ seeded with a Property policy book + customers.
- **AC (fail)** ✗ creds are a **least-privilege app user** (not SYS/SYSTEM); ✗ creds come from **Vault/ESO**, none in Git.
- **DoD** provisioning runbook (not GitOps); data volume + backup to MinIO; disk sizing confirmed (ADR-002 gate).

### S002 — Legacy PL/SQL business logic  · [CLM-01 / legacy] · P1 · 5
The rules that make it a real legacy: PL/SQL you must understand before wrapping.
- **AC** ✓ `PKG_CLAIMS.PROC_CREATE_CLAIM` (allocates claim number, initial state); ✓ `FUNC_CALCULATE_RESERVE`; ✓ in-DB **state-machine** (notified→…→closed) enforced; ✓ `TRG_CLAIM_AUDIT` append-only on every change.
- **AC (fail)** ✗ an illegal state transition is rejected **in the DB**, not just the app.
- **DoD** PL/SQL committed (signed); audit trail demoed.

### S003 — ACL / API: FNOL + coverage check  · [CLM-01 / ACL] · P1 · 8
The modern wrapper's entry point.
- **AC** ✓ `POST /claims` (FNOL) → calls `PROC_CREATE_CLAIM` → returns the claim; ✓ **coverage check** reads the legacy `POLICY` via the ACL and **rejects an out-of-cover claim**; ✓ idempotent FNOL (dedup key).
- **AC (fail)** ✗ no consumer can reach Oracle except the ACL (default-deny egress, T5); ✗ only parameterised/bound calls reach the legacy (no dynamic SQL, T9).
- **DoD** OTel span ACL→Oracle; contract test; egress NetworkPolicy verified.

### S004 — ACL: lifecycle + reserve + settle  · [CLM-01 / ACL] · P1 · 8
- **AC** ✓ lifecycle transitions via the ACL drive the legacy state-machine; ✓ reserve set/adjust (`FUNC_CALCULATE_RESERVE`) with history; ✓ settlement/payment recorded; ✓ **authority matrix** (high reserve/settle → senior) enforced **server-side**.
- **AC (fail)** ✗ a settle above authority is refused (T7); ✗ every change lands on the audit trail (AUD-1).
- **DoD** authority + audit tests.

### S005 — CDC: Debezium → NATS  · [CLM-01 / integration] · P1 · 8
- **AC** ✓ Debezium (Oracle connector, LogMiner/XStream) captures `CLAIM`/`CLAIM_RESERVE` changes; ✓ publishes `CLAIM_CREATED` / `CLAIM_STATUS_CHANGED` / `RESERVE_ADJUSTED` to **NATS**; ✓ CDC lag < 10s (PERF-3).
- **AC (fail)** ✗ if Debezium is down, ACL writes still succeed and the stream catches up on reconnect, **no data loss** (AVL-3).
- **DoD** connector health metric + alert; replay tested.

### S006 — Read-model + workbench reads (CQRS-lite)  · [CLM-01 / read] · P2 · 5
- **AC** ✓ a Postgres projector consumes the NATS events → claims read-model; ✓ the ACL serves claim reads from the read-model (p95 < 1.5s, PERF-1); ✓ **reads never hit Oracle** (T10).
- **AC (fail)** ✗ the read-model is **rebuildable from CDC** (wipe + replay) (DR-2).
- **DoD** rebuild demoed; RED metrics.

### S007 — Governed AI read-tool (optional in v1)  · [CLM-01 / AI, limited] · P3 · 5
- **AC** ✓ one approved SQL-tool behind the ACL (e.g. `get_claim_summary`) returns permitted columns, **as the human's identity**, **PII-masked before the LLM**, traced in Langfuse; ✓ document Q&A via RAG.
- **AC (fail)** ✗ the LLM never emits SQL at Oracle; ✗ AI cannot write (AI-1); ✗ a restricted claim isn't exposed via the tool (T8).
- **DoD** security-gate items (T4/T8) tested.

**Slice total ≈ 44 pts.** Sequence: **S001→S002 (legacy runs) → S003→S004 (the wrap) → S005 (events) →
S006 (reads) → S007 (AI, optional).**

## Sprint-planning readiness gate

| Check | Verdict |
|---|---|
| Business need grounded | ✅ EA §2b + Claims domain + CLM-01 |
| Product contract (PRD, NFR, compliance) | ✅ [PRD](./prd.md) + [NFR](./architecture/nfr-register.md) |
| Architecture spine + C4 + ADRs | ✅ [Solution Architecture](./architecture/solution-architecture.md) + [ADRs](./architecture/adr/000-index.md) |
| Threat model (boundary change) | ✅ [Threat Model](./architecture/threat-model.md) — T4/T8 (AI PII) + T5 (legacy egress/least-priv) = the security-gate blockers |
| Scope disciplined (thin slice, deferrals explicit) | ✅ one LOB, lifecycle spine only; adjusters/subrogation/fraud/litigation deferred; migration a later footnote |
| **Open decisions for the owner** | ⚠️ **(1)** ACL stack — **Java/Spring Boot** (Oracle realism) vs **Python/FastAPI** (consistency w/ underwriting), ADR-006; **(2)** Oracle placement — controller vs a worker node (disk gate, ADR-002) |

**Verdict: CONCERNS → PASS on two owner decisions.** The plan is implementation-ready once you settle
(1) the ACL stack and (2) the Oracle placement. **No Oracle is pulled and no repo is created until this
plan + the SA set are validated** — same discipline as Underwriting #12.

## After validation
1. You validate this + the SA set (and settle the 2 decisions).
2. Evolve **GlobalCore** (`globalcore-legacy`) to Oracle + Claims + write `bmad/stories/claims/clm-v1-slice/` (S001–S007) → sync to board **#11**. No *new* legacy repo.
3. Build in sequence — legacy runs first, security gate (T4/T5/T8) enforced.
