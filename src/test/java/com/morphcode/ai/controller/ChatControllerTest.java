package com.morphcode.ai.controller;

import com.morphcode.ai.dto.chat.ChatResponse;
import com.morphcode.ai.enums.MessageRole;
import com.morphcode.ai.service.AiGenerationService;
import com.morphcode.ai.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ChatController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiGenerationService aiGenerationService;

    @MockBean
    private ChatService chatService;

    @Test
    void getChatHistory_returnsMessageList() throws Exception {
        ChatResponse msg1 = new ChatResponse(1L, MessageRole.USER, List.of(), "Hello", 0, Instant.now());
        ChatResponse msg2 = new ChatResponse(2L, MessageRole.ASSISTANT, List.of(), null, 150, Instant.now());

        when(chatService.getProjectChatHistory(1L)).thenReturn(List.of(msg1, msg2));

        mockMvc.perform(get("/api/chat/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    @Test
    void getChatHistory_noMessages_returnsEmptyArray() throws Exception {
        when(chatService.getProjectChatHistory(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/projects/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
