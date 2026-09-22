package com.andrelair.ktayl.claims.application;

import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.legacy.StubGlobalCoreAdapter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L1 — the S004 lifecycle: reserve → settle, authority matrix (T7), and the legacy state machine. */
class ClaimLifecycleServiceTest {

    private final StubGlobalCoreAdapter legacy = new StubGlobalCoreAdapter();
    private final ClaimLifecycleService service = new ClaimLifecycleService(legacy);

    private final Actor adjuster = new Actor("adj", Authority.ADJUSTER);
    private final Actor senior = new Actor("snr", Authority.SENIOR);

    private String newClaim() {
        return legacy.createClaim("POL-PROP-0001", LocalDate.of(2026, 6, 1), "FIRE", "Durand SARL");
    }

    @Test
    void adjusterReservesThenSettlesWithinAuthority() {
        String clm = newClaim();
        Claim reserved = service.reserve(adjuster, clm, new BigDecimal("40000"));
        assertThat(reserved.status()).isEqualTo(ClaimStatus.RESERVED);

        Claim settled = service.settle(adjuster, clm, new BigDecimal("8000"));
        assertThat(settled.status()).isEqualTo(ClaimStatus.SETTLED);
    }

    @Test
    void reserveAboveAdjusterAuthorityIsRefused() {
        String clm = newClaim();
        assertThatThrownBy(() -> service.reserve(adjuster, clm, new BigDecimal("60000")))
                .isInstanceOf(AuthorityException.class);
    }

    @Test
    void settleAboveAuthorityIsRefusedAndDoesNotTouchTheLegacy() {
        String clm = newClaim();
        service.reserve(adjuster, clm, new BigDecimal("40000"));

        assertThatThrownBy(() -> service.settle(adjuster, clm, new BigDecimal("20000")))
                .isInstanceOf(AuthorityException.class);

        // authority is checked BEFORE the legacy call: the claim is still RESERVED, not SETTLED
        assertThat(legacy.findClaim(clm).orElseThrow().status()).isEqualTo(ClaimStatus.RESERVED);
    }

    @Test
    void seniorCanSettleABiggerAmount() {
        String clm = newClaim();
        service.reserve(senior, clm, new BigDecimal("300000"));
        Claim settled = service.settle(senior, clm, new BigDecimal("150000"));
        assertThat(settled.status()).isEqualTo(ClaimStatus.SETTLED);
    }

    @Test
    void settleBeforeReserveIsAnIllegalTransition() {
        String clm = newClaim();
        assertThatThrownBy(() -> service.settle(senior, clm, new BigDecimal("1000")))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void reserveOnUnknownClaimIsNotFound() {
        assertThatThrownBy(() -> service.reserve(senior, "CLM-2026-999999", new BigDecimal("1000")))
                .isInstanceOf(ClaimNotFoundException.class);
    }
}
