# AGENTS.md — ktayl-claims

Tiny verified context for agents. **Not** a repo overview (the code + [docs](./docs/) are the context).
Policy, command-catches, non-default conventions, observed pitfalls only.

## Policy
- **ktayl-solution insurance IS** (business context), **not** the RNCP cert (that is **Retrieva**). Never label this repo's work as cert evidence.
- **Planning-first / review gate:** the BMAD artefacts in `docs/` ([brief](./docs/brief.md), [prd](./docs/prd.md), [architecture/](./docs/architecture/)) are reviewed **before** implementation. **No Oracle is pulled and no `ktayl-legacy-core` repo is created until the plan + SA set are validated.**
- **This product is a STRANGLER over a legacy Oracle core** (ADR-001). The legacy (`ktayl-legacy-core`) is the **authoritative record and is FROZEN** — you wrap/intercept/strangle it, you do **not** refactor its internals. Nothing (app/portal/AI) touches Oracle except the ACL + the Debezium connector.
- **Thin slice first** (Property, one claim type): FNOL → reserve → settle → closed through the ACL. Defer adjusters/subrogation/fraud/litigation (CLM-02..05). An Oracle→Postgres migration is a **later optional footnote**, never the goal (ADR-007).
- **AI is read-only + governed** (ADR-005): approved SQL-tools behind the ACL (never the LLM emitting SQL at Oracle), RAG for docs, identity propagated, **PII Presidio-masked before any LLM**. No AI writes (parked #19).

## Conventions (non-default)
- **Stack (ADR-006, OPEN at review):** legacy = **Oracle Free + PL/SQL** (container, **outside k8s**); ACL = **Java 21 + Spring Boot** (candidate — Oracle realism) *or* Python/FastAPI (owner decision); CDC = **Debezium → NATS** (**not Kafka**); read-model = **PostgreSQL**; frontend = Next.js (later).
- **Oracle runs OUTSIDE Kubernetes** — a Docker container on the controller/a node (like MinIO), reached at `controller-ip:1521`. Not GitOps-managed (runbook-provisioned).
- **Reads go to the Postgres read-model, never Oracle** (CQRS-lite, ADR-004); the read-model is disposable/rebuildable from CDC.
- Legacy creds via **ESO→Vault** (`secret/platform/oracle-legacy`), a **least-privilege app user** (not SYS/SYSTEM). SSO = Authentik OIDC. **Default-deny egress; only the ACL + Debezium may reach `controller-ip:1521`.**
- Container build file is named **`Dockerfile`** (never `Containerfile`). In-cluster services delivered via the Helm wrapper-chart golden path + Kargo dev→prod.

## Pitfalls
- **No dynamic SQL from user/LLM input reaches Oracle** — parameterised/bound PL/SQL only (injection T9).
- **Enforce settlement/reserve authority server-side** in the ACL — a settle above authority is a real payout (T7).
- **Legacy state-machine + audit are authoritative in-DB** (PL/SQL trigger) — the ACL must not be the only guard.
- Debezium on Oracle needs **LogMiner/XStream** privileges on the legacy — a gated setup (security review).
