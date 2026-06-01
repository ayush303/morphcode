package com.morphcode.ai.service;

import com.morphcode.ai.dto.subscription.CheckoutRequest;
import com.morphcode.ai.dto.subscription.CheckoutResponse;
import com.morphcode.ai.dto.subscription.PortalResponse;
import com.stripe.model.StripeObject;

import java.util.Map;

public interface PaymentProcessor {
    CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request);

    PortalResponse openCustomerPortal();

    void handleWebhookEvent(String type, StripeObject stripeObject, Map<String, String> metadata);
}
