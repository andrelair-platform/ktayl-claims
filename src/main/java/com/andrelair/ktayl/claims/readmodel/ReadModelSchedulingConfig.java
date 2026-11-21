package com.andrelair.ktayl.claims.readmodel;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled only on the {@code readmodel} profile — the profile that owns the Postgres read-model,
 * the DB datasource and the NATS connection — so {@link ClaimEventPublisher}'s outbox drain runs there and
 * nowhere else (CLM-FIN-02, ADR-021).
 */
@Configuration
@Profile("readmodel")
@EnableScheduling
class ReadModelSchedulingConfig {
}
