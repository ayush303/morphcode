# AI Integration

## Config

Provider: OpenRouter (OpenAI-compatible) | Model: `google/gemini-3-flash-preview` | Temp: `0.0`

```properties
spring.ai.openai.api-key=...
spring.ai.openai.base-url=https://openrouter.ai/api
spring.ai.openai.chat.options.model=google/gemini-3-flash-preview
spring.ai.openai.chat.options.temperature=0.0
```

`AiConfig` creates singleton `ChatClient` with `SimpleLoggerAdvisor`. Debug logging: `logging.level.org.springframework.ai.chat.client=DEBUG`

## FileTreeContextAdvisor (`StreamAdvisor`, order=0)

File: `llm/advisors/FileTreeContextAdvisor.java`

Runs before each LLM call. Reads `projectId` from `advisorSpec.params(Map.of("projectId", projectId))`, calls `projectFileRepository.findByProjectId(projectId)`, injects file list as second `SystemMessage`:
```
\n\n ---- FILE_TREE ----\n[FileNode list toString]
```
**No caching** — every LLM call (including tool calls) triggers a DB query.

## CodeGenerationTools

File: `llm/tools/CodeGenerationTools.java` | **NOT `@Component`** — instantiated per-request:
```java
new CodeGenerationTools(projectFileService, projectId)
```

### Tool: `read_files`
```java
@Tool(name = "read_files", description = "Read the content of files...")
public List<String> readFiles(@ToolParam List<String> paths)
```
Per path: strip leading `/` → `projectFileService.getFileContent(projectId, path)` → MinIO GetObject → wrap:
```
--- START OF FILE: {path} ---
{content}
--- END OF FILE ---
```

## System Prompt (`PromptUtils.CODE_GENERATION_SYSTEM_PROMPT`)

File: `llm/PromptUtils.java` — static constant, built once at class load (includes `LocalDateTime.now()` at startup).

Defines: Analyze→Plan→Execute→Stop protocol | XML output format | daisyUI semantic colors, no hex | TypeScript strict, max 100 lines/file, no `any` | generate `<tool>` tag before calling `read_files`

## LLM XML Output Protocol

LLM emits only XML tags — no bare text.

| Tag | Attributes | Purpose |
|---|---|---|
| `<message phase="start\|planning\|completed">` | `phase` | Markdown explanation/planning |
| `<file path="src/App.tsx">` | `path` | Complete file content to write |
| `<tool args="src/App.tsx,src/main.tsx">` | `args` | Signals files to read via `read_files` |

Each `<file path>` appears at most once per response.

```xml
<message phase="start">Let me check the current implementation.</message>
<tool args="src/App.tsx">Reading App.tsx...</tool>
<message phase="planning">I need to wrap the app in a provider.</message>
<file path="src/App.tsx">...complete content...</file>
<message phase="completed">Done!</message>
```

## LlmResponseParser

File: `llm/LlmResponseParser.java` — parses fully buffered response after stream completes.

Regex:
```java
Pattern.compile("(<(message|file|tool)([^>]*)>)([\\s\\S]*?)(</\\2>)",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
// Groups: 1=opening tag, 2=tag name, 3=attrs, 4=content, 5=closing tag
// Attr extraction: (path|args)="([^"]+)"
```

| Tag | `ChatEventType` | Extra fields |
|---|---|---|
| `message` | `MESSAGE` | content = inner text |
| `file` | `FILE_EDIT` | `filePath` from `path` attr; content = full file text |
| `tool` | `TOOL_LOG` | `metadata` = `args` value (comma-separated paths) |

Synthetic `THOUGHT` event (`"Thought for Xs"`) prepended at `sequenceOrder=0`.

`FILE_EDIT` events → `projectFileService.saveFile(projectId, filePath, content)` → MinIO PutObject + upsert `ProjectFile`.

## Full Prompt Structure

```
SystemMessage: CODE_GENERATION_SYSTEM_PROMPT
SystemMessage: "\n\n ---- FILE_TREE ----\n{fileNodes}"   ← FileTreeContextAdvisor
UserMessage:   {userMessage}
+ read_files tool definition
```
