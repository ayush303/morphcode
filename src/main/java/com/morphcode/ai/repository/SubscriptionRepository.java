package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.morphcode.ai.entity.Subscription;
import com.morphcode.ai.enums.SubscriptionStatus;

import java.util.Optional;
import java.util.Set;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /*
     * Get the current active subscription
     */
    Optional<Subscription> findByUserIdAndStatusIn(Long userId, Set<SubscriptionStatus> statusSet);

    boolean existsByStripeSubscriptionId(String subscriptionId);

    Optional<Subscription> findByStripeSubscriptionId(String gatewaySubscriptionId);
}
