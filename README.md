<p align="center">
  <img src="./banner.svg" alt="MORPHCODE" width="900"/>
</p>

<p align="center">
  <strong>AI-powered browser-based React IDE</strong><br/>
  Chat with an LLM to build, edit, and preview React apps in real time.
</p>

---

## What is MorphCode?

MorphCode is a collaborative React IDE that lives in your browser. You create a project, get a full React 18 + TypeScript + Vite + Tailwind + daisyUI codebase out of the box, and then describe changes in plain English. The AI reads your files, rewrites them, and streams the response back to you live — no copy-pasting, no context switching.

**What you can do:**
- Sign up and log in with a secure JWT-based account
- Create multiple React projects, each pre-seeded from a production-ready starter template
- Chat with the AI to add features, fix bugs, redesign UI, or refactor code
- Browse the live file tree and read any file at any time
- Invite teammates as editors or viewers and collaborate on the same project
- Manage your subscription and track daily AI token usage

---

## Prerequisites

| Requirement | Version / Notes |
|---|---|
| Java | 17 |
| Maven | Bundled — use `./mvnw`, no install needed |
| Docker | For running PostgreSQL and MinIO |
| OpenRouter API key | [openrouter.ai](https://openrouter.ai) — used to call Gemini Flash |
| Stripe account | Optional for local development |

---

## Step 1 — Start Infrastructure

The repo includes a Docker Compose file that starts PostgreSQL and MinIO:

```bash
docker-compose -f services.docker-compose.yaml up -d
```

This starts:

| Service | URL | Credentials |
|---|---|---|
| PostgreSQL | `localhost:9010` | user: `user` / password: `password` / db: `pgvector-test` |
| MinIO API | `http://localhost:9000` | user: `minioadmin` / password: `minioadmin123` |
| MinIO Console | `http://localhost:9001` | Same as above |

> The database schema is created and updated automatically by Hibernate on first run (`ddl-auto=update`). No SQL scripts to run.

---

## Step 2 — Set Up MinIO Buckets

MorphCode stores all project files in MinIO. You must create two buckets and seed the starter template **before** running the app for the first time. Without this, project creation will fail.

**Open the MinIO Console at [http://localhost:9001](http://localhost:9001)** and log in with `minioadmin` / `minioadmin123`.

### Create the two buckets

1. Go to **Buckets → Create Bucket**
2. Create a bucket named **`starter-projects`**
3. Create a bucket named **`projects`**

### Upload the React starter template

The `starter-projects` bucket must contain the React + TypeScript + Vite + Tailwind + daisyUI template under a specific prefix. Upload all template files so the object keys follow this pattern:

```
starter-projects/
└── react-vite-tailwind-daisyui-starter/
    ├── index.html
    ├── package.json
    ├── vite.config.ts
    ├── tailwind.config.js
    ├── src/
    │   ├── App.tsx
    │   ├── main.tsx
    │   └── index.css
    └── ...
```

In the MinIO Console:
1. Open the `starter-projects` bucket
2. Click **Upload** and upload your template files, preserving the folder structure under the prefix `react-vite-tailwind-daisyui-starter/`

When a user creates a new project, MorphCode copies every file from this prefix into a new `projects/{projectId}/` path automatically.

---

## Step 3 — Configure Environment Variables

Create a `.env` file in the project root. MorphCode reads it automatically via `spring-dotenv`.

```bash
touch .env
```

Add the following — only these four variables are required:

```env
# Required: OpenRouter API key (https://openrouter.ai/keys)
OPENROUTER_API_KEY=sk-or-...

# Required: JWT signing secret — must be at least 32 characters
JWT_SECRET_KEY=replace-this-with-a-long-random-secret-string

# Required for billing: Stripe secret key
STRIPE_API_SECRET=sk_test_...

# Required for billing: Stripe webhook signing secret
STRIPE_WEBHOOK_SECRET=whsec_...
```

> **Never commit your `.env` file.** It is listed in `.gitignore`.

Everything else (database URL, MinIO URL, MinIO credentials, bucket name, server port) is already configured with correct defaults in `src/main/resources/application.properties` and does not need to be in `.env`.

---

## Step 4 — Run the App

```bash
./mvnw spring-boot:run
```

The API starts on **`http://localhost:8082`**.

On first startup you should see Hibernate creating the tables and the app listening on port 8082. If you see connection errors, confirm Docker containers are running:

```bash
docker ps
```

---

## Running Tests

```bash
# Run the full test suite
./mvnw test

# Run a specific test class
./mvnw test -Dtest=LlmResponseParserTest

# Clean build before running tests
./mvnw clean test
```

---

## How to Use MorphCode

All API requests (except signup and login) require an `Authorization` header:

```
Authorization: Bearer <your-jwt-token>
```

---

### 1. Create an Account

```http
POST /api/auth/signup
Content-Type: application/json

{
  "username": "you@example.com",
  "name": "Your Name",
  "password": "yourpassword"
}
```

**Response**
```json
{
  "token": "eyJhbGci...",
  "user": {
    "id": 1,
    "email": "you@example.com",
    "name": "Your Name"
  }
}
```

Copy the `token` value — you will use it in the `Authorization` header for every other request.

---

### 2. Log In

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "you@example.com",
  "password": "yourpassword"
}
```

Returns the same shape as signup with a fresh JWT token.

---

### 3. Create a Project

Each project is automatically seeded from the React + TypeScript + Vite + Tailwind + daisyUI starter template the moment it is created.

```http
POST /api/projects
Authorization: Bearer <your-token>
Content-Type: application/json

{
  "name": "My First App"
}
```

**Response**
```json
{
  "id": 5,
  "name": "My First App",
  "createdAt": "2026-06-01T10:00:00Z",
  "updatedAt": "2026-06-01T10:00:00Z"
}
```

Note the `id` — you will include it in every file and chat request.

---

### 4. Browse Your Project Files

**Get the full file tree:**
```http
GET /api/projects/5/files
Authorization: Bearer <your-token>
```

**Response**
```json
{
  "files": [
    { "path": "src/App.tsx" },
    { "path": "src/main.tsx" },
    { "path": "index.html" }
  ]
}
```

**Read a specific file:**
```http
GET /api/projects/5/files/content?path=src/App.tsx
Authorization: Bearer <your-token>
```

**Response**
```json
{
  "path": "src/App.tsx",
  "content": "import React from 'react';\n..."
}
```

---

### 5. Chat with the AI

This is the core of MorphCode. Describe what you want in plain English. The AI reads your files and writes the changes back — all streamed live over SSE.

```http
POST /api/chat/stream
Authorization: Bearer <your-token>
Content-Type: application/json
Accept: text/event-stream

{
  "message": "Add a dark mode toggle button to the navbar",
  "projectId": 5
}
```

The response is a **Server-Sent Events** stream. Each event delivers a chunk of the AI's output:

```
data: {"text":"I'll add a dark mode toggle. Let me check App.tsx first."}

data: {"text":"<tool args=\"src/App.tsx\">Reading App.tsx...</tool>"}

data: {"text":"<message>Here is the updated App.tsx with the toggle:</message>"}

data: {"text":"<file path=\"src/App.tsx\">import React, { useState } from 'react';\n..."}
```

When the stream ends, the AI's file edits are automatically saved to MinIO and your file tree is updated in the database.

**What the AI does in a single turn:**

| Step | Description |
|---|---|
| Read files | Uses the `read_files` tool to fetch file content from MinIO |
| Plan | Outputs a `<message>` explaining what it will change |
| Write files | Outputs `<file path="...">` tags with the complete new file content |
| Confirm | Outputs a final `<message>` summarising what was done |

---

### 6. View Chat History

```http
GET /api/chat/projects/5
Authorization: Bearer <your-token>
```

**Response**
```json
[
  {
    "id": 1,
    "role": "USER",
    "content": "Add a dark mode toggle button to the navbar",
    "tokensUsed": 42,
    "createdAt": "2026-06-01T10:05:00Z",
    "events": []
  },
  {
    "id": 2,
    "role": "ASSISTANT",
    "content": null,
    "tokensUsed": 980,
    "createdAt": "2026-06-01T10:05:04Z",
    "events": [
      { "type": "THOUGHT",    "sequenceOrder": 0, "content": "Thought for 3s",         "filePath": null },
      { "type": "TOOL_LOG",   "sequenceOrder": 1, "content": "Reading App.tsx...",      "filePath": null },
      { "type": "MESSAGE",    "sequenceOrder": 2, "content": "Here is the updated...", "filePath": null },
      { "type": "FILE_EDIT",  "sequenceOrder": 3, "content": "import React...",         "filePath": "src/App.tsx" }
    ]
  }
]
```

**Event types:**

| Type | Meaning |
|---|---|
| `THOUGHT` | Time the AI spent before producing its first token |
| `TOOL_LOG` | A file-read the AI performed |
| `MESSAGE` | The AI's explanation or plan in Markdown |
| `FILE_EDIT` | A file the AI wrote — `filePath` contains the path, `content` contains the full new file |

---

### 7. Invite Teammates

You must be the project **OWNER** to manage members.

**Invite someone:**
```http
POST /api/projects/5/members
Authorization: Bearer <your-token>
Content-Type: application/json

{
  "username": "teammate@example.com",
  "role": "EDITOR"
}
```

**Available roles:**

| Role | View files | Edit via AI | Delete project | Manage members |
|---|---|---|---|---|
| `OWNER` | Yes | Yes | Yes | Yes |
| `EDITOR` | Yes | Yes | No | No |
| `VIEWER` | Yes | No | No | No |

**List members:**
```http
GET /api/projects/5/members
Authorization: Bearer <your-token>
```

**Change a member's role:**
```http
PATCH /api/projects/5/members/{userId}
Authorization: Bearer <your-token>
Content-Type: application/json

{ "role": "VIEWER" }
```

**Remove a member:**
```http
DELETE /api/projects/5/members/{userId}
Authorization: Bearer <your-token>
```

---

### 8. Manage Your Projects

**List all projects you belong to:**
```http
GET /api/projects
Authorization: Bearer <your-token>
```

**Get a single project:**
```http
GET /api/projects/5
Authorization: Bearer <your-token>
```

**Rename a project:**
```http
PATCH /api/projects/5
Authorization: Bearer <your-token>
Content-Type: application/json

{ "name": "Renamed App" }
```

**Delete a project** (soft delete — data is kept):
```http
DELETE /api/projects/5
Authorization: Bearer <your-token>
```

---

### 9. Subscription and Billing

**View available plans:**
```http
GET /api/plans
Authorization: Bearer <your-token>
```

**View your current subscription:**
```http
GET /api/me/subscription
Authorization: Bearer <your-token>
```

**Start a Stripe Checkout to upgrade:**
```http
POST /api/payments/checkout
Authorization: Bearer <your-token>
Content-Type: application/json

{ "planId": 2 }
```

Returns `{ "checkoutUrl": "https://checkout.stripe.com/..." }` — open it in the browser.

**Open the Stripe billing portal** to cancel or change your plan:
```http
POST /api/payments/portal
Authorization: Bearer <your-token>
```

Returns `{ "portalUrl": "https://billing.stripe.com/..." }`.

---

## API Quick Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/signup` | No | Create account |
| `POST` | `/api/auth/login` | No | Log in |
| `GET` | `/api/auth/me` | Yes | Get your profile |
| `GET` | `/api/projects` | Yes | List all your projects |
| `POST` | `/api/projects` | Yes | Create a project |
| `GET` | `/api/projects/:id` | Yes | Get a project |
| `PATCH` | `/api/projects/:id` | Yes | Rename a project |
| `DELETE` | `/api/projects/:id` | Yes | Delete a project |
| `GET` | `/api/projects/:id/files` | Yes | List project files |
| `GET` | `/api/projects/:id/files/content?path=` | Yes | Read a file |
| `POST` | `/api/chat/stream` | Yes | Stream AI response (SSE) |
| `GET` | `/api/chat/projects/:id` | Yes | View chat history |
| `GET` | `/api/projects/:id/members` | Yes | List members |
| `POST` | `/api/projects/:id/members` | Yes | Invite a member |
| `PATCH` | `/api/projects/:id/members/:userId` | Yes | Update member role |
| `DELETE` | `/api/projects/:id/members/:userId` | Yes | Remove a member |
| `GET` | `/api/plans` | Yes | List available plans |
| `GET` | `/api/me/subscription` | Yes | View your subscription |
| `POST` | `/api/payments/checkout` | Yes | Start Stripe checkout |
| `POST` | `/api/payments/portal` | Yes | Open billing portal |

---

## Project Structure

```
src/main/java/com/morphcode/ai/
├── config/          # AiConfig, CorsConfig, PaymentConfig, StorageConfig
├── controller/      # REST controllers
├── dto/             # Request / response records
├── entity/          # JPA entities
├── enums/           # Roles, permissions, event types, statuses
├── error/           # GlobalExceptionHandler and custom exceptions
├── llm/             # Prompt, XML parser, Spring AI advisor, tool
├── mapper/          # MapStruct interfaces
├── repository/      # Spring Data JPA repositories
├── security/        # JWT filter, AuthUtil, SecurityExpressions
└── service/         # Service interfaces and implementations
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.3.0 |
| AI | Spring AI 1.0.0, OpenRouter → Gemini Flash |
| Database | PostgreSQL 18, Spring Data JPA, Hibernate |
| Storage | MinIO |
| Security | JJWT 0.12.6, BCrypt |
| Payments | Stripe SDK |
| Mapping | MapStruct |
| Streaming | Server-Sent Events via Project Reactor Flux |

---

## Known Limitations

- `GET /api/auth/me` always returns user ID 1's profile (hardcoded — does not use the JWT).
- `GET /api/plans` always returns an empty list (repository not wired in `PlanServiceImpl`).
- Daily AI token limits are implemented but commented out — currently unenforced.
- `GET /api/usage/today` endpoint exists but is not implemented (returns `null`).
- The `Preview` feature has an entity defined but no API or repository.
