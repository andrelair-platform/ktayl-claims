# Product Brief — Claims, built as a strangler over a legacy Oracle core (ktayl-claims, board #11)

> **BMAD artefact — DISCOVERY.** The business framing above the PRD. Grounded in the
> [EA Blueprint §2b](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/enterprise-architecture-blueprint)
> and [Legacy-Core Modernization](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/legacy-core-modernization).
> **Status: DRAFT for review — no build (no Oracle pulled, no code).**

> **Two-layer reminder.** This is the **ktayl-solution insurance IS** (org/business context), **not** the
> RNCP certification (that is **Retrieva**). Nothing here is cert evidence for a product.

## The problem — two problems, one product

**Business problem:** an IARD insurer's **claims** operation is its core cost engine. A claim must move
through a governed lifecycle (FNOL → assessment → reserve → settlement → closure) with reserves,
payments, SLA timers, a coverage check against the policy, and an immutable audit trail. Today ktayl has
**no claims system** (#11 is an empty scaffold).

**Architecture problem (the real point):** a real insurer does **not** build claims greenfield — it has a
**legacy core that still works** (a GERAS-style Oracle claims/policy system, PL/SQL-heavy, authoritative
for the historical book) that it **cannot casually replace**, and it modernizes *around* it. This product
deliberately simulates that: it stands up a **legacy Oracle core** and wraps it with a **modern
Anti-Corruption Layer / strangler**, rather than pretending the enterprise is greenfield.

## Who it's for

- **Claims handler** (primary) — lives in the claims workbench: opens FNOL, assesses, sets reserves, settles.
- Senior claims handler / referrals · loss adjuster · SIU (fraud) · compliance · finance (payments).
- **Downstream consumers** (later) — risk, analytics, reinsurance — via events, not direct DB access.

## Why now

Claims feeds off the **live `ktayl-policy-service` (PAS)** and is the natural next domain after Underwriting
#12. More importantly, it is the **right domain to carry the legacy-core spine** (EA §2b): Claims #11 is an
empty scaffold (no modern service to conflict with), and it's already framed as *"a GERAS-style claims
platform."* Standing it up as a strangler makes the whole ktayl IS a credible **enterprise-modernization**
system — the exact HDI shape — instead of a set of greenfield apps.

## Goals (v1)

1. Stand up a **real, working legacy Oracle core** (`ktayl-legacy-core`, Oracle Free, outside k8s) holding
   the authoritative claim/legacy-policy record with **PL/SQL** business logic (create-claim, reserve calc,
   state transitions, audit trigger). It runs; it is **frozen** — wrapped, not refactored.
2. Wrap it with a modern **Anti-Corruption-Layer** service (`ktayl-claims`) exposing clean claims APIs — no
   app or AI ever touches Oracle directly.
3. Propagate legacy changes as **events** via **CDC (Debezium → NATS)** — consumers react, they don't poll.
4. Prove **one narrow claim lifecycle end-to-end** through the wrapper: FNOL → reserve → settle → closed,
   with coverage checked against the policy and every change on an immutable audit trail.

## Non-goals (v1) — deliberate, not gaps

- **Not** rebuilding claims greenfield in Postgres — the legacy Oracle core is the authoritative record; the
  point is to *wrap* it.
- **No** loss-adjuster (CLM-02), subrogation (CLM-03), fraud/SIU (CLM-04) or litigation (CLM-05) yet — the
  lifecycle spine first; those hang off it.
- **No** AI copilot / autonomous action (parked #19) — v1 AI, if any, is read-only via approved tools.
- **No** Oracle migration exercise as the point — the legacy **stays**; a bounded migration is a *later*,
  optional footnote, never the goal.
- **No** Oracle inside Kubernetes / RAC / Data Guard — one Free-edition container on traditional infra.

## The thin slice (first thing to build)

```
Legacy Oracle core (a few tables + PL/SQL: PROC_CREATE_CLAIM, FUNC_CALCULATE_RESERVE,
   state-machine + TRG_CLAIM_AUDIT; seeded with a legacy policy book + customers)
      ▲            │ CDC (Debezium)
      │ ACL calls  ▼
ktayl-claims (modern ACL/strangler): FNOL intake → PROC_CREATE_CLAIM → claim
   → coverage check (reads legacy policy) → reserve → settle → closed
   → every change → NATS event (CLAIM_CREATED / CLAIM_STATUS_CHANGED / RESERVE_ADJUSTED)
      → a modern read-model (Postgres) powers the claims workbench
```

Pick **one LOB** (align with Underwriting's **Property**) and **one claim type**. Exercises the ACL spine
+ CDC + the audit trail; defers everything else; produces a real, demoable wrapped-legacy claim lifecycle.

## Success metrics (the honest "measurable outcome")

| Metric | v1 target signal |
|---|---|
| A claim runs FNOL→closed **through the wrapper**, never touching Oracle directly | end-to-end demo |
| The legacy Oracle core holds the **authoritative** record + PL/SQL logic | claim + reserve created via `PROC_CREATE_CLAIM` |
| Every legacy change becomes a **NATS event** via CDC | `CLAIM_STATUS_CHANGED` observed on NATS, no polling |
| **Coverage check** reads the legacy policy through the ACL | FNOL rejects an out-of-cover claim |
| **Immutable audit trail** on every state change | who/when/why queryable |

## Scope boundary & dependencies (contracts, not blockers)

- **`ktayl-legacy-core`** (new, Oracle Free, outside k8s) — the system-of-record this product wraps.
- **`ktayl-policy-service` (live)** — the *modern* PAS; the legacy holds the historical policy book, PAS the
  new one; coverage checks read the legacy via the ACL (documented cross-read).
- **`ktayl-integration` #25 / NATS** — hosts the CDC (Debezium→NATS) capability + ACL egress patterns.
- **Compliance #15** — claims audit + (later) ALFA fraud reporting; evidence to its control library.
- **AI platform** — reaches claims **only via approved SQL-tools behind the ACL** + RAG for docs; never Oracle direct.

## Governance path

**Path C** (new product + a security/architecture boundary — a legacy datastore, CDC, cross-boundary AI) →
the **architecture spine review + security review gates** apply. Regulatory (Solvency II reserves, GDPR PII,
DORA third-party, AI Act if AI) declared in the [PRD](./prd.md) §Compliance + the [Threat Model](./architecture/threat-model.md).

## References

[PRD](./prd.md) · [Solution Architecture](./architecture/solution-architecture.md) · [NFR Register](./architecture/nfr-register.md) · [Threat Model](./architecture/threat-model.md) · [ADR log](./architecture/adr/000-index.md) · [v1 Sprint Plan](./clm-v1-sprint-plan.md)
Epics: `bmad/stories/claims/` (CLM-01…CLM-05) · Board **#11**
