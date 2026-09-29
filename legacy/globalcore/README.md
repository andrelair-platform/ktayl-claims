# GlobalCore — the simulated legacy core (Slice A)

The **legacy system** the Claims ACL wraps (Strangler Fig, ADR-001). It runs **outside k3s** (ADR-002) as a
plain container stack on the controller (`10.0.0.1`), reachable on the LAN; in-cluster consumers reach it by
IP:port, gated by a default-deny **egress** NetworkPolicy:

- the **ACL** → GlobalCore **SOAP** `10.0.0.1:8080` (writes: create/reserve/settle; read: findPolicy) — Slice B
- **Debezium** → GlobalCore **MySQL** `10.0.0.1:3306` (binlog CDC → NATS) — Slice C

> **Simulated legacy, stated honestly.** Per the 2026-09-29 amendment (ADR-002/006), the legacy DB is
> **MySQL 8** (binlog + GTID — Debezium's canonical CDC source), a stand-in for an Oracle-era core: Oracle XE
> is too heavy for the box and the *pattern* (Strangler + ACL + CDC) is the point, not Oracle dialect fidelity.
> **MySQL over Postgres** deliberately — the modern read-model is Postgres, so the legacy being MySQL makes it a
> genuine **cross-engine** pipeline (MySQL → Debezium → NATS → Postgres), a truer strangler than Postgres→Postgres.

## Layout
```
db/              MySQL init (runs once on first container init):
  01-schema.sql    gc_policy · gc_policy_peril · gc_claim (the legacy state machine + eurocents money)
  02-seed.sql      the Property policy book (POL-PROP-0001/0002 — mirrors StubGlobalCoreAdapter)
  03-cdc-user.sql  the `debezium` replication user (Slice C)
service/         GlobalCore SOAP service (Java 21 + Spring Boot + Spring-WS, JDBC/JPA over MySQL)  ← Slice A, in progress
docker-compose.yml   MySQL 8 (binlog ROW + GTID) + the SOAP service
```

## Run (on the controller)
The controller has plain `docker` (no compose plugin). Either install the compose plugin, or run MySQL directly:

```bash
# MySQL only (the CDC source) — verified working 2026-09-29 (schema+seed+binlog+GTID+cdc-user):
docker run -d --name globalcore-mysql --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD=globalcore-root -e MYSQL_DATABASE=globalcore \
  -e MYSQL_USER=globalcore -e MYSQL_PASSWORD=globalcore -p 3306:3306 \
  -v ~/globalcore-legacy/db:/docker-entrypoint-initdb.d:ro \
  -v globalcore-mysql-data:/var/lib/mysql \
  mysql:8.4 --server-id=1 --log-bin=mysql-bin --binlog-format=ROW --binlog-row-image=FULL \
            --gtid-mode=ON --enforce-gtid-consistency=ON

# SOAP service — the image is CI-BUILT (never hand-built on the controller). The GlobalCore workflow
# pushes harbor.10.0.0.200.nip.io/library/globalcore-soap:<sha>; the controller pulls + runs it on the
# same docker network as MySQL:
docker network create globalcore 2>/dev/null || true
docker network connect globalcore globalcore-mysql
docker run -d --name globalcore-soap --restart unless-stopped --network globalcore -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://globalcore-mysql:3306/globalcore \
  -e SPRING_DATASOURCE_USERNAME=globalcore -e SPRING_DATASOURCE_PASSWORD=globalcore \
  harbor.10.0.0.200.nip.io/library/globalcore-soap:<CI-SHA>
# WSDL: http://<controller-LAN-ip>:8080/ws/claims.wsdl
```

## Status
- **DB (CDC source): built + verified live** on the controller (throwaway run — schema/seed/binlog/GTID/cdc-user all OK).
- **SOAP service: built** — Java 21 + Spring-WS (contract-first XSD → JAXB), JPA over MySQL, exposing
  `findPolicy · createClaim · findClaim · reserve · settle` (the operations `GlobalCorePort` declares), with the
  lifecycle state machine enforced legacy-side (ADR-003). **Image is CI-built** (`globalcore.yml` workflow →
  Harbor/ghcr, scan-before-push, signed) — the controller only pulls + runs it (off-cluster, ADR-002).
- **Next:** run the CI image on the controller + smoke a SOAP call; then Slice B swaps the ACL's
  `StubGlobalCoreAdapter` → a `SoapGlobalCoreAdapter`.
