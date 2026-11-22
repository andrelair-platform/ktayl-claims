package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.domain.PaymentRequestNotFoundException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** L1 — the payment request→approve→execute workflow: four-eyes at approve, SoD at execute, state guards. */
class PaymentApprovalServiceTest {

    private final PaymentRequestRepository repo = mock(PaymentRequestRepository.class);
    private final GlobalCorePort legacy = mock(GlobalCorePort.class);
    private final PaymentApprovalService svc = new PaymentApprovalService(repo, legacy, new BigDecimal("100000"));

    private final Actor adjuster = new Actor("adj.sophie", Authority.ADJUSTER);  // maxSettle 10_000
    private final Actor seniorA = new Actor("sen.marc", Authority.SENIOR);       // maxSettle 500_000
    private final Actor seniorB = new Actor("sen.lea", Authority.SENIOR);

    private static Claim claim(ClaimStatus status) {
        return new Claim("CLM-1", "POL-1", status, LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now());
    }

    private PaymentRequestEntity pending(Actor requester, String amount) {
        return new PaymentRequestEntity(UUID.randomUUID(), "CLM-1", new BigDecimal(amount), requester, Instant.now());
    }

    @Test
    void within_authority_pays_directly_no_request_row() {
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.SETTLED)));
        var out = svc.submit(adjuster, "CLM-1", new BigDecimal("8000")); // ≤ 10k adjuster settle cap
        assertThat(out.status()).isEqualTo("PAID");
        verify(legacy).settle("CLM-1", new BigDecimal("8000"));
        verify(repo, never()).save(any());
    }

    @Test
    void above_authority_opens_a_request_and_does_not_pay() {
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.RESERVED)));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var out = svc.submit(adjuster, "CLM-1", new BigDecimal("20000")); // above the 10k adjuster cap
        assertThat(out.status()).isEqualTo("PENDING_APPROVAL");
        verify(legacy, never()).settle(any(), any());
    }

    @Test
    void approve_does_not_pay_then_execute_by_a_third_person_pays() {
        var req = pending(adjuster, "20000");
        when(repo.findById(req.getId())).thenReturn(Optional.of(req));
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.SETTLED)));

        svc.approve(seniorA, req.getId());
        assertThat(req.getStatus()).isEqualTo("approved");
        verify(legacy, never()).settle(any(), any());   // approval alone does NOT disburse

        svc.execute(seniorB, req.getId());               // a different, authorised executor
        verify(legacy).settle("CLM-1", new BigDecimal("20000"));
        assertThat(req.getStatus()).isEqualTo("executed");
    }

    @Test
    void self_approval_is_forbidden() {
        var req = pending(seniorA, "20000");
        when(repo.findById(req.getId())).thenReturn(Optional.of(req));
        assertThatThrownBy(() -> svc.approve(seniorA, req.getId())).isInstanceOf(AuthorityException.class);
    }

    @Test
    void the_requester_may_not_execute_their_own_payment() {
        var req = pending(seniorA, "20000");
        req.approve(seniorB, Instant.now());
        when(repo.findById(req.getId())).thenReturn(Optional.of(req));
        assertThatThrownBy(() -> svc.execute(seniorA, req.getId()))
                .isInstanceOf(AuthorityException.class).hasMessageContaining("requester");
        verify(legacy, never()).settle(any(), any());
    }

    @Test
    void executing_a_not_yet_approved_request_is_a_conflict() {
        var req = pending(adjuster, "20000");  // still 'requested'
        when(repo.findById(req.getId())).thenReturn(Optional.of(req));
        assertThatThrownBy(() -> svc.execute(seniorA, req.getId())).isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void approving_an_unknown_request_is_not_found() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.approve(seniorA, id)).isInstanceOf(PaymentRequestNotFoundException.class);
    }
}
