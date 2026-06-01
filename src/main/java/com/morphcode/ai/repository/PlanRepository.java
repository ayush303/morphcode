package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.morphcode.ai.entity.Plan;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByStripePriceId(String id);
}
