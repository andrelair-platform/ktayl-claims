package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.ClaimLifecycleService;
import com.andrelair.ktayl.claims.application.PaymentApprovalService;
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

/** Web-layer test of the payment request→approve→execute endpoints (CLM-FIN-03b). */
@WebMvcTest(ClaimLifecycleController.class)
class PaymentApprovalControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ClaimLifecycleService lifecycle;
    @MockBean PaymentApprovalService payments;

    private static Claim settled() {
        return new Claim("CLM-1", "POL-1", ClaimStatus.SETTLED, LocalDate.of(2026, 1, 1), "FIRE", "X", Instant.now());
    }

    @Test
    void anAboveAuthoritySettleReturns202Pending() throws Exception {
        UUID id = UUID.randomUUID();
        when(payments.submit(any(), eq("CLM-1"), any())).thenReturn(PaymentApprovalService.Outcome.requested(id));
        mvc.perform(post("/api/claims/CLM-1/settle")
                        .header("X-Claims-Authority", "ADJUSTER").header("X-Claims-User", "adj.sophie")
                        .contentType("application/json").content("{\"amount\":20000}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.requestId").value(id.toString()));
    }

    @Test
    void approveReturns204AndDoesNotPay() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/claims/payment-requests/" + id + "/approve")
                        .header("X-Claims-Authority", "SENIOR").header("X-Claims-User", "sen.marc"))
                .andExpect(status().isNoContent());
    }

    @Test
    void executeReturns200AndTheSettledClaim() throws Exception {
        UUID id = UUID.randomUUID();
        when(payments.execute(any(), eq(id))).thenReturn(settled());
        mvc.perform(post("/api/claims/payment-requests/" + id + "/execute")
                        .header("X-Claims-Authority", "SENIOR").header("X-Claims-User", "sen.lea"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void requesterExecutingTheirOwnPaymentIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AuthorityException("the requester may not execute their own payment")).when(payments).execute(any(), eq(id));
        mvc.perform(post("/api/claims/payment-requests/" + id + "/execute")
                        .header("X-Claims-Authority", "SENIOR").header("X-Claims-User", "adj.sophie"))
                .andExpect(status().isForbidden());
    }
}
