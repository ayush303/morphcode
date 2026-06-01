# API Reference

Base: `http://localhost:8082` | Auth: `Authorization: Bearer <jwt>`

## Auth

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/signup` | No | Body: `{username, password, name}` → `AuthResponse{token}` |
| POST | `/api/auth/login` | No | Body: `{username, password}` → `AuthResponse{token}` |
| GET | `/api/auth/me` | Yes | → `UserProfileResponse{id,username,name,avatarUrl}` — **BROKEN**: hardcodes `userId=1L`, `avatarUrl` always null |

## Projects

| Method | Path | Guard | Notes |
|---|---|---|---|
| GET | `/api/projects` | auth | → `List<ProjectSummaryResponse>` (caller's memberships) |
| POST | `/api/projects` | auth | Body: `{name, isPublic}` → `ProjectResponse`; checks subscription limit; copies template; adds caller as OWNER |
| GET | `/api/projects/{id}` | `canViewProject` | → `ProjectResponse{id,name,isPublic,createdAt,updatedAt}` |
| PATCH | `/api/projects/{id}` | `canEditProject` | Body: `{name, isPublic}` → `ProjectResponse` |
| DELETE | `/api/projects/{id}` | `canDeleteProject` | Sets `deletedAt` → 204 |

## Project Members

| Method | Path | Guard | Notes |
|---|---|---|---|
| GET | `/api/projects/{projectId}/members` | `canViewMembers` | → `List<MemberResponse{userId,username,name,projectRole,invitedAt,acceptedAt}>` |
| POST | `/api/projects/{projectId}/members` | `canManageMembers` | Body: `{username, projectRole}` — sets `invitedAt`; no email sent; `acceptedAt` stays null (no acceptance flow) |
| PATCH | `/api/projects/{projectId}/members` | `canManageMembers` | Body: `{userId, projectRole}` |
| DELETE | `/api/projects/{projectId}/members` | `canManageMembers` | Body: `{userId}` |

## Files

| Method | Path | Notes |
|---|---|---|
| GET | `/api/projects/{projectId}/files` | → `FileTreeResponse{files: FileNode[]}` — DB metadata only |
| GET | `/api/projects/{projectId}/files/content?path={path}` | → `FileContentResponse{path, content}` — reads from MinIO |

## Chat

**POST `/api/chat/stream`** — auth, returns `text/event-stream`
Body: `{message, projectId}` | Chunks: `StreamResponse{content}`
- `canEditProject` enforced on service method
- Creates `ChatSession` if none for `(projectId, userId)`
- DB writes async after stream ends (see architecture.md)

**GET `/api/chat/projects/{projectId}`** — auth
Returns `List<ChatResponse>` with nested events. `content` on ASSISTANT is hardcoded placeholder; real output is in `events[]`:
```json
{"role":"ASSISTANT","content":"Assistant Message here...","events":[
  {"type":"THOUGHT","content":"Thought for 3s","sequenceOrder":0},
  {"type":"MESSAGE","content":"...","sequenceOrder":1},
  {"type":"FILE_EDIT","filePath":"src/App.tsx","content":"...","sequenceOrder":2}
]}
```

## Plans & Billing

| Method | Path | Notes |
|---|---|---|
| GET | `/api/plans` | **BROKEN**: always `[]` — `PlanRepository` never called |
| GET | `/api/me/subscription` | → `SubscriptionResponse{id,status,plan,periodEnd,tokensUsedThisCycle}` — `tokensUsedThisCycle` always null; `plan` null if no active sub |
| POST | `/api/payments/checkout` | Body: `{planId}` → `CheckoutResponse{checkoutUrl}` |
| POST | `/api/payments/portal` | Requires `stripeCustomerId` on User → `PortalResponse{portalUrl}` |
| POST | `/webhooks/payment` | Public; `Stripe-Signature` verified. See [Billing](billing.md) |

## Usage

**GET `/api/usage/today`** — **BROKEN**: returns `null` directly (NPE in dispatcher). Service call commented out.

## Error Responses

`GlobalExceptionHandler` → `ApiError{status, message, timestamp}`

| Exception | HTTP |
|---|---|
| `ResourceNotFoundException` | 404 |
| `BadRequestException` | 400 |
| `AccessDeniedException` | 403 |
| Unhandled | 500 |
