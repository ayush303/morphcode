package com.morphcode.ai.service;

import com.morphcode.ai.dto.subscription.CheckoutRequest;
import com.morphcode.ai.dto.subscription.CheckoutResponse;
import com.morphcode.ai.dto.subscription.PortalResponse;
import com.morphcode.ai.dto.subscription.SubscriptionResponse;

public interface SubscriptionService {
    SubscriptionResponse getCurrentSubscription(Long userId);

    CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request, Long userId);

    PortalResponse openCustomerPortal(Long userId);
}
