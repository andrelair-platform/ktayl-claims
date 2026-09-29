package com.andrelair.globalcore.repo;

import com.andrelair.globalcore.domain.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<PolicyEntity, String> {
}
