package com.morphcode.ai.service;

import com.morphcode.ai.dto.subscription.PlanResponse;
import com.morphcode.ai.dto.subscription.SubscriptionResponse;
import com.morphcode.ai.entity.Plan;
import com.morphcode.ai.entity.Subscription;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.enums.SubscriptionStatus;
import com.morphcode.ai.error.ResourceNotFoundException;
import com.morphcode.ai.mapper.SubscriptionMapper;
import com.morphcode.ai.repository.PlanRepository;
import com.morphcode.ai.repository.ProjectMemberRepository;
import com.morphcode.ai.repository.SubscriptionRepository;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private AuthUtil authUtil;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private User buildUser(Long id) {
        return User.builder().id(id).username("user@example.com").build();
    }

    private Plan buildPlan(Long id, int maxProjects) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setName("Pro");
        plan.setMaxProjects(maxProjects);
        plan.setUnlimitedAi(false);
        return plan;
    }

    private Subscription buildSubscription(SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setStatus(status);
        sub.setPlan(buildPlan(1L, 10));
        return sub;
    }

    @Test
    void getCurrentSubscription_active_returnsMappedResponse() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        Subscription sub = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.of(sub));

        SubscriptionResponse response = new SubscriptionResponse(
                new PlanResponse(1L, "Pro", 10, 5000, false, "$9"), "ACTIVE", Instant.now(), 0L);
        when(subscriptionMapper.toSubscriptionResponse(sub)).thenReturn(response);

        SubscriptionResponse result = subscriptionService.getCurrentSubscription();

        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getCurrentSubscription_noActiveSubscription_returnsEmpty() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionRepository.findByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.empty());

        when(subscriptionMapper.toSubscriptionResponse(any(Subscription.class)))
                .thenReturn(new SubscriptionResponse(null, null, null, null));

        SubscriptionResponse result = subscriptionService.getCurrentSubscription();

        assertThat(result.plan()).isNull();
    }

    @Test
    void activateSubscription_newSubscription_saves() {
        when(subscriptionRepository.existsByStripeSubscriptionId("sub_123")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L)));
        when(planRepository.findById(2L)).thenReturn(Optional.of(buildPlan(2L, 5)));
        when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        subscriptionService.activateSubscription(1L, 2L, "sub_123", "cus_abc");

        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void activateSubscription_duplicate_skips() {
        when(subscriptionRepository.existsByStripeSubscriptionId("sub_123")).thenReturn(true);

        subscriptionService.activateSubscription(1L, 2L, "sub_123", "cus_abc");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelSubscription_setsStatusCanceled() {
        Subscription sub = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123"))
                .thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(sub)).thenReturn(sub);

        subscriptionService.cancelSubscription("sub_123");

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void cancelSubscription_notFound_throwsResourceNotFoundException() {
        when(subscriptionRepository.findByStripeSubscriptionId("bad_id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancelSubscription("bad_id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renewSubscriptionPeriod_pastDue_setsActive() {
        Subscription sub = buildSubscription(SubscriptionStatus.PAST_DUE);
        Instant newEnd = Instant.now().plusSeconds(86400);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_123"))
                .thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(sub)).thenReturn(sub);

        subscriptionService.renewSubscriptionPeriod("sub_123", Instant.now(), newEnd);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(newEnd);
    }

    @Test
    void markSubscriptionPastDue_activeStatus_setsPastDue() {
        Subscription sub = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123"))
                .thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(sub)).thenReturn(sub);

        subscriptionService.markSubscriptionPastDue("sub_123");

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void markSubscriptionPastDue_alreadyPastDue_doesNotSave() {
        Subscription sub = buildSubscription(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_123"))
                .thenReturn(Optional.of(sub));

        subscriptionService.markSubscriptionPastDue("sub_123");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void canCreateNewProject_withNoPlan_usesFreeTier() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionRepository.findByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(subscriptionMapper.toSubscriptionResponse(any()))
                .thenReturn(new SubscriptionResponse(null, null, null, null));
        when(projectMemberRepository.countProjectOwnedByUser(1L)).thenReturn(5);

        assertThat(subscriptionService.canCreateNewProject()).isTrue();
    }

    @Test
    void canCreateNewProject_withPlan_checksPlanLimit() {
        PlanResponse planResponse = new PlanResponse(1L, "Pro", 3, 5000, false, "$9");
        SubscriptionResponse subResponse = new SubscriptionResponse(planResponse, "ACTIVE", Instant.now(), 0L);

        when(authUtil.getCurrentUserId()).thenReturn(1L);
        Subscription sub = buildSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.of(sub));
        when(subscriptionMapper.toSubscriptionResponse(sub)).thenReturn(subResponse);
        when(projectMemberRepository.countProjectOwnedByUser(1L)).thenReturn(3);

        assertThat(subscriptionService.canCreateNewProject()).isFalse();
    }
}
