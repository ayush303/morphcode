package com.morphcode.ai.entity;

import java.time.Instant;

import com.morphcode.ai.enums.MessageRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage {
    Long id;
    ChatSession session;
    String content;
    MessageRole role;
    Integer tokenUsed;
    String toolCalls; // JSON Array of Tools called in this message
    Instant createdAt;

}
