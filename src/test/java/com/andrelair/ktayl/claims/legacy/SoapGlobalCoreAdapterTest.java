package com.andrelair.ktayl.claims.legacy;

import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.domain.OutOfCoverException;
import com.andrelair.ktayl.claims.legacy.gen.CreateClaimResponse;
import com.andrelair.ktayl.claims.legacy.gen.ClaimType;
import com.andrelair.ktayl.claims.legacy.gen.FindPolicyResponse;
import com.andrelair.ktayl.claims.legacy.gen.ReserveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** L1 — the ACL SOAP adapter maps GlobalCore faults → domain exceptions and euros → eurocents. */
@ExtendWith(MockitoExtension.class)
class SoapGlobalCoreAdapterTest {

    @Mock
    WebServiceTemplate ws;

    private SoapGlobalCoreAdapter adapter() {
        return new SoapGlobalCoreAdapter(ws);
    }

    @Test
    void findPolicyMapsFoundPolicy() {
        FindPolicyResponse resp = new FindPolicyResponse();
        resp.setFound(true);
        resp.setPolicyNumber("POL-PROP-0001");
        resp.setHolderName("Durand SARL");
        resp.getPeril().add("FIRE");
        when(ws.marshalSendAndReceive(any())).thenReturn(resp);

        var view = adapter().findPolicy("POL-PROP-0001");
        assertThat(view).isPresent();
        assertThat(view.get().coveredPerils()).contains("FIRE");
    }

    @Test
    void findPolicyNotFoundIsEmpty() {
        FindPolicyResponse resp = new FindPolicyResponse();
        resp.setFound(false);
        when(ws.marshalSendAndReceive(any())).thenReturn(resp);
        assertThat(adapter().findPolicy("POL-NOPE")).isEmpty();
    }

    @Test
    void createClaimReturnsAllocatedNumber() {
        ClaimType c = new ClaimType();
        c.setClaimNumber("CLM-2026-000009");
        CreateClaimResponse resp = new CreateClaimResponse();
        resp.setClaim(c);
        when(ws.marshalSendAndReceive(any())).thenReturn(resp);

        assertThat(adapter().createClaim("POL-PROP-0001", java.time.LocalDate.of(2026, 6, 1), "FIRE", "X"))
                .isEqualTo("CLM-2026-000009");
    }

    @Test
    void reserveConvertsEurosToEurocents() {
        when(ws.marshalSendAndReceive(any())).thenReturn(null);
        adapter().reserve("CLM-2026-000001", new BigDecimal("15000.00"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ws).marshalSendAndReceive(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ReserveRequest.class);
        assertThat(((ReserveRequest) captor.getValue()).getAmountMinor()).isEqualTo(1_500_000L); // €15,000 → eurocents
    }

    @Test
    void notFoundFaultMapsToClaimNotFound() {
        stubFault("NOT_FOUND: claim CLM-2026-999999");
        assertThatThrownBy(() -> adapter().reserve("CLM-2026-999999", BigDecimal.ONE))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void illegalTransitionFaultMaps() {
        stubFault("ILLEGAL_TRANSITION: cannot settle a claim in state NOTIFIED");
        assertThatThrownBy(() -> adapter().settle("CLM-2026-000001", BigDecimal.TEN))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void notCoveredFaultMapsToOutOfCover() {
        stubFault("NOT_COVERED: policy POL-PROP-0001 does not cover peril EARTHQUAKE");
        assertThatThrownBy(() -> adapter().createClaim("POL-PROP-0001", java.time.LocalDate.now(), "EARTHQUAKE", "X"))
                .isInstanceOf(OutOfCoverException.class);
    }

    private void stubFault(String reason) {
        SoapFaultClientException fault = mock(SoapFaultClientException.class);
        when(fault.getFaultStringOrReason()).thenReturn(reason);
        when(ws.marshalSendAndReceive(any())).thenThrow(fault);
    }
}
