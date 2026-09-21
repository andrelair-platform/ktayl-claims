package com.andrelair.ktayl.claims.legacy;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The Anti-Corruption Layer boundary to the frozen GlobalCore legacy (ADR-001 / ADR-003).
 *
 * <p>The real adapter speaks <strong>SOAP</strong> over the legacy's Oracle/PL-SQL (arrives with the
 * legacy, S001/S002); a local {@link StubGlobalCoreAdapter} stands in until then so the slice runs
 * offline. Only <strong>parameterised / bound</strong> calls ever cross this boundary — never dynamic
 * SQL built from user or LLM input (threat T9).
 */
public interface GlobalCorePort {

    /** Read a policy for the coverage check. {@code Optional.empty()} ⇒ unknown policy. */
    Optional<PolicyView> findPolicy(String policyNumber);

    /**
     * Register a claim in the legacy — maps to PL/SQL {@code PKG_CLAIMS.PROC_CREATE_CLAIM}, which
     * allocates the claim number and the initial state. Returns the allocated claim number.
     */
    String createClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName);
}
