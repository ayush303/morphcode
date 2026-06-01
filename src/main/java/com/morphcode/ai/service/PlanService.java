package com.morphcode.ai.service;

import java.util.List;

import com.morphcode.ai.dto.subscription.PlanResponse;

public interface PlanService {
    List<PlanResponse> getAllActivePlans();
}