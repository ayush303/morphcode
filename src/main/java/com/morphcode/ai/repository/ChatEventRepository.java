package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.morphcode.ai.entity.ChatEvent;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
}
