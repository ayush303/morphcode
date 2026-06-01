package com.morphcode.ai.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.morphcode.ai.dto.chat.ChatResponse;
import org.springframework.stereotype.Service;

import com.morphcode.ai.entity.ChatMessage;
import com.morphcode.ai.entity.ChatSession;
import com.morphcode.ai.entity.ChatSessionId;
import com.morphcode.ai.mapper.ChatMapper;
import com.morphcode.ai.repository.ChatMessageRepository;
import com.morphcode.ai.repository.ChatSessionRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.ChatService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AuthUtil authUtil;
    private final ChatMapper chatMapper;

    @Override
    public List<ChatResponse> getProjectChatHistory(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        ChatSession chatSession = chatSessionRepository.getReferenceById(
                new ChatSessionId(projectId, userId));

        List<ChatMessage> chatMessageList = chatMessageRepository.findByChatSession(chatSession);

        return chatMapper.fromListOfChatMessage(chatMessageList);
    }
}
