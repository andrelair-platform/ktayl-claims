# ktayl-claims

> Insurance **claims management** for the ktayl-solution IS — FNOL/claim lifecycle, loss adjusters, subrogation, fraud/SIU, litigation (a GERAS-style claims platform).

**Product board:** https://github.com/orgs/andrelair-platform/projects/11
**Initiative:** Insurance LOB · **Layer:** ktayl-solution IS (business context, not the certification)
**Platform docs:** https://andrelair-platform.github.io/minicloud-platform-docs/

BMAD stories live in `bmad/stories/` and sync to Issues on **Project #11** via the org-shared
reusable workflow. This repo is the product home; it may grow app code as the domain is built.

## Epic backlog

| ID | Epic | Priority |
|---|---|---|
| CLM-01 | Claim lifecycle + FNOL intake | P1 |
| CLM-02 | Loss adjuster / expert panel management | P2 |
| CLM-03 | Subrogation / third-party recovery | P2 |
| CLM-04 | Fraud / SIU investigation + ALFA reporting | P2 |
| CLM-05 | Litigation / contentieux management | P2 |

## Development — the ACL service (Java 21 + Spring Boot)

The modern Claims capability is built **as the ACL / strangler** over the frozen GlobalCore legacy
(SOAP · Oracle) — see [ADR-001 / ADR-006](docs/architecture/adr/000-index.md).

```bash
mvn test                             # L1 unit tests
mvn spring-boot:run                  # run locally → http://localhost:8080
curl localhost:8080/api/claims/info
curl localhost:8080/actuator/health
docker build -t ktayl-claims-acl .   # multi-stage image (non-root)
```

Stack (ADR-006): **Java 21 · Spring Boot 3** · SOAP client to GlobalCore · Oracle (read-critical checks) ·
**PostgreSQL** CQRS read-model · **Debezium → NATS** CDC. The v0 scaffold boots with **no external
datastore**; SOAP / Oracle / CDC / read-model are wired in per story (S003–S006), each shipping with its
resilience behaviour (S008).

## License
MIT — see [LICENSE](LICENSE).
