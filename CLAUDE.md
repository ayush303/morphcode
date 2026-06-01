# MorphCode — CLAUDE.md

AI-powered browser-based React IDE backend. Users create projects seeded from a React 18+TS+Vite+Tailwind+daisyUI template in MinIO, chat with an LLM (OpenRouter → Gemini Flash) to edit files. AI reads file tree and file contents via tool, writes files back to MinIO, streams response via SSE.

## Quick Orientation

- **Entry point**: `AiGenerationServiceImpl.streamResponse()` — owns the full chat loop
- **AI tool**: `CodeGenerationTools` (NOT `@Component`) — instantiated per-request to bind `projectId`
- **XML protocol**: LLM emits `<message>`, `<file path="...">`, `<tool args="...">` tags; `LlmResponseParser` regex-parses buffered response into `ChatEvent` records
- **DB writes after stream**: `finalizeChats()` runs on `Schedulers.boundedElastic()` — decoupled from SSE
- **Auth**: JWT via `JwtAuthFilter`; method security via `@PreAuthorize("@security.canEditProject(#projectId)")`
- **Soft delete**: `deletedAt Instant` on `Project`, `ChatSession`, `User` — no `@Where`; queries must filter manually
- **Composite PKs**: `ProjectMemberId` (`@Embeddable`) and `ChatSessionId` (**missing** `@Embeddable` — bug)

## Run

```
# Prerequisites: PostgreSQL :9010 (db: pgvector-test), MinIO :9000
./mvnw spring-boot:run   # → http://localhost:8082
```

Config: `src/main/resources/application.properties`

## Tech Stack

| Layer | Tech |
|---|---|
| Runtime | Java 17, Spring Boot 3.3.0 |
| AI | Spring AI 1.0.0, OpenRouter (Gemini Flash) |
| DB | PostgreSQL :9010, `pgvector-test` |
| Storage | MinIO :9000 |
| Security | JJWT 0.12.6, BCrypt |
| Payments | Stripe SDK |
| Mapping | MapStruct |

## Project Structure

```
src/main/java/com/morphcode/ai/
├── config/          # AiConfig, CorsConfig, PaymentConfig, StorageConfig
├── controller/      # AuthController, ChatController, FileController,
│                    # ProjectController, ProjectMemberController,
│                    # BillingController, UsageController
├── dto/             # auth/, chat/, member/, project/, subscription/
├── entity/          # JPA entities + embedded IDs
├── enums/           # ChatEventType, MessageRole, ProjectRole, ProjectPermission,
│                    # SubscriptionStatus, PreviewStatus
├── error/           # GlobalExceptionHandler, ApiError, custom exceptions
├── llm/
│   ├── advisors/    # FileTreeContextAdvisor (StreamAdvisor)
│   ├── tools/       # CodeGenerationTools (NOT @Component)
│   ├── LlmResponseParser.java
│   └── PromptUtils.java
├── mapper/          # MapStruct interfaces
├── repository/      # Spring Data JPA repositories
├── security/        # JwtAuthFilter, AuthUtil, SecurityExpressions, WebSecurityConfig
└── service/         # interfaces + impl/
```

## Storage Layout

- Template: bucket `starter-projects`, prefix `react-vite-tailwind-daisyui-starter/`
- Projects: bucket `projects`, key `{projectId}/{path}`

## Before You Edit — Critical Gotchas

1. `ProjectFileServiceImpl` reads with hardcoded `BUCKET_NAME = "projects"`, writes with `@Value("${minio.project-bucket}")`. If property changes, reads/writes silently diverge.
2. `ChatSessionId` missing `@Embeddable` — Hibernate mapping undefined. Use `ProjectMemberId` as pattern.
3. `finalizeChats()` on `Schedulers.boundedElastic()` — pool exhaustion silently delays/loses DB writes and MinIO saves.
4. `FileTreeContextAdvisor` queries DB on every LLM call — no cache. N queries per stream on large projects.
5. `@Transactional` mixed: `ProjectServiceImpl` uses `org.springframework`, `ProjectMemberServiceImpl` uses `jakarta`. Both work but inconsistent.
6. Daily token gate is **commented out** in `AiGenerationServiceImpl.streamResponse()` — limits not enforced.
7. `GET /api/auth/me` hardcodes `userId = 1L` — always returns user 1's profile.
8. `GET /api/plans` always returns `[]` — `PlanRepository` never called in `PlanServiceImpl`.
9. `ASSISTANT ChatMessage.content` is hardcoded `"Assistant Message here..."` — actual content in `ChatEvent` records.
10. `Preview` entity has no `@Entity`, no repository — feature unimplemented.

## Docs

- [Architecture & Data Flow](docs/architecture.md)
- [API Reference](docs/api.md)
- [Security](docs/security.md)
- [AI Integration](docs/ai-integration.md)
- [Data Model](docs/data-model.md)
- [Billing & Stripe](docs/billing.md)
- [Known Issues](docs/known-issues.md)
