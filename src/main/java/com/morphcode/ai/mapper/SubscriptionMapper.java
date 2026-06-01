package com.morphcode.ai.mapper;

import com.morphcode.ai.dto.subscription.PlanResponse;
import com.morphcode.ai.dto.subscription.SubscriptionResponse;
import com.morphcode.ai.entity.Plan;
import com.morphcode.ai.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "periodEnd", source = "currentPeriodEnd")
    @Mapping(target = "tokensUsedThisCycle", ignore = true)
    SubscriptionResponse toSubscriptionResponse(Subscription subscription);

    PlanResponse toPlanResponse(Plan plan);
}
