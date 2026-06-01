package com.morphcode.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.subscription.PlanResponse;
import com.morphcode.ai.service.PlanService;

@Service
public class PlanServiceImpl implements PlanService {
    @Override
    public List<PlanResponse> getAllActivePlans() {
        return List.of();
    }
}
