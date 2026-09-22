package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.FnolService;
import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.OutOfCoverException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L1 — the FNOL endpoint maps outcomes to the right HTTP status. */
@WebMvcTest(ClaimController.class)
class ClaimControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FnolService fnol;

    private static final String BODY = """
            {"policyNumber":"POL-1","lossDate":"2026-06-01","peril":"FIRE","claimantName":"Jean Dupont"}""";

    @Test
    void fnolReturns201WithClaim() throws Exception {
        when(fnol.register(any())).thenReturn(new Claim("CLM-1-000001", "POL-1",
                ClaimStatus.NOTIFIED, LocalDate.of(2026, 6, 1), "FIRE", "Jean Dupont", Instant.now()));

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/claims/CLM-1-000001"))
                .andExpect(jsonPath("$.claimNumber").value("CLM-1-000001"))
                .andExpect(jsonPath("$.status").value("NOTIFIED"));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfCoverReturns422() throws Exception {
        when(fnol.register(any())).thenThrow(new OutOfCoverException("peril not covered by POL-1: FLOOD"));

        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k2").content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k3").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
