package com.morphcode.ai.service;

import java.util.List;

import com.morphcode.ai.dto.chat.ChatResponse;

public interface ChatService {
    List<ChatResponse> getProjectChatHistory(Long projectId);
}
