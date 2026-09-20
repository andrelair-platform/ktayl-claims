# Solution Architecture — Claims (strangler over a legacy Oracle core) (#11)

> **BMAD/SA artefact — the technical spine.** Path-C. Assembles the C4 views, the
> [NFR Register](./nfr-register.md), the [Threat Model](./threat-model.md) and the [ADR log](./adr/000-index.md).
> Owner = SA/TL; approved at the **architecture spine review + security review** gates.
> **Status: DRAFT for review — no build.**

## 1. Overview

`ktayl-claims` is the modern **Anti-Corruption Layer / strangler** over a deliberate **legacy Oracle core**
(`ktayl-legacy-core`). The legacy holds the authoritative claim/legacy-policy record and its PL/SQL business
logic and **stays** (Strangler Fig — wrapped, not replaced). The ACL exposes clean claims APIs, **CDC
(Debezium) publishes legacy changes to NATS**, and a **Postgres read-model** projects those events to power
the workbench (CQRS-lite, keeping read load off Oracle). Nothing but the ACL and the CDC connector touches
Oracle. Delivery follows the platform GitOps + Kargo model.

**Technology stack** (ADR-006): legacy = **Oracle Free + PL/SQL** (container, outside k8s); ACL =
**Java 21 + Spring Boot** (candidate — the realistic enterprise-Oracle-wrapping stack; confirm vs FastAPI);
CDC = **Debezium → NATS**; read-model = **PostgreSQL**; frontend (later) = Next.js.

**Decisions of record** (full log in [ADRs](./adr/000-index.md)):
- **ADR-001** — **Strangler Fig + ACL**: the legacy is authoritative and frozen; modern services wrap it, never write around it.
- **ADR-002** — **Oracle Free, OUTSIDE k8s** (container on the controller/a node) — realism + off the constrained cluster.
- **ADR-003** — **CDC over polling** (Debezium → **NATS**, not Kafka); events are the downstream contract.
- **ADR-004** — **CQRS-lite read-model** (Postgres) projected from CDC; reads never hit Oracle.
- **ADR-005** — **AI reaches structured data only via approved ACL SQL-tools** (RAG for docs); identity propagated; no autonomous action.
- **ADR-006** — stack (above). **ADR-007** — the bounded migration is a *later, optional* footnote; the legacy stays.

## 2. C4 — Level 1: System Context

```mermaid
flowchart LR
  handler(["Claims handler<br/>(primary user)"])
  compliance(["Compliance / SIU"])

  claims["ktayl-claims<br/>ACL / strangler · lifecycle · workbench"]

  legacy["ktayl-legacy-core<br/>Oracle Free (outside k8s) — authoritative claim/legacy-policy + PL/SQL"]
  pas["ktayl-policy-service<br/>modern PAS (LIVE) — new policy book"]
  bus["NATS<br/>event backbone"]
  ai["LiteLLM + vLLM · Qdrant<br/>governed AI (read-only in v1)"]
  down["Risk · Analytics · Reinsurance<br/>downstream (via events)"]

  handler -->|"FNOL, assess, reserve, settle"| claims
  compliance -->|"audit, fraud, ALFA"| claims
  claims -->|"ACL calls + PL/SQL (create claim, reserve)"| legacy
  claims -->|"coverage read (historical book)"| legacy
  claims -->|"coverage read (new book)"| pas
  legacy -->|"CDC (Debezium) change events"| bus
  claims -->|"publishes lifecycle events"| bus
  bus -->|"consumed downstream"| down
  claims -->|"approved SQL-tools + RAG (identity-propagated, PII-masked)"| ai

  classDef person fill:#08427b,stroke:#052e56,color:#fff
  classDef sys fill:#1168bd,stroke:#0b4884,color:#fff
  classDef legacy fill:#8B4513,stroke:#5c2e0e,color:#fff
  classDef ext fill:#e6e6e6,stroke:#999,color:#111
  class handler,compliance person
  class claims sys
  class legacy legacy
  class pas,bus,ai,down ext
```

## 3. C4 — Level 2: Containers

```mermaid
flowchart TB
  handler(["Claims handler"])

  subgraph LEG["ktayl-legacy-core — Oracle Free (OUTSIDE k8s, on the controller)"]
    ora[("Oracle Free · FREEPDB1<br/>CLAIM · CLAIM_RESERVE · PAYMENT · POLICY(legacy) · CUSTOMER<br/>PKG_CLAIMS · FUNC_CALCULATE_RESERVE · TRG_CLAIM_AUDIT")]
  end

  subgraph S["ktayl-claims (system boundary, in k8s)"]
    acl["ACL / API · Java 21 + Spring Boot<br/>FNOL · lifecycle · reserve · coverage-check · authz/audit"]
    proj["Projector · CDC consumer<br/>events → read-model"]
    rm[("PostgreSQL read-model<br/>claims workbench projection (CQRS-lite)")]
    web["Claims workbench · Next.js (later)"]
  end

  cdc["Debezium (Oracle connector)<br/>→ NATS sink"]
  bus["NATS"]
  pas["ktayl-policy-service (PAS)"]
  ai["LiteLLM → vLLM · Qdrant"]

  handler -->|"HTTPS / OIDC"| web
  web --> acl
  acl -->|"JDBC + PL/SQL (writes + coverage read)"| ora
  acl -->|"coverage read (new book)"| pas
  ora -->|"change data"| cdc
  cdc -->|"CLAIM_* events"| bus
  bus --> proj
  proj --> rm
  acl -->|"reads"| rm
  acl -->|"approved SQL-tools + RAG"| ai
  acl -->|"lifecycle events"| bus

  classDef legacy fill:#8B4513,stroke:#5c2e0e,color:#fff
  classDef ext fill:#e6e6e6,stroke:#999,color:#111
  class ora legacy
  class cdc,bus,pas,ai ext
```

## 4. C4 — Level 3: Deployment

```mermaid
flowchart TB
  subgraph CTRL["Controller (traditional infra, OUTSIDE k8s)"]
    ora[("Oracle Free container<br/>Docker · :1521 · data volume + backup")]
  end
  subgraph K["minicloud k3s cluster (GitOps)"]
    subgraph NS["namespace: claims (dev + prod)"]
      acl["claims-acl · Deployment + HPA"]
      proj["claims-projector · Deployment"]
      rm[("postgresql-claims · StatefulSet + Longhorn + Velero")]
      cdc["debezium-connect · Deployment"]
    end
    subgraph SH["shared platform ns (live)"]
      bus["NATS"]
      pas["ktayl-policy-service"]
      ai["LiteLLM / vLLM (ai ns)"]
    end
  end
  acl -->|"egress allow → controller:1521 only"| ora
  cdc -->|"LogMiner/XStream → NATS"| ora
  cdc --> bus
  proj --> bus
  acl --> pas
```

**Delivery:** GitOps (ArgoCD) + **Kargo** dev→prod for the in-cluster services (ACL, projector), Helm
wrapper-chart golden path; Authentik OIDC, **ESO/Vault** for the Oracle creds (`secret/platform/oracle-legacy`),
cert-manager TLS, **default-deny NetworkPolicy with an explicit egress allow to `controller-ip:1521` only**.
The Oracle container itself is **not** GitOps-managed (traditional infra) — provisioned via a documented
runbook, like MinIO.

## 5. Component responsibilities

| Component | Stack | Owns | Notes |
|---|---|---|---|
| **Legacy core** | Oracle Free + PL/SQL | authoritative claim/legacy-policy record + business rules | frozen; wrapped not refactored (ADR-001) |
| **ACL / API** | Java 21 + Spring Boot | FNOL, lifecycle, reserve, coverage-check, authz, audit, AI tools | the only writer to the legacy |
| **Debezium** | Oracle connector → NATS | capture legacy changes as events | ADR-003; not Kafka |
| **Projector** | CDC consumer | events → read-model | ADR-004 |
| **Read-model** | PostgreSQL | workbench projection | reads never hit Oracle |

## 6. Key data flows

1. **FNOL:** handler → ACL → **coverage check** (legacy `POLICY`) → `PROC_CREATE_CLAIM` (legacy) →
   `TRG_CLAIM_AUDIT` records it → Debezium emits `CLAIM_CREATED` → projector updates the read-model.
2. **Reserve:** ACL → `FUNC_CALCULATE_RESERVE` (legacy) → `RESERVE_ADJUSTED` event → read-model + downstream.
3. **Settle/close:** ACL drives the legacy state-machine → `CLAIM_STATUS_CHANGED` / `CLAIM_PAID` events.
4. **AI (v1, read-only):** handler question → ACL SQL-tool (approved columns, identity-scoped, PII-masked) →
   read-model/legacy; document questions → RAG (Qdrant). Never the LLM emitting SQL at Oracle.

## 7. Cross-references

[Brief](../brief.md) · [PRD](../prd.md) · [NFR Register](./nfr-register.md) · [Threat Model](./threat-model.md) · [ADR log](./adr/000-index.md) · [Legacy-Core Modernization](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/legacy-core-modernization)
