package com.morphcode.ai.dto.chat;

import com.morphcode.ai.enums.ChatEventType;

public record ChatEventResponse(
        Long id,
        ChatEventType type,
        Integer sequenceOrder,
        String content,
        String filePath,
        String metadata) {
}
