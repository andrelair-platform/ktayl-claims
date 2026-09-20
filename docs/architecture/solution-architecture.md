# Solution Architecture — Claims (modern wrap over the GlobalCore legacy) (#11)

> **BMAD/SA artefact — the technical spine.** Path-C. Assembles the C4 views, the
> [NFR Register](./nfr-register.md), the [Threat Model](./threat-model.md) and the [ADR log](./adr/000-index.md).
> Owner = SA/TL; approved at the **architecture spine review + security review** gates.
> **Status: DRAFT for review — no build.**

## 1. Overview

`ktayl-claims` is the **modern Claims capability, built AS the Anti-Corruption Layer / strangler** over the
**GlobalCore** legacy (`globalcore-legacy`). GlobalCore is a deliberately-legacy carrier we own and
**freeze** — **Java 8 · Spring · SOAP · nightly batch · stored procedures · Oracle · outside k8s** — evolved
to hold the **Claims** domain (the domain we *need but haven't built*; Policy is already modern in the live
PAS). Wrapping GlobalCore is how modern Claims is *delivered*: the ACL translates **SOAP→JSON**, models the
**async batch** (create → pending → activated), publishes legacy changes as **CDC events on NATS**, and a
**Postgres read-model** powers the workbench. Nothing but the ACL (SOAP) and the CDC connector (Oracle)
touches the legacy.

**Technology stack** (ADR-006): legacy engine = **GlobalCore** (Java 8 / Spring / SOAP, **Oracle Free** +
PL/SQL); ACL/strangler = **Java 21 + Spring Boot** (candidate — mature SOAP client + JPA; confirm vs FastAPI);
CDC = **Debezium (Oracle) → NATS**; read-model = **PostgreSQL**; frontend (later) = Next.js.

**Decisions of record** ([ADRs](./adr/000-index.md)):
- **ADR-001** — **Strangler Fig + ACL** over GlobalCore; the legacy is authoritative and **frozen** (never edited to ease a modern feature).
- **ADR-002** — legacy = **GlobalCore (existing repo) on Oracle Free, OUTSIDE k8s** — not a new `ktayl-legacy-core`, not Postgres.
- **ADR-003** — the wrap surface is **SOAP→JSON (writes) + CDC Debezium→NATS (events)**; the batch async model is preserved, not hidden.
- **ADR-004** — **CQRS-lite read-model** (Postgres) projected from CDC; reads never hit the legacy.
- **ADR-005** — **AI via approved ACL SQL-tools** (RAG for docs); identity-propagated, PII-masked, no AI writes.
- **ADR-006** — stack. **ADR-007** — an Oracle→Postgres migration is a *later, optional* footnote; the legacy stays.

## 2. C4 — Level 1: System Context

```mermaid
flowchart LR
  handler(["Claims handler<br/>(primary user)"])
  compliance(["Compliance / SIU"])

  claims["ktayl-claims<br/>modern Claims = ACL/strangler · workbench"]

  legacy["GlobalCore (globalcore-legacy)<br/>Java 8 · SOAP · batch · Oracle — FROZEN legacy Claims core"]
  pas["ktayl-policy-service<br/>modern PAS (LIVE) — policy is already modern"]
  bus["NATS<br/>event backbone"]
  ai["LiteLLM + vLLM · Qdrant<br/>governed AI (read-only in v1)"]
  down["Risk · Analytics · Reinsurance<br/>downstream (via events)"]

  handler -->|"FNOL, assess, reserve, settle"| claims
  compliance -->|"audit, fraud, ALFA"| claims
  claims -->|"SOAP calls (create/read claim) → JSON"| legacy
  claims -->|"coverage read (policy)"| pas
  legacy -->|"CDC (Debezium on Oracle) change events"| bus
  claims -->|"lifecycle events"| bus
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

  subgraph LEG["GlobalCore — FROZEN legacy (OUTSIDE k8s, on the controller)"]
    app["GlobalCore app · Java 8 / Spring<br/>SOAP /ws (WSDL) · nightly batch (pending→active)"]
    ora[("Oracle Free · PL/SQL<br/>CLAIM · CLAIM_RESERVE · PAYMENT · refs · stored procs")]
    app --> ora
  end

  subgraph S["ktayl-claims (system boundary, in k8s)"]
    acl["ACL / API · Java 21 + Spring Boot<br/>SOAP→JSON · FNOL · lifecycle · authz/audit"]
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
  acl -->|"SOAP (create/read claim)"| app
  acl -->|"coverage read"| pas
  ora -->|"change data"| cdc
  cdc -->|"CLAIM_* events"| bus
  bus --> proj
  proj --> rm
  acl -->|"reads"| rm
  acl -->|"approved SQL-tools + RAG"| ai
  acl -->|"lifecycle events"| bus

  classDef legacy fill:#8B4513,stroke:#5c2e0e,color:#fff
  classDef ext fill:#e6e6e6,stroke:#999,color:#111
  class app,ora legacy
  class cdc,bus,pas,ai ext
```

## 4. C4 — Level 3: Deployment

```mermaid
flowchart TB
  subgraph CTRL["Controller (traditional infra, OUTSIDE k8s — docker compose)"]
    app["GlobalCore · Java 8 container · SOAP :8080 + batch"]
    ora[("Oracle Free container · :1521 · data volume + backup")]
    app --> ora
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
  acl -->|"egress allow → controller:8080 (SOAP) only"| app
  cdc -->|"egress allow → controller:1521 (Oracle CDC) only"| ora
  cdc --> bus
  proj --> bus
  acl --> pas
```

**Delivery:** GitOps (ArgoCD) + **Kargo** dev→prod for the in-cluster services (ACL, projector), Helm
wrapper-chart golden path; Authentik OIDC; **ESO/Vault** for the Oracle CDC creds (`secret/platform/oracle-legacy`);
cert-manager TLS; **default-deny NetworkPolicy** with explicit egress: the **ACL → GlobalCore SOAP
(controller:8080)** and **Debezium → Oracle (controller:1521)** only — nothing else in-cluster reaches the
legacy. GlobalCore + Oracle are **not** GitOps-managed (traditional infra, docker-compose runbook — like MinIO).

## 5. Component responsibilities

| Component | Stack | Owns | Notes |
|---|---|---|---|
| **GlobalCore** | Java 8 / SOAP / batch / **Oracle + PL/SQL** | authoritative claim record + business rules (reserve calc, state) | **frozen** — wrapped not edited (ADR-001) |
| **ACL / API** | Java 21 + Spring Boot | SOAP→JSON, FNOL, lifecycle, coverage-check, authz, audit, AI tools | the only SOAP client of GlobalCore |
| **Debezium** | Oracle connector → NATS | capture legacy Oracle changes as events | ADR-003; not Kafka |
| **Projector** | CDC consumer | events → read-model | ADR-004 |
| **Read-model** | PostgreSQL | workbench projection | reads never hit the legacy |

## 6. Key data flows

1. **FNOL:** handler → ACL → **coverage check** (PAS) → **SOAP `CreateClaim`** to GlobalCore → claim lands
   **`pending`**; GlobalCore's PL/SQL + batch own the record; Debezium emits `CLAIM_CREATED` → projector updates
   the read-model. The ACL models the async "pending until batch activates" honestly (no fake real-time).
2. **Reserve / lifecycle:** ACL → SOAP ops → GlobalCore stored procs drive reserve + state; `RESERVE_ADJUSTED`
   / `CLAIM_STATUS_CHANGED` events flow via CDC.
3. **AI (v1, read-only):** handler question → ACL SQL-tool (approved columns, identity-scoped, PII-masked) →
   read-model; document questions → RAG (Qdrant). Never the LLM speaking SOAP or SQL to the legacy.

## 7. Cross-references

[Brief](../brief.md) · [PRD](../prd.md) · [NFR Register](./nfr-register.md) · [Threat Model](./threat-model.md) · [ADR log](./adr/000-index.md) · [Legacy-Core Modernization](https://andrelair-platform.github.io/minicloud-platform-docs/insurance-platform/legacy-core-modernization) · GlobalCore: `globalcore-legacy` · wrapper spec: `ktayl-integration/docs/legacy-wrapper-initiative-spec.md`
