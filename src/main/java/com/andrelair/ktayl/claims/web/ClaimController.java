package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.FnolCommand;
import com.andrelair.ktayl.claims.application.FnolService;
import com.andrelair.ktayl.claims.domain.Claim;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final FnolService fnol;

    public ClaimController(FnolService fnol) {
        this.fnol = fnol;
    }

    /**
     * FNOL — register a claim (S003). The mandatory {@code Idempotency-Key} makes a client retry create
     * exactly one claim. Out-of-cover ⇒ 422; missing key or invalid body ⇒ 400.
     */
    @PostMapping
    public ResponseEntity<ClaimResponse> fnol(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FnolRequest req) {

        Claim claim = fnol.register(new FnolCommand(
                idempotencyKey, req.policyNumber(), req.lossDate(), req.peril(),
                req.claimantName(), req.description()));

        return ResponseEntity
                .created(URI.create("/api/claims/" + claim.claimNumber()))
                .body(ClaimResponse.from(claim));
    }
}
