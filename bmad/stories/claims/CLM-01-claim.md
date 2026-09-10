---
id: CLM-01
title: "EPIC: Claim lifecycle + FNOL intake"
status: Ready
type: Epic
epic: claims
milestone: "CLM — Claims v1"
estimate: 13
labels: [epic, insurance-lob, claims]
priority: P1
assignee: AndreLiar
repo: andrelair-platform/ktayl-claims
project: 11
---

## Epic

Claim lifecycle + FNOL intake.

## Why
Claims is the core insurance operation. A claim must move through a governed state machine (notification → assessment → reserve → settlement → closure) with an audit trail — the backbone every other claims capability hangs off.

## Scope (epic-level)
- [ ] FNOL intake (first notification of loss): channels, mandatory data, claim number allocation
- [ ] State machine: notified → under-assessment → reserved → settled/refused → closed (+ reopen)
- [ ] Reserve set/adjust with history; payments tracked; SLA timers per stage
- [ ] Full audit trail of every state change (who/what/when); document attachments
- [ ] Links to the policy (coverage check) and the claimant
