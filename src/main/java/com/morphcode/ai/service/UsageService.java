package com.morphcode.ai.service;

import com.morphcode.ai.dto.subscription.PlanLimitsResponse;
import com.morphcode.ai.dto.subscription.UsageTodayResponse;

public interface UsageService {
    UsageTodayResponse getTodayUsageOfUser(Long userId);

    PlanLimitsResponse getCurrentSubscriptionLimitsOfUser(Long userId);
}
