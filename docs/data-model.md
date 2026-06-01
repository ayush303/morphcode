# Data Model

DB: PostgreSQL `pgvector-test` (pgvector planned, unused) | DDL: `spring.jpa.hibernate.ddl-auto=update`

## Entities

### User — `users`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | auto-increment |
| username | String | stores email |
| password | String | BCrypt hash |
| name | String | display name |
| stripeCustomerId | String UNIQUE | set on first Stripe checkout |
| createdAt | Instant | `@CreationTimestamp` |
| updatedAt | Instant | `@UpdateTimestamp` |
| deletedAt | Instant? | soft delete |

Implements `UserDetails`; `getAuthorities()` returns empty list.

### Project — `projects`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| name | String NOT NULL | |
| isPublic | Boolean | default false |
| createdAt/updatedAt | Instant | timestamps |
| deletedAt | Instant? | soft delete |

No FK to User; ownership via `ProjectMember(OWNER)`. Indexes: `updated_at DESC`, `deleted_at+updated_at DESC`, `deleted_at`.

### ProjectFile — `project_files`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| project | Project | `@ManyToOne(LAZY)` |
| path | String | relative, e.g. `src/App.tsx` |
| minioObjectKey | String | `{projectId}/{path}` |
| createdAt/updatedAt | Instant | |

MinIO key: `projects/{projectId}/{path}`

### ProjectMemberId — `@Embeddable`
`projectId: Long`, `userId: Long`

### ProjectMember — `project_members`
| Field | Type | Notes |
|---|---|---|
| id | ProjectMemberId | `@EmbeddedId` |
| project | Project | `@ManyToOne` |
| user | User | `@ManyToOne` |
| projectRole | ProjectRole | OWNER, EDITOR, VIEWER |
| invitedAt | Instant | |
| acceptedAt | Instant? | stays null — no acceptance flow |

### ChatSessionId — **missing `@Embeddable`** (bug)
`projectId: Long`, `userId: Long` — see [Known Issues](known-issues.md#8)

### ChatSession — `chat_sessions`
| Field | Type | Notes |
|---|---|---|
| id | ChatSessionId | `@EmbeddedId` |
| project | Project | `@ManyToOne` |
| user | User | `@ManyToOne` |
| deletedAt | Instant? | soft delete |

One session per `(projectId, userId)`.

### ChatMessage — `chat_messages`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| chatSession | ChatSession | `@ManyToOne(LAZY)` |
| role | MessageRole | USER, ASSISTANT |
| content | String text | USER: actual message; ASSISTANT: hardcoded placeholder |
| events | List\<ChatEvent\> | `@OneToMany @OrderBy("sequenceOrder ASC")` — ASSISTANT only |
| tokensUsed | Integer | default 0 |
| createdAt | Instant | |

### ChatEvent — `chat_events`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| chatMessage | ChatMessage | `@ManyToOne(LAZY)` |
| type | ChatEventType | THOUGHT, MESSAGE, FILE_EDIT, TOOL_LOG |
| sequenceOrder | Integer NOT NULL | ordering within message |
| content | String text | |
| filePath | String? | FILE_EDIT only |
| metadata | String text | TOOL_LOG: raw `args` string (comma-sep paths) |

### Plan — `plan`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| name | String | |
| stripePriceId | String UNIQUE | |
| maxProjects | Integer | |
| maxTokensPerDay | Integer | |
| maxPreviews | Integer | |
| unlimitedAi | Boolean | if true, ignore `maxTokensPerDay` |
| active | Boolean | |

### Subscription — `subscription`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| user | User | `@ManyToOne(LAZY)` |
| plan | Plan | `@ManyToOne(LAZY)` |
| status | SubscriptionStatus | ACTIVE, TRIALING, CANCELED, PAST_DUE, INCOMPLETE |
| stripeSubscriptionId | String | |
| currentPeriodStart/End | Instant | |
| cancelAtPeriodEnd | Boolean | default false |
| createdAt/updatedAt | Instant | |

### UsageLog — `usage_logs`
| Field | Type | Notes |
|---|---|---|
| id | Long PK | |
| userId | Long NOT NULL | column `user_id` |
| date | LocalDate NOT NULL | |
| tokensUsed | Integer | |

UNIQUE `(user_id, date)` — one row per user per day. Upserted by `UsageService.recordTokenUsage()`.

### Preview — NOT a JPA entity
No `@Entity`, no repository, no table. Plain POJO. Fields: `id, project, namespace, previewUrl, podName, startedAt, terminatedAt, createdAt, status(PreviewStatus)`. Feature unimplemented.

## Enums

| Enum | Values |
|---|---|
| `MessageRole` | USER, ASSISTANT |
| `ChatEventType` | THOUGHT, MESSAGE, FILE_EDIT, TOOL_LOG |
| `ProjectRole` | OWNER, EDITOR, VIEWER |
| `ProjectPermission` | VIEW, EDIT, DELETE, VIEW_MEMBERS, MANAGE_MEMBERS |
| `SubscriptionStatus` | ACTIVE, TRIALING, CANCELED, PAST_DUE, INCOMPLETE |
| `PreviewStatus` | (unused) |
