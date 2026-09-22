package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.ClaimLifecycleService;
import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L1 — the lifecycle endpoints map authority + transition outcomes to the right HTTP status. */
@WebMvcTest(ClaimLifecycleController.class)
class ClaimLifecycleControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ClaimLifecycleService lifecycle;

    private static final String AMOUNT = "{\"amount\":8000}";

    private Claim reservedClaim() {
        return new Claim("CLM-2026-000001", "POL-PROP-0001", ClaimStatus.RESERVED,
                LocalDate.of(2026, 6, 1), "FIRE", "Durand SARL", Instant.now());
    }

    @Test
    void reserveReturns200WithUpdatedClaim() throws Exception {
        when(lifecycle.reserve(any(), any(), any())).thenReturn(reservedClaim());

        mvc.perform(post("/api/claims/CLM-2026-000001/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "ADJUSTER")
                        .content(AMOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void settleAboveAuthorityReturns403() throws Exception {
        when(lifecycle.settle(any(), any(), any()))
                .thenThrow(new AuthorityException("settlement 20000 exceeds ADJUSTER authority (10000)"));

        mvc.perform(post("/api/claims/CLM-2026-000001/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "ADJUSTER")
                        .content("{\"amount\":20000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void settleBeforeReserveReturns409() throws Exception {
        when(lifecycle.settle(any(), any(), any()))
                .thenThrow(new IllegalTransitionException("cannot settle a claim in state NOTIFIED"));

        mvc.perform(post("/api/claims/CLM-2026-000001/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "SENIOR")
                        .content(AMOUNT))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownClaimReturns404() throws Exception {
        when(lifecycle.reserve(any(), any(), any()))
                .thenThrow(new ClaimNotFoundException("CLM-2026-999999"));

        mvc.perform(post("/api/claims/CLM-2026-999999/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "SENIOR")
                        .content(AMOUNT))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAuthorityHeaderReturns400() throws Exception {
        mvc.perform(post("/api/claims/CLM-2026-000001/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AMOUNT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAuthorityValueReturns400() throws Exception {
        mvc.perform(post("/api/claims/CLM-2026-000001/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "BOSS")
                        .content(AMOUNT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveAmountReturns400() throws Exception {
        mvc.perform(post("/api/claims/CLM-2026-000001/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Claims-Authority", "ADJUSTER")
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest());
    }
}
