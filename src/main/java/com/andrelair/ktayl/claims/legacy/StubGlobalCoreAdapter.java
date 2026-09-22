package com.andrelair.ktayl.claims.legacy;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LOCAL / DEV stand-in for GlobalCore until the real SOAP + Oracle legacy is provisioned (S001/S002).
 * Seeded with a small <strong>Property</strong> policy book so the FNOL + coverage flow runs end-to-end
 * offline. Replaced by the SOAP adapter (spring-ws) once the legacy is up — activate that with the
 * {@code soap} profile; this stub is the default (any profile except {@code soap}).
 */
@Component
@Profile("!soap")
public class StubGlobalCoreAdapter implements GlobalCorePort {

    private final Map<String, PolicyView> policies = new HashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(0);

    public StubGlobalCoreAdapter() {
        seed("POL-PROP-0001", "Durand SARL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Set.of("FIRE", "WATER_DAMAGE", "STORM", "THEFT"));
        seed("POL-PROP-0002", "Boulangerie Lemoine",
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
                Set.of("FIRE", "WATER_DAMAGE"));
    }

    private void seed(String number, String holder, LocalDate start, LocalDate end, Set<String> perils) {
        policies.put(number, new PolicyView(number, holder, start, end, perils));
    }

    @Override
    public Optional<PolicyView> findPolicy(String policyNumber) {
        return Optional.ofNullable(policies.get(policyNumber));
    }

    @Override
    public String createClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName) {
        return "CLM-%d-%06d".formatted(LocalDate.now().getYear(), sequence.incrementAndGet());
    }
}
