package com.morphcode.ai.service;

import com.morphcode.ai.dto.subscription.PlanResponse;
import com.morphcode.ai.dto.subscription.SubscriptionResponse;
import com.morphcode.ai.entity.UsageLog;
import com.morphcode.ai.repository.UsageLogRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.UsageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceImplTest {

    @Mock private UsageLogRepository usageLogRepository;
    @Mock private AuthUtil authUtil;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks
    private UsageServiceImpl usageService;

    private UsageLog buildLog(Long userId, int tokens) {
        return UsageLog.builder().userId(userId).date(LocalDate.now()).tokensUsed(tokens).build();
    }

    private SubscriptionResponse buildSubscription(boolean unlimited, int maxTokens) {
        PlanResponse plan = new PlanResponse(1L, "Pro", 10, maxTokens, unlimited, "$9");
        return new SubscriptionResponse(plan, "ACTIVE", Instant.now(), 0L);
    }

    @Test
    void recordTokenUsage_existingLog_incrementsTokens() {
        Long userId = 1L;
        UsageLog log = buildLog(userId, 100);
        when(usageLogRepository.findByUserIdAndDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(log));
        when(usageLogRepository.save(log)).thenReturn(log);

        usageService.recordTokenUsage(userId, 50);

        assertThat(log.getTokensUsed()).isEqualTo(150);
        verify(usageLogRepository).save(log);
    }

    @Test
    void recordTokenUsage_noExistingLog_createsNewLog() {
        Long userId = 2L;
        UsageLog newLog = buildLog(userId, 0);

        when(usageLogRepository.findByUserIdAndDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(usageLogRepository.save(any(UsageLog.class))).thenReturn(newLog);

        usageService.recordTokenUsage(userId, 200);

        verify(usageLogRepository, times(2)).save(any(UsageLog.class));
    }

    @Test
    void checkDailyTokensUsage_unlimited_doesNotThrow() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionService.getCurrentSubscription())
                .thenReturn(buildSubscription(true, 1000));
        when(usageLogRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildLog(1L, 99999)));

        usageService.checkDailyTokensUsage();
    }

    @Test
    void checkDailyTokensUsage_withinLimit_doesNotThrow() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionService.getCurrentSubscription())
                .thenReturn(buildSubscription(false, 5000));
        when(usageLogRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildLog(1L, 100)));

        usageService.checkDailyTokensUsage();
    }

    @Test
    void checkDailyTokensUsage_limitReached_throwsTooManyRequests() {
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionService.getCurrentSubscription())
                .thenReturn(buildSubscription(false, 500));
        when(usageLogRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildLog(1L, 500)));

        assertThatThrownBy(() -> usageService.checkDailyTokensUsage())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Daily limit reached");
    }

    @Test
    void checkDailyTokensUsage_noLog_createsLogAndPasses() {
        UsageLog newLog = buildLog(1L, 0);
        when(authUtil.getCurrentUserId()).thenReturn(1L);
        when(subscriptionService.getCurrentSubscription())
                .thenReturn(buildSubscription(false, 5000));
        when(usageLogRepository.findByUserIdAndDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(usageLogRepository.save(any())).thenReturn(newLog);

        usageService.checkDailyTokensUsage();

        verify(usageLogRepository).save(any(UsageLog.class));
    }
}
