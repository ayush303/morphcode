package com.morphcode.ai.dto.chat;

import java.time.Instant;
import java.util.List;

import com.morphcode.ai.enums.MessageRole;

public record ChatResponse(
        Long id,
        MessageRole role,
        List<ChatEventResponse> events,
        String content,
        Integer tokensUsed,
        Instant createdAt

) {
}
