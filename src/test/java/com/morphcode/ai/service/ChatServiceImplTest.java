package com.morphcode.ai.service;

import com.morphcode.ai.dto.chat.ChatResponse;
import com.morphcode.ai.entity.ChatMessage;
import com.morphcode.ai.entity.ChatSession;
import com.morphcode.ai.entity.ChatSessionId;
import com.morphcode.ai.enums.MessageRole;
import com.morphcode.ai.mapper.ChatMapper;
import com.morphcode.ai.repository.ChatMessageRepository;
import com.morphcode.ai.repository.ChatSessionRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private AuthUtil authUtil;
    @Mock private ChatMapper chatMapper;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void getProjectChatHistory_returnsMappedMessages() {
        Long projectId = 1L;
        Long userId = 10L;
        ChatSessionId sessionId = new ChatSessionId(projectId, userId);
        ChatSession session = ChatSession.builder().id(sessionId).build();
        ChatMessage message = ChatMessage.builder()
                .id(1L)
                .role(MessageRole.USER)
                .content("Hello")
                .createdAt(Instant.now())
                .build();

        ChatResponse chatResponse = new ChatResponse(1L, MessageRole.USER, List.of(), "Hello", 0, Instant.now());

        when(authUtil.getCurrentUserId()).thenReturn(userId);
        when(chatSessionRepository.getReferenceById(any(ChatSessionId.class))).thenReturn(session);
        when(chatMessageRepository.findByChatSession(session)).thenReturn(List.of(message));
        when(chatMapper.fromListOfChatMessage(List.of(message))).thenReturn(List.of(chatResponse));

        List<ChatResponse> result = chatService.getProjectChatHistory(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("Hello");
        assertThat(result.get(0).role()).isEqualTo(MessageRole.USER);
    }

    @Test
    void getProjectChatHistory_noMessages_returnsEmptyList() {
        Long projectId = 2L;
        Long userId = 20L;
        ChatSessionId sessionId = new ChatSessionId(projectId, userId);
        ChatSession session = ChatSession.builder().id(sessionId).build();

        when(authUtil.getCurrentUserId()).thenReturn(userId);
        when(chatSessionRepository.getReferenceById(any(ChatSessionId.class))).thenReturn(session);
        when(chatMessageRepository.findByChatSession(session)).thenReturn(List.of());
        when(chatMapper.fromListOfChatMessage(List.of())).thenReturn(List.of());

        assertThat(chatService.getProjectChatHistory(projectId)).isEmpty();
    }
}
