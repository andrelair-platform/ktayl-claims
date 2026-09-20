# ADR Log — Claims (wrap over the GlobalCore legacy) (#11)

> **BMAD/SA artefact.** Index of architecture decisions + rationale. Each: Status · Context · Decision ·
> Consequences. **Status: DRAFT / Proposed — for review.**

| ADR | Title | Status | Owner |
|---|---|---|---|
| [001](#adr-001) | Strangler Fig + ACL over GlobalCore (wrap, don't edit) | Proposed | SA/TL |
| [002](#adr-002) | Legacy = GlobalCore on Oracle Free, OUTSIDE k8s (not a new repo, not Postgres) | Accepted | SA/TL |
| [003](#adr-003) | Wrap surface = SOAP→JSON (writes) + CDC Debezium→NATS (events); keep the batch async model | Proposed | SA/TL |
| [004](#adr-004) | CQRS-lite read-model (Postgres) projected from CDC | Proposed | SA/TL |
| [005](#adr-005) | AI reaches structured data only via ACL SQL-tools | Proposed | SA/TL |
| [006](#adr-006) | Stack: GlobalCore Java8/SOAP/Oracle · ACL Java/Spring Boot · Debezium · Postgres | Accepted | SA/TL |
| [007](#adr-007) | The Oracle→Postgres migration is a later, optional footnote | Proposed | SA/TL |

---

## ADR-001 — Strangler Fig + ACL over GlobalCore {#adr-001}
**Context.** A real insurer's claims run on a legacy core that still works and can't be casually replaced;
modernization means wrapping it, not a rewrite (EA §2b; the ktayl *Strangler Fig / ACL* principle). We own a
deliberately-legacy carrier — **GlobalCore** — for exactly this.
**Decision.** `ktayl-claims` is the modern Claims capability built **AS the ACL/strangler** over GlobalCore.
GlobalCore is **frozen** — you never add a modern feature *inside* it (SOAP/schema/batch stay authentically
legacy); you wrap it.
**Consequences.** The IS gains a credible enterprise-modernization system (the HDI shape) *and* a real needed
domain (Claims) at once. Cost: you must *understand* GlobalCore (SOAP contracts, PL/SQL, batch) before
wrapping — which is the skill. New capability lives in the ACL.

## ADR-002 — Legacy = GlobalCore on Oracle Free, outside k8s {#adr-002}
**Context.** Policy is already modern (live PAS #6), so the legacy must be a domain we *need but haven't
built* — **Claims**. The legacy engine already exists: **GlobalCore** (`globalcore-legacy`). Real **Oracle**
experience is a required outcome.
**Decision.** The legacy is **GlobalCore evolved to Oracle Free + PL/SQL** for the **Claims** domain — **not**
a new `ktayl-legacy-core` repo, **not** Postgres. GlobalCore + its Oracle run as **Docker containers on the
controller, OUTSIDE k3s** (like MinIO) — **decided**; the in-cluster ACL reaches its SOAP (`:8080`) and
Debezium reaches its Oracle (`:1521`).
**Consequences.** One legacy, on real Oracle, delivering Claims — realism + the Oracle credential, off the
constrained cluster (no Longhorn). **Accepted:** single instance (no RAC/Data Guard); a cross-boundary hop.

**Placement options (same access model either way).** GlobalCore runs as a plain `docker compose` (Oracle
`:1521` + SOAP app `:8080`) on a host reachable on the internal LAN (`10.0.0.0/24`); in-cluster consumers
(the ACL, Debezium) reach it by IP:port, gated by a default-deny egress netpol. Two valid hosts:
- **(a) The controller (`10.0.0.1`)** — simplest, alongside MinIO/MAAS. *Caveat, not a blocker:* the
  controller disk is tight (~98 G, MinIO ~33 G) → size the Oracle data volume, add a disk alert.
- **(b) A worker node's local disk (preferred if the controller tightens)** — e.g. a storage worker with
  headroom; **still outside k3s** (its own `docker compose`, not a pod), still on the LAN, so the access
  model, egress netpol, and CDC path are **identical** — it just spreads load off the controller and gives
  Oracle a dedicated local disk (no Longhorn). Start on (a); relocate to (b) the moment controller headroom
  is the binding constraint.

**Rejected alternative — OCI "Always Free" Oracle.** *Autonomous DB (ATP)* is real Oracle + PL/SQL but
**managed** → no LogMiner/SYSDBA, so Debezium CDC (S005) is impossible (Oracle's path there is paid
GoldenGate). *A free VM* doesn't fit either: the generous free box is **Ampere ARM64** but Oracle DB Free
is **x86-64 only** (emulation = unusable DB speed), and the free x86 VMs are 1 GB RAM. Self-managed + local
is the only option that gives x86 + enough RAM + full LogMiner/PL/SQL control. OCI ATP stays a *future,
optional* variant only if we ever drop the Debezium-LogMiner requirement.

GlobalCore's v0 was Postgres-pretending-to-be-Oracle → it evolves to real Oracle here.

## ADR-003 — Wrap surface: SOAP→JSON + CDC; keep the batch {#adr-003}
**Context.** GlobalCore's interfaces are authentically legacy: **SOAP/XML only** (`/ws`), and a create is
**pending until a nightly batch** activates it. The wrap must honour that, not fake real-time.
**Decision.** The ACL calls GlobalCore's **SOAP** for writes/reads (translating cryptic XML → clean JSON) and
models the **async batch** (pending → activated) honestly. **Debezium** on GlobalCore's **Oracle** captures
changes → **NATS** events (`CLAIM_CREATED`, `CLAIM_STATUS_CHANGED`, `RESERVE_ADJUSTED`) — **not** Kafka.
**Consequences.** Teaches the real legacy-wrap (SOAP translation + async issuance + CDC), not a clean-DB
fantasy. Two egress paths to the controller (SOAP :8080, Oracle CDC :1521), each least-privilege.

## ADR-004 — CQRS-lite read-model (Postgres) {#adr-004}
**Context.** The workbench needs fast, rich reads; hammering the legacy (SOAP or Oracle) for reads is slow
and a DoS surface.
**Decision.** A **PostgreSQL read-model** projected from CDC events powers the workbench; the legacy is
written via SOAP and read for authoritative-critical checks only.
**Consequences.** Reads scale off the legacy; the read-model is **disposable/rebuildable from CDC** (DR).
Cost: eventual consistency (bounded by CDC lag) — accepted.

## ADR-005 — AI via ACL SQL-tools only {#adr-005}
**Context.** The LLM must never speak SOAP/SQL to the legacy, nor bypass claim authorization.
**Decision.** AI reaches **structured** claims data only through **approved, parameterised SQL-tools behind
the ACL** (permitted columns/rows), as the **human's identity** (Authentik), **PII-masked before any LLM**;
**documents** via RAG (Qdrant). **No AI writes in v1** (autonomous action = parked #19, high tier).
**Consequences.** The AI can't become an authz bypass or an injection vector into the legacy. Limited-tier v1.

## ADR-006 — Technology stack {#adr-006}
**Context.** Per the org stack-selection rule. This domain = *wrapping a Java/SOAP/Oracle legacy* + a
transactional claims lifecycle.
**Decision.** Legacy engine = **GlobalCore** (Java 8 / Spring / SOAP, **Oracle Free** + PL/SQL). ACL/strangler
= **Java 21 + Spring Boot — decided** (owner, 2026-09-20): mature SOAP client (spring-ws / JAX-WS) + JPA over
Oracle, the realistic enterprise-legacy-wrap stack, and it adds Java to the LOB. Python (FastAPI + zeep) was
considered for consistency with underwriting and **rejected** — the SOAP/Oracle realism is the point of this
build, and Spring is the stronger fit for it. CDC = **Debezium → NATS**. Read-model = **PostgreSQL**. Frontend
(later) = Next.js.
**Consequences.** Java realism (esp. SOAP + Oracle) at the cost of a heavier solo build than a Python ACL —
accepted deliberately. The stack is settled; no open decision remains at the arch gate.

## ADR-007 — Migration is a later, optional footnote {#adr-007}
**Context.** The pattern often ends in "migrate off Oracle" — but the point is the legacy stays.
**Decision.** GlobalCore + Oracle are **permanent** in this simulation. A **bounded, non-critical**
Oracle→Postgres migration may be done **later** purely to demonstrate the technique — never the core, never
the goal.
**Consequences.** Keeps the hybrid honest (wrapping is the steady state). The migration, if done, is a small
capstone, not v1 scope.
