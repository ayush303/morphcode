package com.morphcode.ai.service.impl;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.subscription.CheckoutRequest;
import com.morphcode.ai.dto.subscription.CheckoutResponse;
import com.morphcode.ai.dto.subscription.PortalResponse;
import com.morphcode.ai.dto.subscription.SubscriptionResponse;
import com.morphcode.ai.service.SubscriptionService;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    @Override
    public SubscriptionResponse getCurrentSubscription(Long userId) {
        return null;
    }

    @Override
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request, Long userId) {
        return null;
    }

    @Override
    public PortalResponse openCustomerPortal(Long userId) {
        return null;
    }
}
