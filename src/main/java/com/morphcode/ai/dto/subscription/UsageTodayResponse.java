package com.morphcode.ai.dto.subscription;

public record UsageTodayResponse(
        int tokensUsed,
        int tokensLimit,
        int previewsRunning,
        int previewsLimit) {

}
