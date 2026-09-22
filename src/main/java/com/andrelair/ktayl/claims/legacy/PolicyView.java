package com.andrelair.ktayl.claims.legacy;

import java.time.LocalDate;
import java.util.Set;

/** The slice of a legacy POLICY the ACL needs for a coverage check (read from GlobalCore). */
public record PolicyView(
        String policyNumber,
        String holderName,
        LocalDate coverStart,
        LocalDate coverEnd,
        Set<String> coveredPerils
) {}
