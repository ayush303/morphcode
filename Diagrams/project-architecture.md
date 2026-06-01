# NextGen Studio — Project Architecture

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Solution Space](#2-solution-space)
3. [High-Level System Architecture](#3-high-level-system-architecture)
4. [Microservices Breakdown](#4-microservices-breakdown)
5. [AI Code Generation Flow](#5-ai-code-generation-flow)
6. [Preview Lifecycle](#6-preview-lifecycle)
7. [Entity-Relationship Diagram](#7-entity-relationship-diagram)
8. [Class Diagrams](#8-class-diagrams)
9. [Sequence Diagrams](#9-sequence-diagrams)
10. [Infrastructure & Deployment](#10-infrastructure--deployment)
11. [Feature Surface Map](#11-feature-surface-map)

---

## 1. Problem Statement

Building and shipping a web application today requires expertise across multiple disciplines: UI design, frontend frameworks, backend APIs, database modelling, cloud deployment, and CI/CD pipelines. The barrier is especially high for non-engineers and early-stage founders who have a clear product vision but lack the technical depth to execute it.

Existing low-code tools sacrifice flexibility; traditional development is slow and expensive. There is no middle ground that gives a technical-enough output (real, editable source code) while keeping the interaction as simple as a conversation.

**Key pain points:**
- High time-to-first-working-prototype for non-engineers
- No real-time, iterative AI pair-programmer that operates on a persistent project codebase
- No integrated preview environment — users must set up their own toolchain to see results
- Context loss: AI chat tools forget file state between sessions

---

## 2. Solution Space

NextGen Studio is an AI-powered full-stack application builder. Users describe what they want in natural language; the system generates, stores, and live-previews a real React/Vite codebase — iteratively, across sessions.

**Core design decisions:**

| Decision | Choice | Rationale |
|---|---|---|
| LLM access pattern | Tool-use (list_files, get_file_content) | LLM reads the real file tree, preventing hallucination of non-existent files |
| Session context | Last 10 messages + RAG similarity search | Balances token cost vs. full history; RAG surfaces relevant past code |
| File storage | MinIO object storage | S3-compatible, self-hosted, cost-efficient for binary and text assets |
| Code execution | Kubernetes pod per project namespace | Full isolation; supports any Node.js/Vite stack without a sandbox escape risk |
| Streaming | Server-Sent Events (SSE) | One-directional stream from server to browser; lower overhead than WebSockets for LLM token streaming |
| API routing | Spring Cloud Gateway | Centralised auth, rate limiting, and distributed tracing entry point |
| Embeddings DB | Qdrant Vector DB | Fast ANN search on code chunk embeddings for RAG |

---

## 3. High-Level System Architecture

```mermaid
graph TB
    subgraph Client["Client (Browser)"]
        UI[React Frontend]
    end

    subgraph Gateway["Spring Cloud API Gateway"]
        GW[API Gateway\nAuth · Rate Limiting · Zipkin Tracing]
    end

    subgraph AI["AI Pipeline"]
        INTEL[intelligence-service\nLLM Orchestration · Tool Use · Embedding Ingester]
        LLM[LLM API\nClaude / OpenAI]
    end

    subgraph Context["Context Services"]
        CHAT[chat-service\nSession History · Similarity Search]
        WS[workspace-service\nFile CRUD · Tree API]
    end

    subgraph Storage["Storage Layer"]
        MINIO[(MinIO Storage\nProject Files)]
        QDRANT[(Qdrant Vector DB\nCode Embeddings · RAG)]
        PG[(PostgreSQL\nUsers · Projects · Chat · Plans)]
        REDIS[(Redis\nRate Limiting · Sessions)]
    end

    subgraph Execution["Code Execution Service"]
        EXEC[execution-service\nK8s Namespace Manager]
        subgraph K8S["Kubernetes Cluster"]
            NS1["project-{id} namespace\nIngress + Service"]
            POD1["Vite Dev Server Pod\npackage.json · index.html\nApp.js · Nav.js · vite.config.js"]
        end
    end

    subgraph External["External Services"]
        STRIPE[Stripe Payments]
        ZIPKIN[Zipkin Tracing]
    end

    UI -->|prompt| GW
    GW -->|SSE stream tokens + file_created events| UI
    GW -->|preview url| UI

    GW --> INTEL

    INTEL -->|system prompt + session history + RAG context| LLM
    LLM -->|content stream / tool_calls| INTEL

    INTEL -->|fetch past 10 messages| CHAT
    INTEL -->|similarity search on prompt| CHAT
    CHAT -->|session history + relevant chunks| INTEL

    INTEL -->|list_files tool call| WS
    INTEL -->|get_file_content tool call| WS
    WS --> MINIO

    INTEL -->|similarity search| QDRANT
    QDRANT -->|top-k relevant code chunks| INTEL
    INTEL -->|chunk + embed + ingest new file| QDRANT

    INTEL -->|buffer + store generated file| MINIO
    INTEL -->|code.generated event| EXEC

    EXEC -->|new namespace| NS1
    NS1 --> POD1
    POD1 -.->|mounts project files| MINIO

    CHAT --> PG
    WS --> PG
    GW --> REDIS
    GW --> ZIPKIN
    GW --> STRIPE
```

---

## 4. Microservices Breakdown

```mermaid
graph LR
    subgraph auth-service
        A1[POST /auth/signup]
        A2[POST /auth/login]
        A3[GET /auth/me]
        A4[OAuth Provider]
    end

    subgraph chat-service
        C1[GET /sessions]
        C2[POST /sessions]
        C3[GET /sessions/:id/messages]
        C4[POST /sessions/:id/stream → SSE]
        C5[POST /sessions/:id/retry]
    end

    subgraph workspace-service
        W1[GET /projects/:id/files]
        W2[GET /projects/:id/files/:path]
        W3[GET /projects/:id/download]
        W4[PATCH /projects/:id/files]
    end

    subgraph intelligence-service
        I1[Prompt Builder]
        I2[RAG Retriever]
        I3[Tool Executor\nlist_files · get_file_content]
        I4[Code Writer\nBuffer → MinIO]
        I5[Embedding Ingester]
    end

    subgraph execution-service
        E1[POST /preview/start]
        E2[GET /preview/:id/url]
        E3[GET /preview/:id/logs → SSE]
        E4[DELETE /preview/:id]
        E5[K8s Namespace Controller]
    end

    subgraph project-service
        P1[POST /projects]
        P2[GET /projects]
        P3[PATCH /projects/:id]
        P4[DELETE /projects/:id]
        P5[POST /projects/:id/members]
    end
```

### Responsibility Matrix

| Service | Owns DB Tables | Reads from | Emits Events |
|---|---|---|---|
| auth-service | USER, SUBSCRIPTION, PLAN, USER_LOG | — | user.created, subscription.updated |
| chat-service | CHAT_SESSION, CHAT_MESSAGE | PROJECT | message.created |
| workspace-service | PROJECT_FILE | PROJECT | file.updated, file.created |
| intelligence-service | — | All (via APIs) | code.generated |
| execution-service | PREVIEW | PROJECT_FILE (MinIO) | preview.started, preview.terminated |
| project-service | PROJECT, PROJECT_MEMBER | USER | project.created |

---

## 5. AI Code Generation Flow

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant INTEL as intelligence-service
    participant CHAT as chat-service
    participant WS as workspace-service
    participant LLM as LLM API (Claude)
    participant QDRANT as Qdrant Vector DB
    participant MINIO as MinIO Storage

    User->>GW: POST /sessions/:id/stream {prompt}
    GW->>INTEL: Forward with JWT context

    INTEL->>CHAT: Fetch last 10 messages (session history)
    INTEL->>QDRANT: Similarity search on prompt embeddings
    QDRANT-->>INTEL: Top-k relevant code chunks

    INTEL->>LLM: Stream request\n[system prompt + history + RAG context + prompt]
    
    loop Tool Use Cycle
        LLM-->>INTEL: tool_call: list_files
        INTEL->>WS: GET /projects/:id/files
        WS-->>INTEL: File tree JSON
        INTEL->>LLM: tool_result: file tree

        LLM-->>INTEL: tool_call: get_file_content {path}
        INTEL->>MINIO: Download file bytes
        MINIO-->>INTEL: File content
        INTEL->>LLM: tool_result: file content
    end

    LLM-->>INTEL: code.generated event {filename, content}
    
    INTEL->>MINIO: Buffer + store generated file
    INTEL->>WS: PATCH /projects/:id/files (update metadata in PG)
    INTEL->>QDRANT: Chunk + embed + ingest new file content
    
    INTEL-->>GW: SSE: file_created | content_chunk | done
    GW-->>User: Stream tokens to browser
```

**System Prompt Strategy:**

The system prompt instructs the LLM to:
1. Always use `list_files` before writing to understand the existing project structure
2. Use `get_file_content` to read files before modifying them
3. Return changes as structured `code.generated` events (filename + full content)
4. Preserve existing code that is not related to the request
5. Generate idiomatic React + Vite + Tailwind code by default

---

## 6. Preview Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING : User requests preview
    PENDING --> PROVISIONING : execution-service creates K8s namespace
    PROVISIONING --> RUNNING : Pod starts, Vite dev server binds on :3000
    RUNNING --> RUNNING : User visits preview URL\ningress routes to pod
    RUNNING --> REBUILDING : New code.generated event received
    REBUILDING --> RUNNING : Hot module reload completes
    RUNNING --> TERMINATED : User stops preview / quota exceeded
    TERMINATED --> [*]
    PROVISIONING --> FAILED : Pod crash / image pull error
    FAILED --> [*]
```

```mermaid
graph LR
    subgraph K8s Namespace per Project
        SVC[Service\nport 80 → 3000]
        POD[Pod\nWebContainer / Node.js image]
        PVC[Mounted MinIO\nproject files]
    end

    ING[Ingress\nproject-456.preview.app] --> SVC
    SVC --> POD
    POD --- PVC

    EXEC[execution-service] -->|create namespace| K8s Namespace per Project
    EXEC -->|watch events| POD
    EXEC -->|stream logs| USER
```

**Preview URL pattern:** `https://project-{id}.preview.yourdomain.com`

Each namespace isolates network, filesystem, and process space. The execution-service watches pod events and streams logs back to the frontend via a dedicated SSE endpoint.

---

## 7. Entity-Relationship Diagram

```mermaid
erDiagram
    USER {
        bigint id PK
        string email UK
        string password_hash
        string name
        string avatar_url
        string provider
        string provider_id
        bool email_verified
        string stripe_customer_id
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    PLAN {
        bigint id PK
        string name
        string stripe_price_id
        bigint max_projects
        int max_tokens_per_day
        int max_previews
        bool unlimited
        bool active
    }

    SUBSCRIPTION {
        bigint id PK
        bigint user_id FK
        bigint plan_id FK
        string stripe_subscription_id
        string status
        timestamp current_period_start
        timestamp current_period_end
        bool cancel_at_period_end
        timestamp created_at
        timestamp updated_at
    }

    USER_LOG {
        bigint id PK
        bigint user_id FK
        bool active
        bool token_paid
        bigint duration
        string metadata
        timestamp created_at
    }

    PROJECT {
        bigint id PK
        string name
        string description
        bigint owner_id FK
        string thumbnail_url
        bool is_public
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    PROJECT_MEMBER {
        bigint id PK
        bigint project_id FK
        bigint user_id FK
        string role
        bigint invited_by FK
        timestamp created_at
    }

    PROJECT_FILE {
        bigint id PK
        bigint project_id FK
        string path
        string object_key
        bool is_public
        bigint updated_by FK
        timestamp created_at
        timestamp updated_at
        timestamp added_at
    }

    PREVIEW {
        bigint id PK
        bigint project_id FK
        string namespace
        string pod_name
        string object_key
        string status
        timestamp started_at
        timestamp terminated_at
        timestamp created_at
        timestamp rotated_at
    }

    CHAT_SESSION {
        bigint id PK
        bigint project_id FK
        bigint user_id FK
        string title
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    CHAT_MESSAGE {
        bigint id PK
        bigint session_id FK
        string role
        text content
        jsonb tool_calls
        string tool_info
        int tokens_used
        string model_id
        timestamp created_at
    }

    USER ||--o{ SUBSCRIPTION : "has"
    PLAN ||--o{ SUBSCRIPTION : "defines"
    USER ||--o{ USER_LOG : "generates"
    USER ||--o{ PROJECT : "owns"
    USER ||--o{ PROJECT_MEMBER : "member of"
    PROJECT ||--o{ PROJECT_MEMBER : "has members"
    PROJECT ||--o{ PROJECT_FILE : "contains"
    PROJECT ||--o{ PREVIEW : "has previews"
    PROJECT ||--o{ CHAT_SESSION : "has sessions"
    CHAT_SESSION ||--o{ CHAT_MESSAGE : "contains"
```

---

## 8. Class Diagrams

### 8.1 Domain Model — Core Entities

```mermaid
classDiagram
    class User {
        +Long id
        +String email
        +String passwordHash
        +String name
        +String avatarUrl
        +AuthProvider provider
        +String providerId
        +boolean emailVerified
        +String stripeCustomerId
        +LocalDateTime createdAt
        +LocalDateTime deletedAt
        +login(password) JWT
        +getActiveSubscription() Subscription
    }

    class Subscription {
        +Long id
        +User user
        +Plan plan
        +String stripeSubscriptionId
        +SubscriptionStatus status
        +LocalDateTime currentPeriodStart
        +LocalDateTime currentPeriodEnd
        +boolean cancelAtPeriodEnd
        +isActive() boolean
        +hasQuota(QuotaType) boolean
    }

    class Plan {
        +Long id
        +String name
        +String stripePriceId
        +int maxProjects
        +int maxTokensPerDay
        +int maxPreviews
        +boolean unlimited
        +boolean active
    }

    class Project {
        +Long id
        +String name
        +String description
        +User owner
        +String thumbnailUrl
        +boolean isPublic
        +List~ProjectMember~ members
        +List~ChatSession~ sessions
        +addMember(user, role) void
        +getFileTree() List~ProjectFile~
    }

    class ProjectMember {
        +Long id
        +Project project
        +User user
        +MemberRole role
        +User invitedBy
        +LocalDateTime createdAt
        +canEdit() boolean
        +isOwner() boolean
    }

    class ProjectFile {
        +Long id
        +Project project
        +String path
        +String objectKey
        +boolean isPublic
        +User updatedBy
        +getContent() String
        +getSize() long
    }

    class ChatSession {
        +Long id
        +Project project
        +User user
        +String title
        +List~ChatMessage~ messages
        +getLastNMessages(n) List~ChatMessage~
        +addMessage(role, content) ChatMessage
    }

    class ChatMessage {
        +Long id
        +ChatSession session
        +MessageRole role
        +String content
        +JsonNode toolCalls
        +String toolInfo
        +int tokensUsed
        +String modelId
        +LocalDateTime createdAt
    }

    class Preview {
        +Long id
        +Project project
        +String namespace
        +String podName
        +String objectKey
        +PreviewStatus status
        +LocalDateTime startedAt
        +LocalDateTime terminatedAt
        +getPreviewUrl() String
        +terminate() void
    }

    User "1" --> "0..1" Subscription
    Subscription "1" --> "1" Plan
    User "1" --> "0..*" Project : owns
    Project "1" --> "1..*" ProjectMember
    Project "1" --> "0..*" ProjectFile
    Project "1" --> "0..*" ChatSession
    Project "1" --> "0..*" Preview
    ChatSession "1" --> "0..*" ChatMessage
```

### 8.2 Intelligence Service — AI Pipeline

```mermaid
classDiagram
    class IntelligenceService {
        -LLMClient llmClient
        -RAGRetriever ragRetriever
        -ToolExecutor toolExecutor
        -CodeWriter codeWriter
        -ChatHistoryService historyService
        +streamChat(sessionId, prompt) Flux~SSEEvent~
        -buildSystemPrompt(project) String
        -buildContextWindow(session, ragChunks) List~Message~
    }

    class RAGRetriever {
        -QdrantClient qdrantClient
        -EmbeddingService embeddingService
        +retrieve(prompt, projectId, topK) List~CodeChunk~
        +ingest(file, projectId) void
        +deleteProject(projectId) void
    }

    class EmbeddingService {
        -LLMClient client
        +embed(text) float[]
        +embedBatch(texts) List~float[]~
    }

    class ToolExecutor {
        -WorkspaceServiceClient wsClient
        -MinIOClient minioClient
        +execute(toolCall) ToolResult
        +listFiles(projectId) FileTree
        +getFileContent(projectId, path) String
    }

    class CodeWriter {
        -MinIOClient minioClient
        -WorkspaceServiceClient wsClient
        -RAGRetriever ragRetriever
        +writeFile(projectId, path, content) void
        +bufferAndFlush(event) void
    }

    class LLMClient {
        -String apiKey
        -String model
        +streamWithTools(messages, tools) Flux~Delta~
        +countTokens(messages) int
    }

    class SSEEvent {
        +EventType type
        +String data
        +String filename
    }

    IntelligenceService --> LLMClient
    IntelligenceService --> RAGRetriever
    IntelligenceService --> ToolExecutor
    IntelligenceService --> CodeWriter
    RAGRetriever --> EmbeddingService
    RAGRetriever --> LLMClient
    IntelligenceService ..> SSEEvent : emits
```

### 8.3 Execution Service — Kubernetes Controller

```mermaid
classDiagram
    class ExecutionService {
        -K8sNamespaceController namespaceCtrl
        -PreviewRepository previewRepo
        -MinIOClient minioClient
        +startPreview(projectId) Preview
        +stopPreview(previewId) void
        +getPreviewUrl(previewId) String
        +streamLogs(previewId) Flux~String~
    }

    class K8sNamespaceController {
        -KubernetesClient k8sClient
        +createNamespace(projectId) String
        +deployPod(namespace, config) Pod
        +createService(namespace) Service
        +createIngress(namespace, host) Ingress
        +deleteNamespace(namespace) void
        +watchPodEvents(namespace) Flux~PodEvent~
        +streamPodLogs(namespace, podName) Flux~String~
    }

    class PodConfig {
        +String image
        +String projectId
        +Map~String,String~ env
        +List~VolumeMount~ mounts
        +ResourceRequirements resources
    }

    class PreviewRepository {
        +save(preview) Preview
        +findByProjectId(projectId) List~Preview~
        +findActiveByProjectId(projectId) Optional~Preview~
        +updateStatus(id, status) void
    }

    ExecutionService --> K8sNamespaceController
    ExecutionService --> PreviewRepository
    K8sNamespaceController ..> PodConfig : uses
```

---

## 9. Sequence Diagrams

### 9.1 User Registration & Auth

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant AUTH as auth-service
    participant PG as PostgreSQL
    participant STRIPE as Stripe

    User->>GW: POST /auth/signup {email, password, name}
    GW->>AUTH: Forward request

    AUTH->>PG: Check email uniqueness
    PG-->>AUTH: Not found

    AUTH->>PG: INSERT USER (hashed password)
    PG-->>AUTH: user.id

    AUTH->>STRIPE: Create Stripe customer
    STRIPE-->>AUTH: stripe_customer_id

    AUTH->>PG: UPDATE USER stripe_customer_id
    AUTH->>PG: INSERT SUBSCRIPTION (FREE plan)

    AUTH-->>GW: 201 {user, access_token}
    GW-->>User: JWT + user profile
```

### 9.2 Chat Stream with Code Generation

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant INTEL as intelligence-service
    participant CHAT as chat-service
    participant LLM as LLM API
    participant QDRANT as Qdrant
    participant MINIO as MinIO
    participant WS as workspace-service
    participant EXEC as execution-service

    User->>GW: POST /sessions/:id/stream {prompt}
    GW->>GW: Validate JWT + quota check

    GW->>INTEL: Stream(sessionId, userId, prompt)
    INTEL->>CHAT: GetLastNMessages(sessionId, 10)
    INTEL->>QDRANT: SimilaritySearch(embed(prompt), projectId, k=5)
    QDRANT-->>INTEL: Relevant code chunks

    INTEL->>LLM: StreamChat([system, history, rag, prompt], tools=[list_files, get_file_content])

    loop Tool calls until LLM generates code
        LLM->>INTEL: tool_call: list_files
        INTEL->>WS: GetFileTree(projectId)
        WS-->>INTEL: {files: [...]}
        INTEL->>LLM: tool_result: file tree

        opt LLM needs file content
            LLM->>INTEL: tool_call: get_file_content {path}
            INTEL->>MINIO: GetObject(objectKey)
            MINIO-->>INTEL: file bytes
            INTEL->>LLM: tool_result: content
        end
    end

    LLM->>INTEL: SSE: code.generated {file, content}
    INTEL->>MINIO: PutObject(projectId/path, content)
    INTEL->>WS: UpsertFileMetadata(projectId, path, objectKey)
    INTEL->>QDRANT: ChunkEmbedIngest(projectId, path, content)
    INTEL->>CHAT: SaveMessage(session, ASSISTANT, content)

    Note over EXEC: If preview is RUNNING
    INTEL->>EXEC: NotifyFileChange(projectId)
    EXEC->>EXEC: Trigger HMR in running pod

    INTEL-->>GW: SSE stream: tokens + file_created events
    GW-->>User: Live streamed response
```

### 9.3 Preview Start

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant EXEC as execution-service
    participant PG as PostgreSQL
    participant MINIO as MinIO
    participant K8S as Kubernetes

    User->>GW: POST /preview/start {projectId}
    GW->>EXEC: StartPreview(projectId, userId)

    EXEC->>PG: Check active preview for project
    PG-->>EXEC: None found

    EXEC->>PG: INSERT PREVIEW (status=PENDING)
    EXEC->>K8S: CreateNamespace(project-{id})
    EXEC->>MINIO: List all files for project
    MINIO-->>EXEC: Object list

    EXEC->>K8S: CreatePod(namespace, image, env, volumeMount→MinIO)
    EXEC->>K8S: CreateService(namespace, port=3000)
    EXEC->>K8S: CreateIngress(namespace, host=project-{id}.preview.app)

    K8S-->>EXEC: Pod event: RUNNING
    EXEC->>PG: UPDATE PREVIEW status=RUNNING, pod_name, namespace

    EXEC-->>GW: {previewId, url: "https://project-{id}.preview.app"}
    GW-->>User: Preview URL
```

---

## 10. Infrastructure & Deployment

```mermaid
graph TB
    subgraph Internet
        USER[Browser Client]
        STRIPE_EXT[Stripe Webhook]
    end

    subgraph Load Balancer
        LB[Nginx / Cloud LB\nTLS Termination]
    end

    subgraph K8s Cluster
        subgraph System Namespace
            GW_POD[API Gateway Pod\nSpring Cloud Gateway]
            AUTH_POD[auth-service Pod]
            INTEL_POD[intelligence-service Pod]
            CHAT_POD[chat-service Pod]
            WS_POD[workspace-service Pod]
            EXEC_POD[execution-service Pod]
            PROJ_POD[project-service Pod]
        end

        subgraph Data Namespace
            PG_POD[PostgreSQL StatefulSet]
            MINIO_POD[MinIO StatefulSet]
            QDRANT_POD[Qdrant StatefulSet]
            REDIS_POD[Redis StatefulSet]
        end

        subgraph Observability Namespace
            ZIPKIN_POD[Zipkin Pod]
        end

        subgraph Project Namespaces
            NS1[project-abc namespace\nVite Dev Server Pod]
            NS2[project-xyz namespace\nVite Dev Server Pod]
        end
    end

    USER --> LB
    STRIPE_EXT --> LB
    LB --> GW_POD
    GW_POD --> AUTH_POD
    GW_POD --> INTEL_POD
    GW_POD --> CHAT_POD
    GW_POD --> WS_POD
    GW_POD --> EXEC_POD
    GW_POD --> PROJ_POD
    EXEC_POD --> NS1
    EXEC_POD --> NS2
    AUTH_POD & CHAT_POD & WS_POD & PROJ_POD --> PG_POD
    INTEL_POD & WS_POD --> MINIO_POD
    INTEL_POD --> QDRANT_POD
    GW_POD --> REDIS_POD
    GW_POD --> ZIPKIN_POD
```

### Technology Stack Summary

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, TailwindCSS, TypeScript |
| API Gateway | Spring Cloud Gateway |
| Backend Services | Spring Boot 3 (Java 21) |
| LLM | Claude 3.5 Sonnet / Claude 4 (Anthropic API) |
| Vector DB | Qdrant |
| Relational DB | PostgreSQL 16 |
| Object Storage | MinIO (S3-compatible) |
| Cache / Rate Limit | Redis 7 |
| Container Runtime | Kubernetes (K3s / EKS / GKE) |
| Preview Runtime | Node.js 20 Alpine + Vite |
| Payments | Stripe |
| Tracing | Zipkin |
| Streaming | SSE (Server-Sent Events) |

---

## 11. Feature Surface Map

```mermaid
mindmap
  root((Lovable Clone))
    Auth
      POST /auth/signup
      POST /auth/login
      GET /auth/me
      OAuth Provider
    Projects
      POST /projects
      GET /projects
      PATCH /projects/:id
      DELETE /projects/:id
      POST /projects/:id/members
    AI Code Generation
      POST /sessions
      GET /sessions
      GET /sessions/:id/messages
      POST /sessions/:id/stream
      POST /sessions/:id/retry
    Files
      GET /projects/:id/files
      GET /projects/:id/files/:path
      GET /projects/:id/download.zip
    Preview
      POST /preview/start
      GET /preview/:id/url
      GET /preview/:id/logs
      DELETE /preview/:id
    Billing
      Stripe Checkout
      Webhook Handler
      Quota Enforcement
      Plan FREE vs PRO
    Observability
      Zipkin Distributed Tracing
      Redis Rate Limiting
      Pod Log Streaming
```

### Quota Rules

| Quota | FREE | PRO |
|---|---|---|
| Max Projects | 3 | Unlimited |
| Tokens / day | 50,000 | 2,000,000 |
| Concurrent Previews | 1 | 5 |
| Team Members per Project | 1 (owner only) | 10 |
| File Download (zip) | Yes | Yes |

---

*Last updated: 2026-05-24*
