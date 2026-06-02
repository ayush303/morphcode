# Test Documentation

## Overview

| Category | Files | Test Cases |
|---|---|---|
| Unit — Enums | 1 | 6 |
| Unit — Error | 3 | 10 |
| Unit — Security | 3 | 16 |
| Unit — LLM | 1 | 11 |
| Unit — Services | 6 | 37 |
| Component — Controllers | 4 | 23 |
| **Total** | **18** | **103** |

All unit tests use `@ExtendWith(MockitoExtension.class)` with `@Mock` / `@InjectMocks`.  
All controller component tests use `@WebMvcTest` with Spring Security auto-configuration excluded, isolating pure HTTP layer behavior.

---

## Enums

### `ProjectRoleTest`
**Package:** `com.morphcode.ai.enums`

Tests that each `ProjectRole` carries exactly the right `ProjectPermission` set.

| Scenario | Expected |
|---|---|
| `OWNER` has all 5 permissions | VIEW, EDIT, DELETE, VIEW_MEMBERS, MANAGE_MEMBERS |
| `EDITOR` has 3 permissions | VIEW, EDIT, VIEW_MEMBERS |
| `EDITOR` does NOT have DELETE or MANAGE_MEMBERS | Absent from permission set |
| `VIEWER` has only VIEW and VIEW_MEMBERS | Cannot edit, delete, or manage |
| `VIEWER` cannot edit | EDIT not in permission set |
| `ProjectPermission` string values are correct | e.g. `"project:view"`, `"project_members:manage"` |

---

## Error Handling

### `BadRequestExceptionTest`
**Package:** `com.morphcode.ai.error`

| Scenario | Expected |
|---|---|
| Constructor sets message | `getMessage()` returns the supplied string |
| Is a `RuntimeException` | `instanceof RuntimeException` |

### `ResourceNotFoundExceptionTest`
**Package:** `com.morphcode.ai.error`

| Scenario | Expected |
|---|---|
| Constructor stores `resourceName` and `resourceId` | Getters return correct values |
| `getMessage()` contains both resource name and ID | Full message readable |
| Is a `RuntimeException` | `instanceof RuntimeException` |

### `GlobalExceptionHandlerTest`
**Package:** `com.morphcode.ai.error`

Tests the `@RestControllerAdvice` handler in isolation — no Spring context needed.

| Exception | Expected HTTP Status | Expected Body |
|---|---|---|
| `BadRequestException` | 400 Bad Request | message from exception |
| `ResourceNotFoundException` | 404 Not Found | includes resource name and ID |
| `UsernameNotFoundException` | 404 Not Found | includes username |
| `AuthenticationException` (BadCredentials) | 401 Unauthorized | "Authentication failed" prefix |
| `JwtException` (MalformedJwt) | 401 Unauthorized | "Invalid JWT token" prefix |
| `AccessDeniedException` | 403 Forbidden | "Access denied" |
| `MethodArgumentNotValidException` | 400 Bad Request | "Validation" in message + field errors list |

---

## Security

### `AuthUtilTest`
**Package:** `com.morphcode.ai.security`

Tests JWT generation, verification, and `SecurityContext` extraction. Uses `ReflectionTestUtils` to inject the secret key.

| Scenario | Expected |
|---|---|
| `generateAccessToken` returns non-blank token | Non-empty JWT string |
| `verifyAccessToken` returns correct `userId` and `username` | Matches the `User` object used to sign |
| `verifyAccessToken` with a garbage string throws | Any `Exception` |
| `getCurrentUserId` with valid `JwtUserPrincipal` in context | Returns correct user ID |
| `getCurrentUserId` with no authentication | `AuthenticationCredentialsNotFoundException` |
| `getCurrentUserId` with non-JWT principal (plain string) | `AuthenticationCredentialsNotFoundException` |

### `JwtAuthFilterTest`
**Package:** `com.morphcode.ai.security`

Tests `OncePerRequestFilter` logic by calling `doFilterInternal` directly.

| Scenario | Expected |
|---|---|
| No `Authorization` header | Chain proceeds, `SecurityContext` stays null |
| Header present but not `Bearer` prefix (`Basic ...`) | Chain proceeds, no auth set |
| Valid `Bearer` token | `SecurityContext` populated with `JwtUserPrincipal`, chain proceeds |
| Invalid token causes `AuthUtil.verifyAccessToken` to throw | `HandlerExceptionResolver.resolveException` called, chain NOT invoked |

### `SecurityExpressionsTest`
**Package:** `com.morphcode.ai.security`

Tests the `@Component("security")` bean used in `@PreAuthorize` expressions.

| Method | Role | Expected |
|---|---|---|
| `canViewProject` | OWNER | `true` |
| `canViewProject` | VIEWER | `true` |
| `canViewProject` | Not a member | `false` |
| `canEditProject` | OWNER | `true` |
| `canEditProject` | VIEWER | `false` |
| `canDeleteProject` | OWNER | `true` |
| `canDeleteProject` | EDITOR | `false` |
| `canManageMembers` | OWNER | `true` |
| `canManageMembers` | EDITOR | `false` |
| `canViewMembers` | EDITOR | `true` |

---

## LLM

### `LlmResponseParserTest`
**Package:** `com.morphcode.ai.llm`

Tests the regex-based XML parser that converts raw LLM output into `ChatEvent` records.

| Scenario | Expected |
|---|---|
| `<message>` tag | One event of type `MESSAGE`, content trimmed |
| `<file path="...">` tag | One `FILE_EDIT` event, `filePath` set, content preserved |
| `<tool args="...">` tag | One `TOOL_LOG` event, `metadata` = args value |
| Multiple mixed tags | All events returned in document order |
| `sequenceOrder` starts at 1 | Increments per event: 1, 2, 3… |
| Parent `ChatMessage` is linked | Each event references the same parent object |
| Unknown tag (`<unknown>`) | Skipped — not included in result list |
| Empty response string | Returns empty list |
| Plain text with no XML | Returns empty list |
| Multi-line file content | Full content preserved, newlines kept |
| Case-insensitive tags (`<MESSAGE>`) | Parsed correctly as `MESSAGE` type |
| `<file>` with no `path` attribute | `filePath` is `null` |

---

## Services

### `AuthServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `UserRepository`, `UserMapper`, `PasswordEncoder`, `AuthUtil`, `AuthenticationManager`

| Scenario | Expected |
|---|---|
| Signup — new username | Saves user, encodes password, returns JWT token |
| Signup — duplicate username | `BadRequestException` thrown, no DB save |
| Login — valid credentials | Delegates to `AuthenticationManager`, returns JWT token |
| Login — wrong password | `AuthenticationException` propagated from `AuthenticationManager` |

### `ProjectServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `ProjectRepository`, `UserRepository`, `ProjectMapper`, `ProjectMemberRepository`, `AuthUtil`, `SubscriptionService`, `ProjectTemplateService`

| Scenario | Expected |
|---|---|
| Create project — within plan limit | Saves project, saves `ProjectMember` (OWNER), initialises template |
| Create project — over plan limit | `BadRequestException`, no repository calls |
| Get user projects — has projects | Returns list mapped via `ProjectMapper` |
| Get user projects — empty | Returns empty list |
| Update project — found | Sets new name, saves, returns updated response |
| Update project — not found | `ResourceNotFoundException` |
| Soft delete — found | Sets `deletedAt`, saves |

### `ChatServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `ChatMessageRepository`, `ChatSessionRepository`, `AuthUtil`, `ChatMapper`

| Scenario | Expected |
|---|---|
| Get chat history — messages exist | Returns list of `ChatResponse` via mapper |
| Get chat history — no messages | Returns empty list |

### `ProjectMemberServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `ProjectMemberRepository`, `ProjectRepository`, `ProjectMemberMapper`, `UserRepository`, `AuthUtil`

| Scenario | Expected |
|---|---|
| Get members — has members | Returns all mapped `MemberResponse` objects |
| Invite new member | Saves `ProjectMember`, returns response |
| Invite self | `RuntimeException("Cannot invite yourself")` |
| Invite already-existing member | `RuntimeException("Cannot invite once again")` |
| Update member role | Sets new `projectRole`, saves, returns updated response |
| Remove member — exists | Calls `deleteById` with correct composite key |
| Remove member — not found | `RuntimeException("Member not found in project")` |

### `UsageServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `UsageLogRepository`, `AuthUtil`, `SubscriptionService`

| Scenario | Expected |
|---|---|
| Record tokens — log exists today | Adds to existing `tokensUsed`, saves once |
| Record tokens — no log today | Creates new log (first save), then updates (second save) |
| Check daily usage — unlimited plan | Returns immediately, no limit enforced |
| Check daily usage — within limit | No exception thrown |
| Check daily usage — limit exactly reached | `ResponseStatusException` 429 "Daily limit reached" |
| Check daily usage — no log for today | Creates new log, then checks (passes at 0 tokens) |

### `SubscriptionServiceImplTest`
**Package:** `com.morphcode.ai.service`  
**Mocks:** `AuthUtil`, `SubscriptionRepository`, `SubscriptionMapper`, `UserRepository`, `PlanRepository`, `ProjectMemberRepository`

| Scenario | Expected |
|---|---|
| Get current subscription — active found | Returns mapped `SubscriptionResponse` |
| Get current subscription — none found | Returns response with `null` plan |
| Activate subscription — new | Saves `Subscription` with INCOMPLETE status |
| Activate subscription — duplicate stripe ID | Skips save (idempotent) |
| Cancel subscription | Sets status to CANCELED, saves |
| Cancel subscription — not found | `ResourceNotFoundException` |
| Renew subscription — from PAST_DUE | Sets new period end, status becomes ACTIVE |
| Mark subscription past due — from ACTIVE | Sets status to PAST_DUE, saves |
| Mark subscription past due — already PAST_DUE | No-op, repository save not called |
| Can create project — no plan (free tier) | True when owned < 100 |
| Can create project — on paid plan, at limit | False (count equals `maxProjects`) |

---

## Controllers (Component Tests)

All controller tests use `@WebMvcTest` with Spring Security and filter chain excluded. Services are `@MockBean`.

### `AuthControllerTest`
**Package:** `com.morphcode.ai.controller`  
**Endpoint base:** `/api/auth`

| Method | Path | Scenario | Expected Status | Response |
|---|---|---|---|---|
| POST | `/signup` | Valid body | 200 | `token` + `user` fields |
| POST | `/signup` | Duplicate username | 400 | `message` in body |
| POST | `/login` | Valid credentials | 200 | `token` + `user` fields |
| GET | `/me` | Any request (hardcoded userId=1) | 200 | Profile fields |

### `ProjectControllerTest`
**Package:** `com.morphcode.ai.controller`  
**Endpoint base:** `/api/projects`

| Method | Path | Scenario | Expected Status | Response |
|---|---|---|---|---|
| GET | `/` | Multiple projects | 200 | Array of summaries |
| GET | `/` | No projects | 200 | Empty array `[]` |
| GET | `/{id}` | Project found | 200 | Summary with id and name |
| GET | `/{id}` | Project not found | 400 | Error message |
| POST | `/` | Valid name | 201 | Project response with id |
| POST | `/` | Blank name | 400 | Validation error |
| PATCH | `/{id}` | Valid rename | 200 | Updated project name |
| PATCH | `/{id}` | Project not found | 404 | Error body |
| DELETE | `/{id}` | Existing project | 204 | No body |

### `ChatControllerTest`
**Package:** `com.morphcode.ai.controller`  
**Endpoint base:** `/api/chat`

| Method | Path | Scenario | Expected Status | Response |
|---|---|---|---|---|
| GET | `/projects/{id}` | Messages exist | 200 | Array with role and content fields |
| GET | `/projects/{id}` | No messages | 200 | Empty array `[]` |

### `ProjectMemberControllerTest`
**Package:** `com.morphcode.ai.controller`  
**Endpoint base:** `/api/projects/{projectId}/members`

| Method | Path | Scenario | Expected Status | Response |
|---|---|---|---|---|
| GET | `/` | Members exist | 200 | Array with userId and role |
| GET | `/` | No members | 200 | Empty array `[]` |
| POST | `/` | Valid invite | 201 | Member response |
| POST | `/` | Invalid email format | 400 | Validation error |
| POST | `/` | Self-invite (service throws) | 500 | Error |
| PATCH | `/{memberId}` | Valid role update | 200 | Updated role in response |
| DELETE | `/{memberId}` | Member exists | 204 | No body |
| DELETE | `/{memberId}` | Member not found | 500 | Error |

---

## How to Run

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=LlmResponseParserTest

# Run a specific package
./mvnw test -Dtest="com.morphcode.ai.service.*"

# Run with coverage report (if JaCoCo is configured)
./mvnw verify
```

## What Is Not Tested Here

| Area | Reason |
|---|---|
| `AiGenerationServiceImpl` | Requires live Spring AI / OpenRouter; test via integration test with WireMock |
| `ProjectTemplateService` | Requires MinIO; test via integration test with Testcontainers |
| Repository JPQL queries | Require a real PostgreSQL instance; test via `@DataJpaTest` with Testcontainers |
| `BillingController` / `StripePaymentProcessor` | Require Stripe SDK mocking; add separate Stripe-focused tests |
| `FileController` / `ProjectFileServiceImpl` | Require MinIO mocking; use Testcontainers or Mockito for MinIO client |
| `UsageController` | Follows same pattern as other controllers; straightforward to add |
| MapStruct mappers | Generated code; verified indirectly via service/controller tests |
