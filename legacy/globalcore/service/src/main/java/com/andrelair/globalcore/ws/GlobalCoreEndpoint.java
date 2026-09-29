package com.andrelair.globalcore.ws;

import com.andrelair.globalcore.domain.ClaimEntity;
import com.andrelair.globalcore.domain.PolicyEntity;
import com.andrelair.globalcore.gen.ClaimType;
import com.andrelair.globalcore.gen.CreateClaimRequest;
import com.andrelair.globalcore.gen.CreateClaimResponse;
import com.andrelair.globalcore.gen.FindClaimRequest;
import com.andrelair.globalcore.gen.FindClaimResponse;
import com.andrelair.globalcore.gen.FindPolicyRequest;
import com.andrelair.globalcore.gen.FindPolicyResponse;
import com.andrelair.globalcore.gen.ObjectFactory;
import com.andrelair.globalcore.gen.ReserveRequest;
import com.andrelair.globalcore.gen.ReserveResponse;
import com.andrelair.globalcore.gen.SettleRequest;
import com.andrelair.globalcore.gen.SettleResponse;
import com.andrelair.globalcore.service.GlobalCoreService;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.util.Optional;

/** SOAP facade for GlobalCore — the operations the ktayl-claims ACL's GlobalCorePort declares. */
@Endpoint
public class GlobalCoreEndpoint {

    private static final String NS = "http://globalcore.andrelair.com/claims";

    private final GlobalCoreService service;
    private final ObjectFactory of = new ObjectFactory();

    public GlobalCoreEndpoint(GlobalCoreService service) {
        this.service = service;
    }

    @PayloadRoot(namespace = NS, localPart = "FindPolicyRequest")
    @ResponsePayload
    public FindPolicyResponse findPolicy(@RequestPayload FindPolicyRequest req) {
        FindPolicyResponse resp = of.createFindPolicyResponse();
        Optional<PolicyEntity> p = service.findPolicy(req.getPolicyNumber());
        if (p.isEmpty()) {
            resp.setFound(false);
            return resp;
        }
        PolicyEntity policy = p.get();
        resp.setFound(true);
        resp.setPolicyNumber(policy.getPolicyNumber());
        resp.setHolderName(policy.getHolderName());
        resp.setEffectiveDate(XmlDates.date(policy.getEffectiveDate()));
        resp.setExpiryDate(XmlDates.date(policy.getExpiryDate()));
        resp.getPeril().addAll(policy.getPerils());
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "CreateClaimRequest")
    @ResponsePayload
    public CreateClaimResponse createClaim(@RequestPayload CreateClaimRequest req) {
        ClaimEntity c = service.createClaim(
                req.getPolicyNumber(), XmlDates.toLocalDate(req.getLossDate()), req.getPeril(), req.getClaimantName());
        CreateClaimResponse resp = of.createCreateClaimResponse();
        resp.setClaim(toClaimType(c));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "FindClaimRequest")
    @ResponsePayload
    public FindClaimResponse findClaim(@RequestPayload FindClaimRequest req) {
        FindClaimResponse resp = of.createFindClaimResponse();
        Optional<ClaimEntity> c = service.findClaim(req.getClaimNumber());
        resp.setFound(c.isPresent());
        c.ifPresent(claim -> resp.setClaim(toClaimType(claim)));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "ReserveRequest")
    @ResponsePayload
    public ReserveResponse reserve(@RequestPayload ReserveRequest req) {
        ClaimEntity c = service.reserve(req.getClaimNumber(), req.getAmountMinor());
        ReserveResponse resp = of.createReserveResponse();
        resp.setClaim(toClaimType(c));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "SettleRequest")
    @ResponsePayload
    public SettleResponse settle(@RequestPayload SettleRequest req) {
        ClaimEntity c = service.settle(req.getClaimNumber(), req.getAmountMinor());
        SettleResponse resp = of.createSettleResponse();
        resp.setClaim(toClaimType(c));
        return resp;
    }

    private ClaimType toClaimType(ClaimEntity c) {
        ClaimType t = of.createClaimType();
        t.setClaimNumber(c.getClaimNumber());
        t.setPolicyNumber(c.getPolicyNumber());
        t.setStatus(c.getStatus());
        t.setLossDate(XmlDates.date(c.getLossDate()));
        t.setPeril(c.getPeril());
        t.setClaimantName(c.getClaimantName());
        t.setReserveMinor(c.getReserveMinor());
        t.setSettlementMinor(c.getSettlementMinor());
        t.setRegisteredAt(XmlDates.dateTime(c.getRegisteredAt()));
        return t;
    }
}
