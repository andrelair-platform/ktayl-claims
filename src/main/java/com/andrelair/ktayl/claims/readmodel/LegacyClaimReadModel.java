package com.andrelair.ktayl.claims.readmodel;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default query side (profiles WITHOUT {@code readmodel}) — no read-model DB, so reads delegate
 * straight to the legacy via the ACL write port. Keeps the service answering GET reads when the
 * CQRS projection isn't deployed (stub mode, unit/dev-offline, tests).
 */
@Component
@Profile("!readmodel")
public class LegacyClaimReadModel implements ClaimReadModel {

    private final GlobalCorePort legacy;

    public LegacyClaimReadModel(GlobalCorePort legacy) {
        this.legacy = legacy;
    }

    @Override
    public Optional<Claim> findClaim(String claimNumber) {
        return legacy.findClaim(claimNumber);
    }
}
