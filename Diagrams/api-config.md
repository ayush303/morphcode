# MorphCode API Configuration

## Core APIs

### Auth

| Endpoint | Method | Path |
|---|---|---|
| Login | POST | `/api/auth/login` |
| Sign Up | POST | `/api/auth/signup` |
| Get Profile | GET | `/api/auth/me` |

### Projects

| Endpoint | Method | Path |
|---|---|---|
| Create / Read / Update / Delete Project | CRUD | `/api/projects/{id}` |
| Get All Projects | GET | `/api/projects` |

### Files

| Endpoint | Method | Path |
|---|---|---|
| Get File Tree + Metadata | GET | `/api/projects/{id}/files` |
| Download Single File (path encoded) | GET | `/api/projects/{id}/files/**` |
| Download All Files as ZIP | GET | `/api/projects/{id}/download-zip` |

---

## Additional APIs

### Sharing & Permissions

| Endpoint | Method | Path |
|---|---|---|
| Get All Members | GET | `/api/projects/{id}/members` |
| Invite by Email | POST | `/api/projects/{id}/members` |
| Change Role of a Member | PATCH | `/api/projects/{id}/members/{userId}` |
| Remove Member | DELETE | `/api/projects/{id}/members/{userId}` |

### Subscription & Billing

| Endpoint | Method | Path |
|---|---|---|
| List Available Plans (FREE, PRO) | GET | `/api/plans` |
| Current Plan + Limits + Next Billing Date | GET | `/api/me/subscription` |
| Create Stripe Checkout Session → redirect | POST | `/api/stripe/checkout` |
| Open Customer Portal on Stripe | POST | `/api/stripe/portal` |

### Usage & Quotas

| Endpoint | Method | Path |
|---|---|---|
| Tokens Used, Projects Created, Previews Running | GET | `/api/usage/today` |
| Current Limits Based on Plan | GET | `/api/usage/limits` |

### Chat & AI Generation

| Endpoint | Method | Path |
|---|---|---|
| List Chat Sessions | GET | `/api/projects/{id}/chat-sessions` |
| Create New Chat Session | POST | `/api/projects/{id}/chat-sessions` |
| Load Full Chat History | GET | `/api/chat/sessions/{sessionId}/messages` |
| Chat Stream | SSE; POST | `/api/chat/stream` |

### Preview & Runner

| Endpoint | Method | Path |
|---|---|---|
| Start Live Preview → returns `{ previewUrl, status }` | POST | `/api/projects/{id}/preview` |
| Poll Preview Status (CREATING → RUNNING → FAILED) | GET | `/api/previews/{previewId}/status` |
| Preview Logs — live npm install / Vite HMR | SSE | `/api/previews/{previewId}/logs` |
| Stop and Delete Preview (cleanup) | DELETE | `/api/previews/{previewId}` |
