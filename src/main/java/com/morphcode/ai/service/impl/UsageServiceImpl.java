package com.morphcode.ai.service.impl;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.subscription.PlanLimitsResponse;
import com.morphcode.ai.dto.subscription.UsageTodayResponse;
import com.morphcode.ai.service.UsageService;

@Service
public class UsageServiceImpl implements UsageService {

    @Override
    public UsageTodayResponse getTodayUsageOfUser(Long userId) {
        return null;
    }

    @Override
    public PlanLimitsResponse getCurrentSubscriptionLimitsOfUser(Long userId) {
        return null;
    }
}
