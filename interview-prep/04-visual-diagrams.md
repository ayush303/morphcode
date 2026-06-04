# MorphCode — Visual Memory Diagrams

> Layman-friendly diagrams to help you visualize and memorize how the backend works.
> These are not formal UML — just clear mental models.

---

## 1. The Big Picture — Every Layer at a Glance

```
┌─────────────────────────────────────────────────────┐
│                    CLIENT (Browser)                  │
│          HTTP Requests  /  SSE Stream                │
└───────────────────┬─────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│              SECURITY LAYER                         │
│    JwtAuthFilter  →  reads Bearer token             │
│    SecurityContextHolder  ←  stores identity        │
└───────────────────┬─────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│              CONTROLLERS  (port 8082)               │
│  AuthController  ChatController  ProjectController  │
│  FileController  BillingController  MemberController│
└───────────────────┬─────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────┐
│            SERVICE LAYER  (@PreAuthorize)           │
│  AuthService  ProjectService  ChatService           │
│  AiGenerationService  SubscriptionService           │
│  ProjectFileService  UsageService                   │
└──────────┬──────────────────────────┬───────────────┘
           │                          │
           ▼                          ▼
┌──────────────────┐      ┌───────────────────────────┐
│  REPOSITORIES    │      │   EXTERNAL SERVICES        │
│  Spring Data JPA │      │  OpenRouter (AI/LLM)       │
│  ↓               │      │  MinIO  (file storage)     │
│  PostgreSQL      │      │  Stripe (payments)         │
└──────────────────┘      └───────────────────────────┘
```

---

## 2. What Happens on Every HTTP Request

```mermaid
flowchart TD
    A[📥 HTTP Request arrives] --> B{Has Authorization header?}
    B -- No --> C[Pass through\nno identity set]
    B -- Yes, Bearer token --> D[JwtAuthFilter reads token]
    D --> E{Token valid?}
    E -- No --> F[HandlerExceptionResolver\n→ 401 Unauthorized]
    E -- Yes --> G[Set JwtUserPrincipal\nin SecurityContextHolder]
    G --> H[Request reaches Controller]
    C --> H
    H --> I{URL is /api/auth/**\nor /webhooks/**?}
    I -- Yes permitAll --> J[No auth check needed]
    I -- No --> K{Is user authenticated?}
    K -- No --> L[403 Forbidden]
    K -- Yes --> M[Controller method runs]
    J --> M
    M --> N{Method has @PreAuthorize?}
    N -- Yes --> O[SecurityExpressions.canXxx\nqueries DB for role]
    O -- No permission --> P[AccessDeniedException → 403]
    O -- Has permission --> Q[Service method runs]
    N -- No --> Q
    Q --> R[📤 Response sent]
```

---

## 3. JWT Token — What's Inside and How It Works

```
┌──────────────────────────────────────────────────────────┐
│                    JWT TOKEN                             │
│                                                          │
│  HEADER.PAYLOAD.SIGNATURE                                │
│     ↓         ↓         ↓                               │
│  algorithm   data    HS256 signed with secret key        │
│                                                          │
│  PAYLOAD contains:                                       │
│  ┌─────────────────────────────┐                         │
│  │  sub     = "user@email.com" │  ← username            │
│  │  userId  = "42"             │  ← stored as String    │
│  │  iat     = 1717000000       │  ← issued at           │
│  │  exp     = 1717006000       │  ← expires in 100 min  │
│  └─────────────────────────────┘                         │
└──────────────────────────────────────────────────────────┘

How it flows:

 SIGNUP / LOGIN                    EVERY OTHER REQUEST
 ─────────────────                 ─────────────────────
 1. POST /api/auth/signup          1. Client sends:
 2. Server creates user               Authorization: Bearer eyJhbGci...
 3. Generates JWT token            2. JwtAuthFilter intercepts
 4. Returns: { token: "eyJ..." }   3. Verifies signature with secret key
 5. Client stores token            4. Extracts userId from claims
                                   5. Puts in SecurityContextHolder
                                   6. Service calls getCurrentUserId()
```

---

## 4. Signup and Login Flow

```mermaid
flowchart LR
    subgraph SIGNUP
        A1[POST /api/auth/signup\nusername + password + name] --> B1[Check username\nnot already taken]
        B1 --> C1[MapStruct:\nSignupRequest → User entity]
        C1 --> D1[BCrypt.encode password]
        D1 --> E1[Save User to DB]
        E1 --> F1[Generate JWT\n100-min expiry]
        F1 --> G1[Return token + profile]
    end

    subgraph LOGIN
        A2[POST /api/auth/login\nusername + password] --> B2[authenticationManager.authenticate\nSpring Security loads user from DB]
        B2 --> C2[Cast principal to User]
        C2 --> D2[Generate JWT]
        D2 --> E2[Return token + profile]
    end
```

---

## 5. Who Can Do What — Role Permissions

```
OWNER  ──────────────────────────────────────────────────
  ✅ View files          ✅ Edit via AI
  ✅ Delete project      ✅ View members
  ✅ Manage members (invite / change role / remove)

EDITOR  ─────────────────────────────────────────────────
  ✅ View files          ✅ Edit via AI
  ❌ Delete project      ✅ View members
  ❌ Manage members

VIEWER  ─────────────────────────────────────────────────
  ✅ View files          ❌ Edit via AI
  ❌ Delete project      ✅ View members
  ❌ Manage members

PERMISSION → API GUARD
──────────────────────────────────────────────────────────
VIEW            → canViewProject()    → getUserProjectById()
EDIT            → canEditProject()    → updateProject(), streamResponse()
DELETE          → canDeleteProject()  → softDelete()
VIEW_MEMBERS    → canViewMembers()    → getProjectMembers()
MANAGE_MEMBERS  → canManageMembers()  → invite / update / remove
```

---

## 6. How @PreAuthorize Checks Permission

```mermaid
flowchart TD
    A["@PreAuthorize('@security.canEditProject(#projectId)')\nwritten on service method"] --> B[Spring AOP intercepts the call]
    B --> C[Calls SecurityExpressions bean\n named 'security']
    C --> D[Gets userId from SecurityContextHolder]
    D --> E[DB query:\nSELECT role FROM project_members\nWHERE project_id=? AND user_id=?]
    E --> F{Role found?}
    F -- No role → not a member --> G[Return false → AccessDeniedException → 403]
    F -- Has role --> H{role.permissions.contains EDIT?}
    H -- No --> G
    H -- Yes --> I[Method runs normally ✅]
```

---

## 7. The Full AI Chat Stream — End to End

```mermaid
flowchart TD
    U[👤 User sends message\nPOST /api/chat/stream\n message + projectId ] --> A

    subgraph CONTROLLER
        A[ChatController.streamChat] --> B[Returns Flux wrapped\nin ServerSentEvent]
    end

    subgraph SERVICE - AiGenerationServiceImpl
        B --> C[Check permission\n@PreAuthorize canEditProject]
        C --> D[Find or create ChatSession\nfor this user + project]
        D --> E[Create CodeGenerationTools\nbound to projectId per-request]
        E --> F[FileTreeContextAdvisor runs\nfetches file tree from DB\ninjects into system prompt]
        F --> G[chatClient.prompt.stream\nsends to OpenRouter\nGemini Flash model]
    end

    subgraph STREAMING
        G --> H[📡 Token chunks stream back]
        H --> I[Each chunk appended to buffer\nand sent as SSE event to client]
        I --> J{LLM calls read_files tool?}
        J -- Yes --> K[CodeGenerationTools.readFiles\nfetches file from MinIO\nreturns formatted content]
        K --> G
        J -- No, continues --> L[Stream completes]
    end

    subgraph AFTER STREAM - boundedElastic thread
        L --> M[finalizeChats runs ASYNC]
        M --> N[Record token usage in UsageLog]
        N --> O[Save USER ChatMessage]
        O --> P[Save ASSISTANT ChatMessage]
        P --> Q[LlmResponseParser\nregex-parse full buffer]
        Q --> R[Save FILE_EDIT files → MinIO + DB]
        R --> S[saveAll ChatEvents to DB]
    end
```

---

## 8. What finalizeChats Does — Step by Step

```
STREAM ENDS
     │
     ▼
① Record tokens used today
   UsageLog: userId + date → tokensUsed += N
   (one row per user per day, upsert pattern)

     │
     ▼
② Save USER message to DB
   ChatMessage { role=USER, content="user's text", tokensUsed=promptTokens }

     │
     ▼
③ Save ASSISTANT message to DB
   ChatMessage { role=ASSISTANT, content="hardcoded placeholder", tokensUsed=completionTokens }

     │
     ▼
④ Parse the full buffered LLM response with regex
   LlmResponseParser scans for:
   <message>...</message>   → MESSAGE event
   <file path="...">...</file>  → FILE_EDIT event
   <tool args="...">...</tool>  → TOOL_LOG event

     │
     ▼
⑤ Prepend THOUGHT event at sequenceOrder=0
   content = "Thought for 3s"  (time-to-first-token)

     │
     ▼
⑥ For every FILE_EDIT event:
   → Write file bytes to MinIO
   → Upsert ProjectFile row in PostgreSQL

     │
     ▼
⑦ saveAll ChatEvents to PostgreSQL
   (all at once, not one by one)
```

---

## 9. LLM Output XML Tags — What Each Tag Means

```
LLM RESPONSE (raw text stream):
─────────────────────────────────────────────────────
<message phase="start">
  I'll update App.tsx to add dark mode toggle.
</message>
          ↓ becomes ChatEvent { type=MESSAGE }

<tool args="src/App.tsx">
  Reading App.tsx...
</tool>
          ↓ becomes ChatEvent { type=TOOL_LOG, metadata="src/App.tsx" }
          ↓ ALSO triggers actual read_files() tool call to MinIO

<file path="src/App.tsx">
  import React, { useState } from 'react';
  ...full file content...
</file>
          ↓ becomes ChatEvent { type=FILE_EDIT, filePath="src/App.tsx" }
          ↓ ALSO triggers saveFile() to MinIO

<message phase="completed">
  Done! Added toggle to the navbar.
</message>
          ↓ becomes ChatEvent { type=MESSAGE }
─────────────────────────────────────────────────────

+ A THOUGHT event is always prepended at sequenceOrder=0
  ChatEvent { type=THOUGHT, content="Thought for 3s" }
```

---

## 10. ChatEvent Types — Visual Summary

```mermaid
mindmap
  root((ChatEvent Types))
    THOUGHT
      sequenceOrder 0
      Always first
      Shows thinking time
      e.g. Thought for 3s
    TOOL_LOG
      LLM read a file
      metadata = file paths
      e.g. Reading App.tsx
    MESSAGE
      AI explanation
      Markdown text
      Plan or completion note
    FILE_EDIT
      File was written
      filePath = which file
      content = full new file
      Saved to MinIO + DB
```

---

## 11. MinIO Storage — Two Buckets

```
┌─────────────────────────────────────────────────────────┐
│  BUCKET: starter-projects  (read-only template)         │
│                                                          │
│  react-vite-tailwind-daisyui-starter/                   │
│  ├── index.html                                          │
│  ├── package.json                                        │
│  ├── vite.config.ts                                      │
│  ├── tailwind.config.js                                  │
│  └── src/                                               │
│      ├── App.tsx                                         │
│      ├── main.tsx                                        │
│      └── index.css                                       │
└──────────────────────┬──────────────────────────────────┘
                       │
           When user creates project → copyObject (server-side)
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  BUCKET: projects  (live user files)                    │
│                                                          │
│  {projectId}/                                            │
│  ├── 5/                  ← project ID = 5               │
│  │   ├── index.html                                      │
│  │   ├── src/App.tsx     ← key = "5/src/App.tsx"        │
│  │   └── src/main.tsx                                    │
│  ├── 6/                  ← project ID = 6               │
│  │   └── ...                                             │
└─────────────────────────────────────────────────────────┘

READ  → uses hardcoded "projects" constant  ⚠️ (bug)
WRITE → uses @Value("${minio.project-bucket}")
```

---

## 12. Project Creation — Every Step Visualized

```mermaid
flowchart TD
    A[POST /api/projects\nbody: name] --> B[canCreateNewProject?\ncheck plan limit vs owned count]
    B -- Over limit --> C[❌ BadRequestException\nUpgrade plan]
    B -- Within limit --> D[Get userId from JWT]
    D --> E[userRepository.getReferenceById\nHibernate proxy, no DB hit]
    E --> F[Save Project\nisPublic=false by default]
    F --> G[Save ProjectMember\nrole=OWNER\ninvitedAt=acceptedAt=now]
    G --> H[initializeProjectFromTemplate]
    H --> I[List all files in\nstarter-projects/react-vite.../]
    I --> J[For each file:\ncopyObject to projects/projectId/]
    J --> K[Bulk save ProjectFile rows\nto PostgreSQL]
    K --> L[✅ Return ProjectResponse\nid + name + timestamps]
```

---

## 13. FileTreeContextAdvisor — What It Does Before Every AI Call

```
EVERY TIME user sends a message to the AI:

WITHOUT advisor:
  System Prompt + User Message → sent to OpenRouter

WITH FileTreeContextAdvisor:
  System Prompt
       +
  ---- FILE_TREE ----         ← injected by advisor
  [FileNode(path=src/App.tsx),
   FileNode(path=src/main.tsx),
   FileNode(path=index.html), ...]
       +
  User Message
       ↓
  sent to OpenRouter

So the LLM always knows WHAT FILES EXIST
before deciding which ones to read.

⚠️ Known issue: this does a DB query on EVERY LLM turn.
   No caching. Slow on large projects.
```

---

## 14. Subscription Lifecycle States

```mermaid
stateDiagram-v2
    [*] --> INCOMPLETE : checkout.session.completed\nStripe webhook fires

    INCOMPLETE --> ACTIVE : customer.subscription.updated\nstatus = active

    ACTIVE --> PAST_DUE : invoice.payment_failed\nmarkSubscriptionPastDue()

    PAST_DUE --> ACTIVE : invoice.paid\nrenewSubscriptionPeriod()

    ACTIVE --> CANCELED : customer.subscription.deleted\ncancelSubscription()

    PAST_DUE --> CANCELED : customer.subscription.deleted

    ACTIVE --> TRIALING : subscription starts trial

    TRIALING --> ACTIVE : trial ends, payment succeeds
```

---

## 15. Stripe Webhook — 5 Events and What They Do

```
Stripe → POST /webhooks/payment
              │
              ▼
    Webhook.constructEvent()
    verifies Stripe-Signature header
              │
    ┌─────────┴──────────────────────────────┐
    │  Switch on event.getType()              │
    └────────────────────────────────────────┘
              │
    ┌─────────┬────────────┬────────────┬──────────────┬───────────────┐
    │         │            │            │              │               │
    ▼         ▼            ▼            ▼              ▼               ▼
checkout. customer.   customer.    invoice.       invoice.
session.  subscription. subscription. paid       payment_failed
completed updated      deleted
    │         │            │            │              │
    ▼         ▼            ▼            ▼              ▼
 Create    Update        Set         Renew period   Set status
Subscript  status/plan  CANCELED    Set ACTIVE      PAST_DUE
  in DB    period dates   in DB     if was PAST_DUE  in DB
 Store
customer
  ID on
  User
```

---

## 16. Spring Security Filter Chain — Request Journey

```
Incoming HTTP Request
        │
        ▼
┌────────────────────────┐
│   JwtAuthFilter        │  ← runs FIRST (added before UsernamePasswordAuthFilter)
│                        │
│  1. Read Authorization │
│     header             │
│  2. Extract JWT token  │
│  3. Verify signature   │
│  4. Set principal in   │
│     SecurityContext    │
└──────────┬─────────────┘
           │
           ▼
┌────────────────────────┐
│  authorizeHttpRequests │
│                        │
│  /api/auth/**  →  ✅   │  ← permitAll, no login needed
│  /webhooks/**  →  ✅   │  ← permitAll, Stripe can call
│  ASYNC dispatch → ✅   │  ← needed for SSE streams
│  ERROR dispatch → ✅   │  ← needed for /error endpoint
│  everything else → 🔒 │  ← must be authenticated
└──────────┬─────────────┘
           │
           ▼
┌────────────────────────┐
│   Controller method    │
└──────────┬─────────────┘
           │
           ▼
┌────────────────────────┐
│  @PreAuthorize AOP     │  ← method-level check
│  queries project_members│
│  table for user's role  │
└────────────────────────┘
```

---

## 17. MapStruct — How DTO ↔ Entity Conversion Works

```
REQUEST COMES IN:
  JSON body → Spring deserialises → DTO (Java record)
                                        │
                              MapStruct.toEntity()
                                        │
                                        ▼
                                  JPA Entity
                                        │
                               repository.save()
                                        │
                                        ▼
                                   Saved in DB

RESPONSE GOES OUT:
  JPA Entity
      │
  MapStruct.toResponseDto()
      │
      ▼
  Response DTO (record)
      │
  Jackson serialises to JSON
      │
      ▼
  HTTP Response body

MAPPERS IN THIS PROJECT:
  SignupRequest  ──────────→  User entity         (UserMapper)
  User entity    ──────────→  UserProfileResponse (UserMapper)
  Project        ──────────→  ProjectResponse     (ProjectMapper)
  Project+Role   ──────────→  ProjectSummaryResp  (ProjectMapper)
  ProjectMember  ──────────→  MemberResponse      (ProjectMemberMapper)
  ChatMessage[]  ──────────→  ChatResponse[]      (ChatMapper)
  Subscription   ──────────→  SubscriptionResp    (SubscriptionMapper)

⚡ MapStruct generates ALL this code at compile time.
   Zero reflection at runtime. Very fast.
```

---

## 18. Error Handling Flow

```mermaid
flowchart TD
    A[Exception thrown anywhere\nin Controller or Service] --> B{Type of exception?}

    B --> C[BadRequestException]
    B --> D[ResourceNotFoundException]
    B --> E[MethodArgumentNotValidException]
    B --> F[UsernameNotFoundException]
    B --> G[AuthenticationException]
    B --> H[JwtException]
    B --> I[AccessDeniedException]

    C --> C1[400 Bad Request\nmessage from exception]
    D --> D1[404 Not Found\nresourceName + id not found]
    E --> E1[400 Bad Request\nlist of field errors]
    F --> F1[404 Not Found]
    G --> G1[401 Unauthorized]
    H --> H1[401 Unauthorized\nInvalid JWT token]
    I --> I1[403 Forbidden\nAccess denied]

    C1 & D1 & E1 & F1 & G1 & H1 & I1 --> Z[ApiError record returned\nstatus + message + timestamp + errors]
```

---

## 19. Soft Delete — How It Works Here

```
NORMAL DELETE:
  DELETE FROM projects WHERE id = 5
  → Row is GONE forever ❌

SOFT DELETE (used in MorphCode):
  UPDATE projects SET deleted_at = NOW() WHERE id = 5
  → Row STAYS in DB ✅ just marked as deleted

READING DATA:
  All queries manually filter: WHERE deleted_at IS NULL

  Example:
  SELECT * FROM projects
  WHERE pm.user_id = 10
    AND p.deleted_at IS NULL   ← this line excludes deleted
  ORDER BY updated_at DESC

ENTITIES WITH SOFT DELETE:
  User       → deletedAt field
  Project    → deletedAt field
  ChatSession → deletedAt field

NOTE: MorphCode does NOT use Hibernate's @Where annotation
      Every repository query manually adds the IS NULL check
      This means you CAN accidentally forget to filter!
```

---

## 20. Composite Keys — ProjectMember and ChatSession

```
WHY COMPOSITE KEY?
  A user can be a member of MANY projects.
  A project can have MANY members.
  The COMBINATION of (project_id + user_id) is unique.
  No need for a separate auto-generated ID.

ProjectMember table:
┌─────────────┬─────────┬──────────────┬────────────┐
│ project_id  │ user_id │ project_role │ invited_at │
│ (PK part 1) │(PK part2│              │            │
├─────────────┼─────────┼──────────────┼────────────┤
│      5      │   10    │    OWNER     │ 2026-01-01 │
│      5      │   20    │    EDITOR    │ 2026-01-02 │
│      6      │   10    │    VIEWER    │ 2026-01-03 │
└─────────────┴─────────┴──────────────┴────────────┘
  ↑ User 10 is OWNER of project 5 but VIEWER of project 6

In Java:
  @Embeddable  ← marks it as embeddable key
  class ProjectMemberId {
      Long projectId;
      Long userId;
  }

  @Entity
  class ProjectMember {
      @EmbeddedId ProjectMemberId id;
      @MapsId("projectId") Project project;  ← links FK
      @MapsId("userId") User user;           ← links FK
  }

⚠️ ChatSessionId is missing @Embeddable — this is a known bug.
```

---

## 21. UsageLog — Daily Token Tracking

```
One row per user per day:
┌─────────┬────────────┬─────────────┐
│ user_id │    date    │ tokens_used │
├─────────┼────────────┼─────────────┤
│   10    │ 2026-06-01 │    4500     │
│   10    │ 2026-06-02 │    1200     │
│   20    │ 2026-06-01 │    800      │
└─────────┴────────────┴─────────────┘
  ↑ UNIQUE constraint on (user_id, date)

UPSERT PATTERN:
  findByUserIdAndDate(userId, today)
      │
      ├── Found? → add tokens to existing row
      │
      └── Not found? → create new row with tokensUsed=0
                       then add tokens

LIMIT CHECK (currently commented out):
  if currentUsage >= plan.maxTokensPerDay()
      → throw 429 Too Many Requests
  if plan.unlimitedAi == true
      → skip the check entirely
```

---

## 22. Spring AI Components — How They Connect

```mermaid
mindmap
  root((Spring AI))
    ChatClient
      Created in AiConfig
      SimpleLoggerAdvisor attached
      .prompt .user .tools .advisors .stream
    StreamAdvisor
      FileTreeContextAdvisor
      Runs before request hits OpenRouter
      Injects FILE_TREE into system prompt
      getOrder = 0
    Tool
      CodeGenerationTools
      NOT a Spring bean
      New instance per request
      @Tool read_files method
      Fetches files from MinIO
    ChatClient.Builder
      Auto-configured by Spring AI
      Reads spring.ai.openai.api-key
      Reads spring.ai.openai.base-url
      Points to OpenRouter
    Model
      google/gemini-3-flash-preview
      temperature = 0.0
      deterministic output
```

---

## 23. The getReferenceById vs findById Decision

```
findById(userId)          getReferenceById(userId)
─────────────────         ────────────────────────
SELECT * FROM users       NO SQL query executed
WHERE id = 42             Returns a Hibernate PROXY
                          (a fake object with just the ID)
Returns real User obj
                          Used when you only need the
Used when you need        object as a FOREIGN KEY
to read its fields        e.g. setting project.user = proxy
                          Hibernate fills in the FK column
                          without needing the full row

MorphCode uses getReferenceById() in createProject()
to avoid an extra SELECT when just setting the OWNER FK.
```

---

## 24. Full Component Mind Map

```mermaid
mindmap
  root((MorphCode Backend))
    Controllers
      AuthController - signup login me
      ChatController - stream history
      ProjectController - CRUD
      ProjectMemberController - invite roles
      FileController - tree content
      BillingController - plans checkout portal webhooks
    Services
      AiGenerationServiceImpl - CORE AI loop
      AuthServiceImpl - signup login
      ProjectServiceImpl - project CRUD
      ChatServiceImpl - chat history
      ProjectFileServiceImpl - MinIO files
      ProjectTemplateServiceImpl - copy template
      SubscriptionServiceImpl - subscription lifecycle
      UsageServiceImpl - daily tokens
      StripePaymentProcessor - Stripe API calls
    Security
      JwtAuthFilter - reads Bearer token
      AuthUtil - sign verify getCurrentUserId
      SecurityExpressions - canEditProject etc
      WebSecurityConfig - filter chain rules
      JwtUserPrincipal - record userId username
    LLM
      AiConfig - ChatClient bean
      FileTreeContextAdvisor - inject file tree
      CodeGenerationTools - read_files tool
      LlmResponseParser - parse XML tags
      PromptUtils - system prompt string
    Storage
      ProjectFileServiceImpl - read write MinIO
      ProjectTemplateServiceImpl - copy from template
      Two buckets - starter-projects and projects
    Database
      PostgreSQL port 9010
      Hibernate ddl-auto=update
      Soft delete on User Project ChatSession
      Composite keys ProjectMember ChatSession
```

---

## 25. Known Bugs — Quick Reference Card

```
╔══════════════════════════════════════════════════════════╗
║              ⚠️  KNOWN BUGS TO REMEMBER  ⚠️              ║
╠══════════════════════════════════════════════════════════╣
║ 1. GET /api/auth/me                                      ║
║    hardcodes userId = 1L  (ignores your token)           ║
╠══════════════════════════════════════════════════════════╣
║ 2. GET /api/plans                                        ║
║    always returns []  (repo never called)                ║
╠══════════════════════════════════════════════════════════╣
║ 3. ChatSessionId missing @Embeddable                     ║
║    Hibernate mapping is technically undefined            ║
╠══════════════════════════════════════════════════════════╣
║ 4. ProjectFileServiceImpl bucket mismatch                ║
║    reads  → hardcoded "projects"                         ║
║    writes → @Value("${minio.project-bucket}")            ║
╠══════════════════════════════════════════════════════════╣
║ 5. Daily token limit COMMENTED OUT                       ║
║    usageService.checkDailyTokensUsage() not called       ║
╠══════════════════════════════════════════════════════════╣
║ 6. ASSISTANT ChatMessage.content hardcoded               ║
║    content = "Assistant Message here..."                 ║
║    Real content lives in ChatEvent records               ║
╠══════════════════════════════════════════════════════════╣
║ 7. Preview entity has no @Entity annotation              ║
║    No repository. Feature not implemented.               ║
╠══════════════════════════════════════════════════════════╣
║ 8. FileTreeContextAdvisor hits DB every LLM call         ║
║    No caching. N queries per stream on large projects.   ║
╠══════════════════════════════════════════════════════════╣
║ 9. Mixed @Transactional imports                          ║
║    ProjectServiceImpl  → org.springframework             ║
║    ProjectMemberServiceImpl → jakarta                    ║
╚══════════════════════════════════════════════════════════╝
```
