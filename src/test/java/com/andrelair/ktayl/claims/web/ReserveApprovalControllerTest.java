package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.ClaimLifecycleService;
import com.andrelair.ktayl.claims.application.ReserveApprovalService;
import com.andrelair.ktayl.claims.domain.AuthorityException;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test of the reserve four-eyes endpoints (CLM-FIN-03a). With {@link ReserveApprovalService}
 * present (as on the readmodel profile), the controller routes /reserve through it: 202 when pending, and
 * the approve/reject endpoints enforce four-eyes (403 self-approval) + state (409 already decided).
 */
@WebMvcTest(ClaimLifecycleController.class)
class ReserveApprovalControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ClaimLifecycleService lifecycle;
    @MockBean ReserveApprovalService approvals;

    private static Claim reserved() {
        return new Claim("CLM-1", "POL-1", ClaimStatus.RESERVED, LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now());
    }

    @Test
    void anAboveAuthorityReserveReturns202Pending() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvals.submit(any(), eq("CLM-1"), any())).thenReturn(ReserveApprovalService.Outcome.pending(id));
        mvc.perform(post("/api/claims/CLM-1/reserve")
                        .header("X-Claims-Authority", "ADJUSTER").header("X-Claims-User", "adj.sophie")
                        .contentType("application/json").content("{\"amount\":60000}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalId").value(id.toString()));
    }

    @Test
    void aWithinAuthorityReserveReturns200Applied() throws Exception {
        when(approvals.submit(any(), eq("CLM-1"), any())).thenReturn(ReserveApprovalService.Outcome.applied(reserved()));
        mvc.perform(post("/api/claims/CLM-1/reserve")
                        .header("X-Claims-Authority", "ADJUSTER").header("X-Claims-User", "adj.sophie")
                        .contentType("application/json").content("{\"amount\":40000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void approveReturns200AndTheAppliedClaim() throws Exception {
        UUID id = UUID.randomUUID();
        when(approvals.approve(any(), eq(id))).thenReturn(reserved());
        mvc.perform(post("/api/claims/reserve-approvals/" + id + "/approve")
                        .header("X-Claims-Authority", "SENIOR").header("X-Claims-User", "sen.marc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void selfApprovalIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AuthorityException("self-approval is not allowed")).when(approvals).approve(any(), eq(id));
        mvc.perform(post("/api/claims/reserve-approvals/" + id + "/approve")
                        .header("X-Claims-Authority", "SENIOR").header("X-Claims-User", "sen.marc"))
                .andExpect(status().isForbidden());
    }
}
