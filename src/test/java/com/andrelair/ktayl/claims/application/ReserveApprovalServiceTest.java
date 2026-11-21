package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.ApprovalNotFoundException;
import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.legacy.GlobalCorePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** L1 — the reserve approval workflow: submit (direct vs pending), approve (four-eyes, applies to legacy). */
class ReserveApprovalServiceTest {

    private final ReserveApprovalRepository repo = mock(ReserveApprovalRepository.class);
    private final GlobalCorePort legacy = mock(GlobalCorePort.class);
    private final ReserveApprovalService svc = new ReserveApprovalService(repo, legacy, new BigDecimal("100000"));

    private final Actor adjuster = new Actor("adj.sophie", Authority.ADJUSTER);
    private final Actor senior = new Actor("sen.marc", Authority.SENIOR);

    private static Claim claim(ClaimStatus status) {
        return new Claim("CLM-1", "POL-1", status, LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now());
    }

    @Test
    void a_within_authority_reserve_is_applied_directly_no_approval_row() {
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.RESERVED)));
        var out = svc.submit(adjuster, "CLM-1", new BigDecimal("40000"));
        assertThat(out.status()).isEqualTo("APPLIED");
        verify(legacy).reserve("CLM-1", new BigDecimal("40000"));
        verify(repo, never()).save(any());
    }

    @Test
    void an_above_authority_reserve_is_held_pending_and_NOT_applied() {
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.NOTIFIED)));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var out = svc.submit(adjuster, "CLM-1", new BigDecimal("60000")); // above the 50k adjuster cap
        assertThat(out.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(out.approvalId()).isNotNull();
        verify(legacy, never()).reserve(any(), any());      // not applied until approved
        ArgumentCaptor<ReserveApprovalEntity> cap = ArgumentCaptor.forClass(ReserveApprovalEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getRequestedBy()).isEqualTo("adj.sophie");
    }

    @Test
    void a_different_senior_approves_and_the_reserve_is_driven_into_the_legacy() {
        UUID id = UUID.randomUUID();
        var pending = new ReserveApprovalEntity(id, "CLM-1", new BigDecimal("60000"), adjuster, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(pending));
        when(legacy.findClaim("CLM-1")).thenReturn(Optional.of(claim(ClaimStatus.RESERVED)));

        svc.approve(senior, id);

        verify(legacy).reserve("CLM-1", new BigDecimal("60000"));
        verify(repo).save(eq(pending));
        assertThat(pending.getStatus()).isEqualTo("approved");
        assertThat(pending.getDecidedBy()).isEqualTo("sen.marc");
    }

    @Test
    void self_approval_is_forbidden_and_does_not_touch_the_legacy() {
        UUID id = UUID.randomUUID();
        var pending = new ReserveApprovalEntity(id, "CLM-1", new BigDecimal("60000"), senior, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> svc.approve(senior, id)).isInstanceOf(AuthorityException.class);
        verify(legacy, never()).reserve(any(), any());
    }

    @Test
    void approving_an_already_decided_request_is_a_conflict() {
        UUID id = UUID.randomUUID();
        var decided = new ReserveApprovalEntity(id, "CLM-1", new BigDecimal("60000"), adjuster, Instant.now());
        decided.approve(senior, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(decided));
        assertThatThrownBy(() -> svc.approve(new Actor("sen.other", Authority.SENIOR), id))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void approving_an_unknown_request_is_not_found() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.approve(senior, id)).isInstanceOf(ApprovalNotFoundException.class);
    }
}
