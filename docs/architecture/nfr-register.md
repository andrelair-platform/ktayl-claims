# NFR Register — Claims / Legacy-Core (#11)

> **BMAD/SA artefact.** The PRD's NFRs made **measurable** — target + verification. Reviewed at the
> architecture gate. **Status: DRAFT for review.**

## Performance

| ID | NFR | Target | Verify |
|---|---|---|---|
| PERF-1 | Claim read (via read-model) | p95 < 1.5s | RED metrics / k6 |
| PERF-2 | FNOL create (ACL → legacy PL/SQL) | p95 < 2s | trace ACL→Oracle span |
| PERF-3 | CDC lag (legacy change → event on NATS) | < 10s | Debezium lag metric |
| PERF-4 | Read-model projection catch-up after restart | < 2 min for the demo book | replay test |

## Availability & resilience (the boundary matters)

| ID | NFR | Target | Verify |
|---|---|---|---|
| AVL-1 | Claims workbench (business hours) | 99.5% | uptime SLO |
| AVL-2 | **Graceful degradation if the LEGACY is down** | reads still served from the read-model; writes fail *clearly* (queued/rejected, never silently lost) | chaos drill: stop Oracle container |
| AVL-3 | **Graceful degradation if CDC is down** | ACL writes still succeed to the legacy; read-model goes stale + alerts; catches up on reconnect | stop Debezium; confirm no data loss |
| AVL-4 | Cross-boundary call resilience | ACL→Oracle timeouts + circuit breaker; idempotent FNOL | fault injection |

## Durability / DR

| ID | NFR | Target |
|---|---|---|
| DR-1 | **Legacy is the source of truth** | Oracle Free data volume backed up (container-level snapshot to MinIO) |
| DR-2 | Read-model is disposable | fully **rebuildable from CDC** (replay) — it holds no source-of-truth |
| DR-3 | RPO / RTO | RPO ≤ 24h (legacy backup); read-model RTO = replay time |
| DR-4 | Audit immutability | legacy `TRG_CLAIM_AUDIT` append-only + ACL audit append-only |

## Security ([Threat Model](./threat-model.md))

| ID | NFR | Target |
|---|---|---|
| SEC-1 | AuthN/Z | Authentik OIDC + MFA; RBAC (handler/senior/SIU/compliance) |
| SEC-2 | **Legacy creds** | Oracle user via **ESO → Vault** (`secret/platform/oracle-legacy`); a **least-privilege app schema/user**, not SYS/SYSTEM; none in Git/images |
| SEC-3 | Network | **default-deny egress; the ACL + Debezium may reach `controller-ip:1521` ONLY** (nothing else in-cluster can talk to Oracle) |
| SEC-4 | PII | claimant/beneficiary PII **Presidio-masked before any LLM**; minimisation + retention |
| SEC-5 | AuthZ on write | reserve/settlement authority enforced **server-side** in the ACL (never client trust) |
| SEC-6 | Supply chain | Cosign-signed + SBOM on the ACL/projector images; Trivy CRITICAL gate |

## Observability

| ID | NFR | Target |
|---|---|---|
| OBS-1 | RED + traces **across the boundary** | OTel spans ACL→Oracle + ACL→read-model (Tempo) |
| OBS-2 | CDC health | Debezium connector status + lag as Prometheus metrics + alert |
| OBS-3 | AI observability | Langfuse on every AI tool/RAG call (cost, latency, model) |
| OBS-4 | Claims KPIs | claim cycle time, reserve accuracy, settlement ratio, SLA breaches — from the read-model |

## Explainability & auditability (regulated)

| ID | NFR | Target | Verify |
|---|---|---|---|
| AUD-1 | Every state change | who/when/why, immutable (legacy trigger + ACL audit) | audit query |
| AUD-2 | Reserve history | every reserve set/adjust versioned with author + reason | history query |
| AI-1 | AI is read-only + attributable | no AI write in v1; every AI answer cites source + logs the tool call | flow review |

## Cost

| ID | NFR | Target |
|---|---|---|
| COST-1 | No new cloud spend | Oracle Free = 0 licence; reuse platform substrate |
| COST-2 | Oracle footprint | one container ~2 GB RAM + a few GB disk — **disk-gated** (controller 98 G, MinIO ~33 G); size + place per the architecture |
