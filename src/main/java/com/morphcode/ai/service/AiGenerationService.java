package com.morphcode.ai.service;

import com.morphcode.ai.dto.chat.StreamResponse;

import reactor.core.publisher.Flux;

public interface AiGenerationService {
    Flux<StreamResponse> streamResponse(String message, Long projectId);
}
