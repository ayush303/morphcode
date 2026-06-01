package com.morphcode.ai.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Builder;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ChatSessionId implements Serializable {
    Long projectId;
    Long userId;
}
