package com.andrelair.ktayl.claims.legacy;

import com.andrelair.ktayl.claims.domain.Claim;
import com.andrelair.ktayl.claims.domain.ClaimNotFoundException;
import com.andrelair.ktayl.claims.domain.ClaimStatus;
import com.andrelair.ktayl.claims.domain.IllegalTransitionException;
import com.andrelair.ktayl.claims.domain.OutOfCoverException;
import com.andrelair.ktayl.claims.legacy.gen.ClaimType;
import com.andrelair.ktayl.claims.legacy.gen.CreateClaimRequest;
import com.andrelair.ktayl.claims.legacy.gen.CreateClaimResponse;
import com.andrelair.ktayl.claims.legacy.gen.FindClaimRequest;
import com.andrelair.ktayl.claims.legacy.gen.FindClaimResponse;
import com.andrelair.ktayl.claims.legacy.gen.FindPolicyRequest;
import com.andrelair.ktayl.claims.legacy.gen.FindPolicyResponse;
import com.andrelair.ktayl.claims.legacy.gen.ReserveRequest;
import com.andrelair.ktayl.claims.legacy.gen.SettleRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;

/**
 * The REAL Anti-Corruption Layer adapter (Slice B): a spring-ws SOAP client to the live GlobalCore
 * legacy. Active under the `soap` profile (the {@link StubGlobalCoreAdapter} is the default). Money
 * crosses as eurocents: the ACL works in EUROS (BigDecimal, the authority matrix), the legacy stores
 * eurocents (long) → ×100 here. GlobalCore SOAP faults (prefixed NOT_FOUND / ILLEGAL_TRANSITION /
 * NOT_COVERED) are translated back into the ACL's domain exceptions so the API maps them to 404/409/422.
 */
@Component
@Profile("soap")
public class SoapGlobalCoreAdapter implements GlobalCorePort {

    private static final DatatypeFactory DF;
    static {
        try {
            DF = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final WebServiceTemplate ws;

    public SoapGlobalCoreAdapter(WebServiceTemplate globalCoreWsTemplate) {
        this.ws = globalCoreWsTemplate;
    }

    @Override
    public Optional<PolicyView> findPolicy(String policyNumber) {
        FindPolicyRequest req = new FindPolicyRequest();
        req.setPolicyNumber(policyNumber);
        FindPolicyResponse resp = (FindPolicyResponse) send(req);
        if (!resp.isFound()) {
            return Optional.empty();
        }
        return Optional.of(new PolicyView(
                resp.getPolicyNumber(), resp.getHolderName(),
                toLocalDate(resp.getEffectiveDate()), toLocalDate(resp.getExpiryDate()),
                new HashSet<>(resp.getPeril())));
    }

    @Override
    public String createClaim(String policyNumber, LocalDate lossDate, String peril, String claimantName) {
        CreateClaimRequest req = new CreateClaimRequest();
        req.setPolicyNumber(policyNumber);
        req.setLossDate(toXmlDate(lossDate));
        req.setPeril(peril);
        req.setClaimantName(claimantName);
        CreateClaimResponse resp = (CreateClaimResponse) send(req);
        return resp.getClaim().getClaimNumber();
    }

    @Override
    public Optional<Claim> findClaim(String claimNumber) {
        FindClaimRequest req = new FindClaimRequest();
        req.setClaimNumber(claimNumber);
        FindClaimResponse resp = (FindClaimResponse) send(req);
        if (!resp.isFound()) {
            return Optional.empty();
        }
        return Optional.of(toClaim(resp.getClaim()));
    }

    @Override
    public void reserve(String claimNumber, BigDecimal amount) {
        ReserveRequest req = new ReserveRequest();
        req.setClaimNumber(claimNumber);
        req.setAmountMinor(toMinor(amount));
        send(req);
    }

    @Override
    public void settle(String claimNumber, BigDecimal amount) {
        SettleRequest req = new SettleRequest();
        req.setClaimNumber(claimNumber);
        req.setAmountMinor(toMinor(amount));
        send(req);
    }

    /** Send the payload; translate a GlobalCore SOAP fault into the matching ACL domain exception. */
    private Object send(Object requestPayload) {
        try {
            return ws.marshalSendAndReceive(requestPayload);
        } catch (SoapFaultClientException fault) {
            String reason = fault.getFaultStringOrReason() == null ? "" : fault.getFaultStringOrReason();
            if (reason.contains("NOT_FOUND")) {
                throw new ClaimNotFoundException(reason);
            }
            if (reason.contains("ILLEGAL_TRANSITION")) {
                throw new IllegalTransitionException(reason);
            }
            if (reason.contains("NOT_COVERED")) {
                throw new OutOfCoverException(reason);
            }
            throw fault; // unmapped legacy fault → surfaces as a 500 (a genuine legacy error)
        }
    }

    private Claim toClaim(ClaimType t) {
        return new Claim(
                t.getClaimNumber(), t.getPolicyNumber(), ClaimStatus.valueOf(t.getStatus()),
                toLocalDate(t.getLossDate()), t.getPeril(), t.getClaimantName(),
                toInstant(t.getRegisteredAt()));
    }

    private static long toMinor(BigDecimal euros) {
        return euros.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private static XMLGregorianCalendar toXmlDate(LocalDate d) {
        return DF.newXMLGregorianCalendarDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private static LocalDate toLocalDate(XMLGregorianCalendar c) {
        return c == null ? null : LocalDate.of(c.getYear(), c.getMonth(), c.getDay());
    }

    private static Instant toInstant(XMLGregorianCalendar c) {
        return c == null ? Instant.now() : c.toGregorianCalendar().toInstant().atZone(ZoneOffset.UTC).toInstant();
    }
}
