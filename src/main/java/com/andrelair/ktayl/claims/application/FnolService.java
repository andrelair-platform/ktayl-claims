package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.OutOfCoverException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import com.andrelair.ktayl.claims.legacy.PolicyView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * First Notification of Loss (S003): <em>idempotent</em> register → <em>coverage check</em> against the
 * legacy policy → <em>create the claim</em> in GlobalCore. The coverage decision lives in the ACL; the
 * policy data is read from the legacy via {@link GlobalCorePort}.
 *
 * <p>Note: the check-then-act idempotency here is single-JVM only — the concurrency-safe version
 * (RES-3) and the SOAP retry/circuit-breaker (RES-1/2) arrive with S008.
 */
@Service
public class FnolService {

    private final GlobalCorePort legacy;
    private final IdempotencyStore idempotency;

    public FnolService(GlobalCorePort legacy, IdempotencyStore idempotency) {
        this.legacy = legacy;
        this.idempotency = idempotency;
    }

    public Claim register(FnolCommand cmd) {
        Optional<Claim> replay = idempotency.find(cmd.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();   // idempotent replay ⇒ the same claim, no second create
        }

        PolicyView policy = legacy.findPolicy(cmd.policyNumber())
                .orElseThrow(() -> new OutOfCoverException("unknown policy: " + cmd.policyNumber()));
        if (cmd.lossDate().isBefore(policy.coverStart()) || cmd.lossDate().isAfter(policy.coverEnd())) {
            throw new OutOfCoverException("loss date " + cmd.lossDate() + " outside cover period "
                    + policy.coverStart() + "…" + policy.coverEnd());
        }
        if (!policy.coveredPerils().contains(cmd.peril())) {
            throw new OutOfCoverException("peril not covered by " + policy.policyNumber() + ": " + cmd.peril());
        }

        String claimNumber = legacy.createClaim(
                cmd.policyNumber(), cmd.lossDate(), cmd.peril(), cmd.claimantName());
        Claim claim = new Claim(claimNumber, cmd.policyNumber(), ClaimStatus.NOTIFIED,
                cmd.lossDate(), cmd.peril(), cmd.claimantName(), Instant.now());
        idempotency.save(cmd.idempotencyKey(), claim);
        return claim;
    }
}
