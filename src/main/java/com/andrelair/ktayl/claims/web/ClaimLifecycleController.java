package com.andrelair.ktayl.claims.web;

import com.andrelair.ktayl.claims.application.Actor;
import com.andrelair.ktayl.claims.application.Authority;
import com.andrelair.ktayl.claims.application.ClaimLifecycleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claim lifecycle endpoints (S004): reserve then settle. The acting authority comes from the
 * {@code X-Claims-Authority} header (ADJUSTER / SENIOR) — real auth = Authentik OIDC later (ADR-005).
 * Above-authority ⇒ 403; an out-of-order step (e.g. settle before reserve) ⇒ 409.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimLifecycleController {

    private final ClaimLifecycleService lifecycle;

    public ClaimLifecycleController(ClaimLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    /** The claims inbox — newest-first, optional {@code ?status=} filter. Reads the read-model. */
    @GetMapping
    public java.util.List<ClaimResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        return lifecycle.list(status).stream().map(ClaimResponse::from).toList();
    }

    /** Read a claim's current state. Unknown claim ⇒ 404 (no authority header needed — a plain read). */
    @GetMapping("/{claimNumber}")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return ClaimResponse.from(lifecycle.get(claimNumber));
    }

    @PostMapping("/{claimNumber}/reserve")
    public ClaimResponse reserve(
            @PathVariable String claimNumber,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @Valid @RequestBody ReserveRequest req) {
        return ClaimResponse.from(lifecycle.reserve(new Actor(user, authority), claimNumber, req.amount()));
    }

    @PostMapping("/{claimNumber}/settle")
    public ClaimResponse settle(
            @PathVariable String claimNumber,
            @RequestHeader("X-Claims-Authority") Authority authority,
            @RequestHeader(value = "X-Claims-User", defaultValue = "unknown") String user,
            @Valid @RequestBody SettleRequest req) {
        return ClaimResponse.from(lifecycle.settle(new Actor(user, authority), claimNumber, req.amount()));
    }
}
