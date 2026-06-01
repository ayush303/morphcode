package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.morphcode.ai.entity.ChatSession;
import com.morphcode.ai.entity.ChatSessionId;

public interface ChatSessionRepository extends JpaRepository<ChatSession, ChatSessionId> {
}
