# PRD — Claims (strangler over a legacy Oracle core) — ktayl-claims, board #11

> **BMAD artefact — PRODUCT CONTRACT.** Derived from the [Brief](./brief.md), the
> [EA Blueprint §2b](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/enterprise-architecture-blueprint)
> and [Legacy-Core Modernization](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/legacy-core-modernization).
> **Status: DRAFT for review — no build.** Two-layer: ktayl IS, not the Retrieva cert.

## 1. Vision

A **claims capability** for the ktayl IS, built the way a real insurer modernizes: a **legacy Oracle core**
(**GlobalCore** — Oracle + PL/SQL, SOAP, batch — authoritative) that **still works and stays**, wrapped by a
modern **Anti-Corruption-Layer** service (`ktayl-claims`) that exposes clean APIs, publishes legacy changes
as **CDC events on NATS**, and hosts all *new* claims capability — while nothing (app, portal, AI) touches
Oracle directly.

## 2. Personas

| Persona | Goal | Key need |
|---|---|---|
| **Claims handler** (primary) | Open FNOL, assess, reserve, settle, close | One workbench; coverage auto-checked; audit for free |
| Senior handler | Approve referrals/high reserves | Authority-gated actions |
| Loss adjuster (CLM-02, later) | Field assessment | — |
| SIU / fraud (CLM-04, later) | Investigate suspicious claims | fraud signals from events |
| Compliance | Audit + ALFA fraud reporting | immutable trail + extracts |
| Finance | Claim payments | payment events |

## 3. Functional requirements

⭐ = in the **v1 thin slice**. Full story decomposition: `bmad/stories/claims/` (CLM-01…05) + `docs/clm-v1-sprint-plan.md`.

### Legacy core (GlobalCore — the system of record)
- ⭐ **FR-L1** Evolve **GlobalCore** to **Oracle Free** + the Claims schema: `CLAIM`, `CLAIM_RESERVE`,
  `PAYMENT` + refs (`CUSTOMER`, `POLICY`, `PRODUCT`, `BROKER`).
- ⭐ **FR-L2** **PL/SQL** business logic in GlobalCore: `PKG_CLAIMS` (`PROC_CREATE_CLAIM`,
  `FUNC_CALCULATE_RESERVE`), a claim **state-machine** + append-only audit — reached **only via GlobalCore's SOAP API**.
- ⭐ **FR-L3** Authentically legacy: **SOAP/XML only** (`/ws`), a create is **pending until the nightly batch** activates it; seeded so coverage checks are real.
- **FR-L4** GlobalCore is **frozen**: wrapped/intercepted/strangled — never edited to add a modern feature.

### ACL / strangler (`ktayl-claims` — the modern service)
- ⭐ **FR-A1** **FNOL intake** API → **SOAP `CreateClaim`** to GlobalCore (which runs `PROC_CREATE_CLAIM`) → claim lands **pending**; translated to clean JSON.
- ⭐ **FR-A2** **Coverage check** at FNOL: read the legacy `POLICY` via the ACL; reject out-of-cover.
- ⭐ **FR-A3** Claim **lifecycle** through the ACL: `notified → under-assessment → reserved → settled/refused → closed` (+ reopen); each transition validated (legacy state-machine is authoritative).
- ⭐ **FR-A4** **Reserve** set/adjust via the ACL (`FUNC_CALCULATE_RESERVE`); **payments** recorded.
- ⭐ **FR-A5** **No direct DB access** for any consumer — everything via the ACL API; authz/audit/rate-limit/PII live here.
- **FR-A6** Authority matrix (high reserve / settlement → senior referral) enforced server-side.

### CDC + events (`ktayl-integration` capability)
- ⭐ **FR-C1** **Debezium** captures legacy `CLAIM`/`CLAIM_RESERVE` changes → **NATS** events
  (`CLAIM_CREATED`, `CLAIM_STATUS_CHANGED`, `RESERVE_ADJUSTED`, `CLAIM_PAID`) — **not** Kafka.
- ⭐ **FR-C2** A modern **read-model** (Postgres) projects events → powers the claims workbench (CQRS-lite;
  keeps read load off Oracle).
- **FR-C3** Events are the integration contract for downstream (risk/analytics/reinsurance) — no direct legacy reads.

### Workbench + AI (later / read-only in v1)
- **FR-W1** Claims workbench UI (Next.js) over the read-model + ACL.
- **FR-AI1** AI reaches claims **only via approved SQL-tools behind the ACL** (structured) + RAG (docs);
  identity propagated (Authentik); PII masked before any LLM. No autonomous action (parked #19).

## 4. Non-functional requirements (measurable set: [NFR Register](./architecture/nfr-register.md))

| Category | Target |
|---|---|
| **Performance** | ACL claim read p95 < 1.5s (via read-model); FNOL create (legacy call) p95 < 2s; CDC lag < 10s |
| **Availability** | Business-hours SLO 99.5%; **the wrapper degrades gracefully if the legacy is down** (read-model still serves reads; writes queue/fail-clearly) |
| **Durability/DR** | Oracle Free data volume backed up (container-level); read-model rebuildable from CDC; RPO ≤ 24h |
| **Security** | Authentik SSO+MFA; RBAC; **legacy creds via ESO/Vault**; default-deny egress to `controller-ip:1521`; PII masked before AI |
| **Observability** | RED metrics + OTel traces **across the boundary** (ACL→Oracle), CDC lag metric, Langfuse for AI |
| **Auditability** | Legacy `TRG_CLAIM_AUDIT` (in-DB) + ACL-level audit of every API/AI action — immutable |

## 5. Compliance & regulatory (compliance-by-design)

| Framework | Requirement on this product |
|---|---|
| **Solvency II** | Reserves are authoritative + auditable (legacy reserve calc + history); clean data feed to actuarial |
| **GDPR** | Claimant/beneficiary **PII** in claims — minimisation, retention, **Presidio masking before any LLM**, DPIA if AI tier rises |
| **DORA / Outsourcing** | Oracle + LLM provider = ICT third-party → in the ICT register; egress control; the legacy's DR/BCP posture documented |
| **AML / Anti-fraud (ALFA)** | Fraud/SIU (CLM-04, later) + ALFA reporting; suspicious-claim trail |
| **EU AI Act** | Any v1 AI = **limited tier** (read-only, human-facing): logging, citations. Autonomous claims decisions (#19) = high tier, gated. |

Evidence accrues to **Regulatory & Compliance #15** control library. Cert: **BC02/BC03** (design + secure).

## 5b. Technology stack (ADR-006)

Per the [stack-selection rule](https://github.com/andrelair-platform/minicloud-gitops/blob/main/.claude/rules/tech-stack-selection.md):
- **Legacy:** **GlobalCore** (Java 8 / Spring / **SOAP** / batch) on **Oracle Database Free** (`…/database/free:latest-lite`) + **PL/SQL**, containers **outside k8s** (ADR-002).
- **ACL/strangler:** **Java 21 + Spring Boot** (mature **SOAP client** (spring-ws/JAX-WS); the realistic "modern service wrapping a Java/SOAP/Oracle legacy" stack) — *candidate; confirm at review vs Python (FastAPI + zeep)*.
- **CDC:** **Debezium** (Oracle connector) → **NATS** (Debezium Server sink). **Read-model:** **PostgreSQL**.
- **Frontend (later):** Next.js + React.

## 6. Cost

No new cloud spend. Oracle Free = zero licence. Footprint: one Oracle container (~2 GB RAM + a few GB disk,
on the controller/a node — **disk-gated**, see architecture) + a Spring Boot ACL + a Debezium connector +
Postgres read-model in-cluster. AI (later) via the existing LiteLLM gateway within budget.

## 7. Dependencies

**GlobalCore** (`globalcore-legacy`, evolved to Oracle+Claims) · `ktayl-policy-service` (live, coverage read) · `ktayl-integration`/NATS
(CDC) · Compliance #15 · AI platform (governed) · Vault/ESO (legacy creds). See [Solution Architecture](./architecture/solution-architecture.md).

## 8. Out of scope (v1)

Adjuster mgmt · subrogation · fraud/SIU · litigation · the AI copilot · multi-LOB · an Oracle→Postgres migration.

## 9. Definition of Done (product-level)

A Property claim can be taken **FNOL → reserve → settle → closed entirely through the ACL** (never touching
Oracle directly); the **legacy Oracle core holds the authoritative record** created by its PL/SQL; the
**coverage check** reads the legacy policy; every change is on an **immutable audit trail**; legacy changes
surface as **NATS events via CDC**; and it passes the **architecture + security gates** (legacy creds in
Vault, default-deny egress, PII masked, identity-propagated AI).

## References

[Brief](./brief.md) · [Solution Architecture](./architecture/solution-architecture.md) · [NFR Register](./architecture/nfr-register.md) · [Threat Model](./architecture/threat-model.md) · [ADR log](./architecture/adr/000-index.md)
