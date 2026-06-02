package com.morphcode.ai.llm;

import com.morphcode.ai.entity.ChatEvent;
import com.morphcode.ai.entity.ChatMessage;
import com.morphcode.ai.enums.ChatEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    private LlmResponseParser parser;
    private ChatMessage parentMessage;

    @BeforeEach
    void setUp() {
        parser = new LlmResponseParser();
        parentMessage = ChatMessage.builder().id(1L).build();
    }

    @Test
    void parseChatEvents_messageTag_returnsMessageEvent() {
        String response = "<message>Hello, I will help you!</message>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.MESSAGE);
        assertThat(events.get(0).getContent()).isEqualTo("Hello, I will help you!");
    }

    @Test
    void parseChatEvents_fileTag_returnsFileEditEvent() {
        String response = """
                <file path="src/App.tsx">const App = () => <div>Hello</div>;</file>
                """;

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.FILE_EDIT);
        assertThat(events.get(0).getFilePath()).isEqualTo("src/App.tsx");
        assertThat(events.get(0).getContent()).contains("const App");
    }

    @Test
    void parseChatEvents_toolTag_returnsToolLogEvent() {
        String response = "<tool args=\"src/App.tsx,src/main.tsx\">Reading files...</tool>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.TOOL_LOG);
        assertThat(events.get(0).getMetadata()).isEqualTo("src/App.tsx,src/main.tsx");
        assertThat(events.get(0).getContent()).isEqualTo("Reading files...");
    }

    @Test
    void parseChatEvents_multipleTags_returnsAllEventsInOrder() {
        String response = """
                <message>Starting analysis...</message>
                <tool args="src/App.tsx">Reading App.tsx</tool>
                <file path="src/App.tsx">export default function App() {}</file>
                <message>Done!</message>
                """;

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(4);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.MESSAGE);
        assertThat(events.get(1).getType()).isEqualTo(ChatEventType.TOOL_LOG);
        assertThat(events.get(2).getType()).isEqualTo(ChatEventType.FILE_EDIT);
        assertThat(events.get(3).getType()).isEqualTo(ChatEventType.MESSAGE);
    }

    @Test
    void parseChatEvents_sequenceOrderStartsAt1() {
        String response = "<message>First</message><message>Second</message>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events.get(0).getSequenceOrder()).isEqualTo(1);
        assertThat(events.get(1).getSequenceOrder()).isEqualTo(2);
    }

    @Test
    void parseChatEvents_parentMessageIsSet() {
        String response = "<message>Test</message>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events.get(0).getChatMessage()).isSameAs(parentMessage);
    }

    @Test
    void parseChatEvents_unknownTag_isIgnored() {
        String response = "<unknown>something</unknown><message>Valid</message>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.MESSAGE);
    }

    @Test
    void parseChatEvents_emptyResponse_returnsEmptyList() {
        List<ChatEvent> events = parser.parseChatEvents("", parentMessage);
        assertThat(events).isEmpty();
    }

    @Test
    void parseChatEvents_noMatchingTags_returnsEmptyList() {
        String response = "Just some plain text without XML tags.";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).isEmpty();
    }

    @Test
    void parseChatEvents_multilineFileContent_preservesContent() {
        String response = """
                <file path="src/index.ts">
                import React from 'react';
                const x = 1;
                </file>
                """;

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getContent()).contains("import React");
        assertThat(events.get(0).getContent()).contains("const x = 1");
    }

    @Test
    void parseChatEvents_caseInsensitiveTags_parsedCorrectly() {
        String response = "<MESSAGE>Hello</MESSAGE>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(ChatEventType.MESSAGE);
    }

    @Test
    void parseChatEvents_fileTag_nullFilePath_whenNoPathAttr() {
        String response = "<file>no path here</file>";

        List<ChatEvent> events = parser.parseChatEvents(response, parentMessage);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFilePath()).isNull();
    }
}
