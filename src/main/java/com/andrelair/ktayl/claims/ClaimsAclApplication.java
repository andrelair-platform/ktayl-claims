package com.andrelair.ktayl.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ktayl-claims — the modern Claims capability built <em>as the Anti-Corruption Layer / strangler</em>
 * over the frozen GlobalCore legacy (Java 8 · SOAP · Oracle). See docs/architecture (ADR-001, ADR-006).
 *
 * <p>Layering as the slice is built (S003+): {@code web} (REST) → {@code application} (use cases) →
 * {@code domain} (claim model) → {@code legacy} (SOAP client to GlobalCore) · {@code readmodel}
 * (Postgres CQRS projection) · {@code integration} (Debezium→NATS CDC consumer).
 */
@SpringBootApplication
public class ClaimsAclApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsAclApplication.class, args);
    }
}
