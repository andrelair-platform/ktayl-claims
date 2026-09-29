package com.andrelair.globalcore.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** The legacy policy book (read-only reference for the coverage check). Maps gc_policy + gc_policy_peril. */
@Entity
@Table(name = "gc_policy")
public class PolicyEntity {

    @Id
    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "holder_name")
    private String holderName;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gc_policy_peril", joinColumns = @JoinColumn(name = "policy_number"))
    @Column(name = "peril")
    private Set<String> perils = new HashSet<>();

    public String getPolicyNumber() { return policyNumber; }
    public String getHolderName() { return holderName; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public Set<String> getPerils() { return perils; }
}
