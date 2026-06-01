package com.morphcode.ai.mapper;

import org.mapstruct.Mapper;

import java.util.List;

import com.morphcode.ai.dto.chat.ChatResponse;
import com.morphcode.ai.entity.ChatMessage;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    List<ChatResponse> fromListOfChatMessage(List<ChatMessage> chatMessageList);
}
