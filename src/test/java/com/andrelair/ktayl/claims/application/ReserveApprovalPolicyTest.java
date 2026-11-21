package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** L1 — the reserve four-eyes rules (CLM-FIN-03a): when approval is required, and who may approve. */
class ReserveApprovalPolicyTest {

    private static final BigDecimal LARGE_LOSS = new BigDecimal("100000");
    private final Actor adjuster = new Actor("adj.sophie", Authority.ADJUSTER); // maxReserve 50_000
    private final Actor senior = new Actor("sen.marc", Authority.SENIOR);       // maxReserve 1_000_000

    @Test
    void within_authority_and_not_large_loss_needs_no_approval() {
        assertThat(ReserveApprovalPolicy.requiresApproval(adjuster, new BigDecimal("40000"), LARGE_LOSS)).isFalse();
    }

    @Test
    void above_the_requesters_authority_needs_approval() {
        assertThat(ReserveApprovalPolicy.requiresApproval(adjuster, new BigDecimal("60000"), LARGE_LOSS)).isTrue();
    }

    @Test
    void a_large_loss_needs_approval_even_for_a_senior_within_authority() {
        // 200k is within a SENIOR's 1M authority but above the 100k large-loss threshold → still four-eyes
        assertThat(ReserveApprovalPolicy.requiresApproval(senior, new BigDecimal("200000"), LARGE_LOSS)).isTrue();
    }

    @Test
    void a_senior_may_approve_an_above_adjuster_amount_if_a_different_person() {
        assertThatCode(() -> ReserveApprovalPolicy.checkCanApprove(senior, "adj.sophie", new BigDecimal("60000")))
                .doesNotThrowAnyException();
    }

    @Test
    void self_approval_is_rejected() {
        assertThatThrownBy(() -> ReserveApprovalPolicy.checkCanApprove(senior, "sen.marc", new BigDecimal("60000")))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("self-approval");
    }

    @Test
    void an_approver_whose_authority_does_not_cover_the_amount_is_rejected() {
        assertThatThrownBy(() -> ReserveApprovalPolicy.checkCanApprove(adjuster, "sen.marc", new BigDecimal("60000")))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("exceeds");
    }
}
