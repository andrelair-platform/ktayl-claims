package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L1 — the payment four-eyes + SoD rules (CLM-FIN-03b), keyed off the settlement authority. */
class PaymentApprovalPolicyTest {

    private static final BigDecimal LARGE_LOSS = new BigDecimal("100000");
    private final Actor adjuster = new Actor("adj.sophie", Authority.ADJUSTER); // maxSettle 10_000
    private final Actor seniorA = new Actor("sen.marc", Authority.SENIOR);      // maxSettle 500_000
    private final Actor seniorB = new Actor("sen.lea", Authority.SENIOR);

    @Test
    void within_settle_authority_and_not_large_loss_needs_no_approval() {
        assertThat(PaymentApprovalPolicy.requiresApproval(adjuster, new BigDecimal("8000"), LARGE_LOSS)).isFalse();
    }

    @Test
    void above_settle_authority_needs_approval() {
        assertThat(PaymentApprovalPolicy.requiresApproval(adjuster, new BigDecimal("20000"), LARGE_LOSS)).isTrue();
    }

    @Test
    void a_large_loss_needs_approval_even_for_a_senior() {
        assertThat(PaymentApprovalPolicy.requiresApproval(seniorA, new BigDecimal("150000"), LARGE_LOSS)).isTrue();
    }

    @Test
    void self_approval_and_self_execution_are_rejected() {
        assertThatThrownBy(() -> PaymentApprovalPolicy.checkCanApprove(seniorA, "sen.marc", new BigDecimal("20000")))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("self-approval");
        assertThatThrownBy(() -> PaymentApprovalPolicy.checkCanExecute(seniorA, "sen.marc", new BigDecimal("20000")))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("requester");
    }

    @Test
    void a_different_senior_may_approve_and_a_third_may_execute() {
        assertThatCode(() -> PaymentApprovalPolicy.checkCanApprove(seniorA, "adj.sophie", new BigDecimal("20000")))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentApprovalPolicy.checkCanExecute(seniorB, "adj.sophie", new BigDecimal("20000")))
                .doesNotThrowAnyException();
    }

    @Test
    void an_approver_without_settlement_authority_for_the_amount_is_rejected() {
        assertThatThrownBy(() -> PaymentApprovalPolicy.checkCanApprove(adjuster, "sen.marc", new BigDecimal("20000")))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("exceeds");
    }
}
