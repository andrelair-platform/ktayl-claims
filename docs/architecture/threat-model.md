# Threat Model — Claims / Legacy-Core (#11)

> **BMAD/SA artefact — STRIDE-lite.** Trust boundaries, attack surface, controls. Feeds the **security
> review gate**. **Status: DRAFT for review.** The new/high-attention boundaries here are the
> **legacy Oracle connection** and the **cross-boundary AI access** — this is a Path-C boundary change.

## Trust boundaries

```
[Claims handler] ─(1)─▶ [Claims workbench] ─(2)─▶ [ACL / API] ─(3)─▶ [read-model Postgres]
                                                      │
                                       (4) SOAP        │──▶ [GlobalCore (SOAP+Oracle) · OUTSIDE k8s, controller]
                                                      │
                                       (5) AI tools    │──▶ [Presidio] ─▶ [LiteLLM → vLLM]   (egress)
                                                      │
                        [Oracle] ─(6) CDC─▶ [Debezium] ─▶ [NATS] ─▶ downstream (internal)
```

**Boundaries:** (1) untrusted user session · (2) authenticated app · (3) read data-at-rest · (4) **the k8s→
legacy-Oracle hop (crossing out of the cluster to traditional infra)** · (5) **PII → AI egress** · (6) CDC
capture + event fan-out.

## Assets

Claimant/beneficiary **PII**; the **authoritative claim record + reserves** (integrity-critical — Solvency II);
the **legacy Oracle credentials**; the **claim state-machine / settlement authority** (an unauthorized
settlement is a real payout); the CDC stream.

## STRIDE-lite

| # | Threat (STRIDE) | Boundary | Risk | Control (design-time) |
|---|---|---|---|---|
| T1 | **Spoofing** — unauthenticated access | (2) | High | Authentik OIDC + MFA; no anonymous routes |
| T2 | **Tampering** — claim/reserve/audit altered | (4) | **Critical** | legacy `TRG_CLAIM_AUDIT` append-only + in-DB state-machine; ACL audit append-only; no destructive-update code path |
| T3 | **Repudiation** — "I didn't set that reserve" | (2)(4) | High | who/when/why on every change (AUD-1/2); signed commits for legacy DDL/PLSQL changes |
| T4 | **Info disclosure — PII to the LLM** | **(5)** | **Critical** | **Presidio masking before any LLM (SEC-4)**; egress allow-list to LiteLLM only; provider in ICT register; Langfuse audit |
| T5 | **Info disclosure — the legacy is over-exposed** | **(4)** | **Critical** | ACL uses a **least-privilege app schema/user** (not SYS/SYSTEM); **only the ACL + Debezium may reach `controller:1521`** (default-deny egress, SEC-3); TLS to the listener |
| T6 | **Info disclosure — cross-role claim data** | (2)(3) | High | RBAC (handler/senior/SIU/compliance); row-scoping by team |
| T7 | **Elevation — settle/reserve above authority** | (4) | **Critical** | **authority matrix enforced server-side in the ACL (SEC-5)**; senior referral; never client-trusted |
| T8 | **Elevation — AI bypasses claim authz** | (5) | High | AI runs **as the human's identity**; approved SQL-tools return only permitted columns/rows; AI cannot write in v1 (AI-1) |
| T9 | **Tampering — SQL injection into the legacy** | (4) | High | parameterised calls / bound PL/SQL only; **no dynamic SQL from user/LLM input** reaches Oracle |
| T10 | **DoS — read load crushes the legacy** | (4) | Medium | **reads go to the read-model, never Oracle (ADR-004)**; ACL→Oracle rate-limit + circuit breaker |
| T11 | **Tampering — a poisoned CDC/event** | (6) | Medium | events carry the source txn id; the read-model is idempotent + replayable; DLQ on the projector |
| T12 | **Supply chain** — compromised ACL image/dep | build | Medium | Cosign + SBOM + Trivy CRITICAL gate (SEC-6) |
| T13 | **Compliance — fraudulent/suspicious claim undetected** | (4) | High | (CLM-04, later) SIU routing + ALFA reporting; suspicious-claim trail captured now |

## Residual / accepted (v1)

- **No AI writes** — out of scope; the high-tier AI-Act controls only bind when the parked copilot (#19)
  can act. Accepted, documented, revisit-gated.
- **Oracle Free single instance, no RAC/Data Guard** — the legacy has no HA; accepted for a simulation
  (AVL-2 covers wrapper degradation; DR-1 covers backup).
- **Read-model eventual consistency** — the workbench can lag the legacy by the CDC window; accepted (the
  ACL reads the legacy directly for authoritative-critical checks like coverage).

## Verification (gate checks)

T2/T3 (immutability + audit query) · **T4/T8 (PII-mask + identity-scoped tool test — the security-gate
blocker)** · **T5 (egress-to-Oracle-only + least-priv user — the second blocker)** · T7 (authority enforced
before settle) · T9 (no dynamic SQL to Oracle) · T10 (reads never hit Oracle) · T12 (supply-chain CI).
Evidence → **Compliance #15** control library.
