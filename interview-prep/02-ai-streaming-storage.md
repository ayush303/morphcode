# MorphCode — Interview Preparation: AI/LLM Integration, SSE Streaming, MinIO Storage, and Reactive Programming

> Stack: Java 17 · Spring Boot 3.3.0 · Spring AI 1.0.0 · OpenRouter → Gemini Flash · MinIO 8.6.0 · Project Reactor (Flux)

---

## Table of Contents

1. [Spring AI & ChatClient](#1-spring-ai--chatclient)
2. [StreamAdvisor — FileTreeContextAdvisor](#2-streamadvisor--filetreecontextadvisor)
3. [Tool Calling — CodeGenerationTools](#3-tool-calling--codegenerationtools)
4. [AI Generation Service — AiGenerationServiceImpl](#4-ai-generation-service--aigenerationserviceimpl)
5. [LLM Response Parsing — LlmResponseParser](#5-llm-response-parsing--llmresponseparser)
6. [System Prompt Design — PromptUtils](#6-system-prompt-design--promptutils)
7. [Server-Sent Events & Streaming](#7-server-sent-events--streaming)
8. [Project Reactor — Reactive Programming](#8-project-reactor--reactive-programming)
9. [MinIO Storage Patterns](#9-minio-storage-patterns)
10. [Template Initialization — ProjectTemplateServiceImpl](#10-template-initialization--projecttemplateserviceimpl)
11. [Known Issues & Design Trade-offs](#11-known-issues--design-trade-offs)
12. [Cross-Cutting Concerns](#12-cross-cutting-concerns)

---

## 1. Spring AI & ChatClient

### Q: How is ChatClient configured in MorphCode, and what does the configuration do?

**A:** `ChatClient` is configured in `AiConfig.java` as a Spring `@Bean`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
}
```

Spring AI's `OpenAiAutoConfiguration` auto-configures the `ChatClient.Builder` by reading two properties from `application.properties`:
- `spring.ai.openai.api-key=${OPENROUTER_API_KEY}` — the API key for OpenRouter
- `spring.ai.openai.base-url=https://openrouter.ai/api` — overrides the OpenAI base URL to point at OpenRouter instead

This is how MorphCode routes calls through OpenRouter to reach Google Gemini Flash — it uses the OpenAI-compatible API that OpenRouter exposes at that URL. The model itself is set via `spring.ai.openai.chat.options.model=google/gemini-3-flash-preview` and temperature is fixed at `0.0` for deterministic output.

`SimpleLoggerAdvisor` is added as a default advisor, which means every single ChatClient request and its response will be logged at `DEBUG` level. The log level is activated by `logging.level.org.springframework.ai.chat.client=DEBUG` in `application.properties`.

---

### Q: Why does MorphCode use OpenRouter instead of calling Google directly? What are the trade-offs?

**A:** OpenRouter acts as a unified LLM gateway — it provides a single OpenAI-compatible API endpoint that can route to many different model providers, including Google Gemini. The benefit is that Spring AI's `OpenAiChatModel` can be reused without writing a custom client for Google's API, and switching models becomes a one-line config change. The trade-offs are an extra network hop, an additional pricing layer (OpenRouter charges a markup or usage fee on top of provider costs), and dependency on a third-party intermediary for availability.

---

### Q: What is the role of `temperature=0.0` in the system and why was that chosen?

**A:** Temperature controls how random the LLM's token sampling is. At `0.0`, the model always picks the highest-probability token, making output deterministic and reproducible for the same input. For code generation this is desirable: you want consistent, well-formed XML with `<file>`, `<message>`, and `<tool>` tags in the right structure every time. High temperatures introduce variability that could malform the XML protocol that `LlmResponseParser` depends on, breaking the post-processing pipeline entirely.

---

### Q: How does Spring AI's `ChatClient.Builder` pick up OpenRouter credentials? What would happen if the env var is missing?

**A:** `ChatClient.Builder` is created by `OpenAiAutoConfiguration`, which reads `spring.ai.openai.api-key`. In `application.properties` this is `${OPENROUTER_API_KEY}`, so Spring resolves it from the environment variable at startup. If the env var is absent, Spring fails to resolve the placeholder and throws a `BeanCreationException` immediately on startup — the application will not start at all. No lazy initialization is involved here.

---

### Q: What is `SimpleLoggerAdvisor` and what does it log?

**A:** `SimpleLoggerAdvisor` is a built-in Spring AI advisor that implements `CallAdvisor` and `StreamAdvisor`. It intercepts every request going to the model and every response coming back and logs them at the configured log level (default `DEBUG`). For streaming responses it logs each chunk. The logs include the full message list sent to the model and the full text of each response chunk, which is invaluable for debugging prompt issues but must be disabled in production due to the volume of output and potential data sensitivity.

---

### Q: How does the `ChatClient` fluent API chain work when `streamResponse()` is called?

**A:** The chain in `AiGenerationServiceImpl.streamResponse()` is:

```
chatClient.prompt()
  .system(...)       // sets system message
  .user(userMessage) // sets user message
  .tools(codeGenerationTools) // registers @Tool methods
  .advisors(spec -> {
      spec.params(advisorParams);       // passes context to advisors
      spec.advisors(fileTreeContextAdvisor); // attaches per-call advisor
  })
  .stream()          // switches to streaming mode
  .chatResponse()    // returns Flux<ChatResponse>
```

Each call returns the same builder or a specialized streaming builder. `.stream()` switches from the blocking `call()` path to the reactive streaming path. `.chatResponse()` emits a `Flux<ChatResponse>` where each element carries one text chunk plus metadata. This differs from `.content()` which would emit `Flux<String>` — `chatResponse()` is used here because the code needs access to `response.getMetadata().getUsage()` for token counting.

---

### Q: How does Spring AI perform tool/function calling internally? Walk through what happens when the model wants to read files.

**A:** When the model emits a tool call request in its response, Spring AI's streaming interceptor detects it before forwarding the chunk to the application. Spring AI then:
1. Finds the registered `@Tool`-annotated method that matches the tool name (`read_files`)
2. Deserializes the arguments JSON the model sent into the method's parameter type (`List<String>`)
3. Invokes the method on the `CodeGenerationTools` instance
4. Packages the return value as a tool result message
5. Makes another model API call with the tool result appended to the conversation
6. Resumes streaming the model's continuation response

From the application's perspective the `Flux<ChatResponse>` emits chunks before the tool call, pauses while Spring AI handles the round trip internally, then resumes with the model's post-tool-call response. The `doOnNext` buffer in `AiGenerationServiceImpl` accumulates the entire turn including both the pre- and post-tool text.

---

## 2. StreamAdvisor — FileTreeContextAdvisor

### Q: What is a `StreamAdvisor` in Spring AI and how does it differ from a regular `CallAdvisor`?

**A:** Spring AI defines an `Advisor` chain pattern analogous to servlet filters. A `CallAdvisor` wraps the blocking `call()` path. A `StreamAdvisor` wraps the reactive `stream()` path and its `adviseStream()` method returns `Flux<ChatClientResponse>` — it must be reactive all the way through. `FileTreeContextAdvisor` implements `StreamAdvisor` with two required methods:
- `adviseStream(ChatClientRequest request, StreamAdvisorChain chain)` — intercept the request, modify it, and delegate to `chain.nextStream(augmentedRequest)`
- `getName()` — returns `"FileTreeContextAdvisor"` used for identification
- `getOrder()` — returns `0`, controlling execution order when multiple advisors are chained

---

### Q: Explain exactly what `FileTreeContextAdvisor` does to the request before the LLM sees it.

**A:** When `adviseStream()` is called, the advisor:
1. Reads `projectId` from `request.context()` — this is the map populated by `advisorSpec.params(advisorParams)` in the service layer
2. Calls `augmentRequestWithFileTree(request, projectId)` which:
   a. Extracts all existing messages from `request.prompt().getInstructions()`
   b. Separates them into the system message and all non-system (user/assistant) messages
   c. Queries PostgreSQL via `projectFileService.getFileTree(projectId)` to get a `List<FileNode>`
   d. Creates a **new** `SystemMessage` containing `"\n\n ---- FILE_TREE ----\n" + fileTree.toString()`
   e. Rebuilds the message list as: [original system message, file tree system message, all other messages]
   f. Returns a mutated request using `request.mutate().prompt(...).build()`
3. Passes the augmented request to `streamAdvisorChain.nextStream(augmentedChatClientRequest)`

The result is that the LLM sees two system messages: the main code generation prompt and a separate system message listing every file path in the project. This gives the model the project structure it needs to know which files exist before deciding which ones to read.

---

### Q: Why are there two system messages instead of appending the file tree to the original system message?

**A:** The `FileTreeContextAdvisor` adds a second `SystemMessage` rather than modifying the original one because it doesn't own the original. The original system message comes from `PromptUtils.CODE_GENERATION_SYSTEM_PROMPT` set in the service layer. Modifying it would require string concatenation and knowledge of the original prompt's content, tightly coupling the advisor to the prompt structure. Using a separate `SystemMessage` keeps the advisor self-contained and reusable. Most LLMs (including Gemini through OpenRouter) treat multiple system messages by concatenating their content, so the behavior is functionally equivalent.

---

### Q: What is the performance problem with FileTreeContextAdvisor and how would you fix it?

**A:** The advisor calls `projectFileService.getFileTree(projectId)` which hits PostgreSQL (`projectFileRepository.findByProjectId(projectId)`) on **every single LLM turn**. If a chat session involves 10 back-and-forth messages with tool calls, that's 10 database queries just for file tree context. On large projects with hundreds of files this is significant overhead.

Fixes in order of complexity:
1. **Request-scoped cache**: Cache the result in a `ThreadLocal` or in the `advisorParams` map itself — pass the file tree in the context map from the service layer so the advisor reads it from there rather than querying DB.
2. **Time-based cache**: Use Spring's `@Cacheable` on `getFileTree()` with a short TTL (e.g., 5 seconds) to absorb bursts.
3. **Invalidation-based cache**: Cache the file tree keyed by `projectId` and invalidate it whenever `saveFile()` is called.
4. **One-shot load**: Load the file tree once at the start of `streamResponse()` and pass it through the advisors context map — the advisor would then simply read from context instead of going to DB.

---

### Q: How does `request.context()` work and how is the projectId passed to the advisor?

**A:** In `AiGenerationServiceImpl.streamResponse()`:
```java
Map<String, Object> advisorParams = Map.of("userId", userId, "projectId", projectId);
```
These are passed via `advisorSpec.params(advisorParams)` in the `.advisors()` lambda. Spring AI stores this map as the "context" of the `ChatClientRequest`. When the advisor receives the request, it reads it back with `request.context()`, which returns this map. The advisor does `context.getOrDefault("projectId", 0).toString()` then parses it as `Long`. Note the defensive default of `0` — if projectId were missing from context (e.g., an advisor added to a different chat client call without the params), the advisor would query for project ID 0, likely returning an empty file tree silently rather than failing.

---

### Q: The advisor calls `fileTree.toString()` to format the file tree. What are the risks?

**A:** `fileTree` is a `List<FileNode>`. Calling `.toString()` on a Java List produces output like `[FileNode(path=src/App.tsx), FileNode(path=src/main.tsx), ...]`, using the `FileNode`'s `toString()` output (generated by Lombok's `@ToString` or the default). This is unstructured from the LLM's perspective — it's not JSON, not a tree hierarchy, just a flat list. Problems:
1. The LLM receives a Java object string representation, not a human-readable tree
2. If `FileNode.toString()` is the default Object toString (no Lombok `@ToString`), it produces memory addresses — useless to the LLM
3. For large projects this string can consume a significant chunk of the context window
4. A proper implementation would format this as a sorted tree with indentation, or as a JSON array of path strings

---

## 3. Tool Calling — CodeGenerationTools

### Q: Why is `CodeGenerationTools` not annotated with `@Component`? Explain the design decision.

**A:** `CodeGenerationTools` needs `projectId` bound at construction time:
```java
public CodeGenerationTools(ProjectFileService projectFileService, Long projectId) { ... }
```
If it were a Spring `@Component`, it would be a singleton bean — there would be one instance shared across all requests, with no way to bind a per-request `projectId`. When the tool reads files it must know which project's MinIO bucket prefix to use. Making it a prototype-scoped bean would work (`@Scope("prototype")`) but then you'd need `ApplicationContext.getBean()` with constructor arguments, which is awkward. The chosen pattern — plain Java `new` in the service layer — is simpler: `new CodeGenerationTools(projectFileService, projectId)` is created fresh per request with the correct projectId captured in a closure.

---

### Q: How does Spring AI know about the `readFiles` tool? How is `@Tool` used here?

**A:** Spring AI scans objects passed to `.tools()` for methods annotated with `@Tool`. When `.tools(codeGenerationTools)` is called, Spring AI inspects the `CodeGenerationTools` instance using reflection:
1. Finds `readFiles` annotated with `@Tool(name="read_files", description="...")`
2. Reads the parameter `@ToolParam(description="...")` annotations for schema generation
3. Generates a JSON Schema for the tool: `{ "name": "read_files", "description": "...", "parameters": { "paths": { "type": "array", "items": { "type": "string" } } } }`
4. Includes this schema in the tool definitions sent to the LLM

At inference time when the LLM emits a tool call, Spring AI matches the name `read_files` to the method, deserializes the `paths` JSON array to `List<String>`, and invokes `readFiles(paths)` on the same `CodeGenerationTools` instance.

---

### Q: What does the `readFiles` method actually do step by step?

**A:** For each path in the input list:
1. Strips a leading `/` if present: `path.startsWith("/") ? path.substring(1) : path` — this is necessary because the LLM sometimes outputs absolute paths even when instructed to use relative ones
2. Logs the cleaned path at `INFO` level
3. Calls `projectFileService.getFileContent(projectId, cleanPath)` which reads from MinIO using the hardcoded `"projects"` bucket and key `projectId + "/" + cleanPath`
4. Wraps the content in a delimiter: `"--- START OF FILE: {path} ---\n{content}\n--- END OF FILE ---"`
5. Adds this formatted string to the result list

Returns `List<String>` which Spring AI serializes to JSON to send back as the tool result. The delimiter format is deliberate — it makes it clear to the model where one file's content starts and ends when multiple files are read in a single call.

---

### Q: Could the LLM call `readFiles` with a path that doesn't exist in the project? What happens?

**A:** Yes. The tool description says "Only input the file names present inside the FILE_TREE" but that's advisory — the LLM can hallucinate paths. If a non-existent path is requested, `projectFileService.getFileContent()` calls `minioClient.getObject()` with a key that doesn't exist in MinIO. MinIO throws an `ErrorResponseException` (a subclass of `MinioException`) which is caught in the try-with-resources, logged as an error, and re-thrown as `RuntimeException("Failed to read file content")`. This bubbles back to Spring AI's tool invocation machinery and becomes an error result returned to the model — the model then needs to handle the failure gracefully. There is no input validation on the `paths` list before MinIO is queried.

---

### Q: The system prompt instructs the model to emit a `<tool>` XML tag before calling `read_files`. Why is this needed if Spring AI already handles tool calls natively?

**A:** This is a protocol design choice. Spring AI handles the actual tool call mechanics transparently (via OpenAI-compatible function calling JSON). The `<tool args="...">` XML tag is a **logging/UI signal** emitted by the LLM in its visible text stream, separate from the underlying function-call mechanism. When `LlmResponseParser` processes the full response, it finds `<tool>` tags and creates `ChatEventType.TOOL_LOG` events. These events are stored in the database and served back to the frontend, which can display them in the chat history to show the user "the AI was reading these files." Without this XML tag, the frontend would have no record of tool invocations since they happen inside the Spring AI layer invisibly. It's a separation of concerns: the native tool call does the work; the XML tag creates the audit trail.

---

## 4. AI Generation Service — AiGenerationServiceImpl

### Q: Walk through the entire `streamResponse()` method from the moment the controller calls it.

**A:** Full execution path:

1. **Authorization check**: `@PreAuthorize("@security.canEditProject(#projectId)")` fires before the method body. Spring Security calls `SecurityExpressions.canEditProject(projectId)`, which queries `ProjectMemberRepository` to verify the authenticated user has `EDIT` permission on this project.

2. **User ID resolution**: `authUtil.getCurrentUserId()` reads the `SecurityContextHolder`, extracts the `JwtUserPrincipal`, and returns the `userId` embedded in the JWT.

3. **Chat session**: `createChatSessionIfNotExists(projectId, userId)` builds a `ChatSessionId(projectId, userId)` and looks it up via `chatSessionRepository.findById()`. If absent, loads `Project` and `User` from DB, constructs a new `ChatSession`, and saves it. This is a synchronous blocking DB call on the main servlet thread.

4. **Advisor params**: Builds `Map.of("userId", userId, "projectId", projectId)` to pass context to advisors.

5. **Tools instance**: `new CodeGenerationTools(projectFileService, projectId)` — fresh instance per request.

6. **AtomicReferences**: Three `AtomicReference` containers are created for `startTime`, `endTime`, and `usageRef`. These are needed because Reactor operators run in lambdas which require effectively-final variable references — plain `long` or `Usage` fields declared in the method body cannot be mutated from inside `doOnNext`.

7. **ChatClient chain**: The fluent API is configured (system prompt, user message, tools, advisors) and `.stream().chatResponse()` returns a cold `Flux<ChatResponse>`.

8. **`doOnNext`**: On each emitted `ChatResponse` chunk: appends text to `StringBuilder fullResponseBuffer`, records time-to-first-token in `endTime` on the first non-empty chunk, and captures `Usage` from metadata (only the last chunk typically carries full usage).

9. **`doOnComplete`**: When the stream completes, schedules `finalizeChats()` on `Schedulers.boundedElastic()` — this is a fire-and-forget off-thread DB write.

10. **`doOnError`**: Logs the error with `projectId`.

11. **`.map()`**: Transforms each `ChatResponse` to `StreamResponse(text)` — the DTO that gets serialized to the SSE `data:` field.

The returned `Flux<StreamResponse>` is cold — nothing executes until the controller subscribes (which Spring MVC does automatically when it sees a `Flux` return type).

---

### Q: What is `finalizeChats()` and why is it decoupled from the main stream?

**A:** `finalizeChats()` performs all database persistence after the LLM response is complete. It is scheduled on `Schedulers.boundedElastic()` via `Schedulers.boundedElastic().schedule(...)` inside `doOnComplete`. This means it runs on a separate thread pool, decoupled from the SSE stream's reactive thread. The reason for decoupling is latency: if DB writes ran synchronously in `doOnComplete`, the SSE stream would stay open until all writes finished, which could take hundreds of milliseconds. By offloading to `boundedElastic`, the SSE stream closes as soon as the model finishes streaming, giving the client a faster response.

`finalizeChats()` does: record token usage, save USER ChatMessage, save ASSISTANT ChatMessage (with the hardcoded content bug), parse ChatEvents from the full LLM text, prepend a THOUGHT event, save FILE_EDIT files to MinIO, and save all events to DB.

---

### Q: Why are `AtomicReference` used for `startTime`, `endTime`, and `usageRef`?

**A:** Project Reactor enforces that lambdas passed to operators like `doOnNext` must only reference effectively-final local variables (same as Java 8+ lambdas). You cannot do:
```java
long endTime = 0;
// ...
.doOnNext(response -> { endTime = System.currentTimeMillis(); }) // COMPILE ERROR
```
`AtomicReference<Long>` is a mutable container that is itself effectively final — the reference to the container doesn't change, but the value inside it can. The same applies to `AtomicReference<Usage>`. An alternative would be to use a custom mutable state class passed into the lambda, but `AtomicReference` is idiomatic in Reactor code. Note that while `AtomicReference` provides atomic compare-and-swap semantics, in this code they are simply used as mutable holders — true thread safety from `AtomicReference.compareAndSet()` is not leveraged.

---

### Q: The daily token gate is commented out. What was it supposed to do, and why is commenting it out a problem?

**A:** The commented code is:
```java
// usageService.checkDailyTokensUsage();
```
`UsageServiceImpl.checkDailyTokensUsage()` reads the user's subscription plan, fetches today's `UsageLog`, and throws `ResponseStatusException(429, "Daily limit reached")` if `tokensUsed >= plan.maxTokensPerDay()` for non-unlimited plans. Without this guard, any authenticated user — including free-tier users — can make unlimited LLM calls, incurring unbounded API costs on OpenRouter. This is a billing control bypass, not a minor bug. It means the subscription/billing system is effectively not enforced.

---

### Q: How does `createChatSessionIfNotExists` work, and what is the concurrency risk?

**A:** It looks up a `ChatSession` by composite PK `ChatSessionId(projectId, userId)`. If not found, it creates and saves one. This is a classic check-then-act pattern with a race condition: if two requests from the same user for the same project arrive simultaneously and both find `chatSession == null`, both will attempt to insert a new `ChatSession` with the same composite primary key. One will succeed; the other will throw a `DataIntegrityViolationException` from the unique constraint. The fix is either:
- Add `@Transactional` with a pessimistic lock on the lookup
- Use `INSERT ... ON CONFLICT DO NOTHING` via a custom `@Modifying` query
- Catch `DataIntegrityViolationException` and retry the lookup

---

### Q: `ChatSession` uses an `@EmbeddedId`. Walk through how this composite PK works.

**A:** `ChatSession` has:
```java
@EmbeddedId
private ChatSessionId id;
```
With `@MapsId("projectId")` on the `project` field and `@MapsId("userId")` on the `user` field. `ChatSessionId` is a plain Java class with `Long projectId` and `Long userId`. The problem is that `ChatSessionId` is **missing `@Embeddable`** — it has `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`, `@ToString` from Lombok but no `@Embeddable`. Hibernate requires `@Embeddable` on embedded ID classes to understand it as a mapped type. Contrast with `ProjectMemberId` which correctly has `@Embeddable`. This is a known bug documented in `CLAUDE.md`. In practice Hibernate might tolerate it in some versions or with `ddl-auto=update`, but it's technically undefined behavior and will likely cause mapping issues in production.

---

### Q: How does `THOUGHT` event get its `sequenceOrder=0` when parsing assigns orders starting at 1?

**A:** `LlmResponseParser.parseChatEvents()` initializes `orderCounter = 1` and increments it for each parsed event, so parsed events get orders 1, 2, 3, etc. In `finalizeChats()`, after parsing is complete:
```java
chatEventList.add(0, ChatEvent.builder()
        .type(ChatEventType.THOUGHT)
        .sequenceOrder(0)
        .content("Thought for " + duration + "s")
        .build());
```
The THOUGHT event is inserted at index 0 of the list with `sequenceOrder=0`. This places it first both in the list and in sort order. `duration` is computed as `(endTime - startTime) / 1000` — the time in seconds from the moment `streamResponse()` started to when the first non-empty text chunk arrived, which approximates the model's "thinking" latency (time-to-first-token).

---

## 5. LLM Response Parsing — LlmResponseParser

### Q: Explain the regex `GENERIC_TAG_PATTERN` in detail. What does each group capture?

**A:** The pattern is:
```
(<(message|file|tool)([^>]*)>)([\s\S]*?)(</\2>)
```
With flags `CASE_INSENSITIVE | DOTALL`.

- `(<(message|file|tool)([^>]*)>)` — Group 1: the complete opening tag
  - `(message|file|tool)` — Group 2: the tag name (capturing group for backreference)
  - `([^>]*)` — Group 3: everything between the tag name and `>`, i.e. attributes like ` path="src/App.tsx"` (zero or more non-`>` chars)
- `([\s\S]*?)` — Group 4: the tag content (lazy match, stops at first closing tag). `[\s\S]` matches any character including newlines, which `.` alone would not without DOTALL. DOTALL is also set as a redundant safety.
- `(</\2>)` — Group 5: the closing tag, using `\2` as a backreference to the exact tag name captured in Group 2 — ensures `</file>` only matches the opening `<file>` tag, not a stray `</message>` tag.

The lazy `*?` on the content group is critical: it ensures the regex matches the **shortest** possible content between tags, so two consecutive `<message>` tags don't get merged into one giant match.

---

### Q: What is `ATTRIBUTE_PATTERN` and why is it a separate regex?

**A:** `ATTRIBUTE_PATTERN = Pattern.compile("(path|args)=\"([^\"]+)\"")`

It captures attribute key-value pairs from Group 3 of the main match. Group 1 captures the attribute name (`path` or `args`), Group 2 captures the value (everything between quotes, excluding the quote character via `[^\"]+`). It is separate because attributes are optional and variable — not every tag has attributes. Running `ATTRIBUTE_PATTERN.matcher(attributes)` where `attributes` is the Group 3 string extracted from `GENERIC_TAG_PATTERN` is cleaner than trying to handle optional attributes in one complex regex. The extracted attributes are stored in a `HashMap<String, String>` keyed by attribute name.

---

### Q: What are the failure modes of the regex-based parser?

**A:** Several:
1. **Nested tags**: `<message>text with a <file> in it</message>` — the lazy match stops at the first `</message>` it finds, potentially cutting off content if the LLM embeds one tag inside another in a way not expected.
2. **Unclosed tags**: If the LLM outputs `<file path="src/App.tsx">` without a closing `</file>`, the regex finds no match for that file. Its content is lost silently.
3. **Streaming splits**: The regex runs on the full accumulated `fullResponseBuffer.toString()` in `finalizeChats()`, not on streaming chunks. This is correct — parsing on incomplete chunks would be fragile. But it means no events are available until the entire response completes.
4. **Attribute values with quotes**: If a file path contained a quote (e.g., `path="src/it's.tsx"`), the `[^\"]+` attribute pattern would break at the quote.
5. **Case sensitivity**: `CASE_INSENSITIVE` is set so `<File>` and `<FILE>` would match, but this could match unintended capitalized words if the LLM deviates from the protocol.
6. **NullPointerException risk**: If `usage` is `null` (e.g., the model returned no usage metadata), `finalizeChats()` calls `usage.getPromptTokens()` without a null check, throwing an NPE. There is a `if (usage != null)` guard for `recordTokenUsage()` but not for the ChatMessage token fields.

---

### Q: How does the parser handle the `file` tag case? Is there anything notable?

**A:** For `"file"` tags:
```java
case "file" -> {
    builder.type(ChatEventType.FILE_EDIT);
    builder.filePath(attrMap.get("path")); // Required for files
    // builder.content(null);
}
```
The commented-out `builder.content(null)` line is interesting — it was apparently considered whether to clear the content for file events, but was left as-is, meaning the full file content is stored in `ChatEvent.content`. Since `content` is `@Column(columnDefinition = "text")`, the DB column can hold arbitrarily large text. The file content could be thousands of lines of code stored directly in PostgreSQL. This is an architectural choice: file content is stored redundantly in both MinIO (the canonical source) and PostgreSQL `chat_events.content`. This makes chat history retrieval fast (no MinIO roundtrip) but increases DB storage significantly.

---

### Q: How does `sequenceOrder` work and why does it matter for the frontend?

**A:** `sequenceOrder` is an integer assigned to each `ChatEvent` as it's parsed, starting at `1` (with THOUGHT manually inserted at `0`). It represents the chronological ordering of events within a single assistant response. The `ChatMessage` entity has `@OrderBy("sequenceOrder ASC")` on its `events` list, ensuring they're always retrieved in order from the DB. This ordering matters for the frontend chat UI: the user sees events in sequence — first the THOUGHT, then tool logs, then the planning message, then file edit events, then the completion message. Without `sequenceOrder`, events could be returned in DB insertion order (which might differ due to `saveAll()` batching) or random order.

---

## 6. System Prompt Design — PromptUtils

### Q: How is `CODE_GENERATION_SYSTEM_PROMPT` constructed and what is the `LocalDateTime.now()` bug?

**A:** The prompt is a static `final String` field initialized at class load time:
```java
public final static String CODE_GENERATION_SYSTEM_PROMPT = """
        ...
        Time now: """ + LocalDateTime.now() + """
        Stack: React 18 + TypeScript...
```
`LocalDateTime.now()` is evaluated exactly **once** when the `PromptUtils` class is first loaded by the JVM. After that, the timestamp is frozen for the entire lifetime of the application. If the server runs for days, the "Time now" in every prompt will be the server's startup time, not the actual current time. This means the LLM is given stale temporal context. The fix is to make it a method that interpolates `LocalDateTime.now()` at call time, or to embed the date in the per-request system prompt rather than as a static string.

---

### Q: What is the "ATOMIC UPDATES" rule and why does it exist?

**A:** The system prompt instructs: "You may output a `<file path="...">` EXACTLY ONCE per response." This constraint exists because the parser stores file content in DB and saves it to MinIO in `finalizeChats()`. If the LLM output the same file twice in one response (perhaps "re-tweaking" it after seeing it), the second write would overwrite the first in MinIO (last-write-wins). The regex parser's lazy match would find both occurrences as separate `FILE_EDIT` events, both would be written to MinIO, and both would be saved in the DB's `chat_events` table. The ATOMIC UPDATES rule prevents this inconsistency by telling the model not to re-output files it has already written in the same turn.

---

### Q: Why does the prompt require the model to emit `<tool args="...">` before calling `read_files`?

**A:** As explained in Section 3, this is a UI logging requirement, not a technical one. The prompt instructs: "MUST be called before a tool call of read_files tool." In the tool call sequence the flow is: LLM emits `<tool args="src/App.tsx">...</tool>` in the visible text → Spring AI simultaneously processes the function call in the background → LLM emits the result. The `<tool>` tag in the text stream is captured by `doOnNext` into `fullResponseBuffer`, later parsed into `ChatEventType.TOOL_LOG` events, and displayed in the frontend as "Reading App.tsx..." style indicators. This gives users visibility into the model's reasoning process.

---

### Q: The prompt has `THINK → PLAN → EXECUTE → STOP` sequence. How does this map to ChatEvent types?

**A:** The mapping:
- **THINK** phase: The LLM uses `<tool>` to read files — creates `TOOL_LOG` events
- **PLAN** phase: The LLM outputs `<message phase="planning">` — creates `MESSAGE` events
- **EXECUTE** phase: The LLM outputs `<file path="...">` tags — creates `FILE_EDIT` events
- **STOP** phase: Final `<message phase="completed">` — creates another `MESSAGE` event
- **Before all of these**: A `THOUGHT` event is manually prepended at `sequenceOrder=0` representing time-to-first-token

The `phase` attribute on `<message>` tags (e.g., `start`, `planning`, `completed`) is parsed by `ATTRIBUTE_PATTERN` but the attribute name `phase` is not in the pattern `(path|args)="..."` — so `phase` attributes are silently ignored. Only `path` and `args` attributes are captured.

---

## 7. Server-Sent Events & Streaming

### Q: How does the SSE endpoint work? What makes it stream?

**A:** The endpoint in `ChatController`:
```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<StreamResponse>> streamChat(@RequestBody ChatRequest request) {
    return aiGenerationService.streamResponse(request.message(), request.projectId())
            .map(data -> ServerSentEvent.<StreamResponse>builder().data(data).build());
}
```

Three things make it stream:
1. `produces = MediaType.TEXT_EVENT_STREAM_VALUE` — tells Spring MVC to set the `Content-Type: text/event-stream` response header and keep the connection open
2. `Flux<...>` return type — Spring MVC detects `Flux` via `ReactiveAdapterRegistry` and subscribes to it, writing each emitted element to the response immediately rather than buffering the entire collection
3. `ServerSentEvent<StreamResponse>` wrapping — each element is written in the SSE wire format: `data: {"text":"..."}\n\n`

This works in Spring MVC (servlet stack) without requiring Spring WebFlux. Spring MVC has had `Flux` return type support since Spring 5 via its `ReactiveAdapterRegistry`.

---

### Q: What does each SSE frame look like on the wire?

**A:** Each `ServerSentEvent<StreamResponse>` element is serialized as:
```
data: {"text":"partial text chunk here"}\n\n
```
The `StreamResponse` record is serialized to JSON by Jackson: `record StreamResponse(String text)` becomes `{"text":"..."}`. The `ServerSentEvent.builder().data(data).build()` wraps it with the `data:` prefix. The double newline `\n\n` is the SSE protocol's event delimiter, signaling to the client that this event is complete and the next one follows. Spring writes each element to the response output stream as soon as it's emitted from the `Flux`, giving real-time token-by-token streaming to the browser.

---

### Q: A frontend connects with `EventSource`. What happens if the connection drops mid-stream?

**A:** The browser's `EventSource` API automatically reconnects after a configurable retry delay (default 3 seconds). However, SSE reconnection sends the last seen event ID via the `Last-Event-ID` header. Since no `id` is set on the `ServerSentEvent` objects in this code (`.builder().data(data).build()` — no `.id()` call), the reconnection header has no meaningful value. The server has no checkpoint mechanism: on reconnect a new `streamResponse()` call is made, starting a completely fresh LLM generation. This means:
- The user may see duplicate content (if the frontend appended partial streaming before disconnect and then the same content starts again)
- The model re-incurs full input token costs
- A new `ChatSession` lookup occurs

A proper fix would assign incrementing IDs to SSE events and implement a resume-from-offset mechanism.

---

### Q: Why use `@PostMapping` for a streaming endpoint instead of `@GetMapping`? What are the implications?

**A:** The endpoint receives a `@RequestBody ChatRequest(String message, Long projectId)`. HTTP GET requests do not conventionally carry a request body (and many clients/proxies strip it). POST is the correct HTTP verb here since a body is required to pass the message and project ID. The implication is that this streaming endpoint is not compatible with the browser's native `EventSource` API, which only supports GET requests. The frontend must use `fetch()` with streaming body reading or a library like `@microsoft/fetch-event-source` that supports POST SSE. This is a deliberate design choice that accepts the EventSource incompatibility in exchange for having a clean request body structure.

---

### Q: How does Spring MVC handle the `Flux` return type without WebFlux?

**A:** Spring MVC integrates with Project Reactor via `ReactiveAdapterRegistry` (added in Spring 5). When a controller method returns a `Flux`, Spring MVC:
1. Detects the reactive return type through `ReactiveAdapterRegistry`
2. Does not subscribe immediately — it configures a `ResponseBodyResultHandler` that wraps the subscription
3. Uses a `MediaTypeMessageWriter` configured for `text/event-stream` which knows to write SSE format
4. For each element emitted by the `Flux`, writes it to an `HttpServletResponse` using async servlet dispatch (`AsyncContext`). The thread is released between emissions — the servlet container's async support means no thread is held while waiting for the next Reactor emission
5. Completes the response when the `Flux` completes

This is importantly **not** the same as WebFlux's non-blocking I/O. The underlying servlet container (Tomcat/Undertow/Jetty) still uses blocking I/O at the socket level — the reactivity here is only about not blocking a servlet thread between emissions.

---

### Q: `DispatcherType.ASYNC` is permitted in `WebSecurityConfig`. Why is this needed for SSE?

**A:** 
```java
.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
```
When Spring MVC processes async requests (including Flux-based streaming), Tomcat internally makes a secondary `ASYNC` dispatch after the initial `REQUEST` dispatch completes. Without this permission, Spring Security would intercept the `ASYNC` dispatch and attempt to re-authenticate it, which would fail (the `SecurityContext` may not be propagated to the async thread). Permitting `ASYNC` dispatches bypasses security for these internal re-dispatches. `DispatcherType.ERROR` is similarly permitted for error-forwarding dispatches to `/error`.

---

## 8. Project Reactor — Reactive Programming

### Q: What reactive operators are used in `streamResponse()` and what does each one do?

**A:** Four operators are chained on the `Flux<ChatResponse>`:

1. **`doOnNext(response -> {...})`**: A side-effect operator that runs a `Consumer<T>` for every element emitted, without modifying the element. Used here to: append text to `fullResponseBuffer`, record time-to-first-token in `endTime`, capture `Usage`. The stream element passes through unchanged. Important: `doOnNext` is fire-and-forget — if it throws, the exception propagates downstream as an error signal.

2. **`doOnComplete(() -> {...})`**: A side-effect operator that runs a `Runnable` exactly once when the upstream signals completion (no more elements). Used to schedule `finalizeChats()`. Runs on whichever thread signals completion (the Reactor scheduler thread used by Spring AI's streaming). Hence the `Schedulers.boundedElastic().schedule()` to move the blocking work off that thread.

3. **`doOnError(error -> {...})`**: A side-effect operator that runs when the upstream signals an error. Does not handle (recover from) the error — the error signal continues propagating downstream. Used for logging with `projectId` context.

4. **`map(response -> new StreamResponse(...))`**: A transformation operator. For each `ChatResponse`, extracts the text content and wraps it in a `StreamResponse` record. Null-safe: `text != null ? text : ""`. This is the only operator that changes the element type (from `ChatResponse` to `StreamResponse`).

---

### Q: What is `Schedulers.boundedElastic()` and why is it used for `finalizeChats()`?

**A:** `Schedulers.boundedElastic()` is a Reactor scheduler backed by a bounded thread pool optimized for blocking I/O operations. It dynamically grows (up to a configured bound, defaulting to `10 * CPU cores`) and supports work queuing. It is the recommended scheduler for blocking operations that must not run on Reactor's event loop threads (like `Schedulers.parallel()` which has a fixed CPU-core-count pool and would be starved by blocking calls).

`finalizeChats()` performs blocking operations: two JDBC writes (`chatMessageRepository.save()`), MinIO PUT calls (`projectFileService.saveFile()`), and a `chatEventRepository.saveAll()`. Running these on the reactive thread that delivers SSE chunks would block that thread from delivering other in-flight stream elements or responses, causing latency spikes or deadlocks. `boundedElastic` isolates the blocking work.

---

### Q: What is the risk of using `Schedulers.boundedElastic().schedule(...)` for `finalizeChats()`?

**A:** Two risks:

1. **Pool exhaustion**: `boundedElastic` has a bounded queue. If many requests complete simultaneously and each schedules a `finalizeChats()` task, the queue can fill up. New tasks are then dropped (or depending on configuration, the `schedule()` call throws). Dropped tasks mean token usage is not recorded, chat messages are not saved, and file writes to MinIO don't happen — silently lost writes with no error surfaced to the user.

2. **No error handling**: The `finalizeChats()` call is fire-and-forget. If it throws (e.g., DB unavailable, MinIO connection refused, NPE from null `usage`), the exception is swallowed by the scheduler. There is no retry mechanism, no dead-letter queue, no alerting. The user's SSE stream completed successfully from their perspective, but their chat history is missing.

Solutions: wrap in `Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic()).subscribe(null, error -> log.error(...))`, add retry logic, or use a persistent task queue.

---

### Q: What is the difference between `doOnNext`, `doOnComplete`, `doOnError` versus `map`, `flatMap`, `filter`?

**A:**
- **Side-effect operators** (`doOnNext`, `doOnComplete`, `doOnError`, `doOnSubscribe`, `doFinally`): Run callbacks purely for side effects. They do not transform the stream elements. The element type and value are unchanged. They are transparent to the downstream pipeline.
- **Transformation operators** (`map`, `flatMap`, `switchMap`, `concatMap`): Transform stream elements. `map` is synchronous 1:1 transformation. `flatMap` maps each element to a new `Publisher` and merges them concurrently. `concatMap` maps to Publishers sequentially.
- **Filtering operators** (`filter`, `take`, `skip`, `distinct`): Control which elements proceed downstream.

In `streamResponse()`, the use of `doOnNext` for buffering and `map` for transformation is idiomatic — side effects are explicitly separated from the data transformation pipeline, making the intent clear.

---

### Q: What is a cold vs. hot Flux? Which one does `streamResponse()` return?

**A:** A **cold** `Flux` starts producing elements only when a subscriber subscribes — each subscription gets its own independent execution. A **hot** `Flux` produces elements regardless of subscribers (e.g., an event bus); late subscribers miss earlier emissions.

`streamResponse()` returns a **cold** `Flux`. The ChatClient's `.stream().chatResponse()` creates a cold Flux — the HTTP request to OpenRouter is only made when someone subscribes. Spring MVC subscribes when it processes the controller method's return value. If no one subscribes (e.g., in tests without subscription), no LLM call is made. This is the correct behavior for HTTP request-response patterns.

---

### Q: Could the `fullResponseBuffer` StringBuilder cause thread safety issues?

**A:** In theory, a `StringBuilder` is not thread-safe. However, in this code the reactive operators `doOnNext` and the `doOnComplete` lambda accessing `fullResponseBuffer` are guaranteed by Reactor to execute serially — Reactor provides sequential delivery guarantees per subscriber. `doOnNext` calls are serialized, so only one thread writes to `fullResponseBuffer` at a time. The only potential issue is the transition between `doOnNext` (which appends) and `doOnComplete` → `finalizeChats()` which reads the buffer. Since `doOnComplete` fires after all `doOnNext` calls on the same sequence are complete, by the time `fullResponseBuffer.toString()` is called in `finalizeChats()`, no further appends can occur. Thread visibility could be an issue (the write thread's cache not being visible to the `boundedElastic` thread), but `Schedulers.boundedElastic().schedule()` uses Java's `ScheduledExecutorService` which provides a happens-before relationship from the scheduling point.

---

### Q: What happens if the LLM takes too long? Is there a timeout?

**A:** There is no explicit timeout configured in `AiGenerationServiceImpl`. No `.timeout()` operator is applied to the `Flux`. No timeout is set on the `ChatClient.Builder`. The response will stream until the LLM finishes or until the client disconnects. If the LLM hangs indefinitely (a stuck generation), the `doOnComplete` lambda never fires, `finalizeChats()` is never scheduled, and the servlet's async context holds open. In production this should be handled with `.timeout(Duration.ofMinutes(2))` which would signal an error after 2 minutes, trigger `doOnError` logging, and close the connection.

---

## 9. MinIO Storage Patterns

### Q: How is `MinioClient` configured and what credentials does it use?

**A:** `StorageConfig` uses `@ConfigurationProperties(prefix = "minio")` combined with `@Configuration`:
```java
@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
public class StorageConfig {
    private String url;
    private String accessKey;
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```
Spring Boot binds `minio.url`, `minio.access-key` (converted from `accessKey` via relaxed binding), and `minio.secret-key` from `application.properties`. The values are `http://localhost:9000`, `minioadmin`, `minioadmin123` — these are the MinIO Docker Compose defaults. In production these must be overridden via environment variables or Kubernetes secrets.

---

### Q: What are the two MinIO buckets and what is stored in each?

**A:**
- **`starter-projects`**: Read-only template bucket. Contains the Vite + React 18 + TypeScript + Tailwind CSS + daisyUI starter template under the prefix `react-vite-tailwind-daisyui-starter/`. Files are never written here by the application — only read during project initialization via server-side copy.
- **`projects`**: Per-project mutable bucket. Object keys follow the pattern `{projectId}/{relative/path}`. For example, `42/src/App.tsx` is the `App.tsx` file for project 42. Files are written here by `saveFile()` and read by `getFileContent()`.

---

### Q: Explain the critical bucket name bug in `ProjectFileServiceImpl`.

**A:** The class has two ways to reference the project bucket:
```java
@Value("${minio.project-bucket}")
private String projectBucket;  // Used in saveFile() for PutObjectArgs

private static final String BUCKET_NAME = "projects"; // Used in getFileContent() for GetObjectArgs
```
Both currently point to `"projects"` since `minio.project-bucket=projects` in `application.properties`. But they are independent references. If a deployment changes the environment variable `minio.project-bucket` to `"production-projects"` (or any other name), `saveFile()` will write to `"production-projects"` while `getFileContent()` will read from `"projects"`. The result: files written by AI generation are invisible to reads, appearing as if the AI's edits were lost. This fails silently — no exception until `getFileContent()` gets a 404 from MinIO for an object that was written to a different bucket.

The fix: remove the hardcoded `BUCKET_NAME` constant and use `projectBucket` (the `@Value` field) in both methods.

---

### Q: How does `getFileContent()` work? What are the failure modes?

**A:** 
```java
try (InputStream is = minioClient.getObject(
        GetObjectArgs.builder()
                .bucket(BUCKET_NAME)
                .object(projectId + "/" + path)
                .build())) {
    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    return new FileContentResponse(path, content);
} catch (Exception e) {
    log.error(...);
    throw new RuntimeException("Failed to read file content", e);
}
```
Uses try-with-resources to ensure the `InputStream` is closed. `readAllBytes()` loads the entire file into memory — for large source files this is fine, but for binary assets (images accidentally committed to a project) this could consume significant heap. Failure modes:
1. Object not found: MinIO throws `ErrorResponseException` → caught → `RuntimeException` thrown → bubbles up to the LLM tool call as a tool error
2. Network error: `IOException` → same path
3. Binary file: `readAllBytes()` succeeds but the bytes may not be valid UTF-8 → `new String(bytes, UTF_8)` replaces unmappable characters with the replacement character rather than throwing, potentially corrupting binary content silently

---

### Q: How does `saveFile()` determine the content type? Why does it matter?

**A:** `determineContentType(path)` uses a two-stage approach:
1. `URLConnection.guessContentTypeFromName(path)` — Java's built-in MIME detector based on file extension (reads from the JVM's `content-types.properties`)
2. Falls back for extensions not in the JDK's default mapping: `.jsx`, `.ts`, `.tsx` → `text/javascript`; `.json` → `application/json`; `.css` → `text/css`
3. Default fallback: `text/plain`

Content type matters because MinIO stores it as object metadata and returns it in `Content-Type` response headers when the file is fetched directly. If a browser fetches a `.tsx` file and the content type is `text/plain`, the browser displays it as plain text. If it's `text/javascript`, the browser may try to execute it. For a code editor serving files to a browser-based IDE, having correct MIME types ensures the browser's file fetcher/caching behaves correctly.

---

### Q: Why does `initializeProjectFromTemplate` use `copyObject` instead of downloading and re-uploading?

**A:** `minioClient.copyObject(CopyObjectArgs...)` is a **server-side copy** — MinIO copies data internally within its storage cluster without transferring bytes through the application server. The advantages:
1. **Bandwidth**: No data travels from MinIO → application → MinIO. For a full Vite project with dozens of files, this saves significant data transfer.
2. **Speed**: Server-side copy is much faster than download + re-upload, especially for binary assets or many small files.
3. **Memory**: The application needs no buffer for file content — it just sends metadata (source key, destination key).
4. **Atomicity**: Each file copy is an atomic MinIO operation.

The trade-off is that both source and destination must be in the same MinIO instance. If the template were on a different storage system (e.g., a public S3 bucket), server-side copy would not be possible.

---

### Q: What is the `PutObjectArgs` `-1` for the multipart threshold? What does it mean?

**A:** In `saveFile()`:
```java
PutObjectArgs.builder()
        .stream(inputStream, contentBytes.length, -1)
```
The third argument to `.stream()` is the part size for multipart upload. A value of `-1` means "use the MinIO SDK default" — the SDK will automatically determine whether to use a single-part upload or multipart upload based on content size. For files smaller than 5 MB, MinIO uses a single PutObject HTTP request. For larger files, it automatically switches to multipart upload. This is the correct default for text files (source code is almost always well under 5 MB), and it means the application doesn't need to implement manual multipart handling.

---

### Q: `getFileTree()` queries PostgreSQL, not MinIO. Why is this architectural choice made?

**A:** File metadata (path, project association, timestamps) is stored in the `project_files` PostgreSQL table. `getFileTree()` does `projectFileRepository.findByProjectId(projectId)` — a simple indexed DB query. This is intentional because:
1. **Performance**: Listing MinIO objects with `listObjects()` requires an HTTP round trip to MinIO for every call. A DB query for the file tree is faster, especially with a DB connection pool.
2. **Rich metadata**: The DB record stores `createdAt`, `updatedAt`, the `minioObjectKey`, and the project relationship — information MinIO doesn't have.
3. **Consistency**: The file tree used for the LLM context should be consistent with what was last saved, not a live MinIO listing that might include partially-written objects.

The trade-off is that the DB and MinIO can drift out of sync: if MinIO is modified directly (e.g., manual file upload), the DB doesn't know. `ProjectFile` records without corresponding MinIO objects, or MinIO objects without `ProjectFile` records, would cause errors.

---

### Q: How are new project files handled in `saveFile()`? Is it an upsert?

**A:** Yes, it is an upsert pattern implemented with `orElseGet`:
```java
ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, cleanPath)
        .orElseGet(() -> ProjectFile.builder()
                .project(project)
                .path(cleanPath)
                .minioObjectKey(objectKey)
                .createdAt(Instant.now())
                .build());
file.setUpdatedAt(Instant.now());
projectFileRepository.save(file);
```
If the `ProjectFile` row exists (file was created earlier), it updates `updatedAt`. If it doesn't exist, a new `ProjectFile` entity is built and `save()` inserts it. In both cases, MinIO always receives a `putObject()` call with the full content — MinIO's PUT is itself an upsert (creates or overwrites the object). This two-step DB upsert is not atomic. In a concurrent scenario where two saves for the same path race, both could enter `orElseGet` and try to insert a new row. The second `save()` would violate any unique constraint on `(project_id, path)` — but looking at the `ProjectFile` entity, there is no such unique constraint defined, so both inserts would succeed, leaving duplicate rows.

---

## 10. Template Initialization — ProjectTemplateServiceImpl

### Q: Walk through the complete project initialization flow.

**A:** `initializeProjectFromTemplate(Long projectId)`:

1. **Load project**: `projectRepository.findById(projectId).orElseThrow(...)` — fetches the `Project` entity (must already exist in DB before this is called)

2. **List template files**: 
```java
minioClient.listObjects(ListObjectsArgs.builder()
        .bucket("starter-projects")
        .prefix("react-vite-tailwind-daisyui-starter/")
        .recursive(true)
        .build())
```
Returns an `Iterable<Result<Item>>` — lazy enumeration of all object metadata under the template prefix. `recursive=true` traverses subdirectories.

3. **For each file**:
   a. `item.objectName()` gets the full object key (e.g., `react-vite-tailwind-daisyui-starter/src/App.tsx`)
   b. `replaceFirst(TEMPLATE_NAME + "/", "")` strips the template prefix to get the clean relative path (`src/App.tsx`)
   c. Builds destination key: `projectId + "/" + cleanPath` (e.g., `42/src/App.tsx`)
   d. `minioClient.copyObject(...)` performs the server-side copy from `starter-projects` to `projects` bucket
   e. Builds a `ProjectFile` entity with path, minioObjectKey, timestamps

4. **Bulk insert**: `projectFileRepository.saveAll(filesToSave)` — single batch INSERT for all file metadata records

5. **Error handling**: Any exception wraps in `RuntimeException("Failed to initialize project from template")` — there is no partial-success handling. If file #37 fails to copy, the first 36 are already in MinIO but the transaction may or may not have committed depending on whether `@Transactional` is on the service layer. There is no `@Transactional` annotation on this method, so each `copyObject()` call is independent and not rollbackable.

---

### Q: What is the risk of having no `@Transactional` on `initializeProjectFromTemplate()`?

**A:** Without `@Transactional`, the `projectFileRepository.saveAll()` call still runs in a transaction (Spring Data JPA's default repository method transaction), but the `copyObject()` calls to MinIO are not transactional at all. If the method fails halfway through (network error, MinIO timeout, or an exception from `result.get()`), the state is:
- Some files are copied to MinIO `projects` bucket
- Some are not
- `projectFileRepository.saveAll()` may not have been reached yet (if the exception occurred in the loop)
- Even if `saveAll()` was reached, it only saves the entities in `filesToSave`, which only includes files processed before the exception

The project ends up in a half-initialized state with an incomplete file tree and partial MinIO objects. There is no cleanup or compensation logic. Adding `@Transactional` on the method helps only with the DB portion — MinIO operations are not transactional by nature and cannot be rolled back.

---

### Q: The `listObjects` call returns `Iterable<Result<Item>>`, not a `List`. How is this handled?

**A:** MinIO's `listObjects()` is a lazy iterator that pages through MinIO's listing API internally. Each call to `result.get()` can throw a checked exception (`Exception`) since it wraps the MinIO HTTP response. The for-each loop `for (Result<Item> result : results)` calls `result.get()` which is what throws inside the loop body. This is why the entire method is wrapped in `try { ... } catch (Exception e)`. The lazy iterator pattern means not all objects are loaded into memory at once — for projects with thousands of files, this prevents OOM errors.

---

## 11. Known Issues & Design Trade-offs

### Q: List all known bugs in the codebase and explain each one.

**A:** From `CLAUDE.md` and code inspection:

1. **Hardcoded user ID in `GET /api/auth/me`**: Returns `userId = 1L` always. Every user sees user 1's profile when hitting `/api/auth/me`. (AuthController issue)

2. **`GET /api/plans` returns empty list**: `PlanServiceImpl` never calls `PlanRepository`. The plans feature is stubbed.

3. **`ChatSessionId` missing `@Embeddable`**: `ChatSessionId` has `@AllArgsConstructor`, `@NoArgsConstructor`, etc., but no `@Embeddable`. Hibernate requires this annotation for embedded ID classes. Compare with `ProjectMemberId` which has it correctly.

4. **ASSISTANT message content hardcoded**: `ChatMessage.content = "Assistant Message here..."` — the actual LLM response text is stored only in `ChatEvent.content` records, not in the parent `ChatMessage.content` field.

5. **Bucket name divergence**: `getFileContent()` uses `BUCKET_NAME = "projects"` constant while `saveFile()` uses `@Value("${minio.project-bucket}")`. If the env var changes, reads and writes silently diverge.

6. **Daily token gate commented out**: `usageService.checkDailyTokensUsage()` is commented out in `streamResponse()`. Token limits are not enforced.

7. **`Preview` entity missing `@Entity`**: The `Preview` class has no `@Entity` annotation and no repository. The live preview feature is entirely unimplemented.

8. **`LocalDateTime.now()` in static prompt**: Captured at class load time, never updated. The LLM receives a stale timestamp.

9. **`usage` null safety in `finalizeChats()`**: `usage.getPromptTokens()` is called without a null check when building the USER ChatMessage, even though there's a null guard for `recordTokenUsage()`. An NPE here crashes `finalizeChats()` silently on `boundedElastic`.

10. **Race condition in `createChatSessionIfNotExists()`**: Non-atomic check-then-insert can create duplicate sessions under concurrent requests.

11. **`finalizeChats()` failures are silent**: Scheduled on `boundedElastic()` with no error propagation to the caller.

---

### Q: How would you fix the hardcoded ASSISTANT message content bug?

**A:** The fix is to pass `fullText` (the entire buffered LLM response) as the `content` of the ASSISTANT `ChatMessage` instead of the hardcoded string:
```java
// Instead of:
.content("Assistant Message here...")
// Use:
.content(fullText)
```
However, the `content` column is `@Column(columnDefinition = "text")` which maps to PostgreSQL `TEXT` (unlimited length), so long responses won't overflow. The design choice to store structured content in `ChatEvent` records rather than as a raw string is valid for the frontend (structured rendering of message/file/tool blocks), but the parent `ChatMessage.content` field should still hold the raw full text for debugging, search, and API completeness.

---

### Q: How would you fix the `FileTreeContextAdvisor` N+1 DB query problem for a multi-turn conversation?

**A:** The root issue is that the advisor queries DB on every LLM call, including the intermediate calls made when tool calls trigger a second model request. The cleanest fix for the current architecture:

```java
// In AiGenerationServiceImpl.streamResponse():
FileTreeResponse fileTree = projectFileService.getFileTree(projectId);
Map<String, Object> advisorParams = Map.of(
        "userId", userId,
        "projectId", projectId,
        "fileTree", fileTree  // pre-loaded
);
```

Then in `FileTreeContextAdvisor.adviseStream()`, read from context instead of querying:
```java
FileTreeResponse fileTree = (FileTreeResponse) context.getOrDefault("fileTree", null);
if (fileTree == null) {
    fileTree = projectFileService.getFileTree(projectId); // fallback
}
```

This loads the file tree once per user request regardless of how many tool-call round trips the model makes.

---

### Q: What would be the ideal retry strategy for `finalizeChats()`?

**A:** The current fire-and-forget approach should be replaced with:

1. **Reactive pipeline**: Use `Mono.fromRunnable(() -> finalizeChats(...)).subscribeOn(Schedulers.boundedElastic()).retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).filter(e -> e instanceof TransientDataAccessException))` for transient DB failures.

2. **Dedicated retry for MinIO**: MinIO writes in `projectFileService.saveFile()` should have their own retry with exponential backoff.

3. **Dead-letter logging**: If all retries fail, log a structured error with enough context (userId, projectId, full LLM text) to replay the operation manually.

4. **Outbox pattern**: For production robustness, write a "pending finalization" record to DB atomically with a minimal initial save, then process it asynchronously with guaranteed delivery.

---

### Q: The `@PreAuthorize` annotation calls `@security.canEditProject(#projectId)`. How does this work end-to-end?

**A:** `@EnableMethodSecurity` in `WebSecurityConfig` enables Spring Security's method-level security processing. When `streamResponse(String userMessage, Long projectId)` is called:

1. Spring Security's AOP proxy intercepts the call
2. Evaluates the SpEL expression `@security.canEditProject(#projectId)`:
   - `@security` resolves to the bean named `"security"` — `SecurityExpressions` has `@Component("security")`
   - `#projectId` resolves to the method parameter value via Spring EL parameter name binding
3. `SecurityExpressions.canEditProject(projectId)` calls `hasPermission(projectId, ProjectPermission.EDIT)`
4. `hasPermission()` calls `authUtil.getCurrentUserId()` → reads `JwtUserPrincipal` from `SecurityContextHolder`
5. Queries `projectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId)` → returns `Optional<ProjectRole>`
6. Maps to `role.getPermissions().contains(ProjectPermission.EDIT)` — `ProjectRole.EDITOR` and `ProjectRole.OWNER` have `EDIT` permission; `VIEWER` does not
7. If `false` or the user is not a member, Spring Security throws `AccessDeniedException` → `GlobalExceptionHandler` catches it → 403 response

---

## 12. Cross-Cutting Concerns

### Q: How does the system handle the case where a user sends a message to a project they just created with no prior chat session?

**A:** `createChatSessionIfNotExists()` handles this:
1. Builds `ChatSessionId(projectId, userId)`
2. `chatSessionRepository.findById(chatSessionId)` returns empty
3. Loads the `Project` entity (must exist — checked by `@PreAuthorize` which verified membership)
4. Loads the `User` entity from `userRepository.findById(userId)`
5. Builds `ChatSession.builder().id(chatSessionId).project(project).user(user).build()`
6. `chatSessionRepository.save(chatSession)` — inserts new row with `@CreationTimestamp` and `@UpdateTimestamp` fields auto-populated by Hibernate

Note: the `ChatSession` is created before the LLM call. This means if the LLM call fails (e.g., OpenRouter is down), the `ChatSession` still exists in DB. On the next request, `findById` finds it and reuses it. This is correct behavior — the session represents "this user has chatted with this project", not "this user has successfully chatted."

---

### Q: How does `recordTokenUsage()` work and what is the database design for it?

**A:** `UsageServiceImpl.recordTokenUsage(Long userId, int actualTokens)`:
1. Gets `LocalDate.now()` — today's date
2. `usageLogRepository.findByUserIdAndDate(userId, today)` — looks for an existing daily log
3. If not found: `createNewDailyLog()` inserts a new `UsageLog` with `tokensUsed=0`
4. Adds `actualTokens` to `todayLog.getTokensUsed()`
5. `usageLogRepository.save(todayLog)` — UPDATE or INSERT

`UsageLog` has a `@UniqueConstraint(columnNames = {"user_id", "date"})` ensuring one row per user per day. The pattern is an additive upsert — tokens accumulate over the day. The `findOrCreate + update` pattern is a race condition: two concurrent calls for the same user on the same day could both call `createNewDailyLog()` and both try to insert a row with the same `(user_id, date)`, causing a `DataIntegrityViolationException` from the unique constraint. The proper fix is `INSERT ... ON CONFLICT (user_id, date) DO UPDATE SET tokens_used = tokens_used + excluded.tokens_used`.

---

### Q: The code uses both `org.springframework.transaction.annotation.Transactional` and `jakarta.transaction.Transactional`. Is this a problem?

**A:** Both annotations are functionally equivalent when used with Spring's transaction management. `jakarta.transaction.Transactional` is the Jakarta EE standard annotation; `org.springframework.transaction.annotation.Transactional` is Spring's own version with slightly more configuration options (e.g., `rollbackFor`, `propagation` with Spring-specific enum). Spring's `TransactionInterceptor` recognizes both. The inconsistency (`ProjectServiceImpl` uses Spring's, `ProjectMemberServiceImpl` uses Jakarta's) is a code style issue — mixing them makes codebase harder to maintain and search. It is not a runtime bug. Best practice is to standardize on one; Spring's annotation is preferred in Spring Boot projects for its richer options.

---

### Q: If an interviewer asks "What would you do differently if you rebuilt this?", what are the strongest answers?

**A:** Key architectural improvements:

1. **Fix the bucket name bug**: Use a single `@Value` field for the bucket name in `ProjectFileServiceImpl`. Eliminate the hardcoded constant.

2. **Add `@Embeddable` to `ChatSessionId`**: One-line fix that resolves a Hibernate mapping bug.

3. **Cache the file tree**: Load `getFileTree()` once per request, pass through advisor context. Reduces N DB queries per stream to 1.

4. **Restore the token gate**: Uncomment `usageService.checkDailyTokensUsage()` and add proper null-safety throughout `finalizeChats()`.

5. **Fix `finalizeChats()` error handling**: Replace fire-and-forget with `Mono.fromRunnable().subscribeOn().doOnError()` with retry and alerting.

6. **Fix the static timestamp in `PromptUtils`**: Make `CODE_GENERATION_SYSTEM_PROMPT` a method that interpolates the current time per request.

7. **Add SSE event IDs**: Set `.id(String.valueOf(counter++))` on each `ServerSentEvent` to enable reconnection resumption.

8. **Implement proper upsert for `UsageLog`**: Use a native JPQL/SQL upsert with `ON CONFLICT` to eliminate the race condition in token tracking.

9. **Add file path validation in `readFiles`**: Check the requested path against the known file tree before hitting MinIO.

10. **`Preview` entity**: Add `@Entity`, create a `PreviewRepository`, implement the Kubernetes/container-based preview service.

---

### Q: How would you add conversation history (multi-turn memory) to the LLM integration?

**A:** Currently, `streamResponse()` sends only the current user message as context — there is no conversation history sent to the model. Each message is a fresh one-shot request. To add multi-turn memory:

1. Load previous `ChatMessage` records for the session from `chatMessageRepository`
2. Convert them to Spring AI `Message` objects: `new UserMessage(content)` for USER role, `new AssistantMessage(content)` for ASSISTANT role
3. Assemble them in chronological order before the current user message
4. Pass the full list to the ChatClient: `.messages(historyMessages).user(currentMessage)` or add them to the prompt builder

Considerations:
- **Context window limits**: Gemini Flash has a large context window but old messages should be truncated or summarized to avoid hitting limits
- **Token costs**: Every historical message is re-sent as input tokens on each turn
- **Content issue**: Since `ASSISTANT ChatMessage.content` is hardcoded `"Assistant Message here..."`, the history would be useless garbage. The fix to store actual content in `ChatMessage.content` (from `fullText`) must be done first.

---

### Q: The `@RequestBody ChatRequest` is a POST. How would you add rate limiting at the endpoint level?

**A:** Several approaches:

1. **In-memory rate limiter**: Use a `ConcurrentHashMap<Long, AtomicInteger>` keyed by `userId` with a time-window reset. Check in `streamResponse()` before the LLM call. Simple but doesn't survive restart and doesn't work across instances.

2. **Token bucket with Bucket4j**: Add `bucket4j-spring-boot-starter` and configure a `@RateLimiter` on the endpoint or service method. Supports Redis for distributed rate limiting.

3. **Restore `checkDailyTokensUsage()`**: The existing infrastructure is almost complete — `UsageLog` tracks daily tokens, `UsageServiceImpl` has the check logic, it just needs to be uncommented and the null-safety issues fixed.

4. **API gateway**: If behind a gateway (NGINX, Kong, AWS API Gateway), configure rate limiting there before requests reach the Spring Boot application.

The cleanest fix for the current codebase is option 3 — the scaffolding is already in place.

---

*End of interview preparation document. This file covers 60+ questions across Spring AI, ChatClient configuration, StreamAdvisor internals, tool calling mechanics, reactive programming with Project Reactor, SSE streaming protocol, MinIO storage patterns, template initialization, LLM response parsing, and known system issues.*
