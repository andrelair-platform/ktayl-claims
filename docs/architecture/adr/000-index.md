# ADR Log — Claims / Legacy-Core (#11)

> **BMAD/SA artefact.** Index of architecture decisions + rationale. Each: Status · Context · Decision ·
> Consequences. **Status: DRAFT / Proposed — for review.**

| ADR | Title | Status | Owner |
|---|---|---|---|
| [001](#adr-001) | Strangler Fig + Anti-Corruption Layer (wrap, don't replace) | Proposed | SA/TL |
| [002](#adr-002) | Oracle Free, OUTSIDE Kubernetes (traditional infra) | Proposed | SA/TL |
| [003](#adr-003) | CDC over polling — Debezium → NATS (not Kafka) | Proposed | SA/TL |
| [004](#adr-004) | CQRS-lite read-model (Postgres) projected from CDC | Proposed | SA/TL |
| [005](#adr-005) | AI reaches structured data only via ACL SQL-tools | Proposed | SA/TL |
| [006](#adr-006) | Stack: legacy Oracle+PL/SQL · ACL Java/Spring Boot · Debezium · Postgres | Proposed | SA/TL |
| [007](#adr-007) | The Oracle→Postgres migration is a later, optional footnote | Proposed | SA/TL |

---

## ADR-001 — Strangler Fig + Anti-Corruption Layer {#adr-001}
**Context.** A real insurer's claims run on a legacy core that still works and can't be casually replaced;
modernization means wrapping it, not a rewrite (EA §2b; the ktayl *Strangler Fig / ACL* principle).
**Decision.** Stand up a deliberate **legacy Oracle core** as the authoritative record; `ktayl-claims` is
its **ACL/strangler**. The legacy is **frozen** (wrapped, intercepted, strangled — never refactored). No
app/portal/AI writes around the ACL.
**Consequences.** The IS becomes a credible enterprise-modernization system (the HDI shape), not greenfield.
Cost: you must *understand* the legacy (PL/SQL) before wrapping — which is the point/skill. New capability is
built modern in the ACL; the legacy shrinks in scope over time but keeps running.

## ADR-002 — Oracle Free, outside Kubernetes {#adr-002}
**Context.** Need a real Oracle without RAC/Exadata/licensing, on a resource-constrained cluster already
strained by Longhorn.
**Decision.** **Oracle Database Free** (`container-registry.oracle.com/database/free:latest-lite`; community
mirror `gvenzl/oracle-free` as fallback), **one Docker container on the controller (or a node), OUTSIDE
k3s** — like MinIO. In-cluster services reach it at `controller-ip:1521`.
**Consequences.** Realistic ("legacy on traditional infra, modern platform on k8s"), and keeps ~2 GB Oracle
+ its storage off the cluster (no Longhorn PVC). **Accepted:** the legacy has no HA (single instance) and
adds a cross-boundary network hop; the controller disk is tight → **sizing/placement gated** in the Path-C
architecture (may move to a worker's local disk). Not GitOps-managed — provisioned via a runbook.

## ADR-003 — CDC over polling (Debezium → NATS) {#adr-003}
**Context.** Modern services and analytics must react to legacy changes without polling Oracle (load + lag).
**Decision.** **Debezium** captures legacy `CLAIM`/`CLAIM_RESERVE` changes and publishes to **NATS**
(Debezium Server sink) — **not** Kafka (NATS is the platform backbone). Events are the downstream contract.
**Consequences.** Real event-driven integration; downstream never reads the legacy directly. A new platform
capability (`ktayl-integration`/IS Foundations) valuable beyond claims. Debezium on Oracle needs LogMiner/
XStream config on the legacy — a documented setup, gated at the security review (privileged DB access).

## ADR-004 — CQRS-lite read-model (Postgres) {#adr-004}
**Context.** The workbench needs fast, rich reads; hammering Oracle for reads is both slow and a DoS surface (T10).
**Decision.** A **PostgreSQL read-model** projected from the CDC events powers the workbench; the legacy is
written via the ACL and read for authoritative-critical checks (coverage) only.
**Consequences.** Reads scale off Oracle; the read-model is **disposable/rebuildable from CDC** (DR-2).
Cost: eventual consistency (bounded by CDC lag) — accepted; authoritative-critical reads bypass it.

## ADR-005 — AI via ACL SQL-tools only {#adr-005}
**Context.** The LLM must never emit SQL at Oracle, nor bypass claim authorization (T4/T8/T9).
**Decision.** AI reaches **structured** claims data only through **approved, parameterised SQL-tools behind
the ACL** (permitted columns/rows), running **as the human's identity** (Authentik), with **PII masked
before any LLM**; **documents** via RAG (Qdrant). **No AI writes in v1** (autonomous action = parked #19, high tier).
**Consequences.** The AI can't become an authz bypass or an injection vector. Limited-tier AI-Act posture in v1.

## ADR-006 — Technology stack {#adr-006}
**Context.** Per the org stack-selection rule (best-fit per project). This domain is *wrapping Oracle* + a
transactional claims lifecycle.
**Decision.** Legacy = **Oracle Free + PL/SQL**. ACL/strangler = **Java 21 + Spring Boot** — the realistic,
mature enterprise-Oracle-integration stack (JDBC, transactions, the language a bank/insurer actually wraps
Oracle in) and it adds Java to the LOB. CDC = **Debezium → NATS**. Read-model = **PostgreSQL**. Frontend
(later) = Next.js + React. **Open at review:** Java/Spring vs Python+FastAPI — Spring for the transactional/
Oracle realism; FastAPI if we want consistency with underwriting. **Confirm at the architecture gate.**
**Consequences.** Java realism vs a heavier build than FastAPI for a solo dev; recorded as an open decision.

## ADR-007 — Migration is a later, optional footnote {#adr-007}
**Context.** The essay/pattern often ends in "migrate off Oracle" — but the *point* is the legacy stays.
**Decision.** The legacy Oracle core is **permanent** in this simulation. A **bounded, non-critical**
Oracle→Postgres migration (e.g. `BROKER_NOTES`) may be done **later** purely to demonstrate the technique —
never the mission-critical core, never the goal.
**Consequences.** Keeps the hybrid honest (wrapping is the steady state). The migration, if done, is a small
capstone exercise, not v1 scope.
