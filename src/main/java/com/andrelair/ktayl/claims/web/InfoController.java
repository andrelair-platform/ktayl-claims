package com.andrelair.ktayl.claims.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Service identity endpoint. Liveness/readiness/metrics are served by Spring Boot Actuator
 * ({@code /actuator/health}); this is a small human-readable "what am I" for the ACL.
 */
@RestController
@RequestMapping("/api/claims")
public class InfoController {

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
                "service", "ktayl-claims-acl",
                "role", "ACL / strangler over GlobalCore (SOAP · Oracle)",
                "slice", "clm-v1 — Property FNOL → reserve → settle → closed",
                "status", "bootstrap"
        );
    }
}
