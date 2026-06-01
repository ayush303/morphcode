# Architecture

## Request Flow

```
HTTP → JwtAuthFilter → Spring Security filter chain
     → Controller (@PreAuthorize @security.canXxx)
     → Service Interface → ServiceImpl (@Transactional)
     → Repository → PostgreSQL :9010 / MinIO :9000
```

Permit-all: `DispatcherType.ASYNC`, `DispatcherType.ERROR`, `/api/auth/**`, `/webhooks/**`

## Patterns

**Service layer**: every service has `service/Foo.java` + `service/impl/FooImpl.java`. Never inject Impl directly.

**Composite PKs**:
| Entity | ID Class | `@Embeddable` |
|---|---|---|
| `ProjectMember` | `ProjectMemberId` | Yes |
| `ChatSession` | `ChatSessionId` | **No** (bug — see [Known Issues](known-issues.md)) |

**Method security**: `SecurityExpressions` (`@Component("security")`) → `ProjectMemberRepository.findRoleByProjectIdAndUserId()` → `ProjectRole.getPermissions().contains(permission)`. Used as `@PreAuthorize("@security.canEditProject(#projectId)")`.

**MapStruct**: all entity ↔ DTO conversions via `mapper/` interfaces. Generated at compile time.

**Soft delete**: `deletedAt Instant` on `Project`, `ChatSession`, `User`. No `@Where`/`@Filter` — queries must filter `deletedAt IS NULL` explicitly.

## Entity Relationships

```
User ──(ProjectMember.userId)──► ProjectMember ──► Project
 │                                (OWNER/EDITOR/VIEWER)  │
 │                                                        │ (ProjectFile.project)
 └──(Subscription.user)──► Subscription ── Plan           ▼
                                                     ProjectFile → MinIO {projectId}/{path}

ChatSession(projectId, userId) → ChatMessage(role: USER|ASSISTANT)
                                  → ChatEvent[](THOUGHT|MESSAGE|FILE_EDIT|TOOL_LOG, sequenceOrder)

UsageLog(userId, date, tokensUsed) — UNIQUE(userId, date)
```

## Chat Stream: End-to-End

```
POST /api/chat/stream {message, projectId}
  → AiGenerationServiceImpl.streamResponse()
      ├─ canEditProject(projectId) check
      ├─ createChatSessionIfNotExists(projectId, userId)
      ├─ new CodeGenerationTools(projectFileService, projectId)   ← per-request
      └─ chatClient.prompt()
           .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
           .user(userMessage)
           .tools(codeGenerationTools)       ← read_files tool
           .advisors(→ FileTreeContextAdvisor [order=0]
                        reads projectId from advisor params
                        calls projectFileService.getFileTree() → DB query
                        injects "\n\n ---- FILE_TREE ----\n{nodes}" as SystemMessage)
           .stream().chatResponse()
           → [OpenRouter → gemini-3-flash-preview]
           → LLM may call read_files(paths[]): MinIO GetObject per path

  → Flux<ChatResponse> as SSE (text/event-stream)
      .doOnNext(): accumulate chunks → fullResponseBuffer
      .doOnComplete(): [Schedulers.boundedElastic()]
          → usageService.recordTokenUsage(userId, tokens) → upsert UsageLog
          → save ChatMessage(USER), ChatMessage(ASSISTANT, "Assistant Message here...")
          → LlmResponseParser.parseChatEvents(fullText) → List<ChatEvent>
              prepend THOUGHT("Thought for Xs") at sequenceOrder=0
          → FILE_EDIT events → projectFileService.saveFile() → MinIO PutObject + upsert ProjectFile
          → chatEventRepository.saveAll(events)
```

## Project Creation

```
POST /api/projects {name, isPublic}
  → ProjectServiceImpl.createProject()
      ├─ subscriptionService.canCreateNewProject() — check OWNER membership count
      ├─ save Project
      ├─ save ProjectMember(OWNER)
      └─ projectTemplateService.initializeProjectFromTemplate(projectId)
           ├─ list "starter-projects" / "react-vite-tailwind-daisyui-starter/"
           ├─ copy each → "projects"/{projectId}/{relativePath}
           └─ bulk save ProjectFile records
```

## Config Beans

| Bean | Class | Notes |
|---|---|---|
| `ChatClient` | `AiConfig` | includes `SimpleLoggerAdvisor` |
| `MinioClient` | `StorageConfig` | url/access/secret from properties |
| `Stripe` | `PaymentConfig` | sets `Stripe.apiKey` on startup |
| CORS | `CorsConfig` | allows `localhost:5173`, `localhost:5174` |
| `SecurityFilterChain` | `WebSecurityConfig` | stateless, JWT before UsernamePassword filter |
