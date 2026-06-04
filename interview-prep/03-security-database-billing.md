# MorphCode — Interview Prep: Security, Database, Billing & System Design

This document covers every question an interviewer is likely to ask about the MorphCode backend across JWT security, Spring Security, RBAC, database design, JPQL, Stripe billing, API design, and general system design. All answers are grounded in the actual code.

---

## Table of Contents

1. [JWT Internals](#jwt-internals)
2. [JwtAuthFilter Mechanics](#jwtauthfilter-mechanics)
3. [Spring Security Filter Chain & Configuration](#spring-security-filter-chain--configuration)
4. [Method-Level Security with @PreAuthorize](#method-level-security-with-preauthorize)
5. [Role-Based Access Control (RBAC) Design](#role-based-access-control-rbac-design)
6. [Database Schema Design](#database-schema-design)
7. [Composite Primary Keys & Embeddable IDs](#composite-primary-keys--embeddable-ids)
8. [Soft Delete](#soft-delete)
9. [JPQL Custom Queries](#jpql-custom-queries)
10. [Stripe Webhook Integration](#stripe-webhook-integration)
11. [Subscription Lifecycle](#subscription-lifecycle)
12. [API Design Patterns](#api-design-patterns)
13. [General Backend System Design](#general-backend-system-design)
14. [Known Bugs & Gotchas](#known-bugs--gotchas)

---

## JWT Internals

### Q: What JWT algorithm does MorphCode use and why?
**A:** MorphCode uses HS256 (HMAC-SHA-256), a symmetric signing algorithm. `AuthUtil.getSecretKey()` creates the key with `Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8))` from the JJWT library. HS256 is appropriate for a single-service backend — the same secret both signs and verifies tokens. For a distributed microservices architecture, RS256 (asymmetric) would be preferred so services can verify tokens using only the public key, without ever touching the private key.

### Q: What is the minimum key length requirement, and why does MorphCode enforce 32 characters for the secret?
**A:** HS256 requires at minimum a 256-bit (32-byte) key. Since `jwtSecretKey.getBytes(StandardCharsets.UTF_8)` converts each character to 1 byte in ASCII range, a 32-character ASCII string produces exactly 256 bits. JJWT's `Keys.hmacShaKeyFor()` will throw a `WeakKeyException` at startup if the key is shorter, making this a fail-fast contract enforced by the library — not application code. The secret is injected via `@Value("${jwt.secret-key}")` from `application.properties`, keeping it out of source code.

### Q: Walk me through the exact claims structure of a MorphCode JWT.
**A:** `AuthUtil.generateAccessToken(User user)` creates a token with:
- **`sub`** (subject): `user.getUsername()` — the username string
- **`userId`** (custom claim): `user.getId().toString()` — the database `Long` ID stored as a `String`
- **`iat`** (issued at): `new Date()` — current timestamp
- **`exp`** (expiration): `new Date(System.currentTimeMillis() + 1000 * 60 * 100)` — exactly 100 minutes from issuance
- **Signature**: HMAC-SHA256 over the header+payload using `getSecretKey()`

### Q: Why is `userId` stored as a String in the JWT claim rather than a Long?
**A:** JSON numbers are decoded differently by parsers — some deserialize them as `Integer`, some as `Long`, some as `Double`. By storing `userId` as a `String`, `AuthUtil` avoids type-casting exceptions at parse time. When reading back in `verifyAccessToken()`, the code does `claims.get("userId", String.class)` then `Long.parseLong(...)`, making the intent explicit and the parse safe. If it were stored as a numeric claim, `claims.get("userId", Long.class)` might fail with a `ClassCastException` on certain JJWT versions.

### Q: What is the token expiry and what are its trade-offs?
**A:** Tokens expire after 100 minutes (`1000ms * 60s * 100 = 6,000,000ms`). This is a deliberate balance: short enough to limit the window of a stolen token being used, long enough to avoid forcing users to re-authenticate too frequently during a work session. The downside of this (and any stateless JWT approach) is that a compromised token cannot be revoked before it expires — MorphCode has no token blacklist, no refresh token mechanism, and no session invalidation capability. An improvement would be to add short-lived access tokens (15 minutes) paired with longer-lived refresh tokens stored server-side.

### Q: How does MorphCode verify a JWT token? Walk through `verifyAccessToken()`.
**A:** `AuthUtil.verifyAccessToken(String token)` does the following:
1. Builds a `JwtParser` with `Jwts.parser().verifyWith(getSecretKey()).build()` — this ties the parser to the same HMAC-SHA256 secret used at signing time.
2. Calls `.parseSignedClaims(token)` — JJWT validates the signature, checks `exp` is not in the past, and returns a `Jws<Claims>` object.
3. Calls `.getPayload()` to extract the `Claims` map.
4. Reads `userId` with `claims.get("userId", String.class)` and parses it to `Long`.
5. Reads `username` with `claims.getSubject()`.
6. Returns `new JwtUserPrincipal(userId, username, new ArrayList<>())`.

If the signature is invalid, the token is expired, or the token is malformed, JJWT throws a subclass of `JwtException` — which is caught in `JwtAuthFilter` and delegated to `GlobalExceptionHandler`.

### Q: What is `JwtUserPrincipal` and why is it a Java record?
**A:** `JwtUserPrincipal` is defined as `public record JwtUserPrincipal(Long userId, String username, List<GrantedAuthority> authorities)`. Records in Java 16+ are immutable data carriers — they auto-generate constructor, `equals()`, `hashCode()`, `toString()`, and accessor methods. Since a principal is a read-only snapshot of identity extracted from a token, immutability is appropriate. Notably, `JwtUserPrincipal` does **not** implement `UserDetails` — it is a custom principal placed directly into the `UsernamePasswordAuthenticationToken`, which only requires that the principal be non-null.

### Q: `JwtUserPrincipal` returns an empty authorities list. Is that a problem?
**A:** For the MorphCode RBAC model it is not — role checking is done outside Spring Security's `GrantedAuthority` system. `SecurityExpressions.hasPermission()` queries the `project_members` table directly on each authorization check. Spring Security's `GrantedAuthority` list is only needed for path-level `hasRole()`/`hasAuthority()` checks on `antMatchers`. Since MorphCode uses `@PreAuthorize` with custom SpEL beans (`@security.canEditProject(#projectId)`) rather than role-based path matchers, an empty authorities list causes no functional gaps.

### Q: How does `getCurrentUserId()` work and what does it throw if there is no token?
**A:** `AuthUtil.getCurrentUserId()` calls `SecurityContextHolder.getContext().getAuthentication()`. If `authentication` is null or if the principal is not a `JwtUserPrincipal` (e.g., an anonymous request), it throws `AuthenticationCredentialsNotFoundException("No JWT Found")`. This is a subclass of `AuthenticationException`, so `GlobalExceptionHandler.handleAuthenticationException()` catches it and returns HTTP 401. The pattern guard `!(authentication.getPrincipal() instanceof JwtUserPrincipal userPrincipal)` uses Java 16 pattern matching for `instanceof`, making the null check and cast happen atomically.

---

## JwtAuthFilter Mechanics

### Q: Why does `JwtAuthFilter` extend `OncePerRequestFilter` rather than `GenericFilterBean`?
**A:** `OncePerRequestFilter` is a Spring convenience class that guarantees `doFilterInternal()` is called exactly once per HTTP request, even when requests are forwarded internally (e.g., Spring MVC error forwarding). `GenericFilterBean` would not provide that guarantee — the filter might execute again for async dispatches or error dispatches. For a stateless JWT filter this matters: running JWT validation twice wastes CPU and could produce confusing behaviour if the `SecurityContext` is populated, then cleared, then re-checked.

### Q: What happens when a request arrives without an Authorization header?
**A:** `JwtAuthFilter.doFilterInternal()` checks `if (requestHeaderToken == null || !requestHeaderToken.startsWith("Bearer "))` and, if true, calls `filterChain.doFilter(request, response)` and returns immediately — the filter does **not** set any authentication on the `SecurityContext`. Spring Security then treats this as an anonymous request. The route's authorization rule decides the outcome: `/api/auth/**` and `/webhooks/**` are `permitAll()` so they proceed; any other route is `anyRequest().authenticated()` so Spring Security rejects it with 401.

### Q: How does the filter extract the token from the `Bearer` header?
**A:** `requestHeaderToken.split("Bearer ")[1]` splits on the literal string `"Bearer "` (with a trailing space) and takes the second element. This is slightly fragile — if someone sends `Bearer` without a space, `split()` returns a single-element array and `[1]` throws `ArrayIndexOutOfBoundsException`. A more robust approach is `requestHeaderToken.substring("Bearer ".length())` or `.replace("Bearer ", "")`. In practice, the outer `try/catch` in `doFilterInternal` would catch the exception and delegate to `GlobalExceptionHandler`, returning 500 rather than 401 — a bug in error reporting.

### Q: Explain the double-check `user != null && SecurityContextHolder.getContext().getAuthentication() == null`.
**A:** The `user != null` guard is technically always true — `verifyAccessToken()` either returns a `JwtUserPrincipal` or throws a `JwtException` (never returns null). The real guard is `SecurityContextHolder.getContext().getAuthentication() == null`: this prevents overwriting an `Authentication` that was already set by an upstream filter or by a prior invocation. In a stateless setup, the `SecurityContext` should always be null at the start of a filter chain since there is no session, but it is a defensive check following the standard pattern from Spring Security documentation.

### Q: How are JWT exceptions propagated to produce proper HTTP responses?
**A:** The entire body of `doFilterInternal()` is wrapped in `try { ... } catch (Exception e)`. On any exception, `handlerExceptionResolver.resolveException(request, response, null, e)` is called. This routes the exception to `GlobalExceptionHandler` (a `@RestControllerAdvice`), which handles:
- `JwtException` → HTTP 401 with `"Invalid JWT token: ..."` message
- `AuthenticationException` → HTTP 401
- Any other exception → depends on handler mapping

This approach is necessary because exceptions thrown inside a servlet filter happen **before** Spring MVC's `DispatcherServlet`, so `@ExceptionHandler` methods do not fire automatically. Injecting `HandlerExceptionResolver` as a bridge is a well-known pattern to unify error handling between the filter chain and the MVC layer.

### Q: The filter logs every incoming request URI at INFO level. What are the risks?
**A:** `log.info("incoming request: {}", request.getRequestURI())` logs all URIs including potentially sensitive path parameters (e.g., `/api/projects/12345`). In high-traffic environments, this creates verbose log output that could saturate log storage. More importantly, if URIs contain sensitive data (e.g., tokens in path params, which is an anti-pattern but can happen), they would be logged in plaintext. Production systems typically log at DEBUG or redact path parameters. A safer alternative is to log only method and path at DEBUG level, keeping INFO for error cases.

---

## Spring Security Filter Chain & Configuration

### Q: Walk through the full `WebSecurityConfig` configuration.
**A:** `WebSecurityConfig` is annotated `@Configuration`, `@RequiredArgsConstructor`, and `@EnableMethodSecurity`. It defines a `SecurityFilterChain` bean with:
1. **CSRF disabled** — justified because this is a stateless REST API using JWT (no cookies, no session), so CSRF attacks don't apply.
2. **CORS with defaults** — delegates to Spring's `CorsConfigurationSource` bean; actual CORS rules live in `CorsConfig`.
3. **Session policy: STATELESS** — Spring Security will never create an `HttpSession`, preventing accidental session-based auth alongside JWT.
4. **Dispatcher type matchers**: `ASYNC` and `ERROR` are `permitAll()` — necessary for SSE streaming (async dispatch) and for Spring's internal `/error` forwarding.
5. **Path matchers**: `/api/auth/**` (signup, login, me) and `/webhooks/**` (Stripe) are `permitAll()`. Everything else requires authentication.
6. **Filter order**: `jwtAuthFilter` is added `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` — it runs before the default form-login filter, which is inactive anyway.
7. **Access denied handler**: routes `AccessDeniedException` through `handlerExceptionResolver` to `GlobalExceptionHandler`, which returns HTTP 403.

### Q: Why must `DispatcherType.ASYNC` be `permitAll()`? What breaks without it?
**A:** SSE (Server-Sent Events) works by Spring MVC dispatching an async request: the initial HTTP request is held, and when Flux emits, Spring creates an internal async dispatch to write the chunk. Spring Security by default applies authorization checks on async dispatches too — but the async dispatch happens on a different thread where the `SecurityContext` propagation may not be guaranteed, or the dispatch type may not match the URL-level security matchers. Without `dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`, SSE responses from `/api/chat/stream` would get blocked mid-stream, producing a broken SSE connection for the client.

### Q: Why is CSRF disabled? When is it safe to do so?
**A:** CSRF (Cross-Site Request Forgery) requires that the victim's browser automatically sends credentials — typically session cookies — to the target server. Since MorphCode uses JWT delivered via the `Authorization: Bearer` header (not cookies), browsers will never automatically attach the token to cross-origin requests. Therefore, CSRF protection is unnecessary and disabling it is safe. The rule of thumb: if authentication state is stored exclusively in a header (not cookies), CSRF is irrelevant.

### Q: What is the purpose of `SessionCreationPolicy.STATELESS`?
**A:** It instructs Spring Security to never create an `HttpSession` and never use a session to store the `SecurityContext` between requests. Each request is self-contained — identity comes from the JWT on that request alone. This enables horizontal scaling (any server can handle any request without sticky sessions or session replication) and removes an entire class of session fixation vulnerabilities.

### Q: How is `BCryptPasswordEncoder` configured and what strength does it use?
**A:** `WebSecurityConfig` declares `@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }`. The no-argument constructor uses BCrypt's default cost factor of 10 (2^10 = 1,024 iterations), which makes brute-force attacks slow. The encoder is used in `AuthServiceImpl.signup()` with `passwordEncoder.encode(request.password())` and in `login()` implicitly through Spring's `DaoAuthenticationProvider`, which calls `passwordEncoder.matches(rawPassword, storedHash)` when `authenticationManager.authenticate()` processes the `UsernamePasswordAuthenticationToken`.

### Q: How does `AuthenticationManager` work for the login flow?
**A:** `WebSecurityConfig` exposes `AuthenticationManager` via `authenticationConfiguration.getAuthenticationManager()`. Spring Boot auto-configures a `DaoAuthenticationProvider` because `User` implements `UserDetails` and `UserRepository.findByUsername()` is wired into a `UserDetailsService` bean. When `AuthServiceImpl.login()` calls `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, rawPassword))`, the provider: (1) loads the `User` by username via `UserDetailsService`, (2) calls `passwordEncoder.matches(rawPassword, user.getPassword())`, (3) if successful, returns an authenticated `Authentication` with the `User` as principal. `AuthServiceImpl` then casts `authentication.getPrincipal()` to `User` and generates the JWT.

### Q: `User` implements `UserDetails` and returns an empty `getAuthorities()` list. Does this break anything?
**A:** For MorphCode's authorization model, no. Spring's `DaoAuthenticationProvider` does not require non-empty authorities — it calls `getAuthorities()` when constructing the post-authentication `UsernamePasswordAuthenticationToken`, but the result is only used for `GrantedAuthority`-based checks like `hasRole()`. Since MorphCode's access decisions are made by `SecurityExpressions` via `@PreAuthorize`, not by role matchers, the empty list is harmless.

---

## Method-Level Security with @PreAuthorize

### Q: What does `@EnableMethodSecurity` do and how does it differ from the older `@EnableGlobalMethodSecurity`?
**A:** `@EnableMethodSecurity` (Spring Security 6+) replaces the deprecated `@EnableGlobalMethodSecurity(prePostEnabled = true)`. It enables AOP-based interception of `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, and `@PostFilter` annotations on Spring beans. The key difference is that `@EnableMethodSecurity` defaults `prePostEnabled = true`, uses the newer Spring Security authorization infrastructure (`AuthorizationManager`), and is more composable. MorphCode uses it on `WebSecurityConfig`.

### Q: Why is `@PreAuthorize` placed on service methods rather than controller methods?
**A:** Placing `@PreAuthorize` on the service layer (`ProjectServiceImpl`, `AiGenerationServiceImpl`, `ProjectMemberServiceImpl`) rather than controllers enforces the authorization rule at the business logic boundary. This means the rule holds regardless of how the service method is invoked — whether from a REST controller, a scheduled task, an event listener, or a test. If placed only on controllers, direct programmatic calls to the service from other beans would bypass the check. It also keeps HTTP-mapping concerns (controllers) separate from business authorization concerns (services).

### Q: How does `@PreAuthorize("@security.canEditProject(#projectId)")` work mechanically?
**A:** Spring Security wraps the annotated bean with a CGLIB proxy. Before the method executes, the proxy evaluates the SpEL expression `@security.canEditProject(#projectId)`. Here:
- `@security` resolves the bean named `"security"` from the `ApplicationContext` — this is `SecurityExpressions`, annotated `@Component("security")`.
- `#projectId` resolves the method parameter named `projectId` (Spring uses parameter name discovery via reflection or `-parameters` compiler flag).
- `.canEditProject(Long projectId)` is called on `SecurityExpressions`, which calls the private `hasPermission()` method, which queries the DB and returns a boolean.
- If `false`, Spring throws `AccessDeniedException`, which the `accessDeniedHandler` in `WebSecurityConfig` routes to `GlobalExceptionHandler`, returning HTTP 403.

### Q: What is the `#projectId` syntax in SpEL and how does Spring resolve parameter names?
**A:** The `#` prefix in SpEL denotes a variable. Inside `@PreAuthorize`, Spring Security makes method parameters available as variables named `#<paramName>`. Spring resolves parameter names either from bytecode (`-parameters` compiler flag, required with Spring 6) or from debug info in the class file. The `pom.xml` for a Spring Boot 3.x project typically enables `-parameters` by default via the `spring-boot-maven-plugin`. Without it, you'd need `@Param("projectId")` or use positional syntax like `#a0`.

### Q: What is the order of operations — does `@PreAuthorize` check before or after the method body?
**A:** `@PreAuthorize` runs **before** the method body executes. If the expression evaluates to `false`, the method is never entered. This is the correct behaviour for authorization: you want to reject unauthorized access before any side effects occur. `@PostAuthorize` (not used in MorphCode) runs after, and can filter the return value.

### Q: What happens if `SecurityExpressions.hasPermission()` throws a runtime exception mid-evaluation?
**A:** If an exception propagates out of the SpEL evaluation (e.g., a `DataAccessException` from the DB query in `projectMemberRepository.findRoleByProjectIdAndUserId()`), Spring Security wraps it in a `MethodSecurityInterceptionException`, which is a subclass of `RuntimeException`. The exception bubbles up through the controller and reaches `GlobalExceptionHandler`. There is no explicit handler for `MethodSecurityInterceptionException` — it would fall through to a default 500 response. A production system should add a DB exception handler or retry logic inside `hasPermission()`.

---

## Role-Based Access Control (RBAC) Design

### Q: Explain the RBAC model in MorphCode.
**A:** MorphCode uses a resource-scoped, permission-based RBAC. The model has three layers:
1. **Roles** (`ProjectRole` enum): `OWNER`, `EDITOR`, `VIEWER` — each associated with a `Set<ProjectPermission>`.
2. **Permissions** (`ProjectPermission` enum): `VIEW`, `EDIT`, `DELETE`, `VIEW_MEMBERS`, `MANAGE_MEMBERS` — each with a string value like `"project:view"`.
3. **Role-to-permission mapping** (in `ProjectRole`): owned by the enum via a `final Set<ProjectPermission> permissions` field with `@Getter`.

At runtime, `SecurityExpressions.hasPermission(Long projectId, ProjectPermission permission)`:
1. Calls `authUtil.getCurrentUserId()` to get the caller's ID from the `SecurityContext`.
2. Queries `projectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId)` to find their role in that specific project.
3. Calls `role.getPermissions().contains(permission)` — O(1) because `Set.of()` creates a hash set.
4. Returns `false` if no membership record exists (not a member at all).

### Q: Why are permissions stored in the enum rather than in a separate DB table?
**A:** Storing role-to-permission mappings in the enum code rather than a DB table (`role_permissions`) is a trade-off favouring simplicity. Advantages: no additional DB query needed per check, no join table to maintain, zero cache invalidation complexity, permissions are statically typed and compile-checked. Disadvantages: changing permissions requires a code deployment (no hot update), and you cannot create dynamic roles at runtime. For an application with a small, stable set of roles (3 roles, 5 permissions), the enum approach is well-suited. If MorphCode ever needed custom roles per organization, the model would need to move to a DB-backed permission table.

### Q: How would you add a new permission, say `EXPORT`?
**A:** 
1. Add `EXPORT("project:export")` to `ProjectPermission` enum.
2. Add `ProjectPermission.EXPORT` to the appropriate role sets in `ProjectRole` — e.g., add to `OWNER` and `EDITOR`.
3. Add a new method `canExportProject(Long projectId)` to `SecurityExpressions` that calls `hasPermission(projectId, ProjectPermission.EXPORT)`.
4. Annotate the relevant service method with `@PreAuthorize("@security.canExportProject(#projectId)")`.
5. Deploy. No DB schema change required.

### Q: How does a VIEWER differ from an EDITOR in terms of what they can do?
**A:** Looking at `ProjectRole` enum:
- `VIEWER`: `{VIEW, VIEW_MEMBERS}` — can read project content and see who is in the project.
- `EDITOR`: `{VIEW, EDIT, VIEW_MEMBERS}` — additionally can edit files (trigger AI generation, update project metadata).
- `OWNER`: `{VIEW, EDIT, DELETE, VIEW_MEMBERS, MANAGE_MEMBERS}` — additionally can delete the project and invite/remove/change roles of members.

A `VIEWER` cannot call `streamResponse()` or `updateProject()` — those require `EDIT` permission. They also cannot invite members — that requires `MANAGE_MEMBERS`.

### Q: Can an OWNER be removed from a project? What prevents self-removal?
**A:** `ProjectMemberServiceImpl.removeProjectMember()` is guarded by `@PreAuthorize("@security.canManageMembers(#projectId)")`, which only OWNERs pass. The method checks `!projectMemberRepository.existsById(projectMemberId)` and then calls `projectMemberRepository.deleteById(projectMemberId)`. There is no check preventing an OWNER from removing themselves or the last OWNER from a project. This is a bug: it could result in an ownerless project that nobody can delete or manage. A fix would add a guard in `removeProjectMember()` that prevents removal if it would leave zero OWNERs.

---

## Database Schema Design

### Q: What entities exist and how do they relate?
**A:** 
- `User` — core identity, implements `UserDetails`
- `Project` — has `name`, `isPublic`, soft delete `deletedAt`
- `ProjectMember` — join table between `Project` and `User`, with composite PK `ProjectMemberId(projectId, userId)`, stores `ProjectRole`, `invitedAt`, `acceptedAt`
- `ChatSession` — composite PK `ChatSessionId(projectId, userId)`, one session per (user, project) pair, soft delete
- `ChatMessage` — references `ChatSession`, has `role` (USER/ASSISTANT), `content`, `tokensUsed`
- `ChatEvent` — fine-grained events within a message: `MESSAGE`, `FILE_EDIT`, `THOUGHT`, with `sequenceOrder`, `filePath`, `content`
- `Plan` — subscription plan, has `stripePriceId`, `maxProjects`, `maxTokensPerDay`, `unlimitedAi`
- `Subscription` — links `User` to `Plan`, has Stripe IDs, `status`, billing period dates
- `UsageLog` — one row per (user, date) with `tokensUsed`; unique constraint enforces the invariant
- `ProjectFile` — stores file path and metadata for files within a project (content in MinIO)
- `Preview` — declared but no `@Entity` annotation; feature is unimplemented

### Q: Why does `Project` use `GenerationType.IDENTITY` for its PK?
**A:** `GenerationType.IDENTITY` delegates PK generation to the database (`SERIAL`/`BIGSERIAL` in PostgreSQL). This is simple and efficient for PostgreSQL, which natively supports auto-increment sequences. The alternative `GenerationType.SEQUENCE` would use an explicit DB sequence, allowing batch ID pre-allocation (better for high-volume inserts), but the overhead is unnecessary for MorphCode's scale. `GenerationType.AUTO` would pick the strategy per database, which is less predictable.

### Q: Why are `Instant` fields used rather than `LocalDateTime` for timestamps?
**A:** `Instant` is a timezone-neutral moment in time (UTC epoch), while `LocalDateTime` has no timezone information and can cause subtle bugs when application servers or database servers are in different timezones. Using `Instant` everywhere means timestamps mean the same thing regardless of where the JVM or PostgreSQL is running. Hibernate maps `Instant` to `TIMESTAMP WITH TIME ZONE` in PostgreSQL (with appropriate JPA configuration), preserving full temporal accuracy.

### Q: Explain `@CreationTimestamp` and `@UpdateTimestamp`.
**A:** These are Hibernate-specific annotations:
- `@CreationTimestamp` — Hibernate sets this field to the current time when the entity is first persisted. It ignores any value provided by application code on insert.
- `@UpdateTimestamp` — Hibernate updates this field to the current time on every `UPDATE` operation.

They are used on `createdAt` and `updatedAt` in `Project`, `User`, `Subscription`, and `ChatSession`. No application code needs to manage these timestamps, reducing the risk of forgetting to set them.

### Q: What are the three indexes on the `projects` table and why were they chosen?
**A:** 
```java
@Index(name="idx_projects_updated_at_desc", columnList="updated_at DESC, deleted_at")
@Index(name="idx_projects_deleted_at_updated_at_desc", columnList="deleted_at, updated_at DESC")
@Index(name="idx_project_deleted_at", columnList="deleted_at")
```
- **`idx_projects_updated_at_desc`**: Supports queries that sort by `updated_at DESC` while also filtering on `deleted_at IS NULL` — covers `findAllAccessibleByUser()` which returns projects ordered by `updatedAt DESC` and filters soft-deleted rows. Including `deleted_at` in the index allows an index-only scan.
- **`idx_projects_deleted_at_updated_at_desc`**: A covering index with `deleted_at` leading — optimizes point lookups on `deleted_at = NULL` followed by ordering on `updated_at DESC`. Useful when the DB planner chooses to scan by `deleted_at` first.
- **`idx_project_deleted_at`**: Simple index on `deleted_at` alone — used by simpler queries that just filter by `deleted_at IS NULL` without needing the `updated_at` ordering.

Multiple indexes allow the PostgreSQL query planner to pick the best execution plan for different query shapes.

### Q: What is the `@ManyToOne(fetch = FetchType.LAZY)` on `Subscription.user` and why is it important?
**A:** `FetchType.LAZY` means that when a `Subscription` entity is loaded, the associated `User` is NOT loaded immediately. Instead, Hibernate creates a proxy. The actual `User` SQL query only fires if the code calls `subscription.getUser()`. This is important for `SubscriptionServiceImpl` methods like `cancelSubscription()`, which loads a `Subscription` by Stripe ID — if the join to `User` were `EAGER`, every subscription lookup would trigger an extra `SELECT` for the user that may be completely unused. `FetchType.LAZY` is the JPA default for `@ManyToOne` in theory, but Hibernate's actual default is `EAGER` unless specified, so the explicit annotation matters.

---

## Composite Primary Keys & Embeddable IDs

### Q: What is `@EmbeddedId` and how does it differ from `@IdClass`?
**A:** Both are JPA strategies for composite primary keys. `@EmbeddedId` embeds an `@Embeddable` class directly as the PK field:
```java
@EmbeddedId
ProjectMemberId id;
```
`@IdClass` separates the PK class from the entity — you annotate individual `@Id` fields in the entity, and reference a `@IdClass` at class level. MorphCode uses `@EmbeddedId` for `ProjectMember` (correctly with `@Embeddable` on `ProjectMemberId`) and `@EmbeddedId` for `ChatSession` (incorrectly — `ChatSessionId` lacks `@Embeddable`, a known bug). `@EmbeddedId` is generally preferred because the PK object can be constructed and used in typed repository calls like `repository.findById(new ProjectMemberId(projectId, userId))`.

### Q: `ProjectMemberId` has `@Embeddable` but no `equals()`/`hashCode()`. What is the consequence?
**A:** JPA spec requires embeddable PKs to correctly implement `equals()` and `hashCode()` based on the PK fields. Without them:
- **Identity tracking**: Hibernate uses object identity (`==`) for the PK, meaning two `ProjectMemberId(1L, 2L)` objects are not considered equal, so the entity cannot be found in the first-level cache by a newly constructed ID.
- **`existsById()` and `findById()`**: These methods construct a new PK object and pass it to Hibernate. If `equals()` is incorrect, the first-level cache lookup fails and Hibernate always hits the DB.
- **Unit tests**: Test assertions comparing `ProjectMemberId` instances by value fail. The workaround used in MorphCode's tests is to use `any()` matchers (Mockito's argument matcher) instead of exact equality.
- **Fix**: Add `@EqualsAndHashCode` from Lombok to `ProjectMemberId`.

### Q: `ChatSessionId` is missing `@Embeddable`. What happens at runtime?
**A:** `ChatSession` uses `@EmbeddedId private ChatSessionId id`, but `ChatSessionId` lacks `@Embeddable`. Hibernate cannot map an `@EmbeddedId` type that is not annotated `@Embeddable` — this results in a `MappingException` at application startup: `"Could not determine type for: com.morphcode.ai.entity.ChatSessionId"` or similar. This would crash the application. Since the CLAUDE.md marks this as a known bug, it likely means either: (a) the application currently uses `ChatSession` in a limited way that avoids the crash path, or (b) there is a Hibernate configuration quirk allowing it. The fix is simply to add `@Embeddable` to `ChatSessionId`.

### Q: Explain `@MapsId` as used in `ProjectMember`.
**A:** `@MapsId("projectId")` on the `project` field tells Hibernate to derive the `projectId` portion of the embedded PK from the associated `Project` entity's ID. Similarly `@MapsId("userId")` derives `userId` from the `User` entity. This creates a consistent linkage: when you set `projectMember.project = someProject`, the `id.projectId` is automatically populated from `someProject.getId()`. This avoids having to manually keep the PK and the FK in sync. JPQL queries can then reference `pm.id.projectId` as a path expression.

### Q: Why does `UsageLog` use a surrogate PK (`Long id`) rather than a natural composite key `(userId, date)`?
**A:** Using a surrogate PK simplifies JPA mapping — no `@Embeddable` needed, and Spring Data's `findById()` works with a single scalar. The uniqueness of `(user_id, date)` is still enforced by `@UniqueConstraint(columnNames = {"user_id", "date"})`, which creates a unique index in PostgreSQL. The upsert pattern (`findByUserIdAndDate().orElseGet(...)`) relies on this constraint for correctness. A natural composite key would be equally correct but add boilerplate.

---

## Soft Delete

### Q: What is soft delete and why does MorphCode use it?
**A:** Soft delete marks records as logically deleted by setting a `deletedAt Instant` field rather than issuing a `DELETE` SQL statement. Hard-deleting user data, projects, or sessions would make recovery impossible and break audit trails. With soft delete, an admin can inspect or restore records. In MorphCode, `User`, `Project`, and `ChatSession` use `deletedAt`. The `ProjectServiceImpl.softDelete()` method simply calls `project.setDeletedAt(Instant.now())` and saves.

### Q: Why is there no Hibernate `@Where` annotation filtering `deletedAt IS NULL` automatically?
**A:** `@Where` (Hibernate-specific) or the JPA `@Filter` adds a SQL WHERE clause to every query on that entity — convenient but can be surprising. MorphCode's CLAUDE.md notes: "No `@Where`; queries must filter manually." The reason is explicitness and control: `@Where` would filter soft-deleted records from *all* queries, including administrative ones (e.g., a future admin endpoint to view deleted projects). Manual filtering in each JPQL query makes the intent explicit. The downside is that a developer forgetting to add `AND p.deletedAt IS NULL` to a new query would accidentally expose soft-deleted data.

### Q: How does the soft delete filter appear in the custom JPQL queries?
**A:** In `ProjectRepository.findAllAccessibleByUser()`:
```jpql
WHERE pm.user.id = :userId AND p.deletedAt IS NULL
```
In `findAccessibleProjectById()`:
```jpql
WHERE p.id = :projectId AND p.deletedAt IS NULL
```
In `findAccessibleProjectByIdWithRole()`:
```jpql
WHERE p.id = :projectId AND pm.user.id = :userId AND p.deletedAt IS NULL
```
The pattern is consistently `p.deletedAt IS NULL` for the Project entity. `ChatSession` also has `deletedAt` but no custom query filters are currently visible for sessions — a potential bug if chat sessions are soft-deleted but not excluded from lookups.

### Q: What is the difference between soft deleting a `Project` and deleting a `ProjectMember`?
**A:** `ProjectServiceImpl.softDelete()` sets `project.deletedAt = now()` — the project row remains in the DB. All related `ProjectMember` rows, `ChatSession` rows, etc. still exist and still reference the project. They are only effectively hidden because all project queries filter `deletedAt IS NULL`. `ProjectMemberServiceImpl.removeProjectMember()` calls `projectMemberRepository.deleteById(projectMemberId)` — a real SQL `DELETE`. Membership removal is permanent because there is no audit need to track historical membership and because the member ID is already stored in the `ProjectMember` table's history via `invitedAt`/`acceptedAt`.

---

## JPQL Custom Queries

### Q: Why use JPQL rather than native SQL queries?
**A:** JPQL (Java Persistence Query Language) operates on entity objects and their mappings rather than raw tables and columns. Benefits in MorphCode:
- Database portability: switching from PostgreSQL to another DB would not require rewriting queries.
- Type safety: `pm.id.projectId` references the field path in the entity/embeddable, caught at deployment time by Hibernate.
- Relationship traversal: `pm.user.id` crosses an association without writing an explicit JOIN ON clause.
- Cleaner code: text block strings (`"""..."""`) allow multi-line JPQL that is readable.
The only significant use of native SQL would be for PostgreSQL-specific features like `pg_vector` similarity search, `ON CONFLICT` upserts, or CTEs — none of which appear in the current query set.

### Q: Explain the `ProjectWithRole` nested interface projection.
**A:** `ProjectRepository` defines:
```java
interface ProjectWithRole {
    Project getProject();
    ProjectRole getRole();
}
```
JPQL returns multiple values per row with `SELECT p as project, pm.projectRole as role`. Spring Data JPA maps these to interface projections: when a query returns a row with aliases matching the getter names (after removing "get"), Spring creates a dynamic proxy implementing the interface. `getProject()` returns the full `Project` entity, `getRole()` returns the `ProjectRole` enum. This avoids creating a dedicated DTO class while still allowing strongly-typed access to the query result.

### Q: What does the `EXISTS` subquery do in `findAccessibleProjectById()`?
**A:**
```jpql
EXISTS (
    SELECT 1 FROM ProjectMember pm
    WHERE pm.id.userId = :userId AND pm.id.projectId = :projectId
)
```
This checks that the user has *any* membership record for the project, regardless of role. It is an existence check, not a permission check — the permission check happens in `@PreAuthorize`. The EXISTS subquery is more efficient than a JOIN for a pure membership gate: the DB can short-circuit as soon as it finds one matching row rather than materializing the join. `pm.id.userId` and `pm.id.projectId` reference the embedded composite PK, which Hibernate translates to the actual column names.

### Q: How does `findByIdProjectId(Long projectId)` work as a derived query on an embedded ID?
**A:** Spring Data JPA generates JPQL from the method name. `findBy` + `Id` (the field name in `ProjectMember`) + `ProjectId` (the field inside `ProjectMemberId`) — Spring Data traverses the path `id.projectId`. The generated JPQL is effectively `SELECT pm FROM ProjectMember pm WHERE pm.id.projectId = :projectId`. This works because `ProjectMemberId` is `@Embeddable`, so `pm.id.projectId` is a valid embedded field path in JPQL.

### Q: Why does `countProjectOwnedByUser()` use a string literal `'OWNER'` in JPQL?
**A:**
```jpql
WHERE pm.id.userId = :userId AND pm.projectRole = 'OWNER'
```
When `@Enumerated(EnumType.STRING)` is used, Hibernate stores the enum name as a string. In JPQL, you can compare an enum-typed field to a string literal that matches the enum constant name. This is valid JPQL syntax and Hibernate will map it correctly. An alternative is to use a bound parameter: `pm.projectRole = :role` with `@Param("role") ProjectRole role = ProjectRole.OWNER`. The literal is slightly brittle (refactoring the enum name without updating the query string would silently break it), but JPQL string literals for enums are widely used.

### Q: The JPQL in `findAllAccessibleByUser()` uses `JOIN` not `JOIN FETCH`. What does that mean?
**A:** A regular `JOIN` in JPQL produces a SQL JOIN for filtering/grouping purposes but does NOT eagerly load the joined entity's fields (they remain lazily loaded). `JOIN FETCH` would include all columns of the joined entity in the SELECT, initializing it immediately. Since the query projects `p as project, pm.projectRole as role`, only the `project_role` column from `project_members` is needed — the full `ProjectMember` entity is not required. A `JOIN FETCH` here would load more data than needed. The trade-off is that accessing `pm.user` later would trigger a lazy load query, but since only `pm.projectRole` is accessed in the mapping step (`p -> projectMapper.toProjectSummaryResponse(p.getProject(), p.getRole())`), no extra query fires.

---

## Stripe Webhook Integration

### Q: Walk through the complete checkout-to-subscription flow.
**A:**
1. **Client** calls `POST /api/payments/checkout` with `{planId}`.
2. **`StripePaymentProcessor.createCheckoutSessionUrl()`** looks up the `Plan` by ID to get its `stripePriceId`. Gets the authenticated user's ID via `authUtil.getCurrentUserId()`. Builds a `SessionCreateParams` with `Mode.SUBSCRIPTION`, the price, success/cancel URLs, and metadata `{user_id: "123", plan_id: "2"}`. If the user has a `stripeCustomerId`, passes it; otherwise passes `customerEmail` so Stripe creates a new customer. Calls `Session.create(params)` — an HTTP call to Stripe's API. Returns the `session.getUrl()` to the client.
3. **Client** is redirected to Stripe's hosted checkout page, completes payment.
4. **Stripe** sends `POST /webhooks/payment` to MorphCode's server.
5. **`BillingController.handlePaymentWebhooks()`** calls `Webhook.constructEvent(payload, sigHeader, webhookSecret)` to verify the signature. Deserializes the event's data object. If it is a `Session`, extracts `metadata`. Calls `paymentProcessor.handleWebhookEvent(type, stripeObject, metadata)`.
6. **`StripePaymentProcessor.handleCheckoutSessionCompleted()`** extracts `userId` and `planId` from metadata. Updates `User.stripeCustomerId` if not yet set. Calls `subscriptionService.activateSubscription(userId, planId, subscriptionId, customerId)`.
7. **`SubscriptionServiceImpl.activateSubscription()`** checks idempotency via `existsByStripeSubscriptionId()`. Creates a new `Subscription` with status `INCOMPLETE` (awaiting the `customer.subscription.updated` event to set it to `ACTIVE`).

### Q: How does webhook signature verification work? What does `Webhook.constructEvent()` do?
**A:** `Webhook.constructEvent(payload, sigHeader, webhookSecret)` implements Stripe's signature verification scheme:
1. Parses the `Stripe-Signature` header to extract the `t=` timestamp and `v1=` HMAC signature.
2. Constructs the signed payload as `"<timestamp>.<raw body>"`.
3. Computes `HMAC-SHA256(webhookSecret, signedPayload)` using the webhook signing secret from Stripe.
4. Compares the computed signature with `v1=` from the header.
5. Optionally checks that the timestamp is within a tolerance window (default 300 seconds) to prevent replay attacks.
6. If valid, parses the JSON body and returns an `Event` object.
7. If invalid, throws `SignatureVerificationException`.

This ensures that only genuine Stripe events reach the handler — an attacker cannot forge or replay webhook events. The `webhookSecret` is injected from `@Value("${stripe.webhook.secret}")`.

### Q: Why is `/webhooks/**` declared `permitAll()` in `WebSecurityConfig`?
**A:** Webhook calls come from Stripe's servers, which do not have a user JWT. They arrive as unauthenticated HTTP requests. If the path were protected by JWT authentication, every webhook call would be rejected with 401. Security is instead provided by signature verification inside the handler — `Webhook.constructEvent()` is the authentication gate. This is the standard pattern for webhook security: no JWT, but signed payload verification.

### Q: What is the fallback `deserializeUnsafe()` path and when does it trigger?
**A:** `event.getDataObjectDeserializer().getObject()` returns an `Optional` that is empty when the Stripe Java SDK's API version does not match the version of the received event (which includes the schema version). This can happen when Stripe sends a new event schema version before the SDK is updated. `deserializer.deserializeUnsafe()` ignores API version mismatches and deserializes the raw JSON anyway, using the current SDK's model classes. It can produce partially populated objects if fields have been renamed or restructured between versions. The `BillingController` logs a warning and falls back to it, which is a reasonable production pattern: better to process an imperfect object than to fail silently.

### Q: How does MorphCode handle idempotency for the `checkout.session.completed` event?
**A:** `SubscriptionServiceImpl.activateSubscription()` checks `subscriptionRepository.existsByStripeSubscriptionId(subscriptionId)` before creating a new `Subscription`. If a subscription with that Stripe subscription ID already exists, the method returns immediately. Stripe guarantees at-least-once delivery of webhooks, so the same event may arrive multiple times (e.g., due to retries after a 5xx from MorphCode). This idempotency check prevents duplicate `Subscription` rows from being created. However, since `stripeSubscriptionId` has no unique constraint at the DB level, a race condition with two simultaneous webhook deliveries could bypass the application-level check and create duplicates. Adding a `UNIQUE` constraint on `stripeSubscriptionId` would make the idempotency bulletproof at the DB level.

### Q: Why does `handleInvoicePaid()` make an additional Stripe API call to `Subscription.retrieve()`?
**A:** The `Invoice` object in the `invoice.paid` event payload does not directly contain the current subscription period dates (the data reflects the invoice's period, not the subscription's current period). To get accurate `currentPeriodStart` and `currentPeriodEnd`, `StripePaymentProcessor` calls `Subscription.retrieve(subId)` to fetch the live subscription data from Stripe. The first item's period dates are then extracted and used to call `subscriptionService.renewSubscriptionPeriod()`. This extra API call adds latency to webhook processing but is necessary for data accuracy.

### Q: What Stripe events does MorphCode handle and what does each do?
**A:**
- **`checkout.session.completed`**: First payment succeeds; creates `Subscription` record with `INCOMPLETE` status, saves `stripeCustomerId` on `User`.
- **`customer.subscription.updated`**: Fired when subscription status changes (cancel scheduled, plan upgrade, trial ends, etc.); calls `subscriptionService.updateSubscription()` to sync status, dates, and plan.
- **`customer.subscription.deleted`**: Subscription is fully ended; calls `cancelSubscription()` to set status `CANCELED`.
- **`invoice.paid`**: Successful renewal; updates billing period via `renewSubscriptionPeriod()`, resets status to `ACTIVE` if it was `PAST_DUE` or `INCOMPLETE`.
- **`invoice.payment_failed`**: Renewal payment fails; calls `markSubscriptionPastDue()` to set status `PAST_DUE`.

Unhandled events are logged at DEBUG and ignored (`default -> log.debug("Ignoring the event: {}", type)`).

---

## Subscription Lifecycle

### Q: What subscription statuses exist and how do they map from Stripe?
**A:** `SubscriptionStatus` enum: `ACTIVE`, `TRIALING`, `CANCELED`, `PAST_DUE`, `INCOMPLETE`.

Stripe status → MorphCode status mapping in `mapStripeStatusToEnum()`:
- `"active"` → `ACTIVE`
- `"trialing"` → `TRIALING`
- `"past_due"`, `"unpaid"`, `"paused"`, `"incomplete_expired"` → `PAST_DUE` (all treated as payment-failed states)
- `"canceled"` → `CANCELED`
- `"incomplete"` → `INCOMPLETE` (payment initiated but not yet confirmed)

Multiple Stripe statuses map to `PAST_DUE` because MorphCode's business logic treats them equivalently: the user's access may be restricted.

### Q: How does `canCreateNewProject()` enforce plan limits?
**A:** `SubscriptionServiceImpl.canCreateNewProject()`:
1. Calls `getCurrentSubscription()` to find the user's active/trialing/past_due subscription.
2. Calls `projectMemberRepository.countProjectOwnedByUser(userId)` — counts rows where the user is an OWNER.
3. If no active subscription: compares against `FREE_TIER_PROJECTS_ALLOWED = 100`.
4. If has subscription: compares against `currentSubscription.plan().maxProjects()`.
5. Returns `true` if under the limit.

This is called at the start of `ProjectServiceImpl.createProject()` before any DB writes. If it returns false, `BadRequestException` is thrown.

### Q: Why does `activateSubscription()` set `status = INCOMPLETE` rather than `ACTIVE`?
**A:** After a `checkout.session.completed` event, Stripe has collected payment intent but the underlying subscription's lifecycle events (specifically `customer.subscription.updated` with status `"active"`) have not yet been processed. Setting to `INCOMPLETE` signals that the subscription row exists but is awaiting confirmation. The `customer.subscription.updated` event (which fires shortly after checkout completion) will call `updateSubscription()` with status `ACTIVE`, completing the lifecycle. `renewSubscriptionPeriod()` also auto-heals `INCOMPLETE` to `ACTIVE` when an `invoice.paid` event arrives.

### Q: What is `cancelAtPeriodEnd` on `Subscription` and how does it get set?
**A:** `cancelAtPeriodEnd: Boolean` mirrors Stripe's `cancel_at_period_end` flag. When a user cancels through the Stripe customer portal, Stripe does not immediately delete the subscription — it schedules cancellation at the end of the current billing period. Stripe fires `customer.subscription.updated` with `cancelAtPeriodEnd = true` and status still `"active"`. `StripePaymentProcessor.handleCustomerSubscriptionUpdated()` extracts this flag and passes it to `subscriptionService.updateSubscription()`, which sets it on the `Subscription` entity. The UI can use this flag to show "Your subscription cancels on [date]".

### Q: How does the customer portal work and what can users do there?
**A:** `StripePaymentProcessor.openCustomerPortal()` calls Stripe's billing portal API: `com.stripe.model.billingportal.Session.create(params with setCustomer(stripeCustomerId).setReturnUrl(frontendUrl))`. Stripe creates a hosted portal session and returns a URL. The user is redirected to Stripe's hosted page where they can: update payment methods, view invoice history, change plan (if configured in Stripe dashboard), or cancel subscription. After completing portal actions, Stripe fires webhook events that MorphCode handles. The `returnUrl` brings them back to the frontend. Users without a `stripeCustomerId` get a `BadRequestException` — they have never completed a checkout, so there is no Stripe customer to manage.

---

## API Design Patterns

### Q: Describe the overall REST API design conventions in MorphCode.
**A:** MorphCode follows resource-oriented REST conventions:
- `GET /api/projects` — list resources
- `POST /api/projects` — create resource
- `GET /api/projects/{projectId}` — read one resource
- `PATCH /api/projects/{projectId}` — partial update (though controllers use `@PutMapping` for some updates)
- `DELETE /api/projects/{projectId}` — soft delete
- Plural resource names, `/api` prefix, nested resources for relationships (e.g., `/api/projects/{projectId}/members`)
- Response bodies are structured DTOs (not entities) via MapStruct mappers
- Error responses follow `ApiError(HttpStatus status, String message)` with optional `List<ApiFieldError> errors` for validation failures

### Q: Why is SSE used for AI streaming rather than WebSockets?
**A:** `POST /api/chat/stream` returns `Flux<ServerSentEvent>` (SSE). SSE is simpler than WebSockets for unidirectional server-to-client streaming: it uses a standard HTTP connection, works through proxies and firewalls that understand HTTP chunked transfer, and requires no upgrade handshake. For an AI code generation use case where the client sends one message and the server streams the response token by token, SSE is the natural fit — there is no need for bidirectional communication. WebSockets add complexity (connection management, ping/pong keepalives, state management) that SSE avoids.

### Q: How does `ProjectServiceImpl` use `getReferenceById()` and why is it better than `findById()` in `createProject()`?
**A:** In `createProject()`, the user entity is needed only to set a FK reference on `ProjectMember` — `projectMember.user = owner`. Calling `userRepository.getReferenceById(userId)` returns a Hibernate proxy without issuing a `SELECT`. Hibernate knows the ID and uses it when building the `INSERT` for `project_members`. `findById(userId)` would issue an extra `SELECT users WHERE id = ?` whose result is immediately discarded. Since `userId` comes from the validated JWT, we can trust it exists without a DB round-trip. The proxy only materializes into a real SQL query if a field other than `id` is accessed — which never happens in this code path.

### Q: Why is `@Transactional` on `ProjectServiceImpl` but the import is `org.springframework` while `ProjectMemberServiceImpl` uses `jakarta`?
**A:** `org.springframework.transaction.annotation.Transactional` is the Spring-specific annotation. `jakarta.transaction.Transactional` is the Jakarta EE/CDI standard annotation, also supported by Spring. Both work correctly in a Spring application — Spring's transaction management processes both. The inconsistency is a code style issue rather than a functional bug, but it adds cognitive overhead. A project convention should pick one. The Spring-native annotation offers additional attributes like `propagation`, `isolation`, `readOnly`, and `rollbackFor` that the Jakarta annotation also supports, so neither is strictly superior.

### Q: What is MapStruct and how is it used?
**A:** MapStruct is a compile-time code generator for Java bean mapping. It reads interface annotations like `@Mapper` and generates implementations at build time. MorphCode uses it in `UserMapper`, `ProjectMapper`, `SubscriptionMapper`, `ProjectMemberMapper` to convert between entity objects and DTOs. For example, `userMapper.toEntity(SignupRequest)` converts a request record to a `User` entity, and `userMapper.toUserProfileResponse(User)` converts a `User` entity to `UserProfileResponse`. This avoids hand-written boilerplate conversion code and keeps DTOs separate from entities. Compile-time generation also means mapping errors (e.g., missing fields) are caught at build time, not at runtime.

### Q: Explain the `GlobalExceptionHandler` and its relationship to `JwtAuthFilter`.
**A:** `GlobalExceptionHandler` is annotated `@RestControllerAdvice`, making it an AOP advice applied to all `@RestController` classes. It defines `@ExceptionHandler` methods for each exception type, returning `ResponseEntity<ApiError>`. The handled types include: `BadRequestException` (400), `ResourceNotFoundException` (404), `MethodArgumentNotValidException` (400 with field errors), `UsernameNotFoundException` (404), `AuthenticationException` (401), `JwtException` (401), `AccessDeniedException` (403).

The connection to `JwtAuthFilter`: since the filter runs outside the DispatcherServlet's exception handling scope, exceptions from `verifyAccessToken()` (like `JwtException`) would produce a raw 500 error page. `JwtAuthFilter` injects `HandlerExceptionResolver` and calls `handlerExceptionResolver.resolveException(request, response, null, e)` in its catch block, which routes the exception to `GlobalExceptionHandler` as if it had been thrown from a controller method. This unifies error handling across filters and controllers.

---

## General Backend System Design

### Q: How does MorphCode handle horizontal scalability?
**A:** MorphCode is designed to be stateless at the application layer:
- **No in-memory session state**: `SessionCreationPolicy.STATELESS` ensures Spring Security never uses sessions.
- **JWT authentication**: Identity is self-contained in each request's token.
- **External state stores**: All persistent state is in PostgreSQL and MinIO — both can be run as separate, scalable services.
- **Reactive streaming**: Project uses Project Reactor's `Flux` for SSE, which is non-blocking and scales to many concurrent connections.

However, `finalizeChats()` uses `Schedulers.boundedElastic()`, whose thread pool is bounded. Under high load, the post-processing (saving messages, writing MinIO files) would queue up and could exhaust the pool, silently delaying or dropping DB writes. True horizontal scale would require an async task queue (RabbitMQ, Kafka) for post-stream work.

### Q: What are the main known bugs in MorphCode and how would you fix them?
**A:**
1. **`ChatSessionId` missing `@Embeddable`**: Add `@Embeddable` annotation to `ChatSessionId`. Without it, Hibernate throws a `MappingException` at startup.
2. **`ProjectMemberId` missing `equals()`/`hashCode()`**: Add `@EqualsAndHashCode` from Lombok. Without it, entity cache lookups and test assertions break.
3. **`GET /api/auth/me` hardcodes `userId = 1L`**: Replace with `authUtil.getCurrentUserId()` in `AuthController.getProfile()`.
4. **`GET /api/plans` returns `[]`**: `PlanServiceImpl.getAllActivePlans()` never calls `PlanRepository`. Fix by calling `planRepository.findAll()` (or `findByActiveTrue()`).
5. **`ASSISTANT ChatMessage.content` is hardcoded `"Assistant Message here..."`**: The actual AI response is stored in `ChatEvent` records, but the `ChatMessage.content` field is not populated. Either store the full text or mark the field as intentionally empty.
6. **Daily token gate commented out**: `usageService.checkDailyTokensUsage()` is commented out in `AiGenerationServiceImpl.streamResponse()`, so plan limits on AI usage are not enforced.
7. **`stripeSubscriptionId` missing DB unique constraint**: Adding `@Column(unique = true)` would prevent duplicate subscriptions under race conditions with simultaneous webhook delivery.
8. **`Preview` entity missing `@Entity`**: The `Preview` class is not annotated `@Entity`, so it is not mapped to any table and the feature is completely unimplemented.
9. **`ProjectFileServiceImpl` bucket name inconsistency**: `BUCKET_NAME = "projects"` (hardcoded for reads) vs `@Value("${minio.project-bucket}")` (property for writes) — if the property value differs from `"projects"`, reads and writes silently target different buckets.

### Q: Why does `finalizeChats()` run on `Schedulers.boundedElastic()` instead of the main reactive pipeline?
**A:** The main stream pipeline (`chatClient.prompt().stream()`) runs on a reactive (non-blocking) scheduler. Database operations via Spring Data JPA are blocking (they use JDBC, which blocks a thread). Mixing blocking calls into a non-blocking reactive pipeline would starve the reactive thread pool. `Schedulers.boundedElastic()` is designed for blocking I/O — it creates threads on demand up to a maximum (default 10x CPU cores), backed by a queue for overflow. Offloading `finalizeChats()` to this scheduler via `doOnComplete(() -> Schedulers.boundedElastic().schedule(...))` keeps the reactive stream clean and non-blocking while still executing DB writes. The risk is pool exhaustion under high concurrent load.

### Q: How does MorphCode ensure a user can only see their own projects?
**A:** There are two layers:
1. **Service query layer**: `ProjectRepository.findAllAccessibleByUser(userId)` and `findAccessibleProjectById(projectId, userId)` always include the caller's `userId` in the query — a user can never get back rows for projects they are not a member of.
2. **Method-level security**: `@PreAuthorize("@security.canViewProject(#projectId)")` calls `SecurityExpressions.canViewProject()`, which queries `projectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId)`. If no membership record exists, `orElse(false)` makes the check return `false`, throwing `AccessDeniedException`.

The combination means: even if a controller bug passed the wrong `projectId`, the service method would reject unauthorized access before querying further.

### Q: Describe the project creation flow and all side effects.
**A:** `ProjectServiceImpl.createProject(ProjectRequest request)`:
1. Calls `subscriptionService.canCreateNewProject()` — throws `BadRequestException` if user is at plan limit.
2. Gets `userId` from `authUtil.getCurrentUserId()`.
3. Gets a `User` proxy via `userRepository.getReferenceById(userId)` — no SELECT issued.
4. Creates and saves a `Project` entity with `name` and `isPublic = false` — PostgreSQL assigns ID via IDENTITY.
5. Creates `ProjectMemberId(project.getId(), owner.getId())` and saves a `ProjectMember` with `OWNER` role, both `invitedAt` and `acceptedAt` set to `Instant.now()` (owner is auto-accepted).
6. Calls `projectTemplateService.initializeProjectFromTemplate(project.getId())` — this copies the template files from MinIO bucket `starter-projects` to the `projects` bucket under prefix `{projectId}/`, and creates `ProjectFile` DB rows for each file.
7. Maps the saved `Project` to `ProjectResponse` via MapStruct and returns.

All of this is within a `@Transactional` method (`@Transactional` on class level in `ProjectServiceImpl`), so if the MinIO copy fails and throws an exception, the DB transaction rolls back — no orphaned project or project member rows.

### Q: What happens to `FileTreeContextAdvisor` and why is it a performance concern?
**A:** `FileTreeContextAdvisor` is a `StreamAdvisor` that intercepts every LLM call and queries the database for the project's file tree before sending the request to the AI. It receives `userId` and `projectId` from `advisorParams` and runs a query against `ProjectFileRepository` (or similar). The CLAUDE.md notes: "queries DB on every LLM call — no cache. N queries per stream on large projects." Since SSE streams may involve multiple internal LLM calls (tool calls trigger re-entry into the model), the file tree is fetched on every iteration. An improvement would be caching the file tree in a request-scoped bean or Redis with a short TTL.

### Q: Why does the `Subscription` entity have `ManyToOne(fetch = FetchType.LAZY)` for both `user` and `plan`?
**A:** Most subscription queries (`findByStripeSubscriptionId`, `findByUserIdAndStatusIn`) only need the subscription's scalar fields plus the plan's `maxProjects` limit. Eagerly loading `User` and `Plan` on every subscription fetch would cause two extra `SELECT` queries. With `LAZY`, they only load if accessed. In `SubscriptionServiceImpl.canCreateNewProject()`, `currentSubscription.plan().maxProjects()` does access the plan — triggering one lazy load. But `currentSubscription.user()` is never accessed, so the user load is skipped. This selective loading is more efficient than `EAGER` defaults.

### Q: How would you add refresh tokens to MorphCode?
**A:** Currently, `generateAccessToken()` in `AuthUtil` creates a 100-minute JWT. To add refresh tokens:
1. Create a `RefreshToken` entity with `token` (UUID), `userId`, `expiresAt` (e.g., 30 days), `revoked` flag.
2. In `AuthServiceImpl.signup()` and `login()`, generate both an access token (shorter expiry, e.g., 15 minutes) and a refresh token UUID, saving the refresh token to DB.
3. Return both in `AuthResponse`.
4. Add `POST /api/auth/refresh` endpoint that: validates the refresh token UUID against the DB, checks `!revoked && !expired`, returns a new access token (optionally rotating the refresh token).
5. On logout, set `refreshToken.revoked = true`.
6. `@PreAuthorize` and `JwtAuthFilter` need no changes — they only see access tokens.
This gives revocation capability (logout immediately invalidates refresh tokens) while keeping access tokens short-lived and stateless.

### Q: How does `AuthServiceImpl.signup()` prevent race conditions on username uniqueness?
**A:** `signup()` calls `userRepository.findByUsername(username).ifPresent(u -> throw BadRequestException)`. This is a check-then-act pattern with no concurrency protection: two simultaneous signups with the same username could both pass the `ifPresent` check before either saves, resulting in duplicate users. The correct fix is a `UNIQUE` constraint on `users.username` at the DB level. If a duplicate insert occurs, PostgreSQL throws a `DataIntegrityViolationException` (which Spring wraps). `GlobalExceptionHandler` should handle this with a 409 Conflict response. As a secondary check, the application-level `findByUsername` is still useful for a friendly error message before the DB roundtrip, but the DB constraint is the true invariant.

### Q: Walk through what happens when a VIEWER tries to call the AI generation endpoint.
**A:**
1. Client sends `POST /api/chat/stream` with a valid JWT.
2. `JwtAuthFilter` validates the JWT, sets `JwtUserPrincipal` in `SecurityContext`.
3. `ChatController` calls `aiGenerationService.streamResponse(userMessage, projectId)`.
4. Spring's method security proxy intercepts the call, evaluates `@PreAuthorize("@security.canEditProject(#projectId)")`.
5. `SecurityExpressions.canEditProject(projectId)` calls `hasPermission(projectId, ProjectPermission.EDIT)`.
6. `projectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId)` returns `Optional<ProjectRole.VIEWER>`.
7. `ProjectRole.VIEWER.getPermissions().contains(ProjectPermission.EDIT)` returns `false` (VIEWER's permission set is `{VIEW, VIEW_MEMBERS}`).
8. `hasPermission()` returns `false`, `canEditProject()` returns `false`.
9. Spring throws `AccessDeniedException`.
10. `WebSecurityConfig`'s `accessDeniedHandler` routes it to `handlerExceptionResolver.resolveException()`.
11. `GlobalExceptionHandler.handleAccessDeniedException()` returns `ResponseEntity(403, "Access denied: Insufficient permissions")`.
12. The client receives HTTP 403 — the AI generation method body never executes.

---

## Known Bugs & Gotchas

### Q: The `GET /api/auth/me` endpoint always returns user ID 1. How would you fix it?
**A:** In `AuthController.getProfile()`, replace `Long userId = 1L;` with `Long userId = authUtil.getCurrentUserId();`. The route is in `/api/auth/**`, which is `permitAll()`, but the route does require a JWT in practice for the response to be useful. After the fix, the method would throw `AuthenticationCredentialsNotFoundException` if called without a JWT, returning 401. For a proper "me" endpoint, the route should ideally not be `permitAll()` — it should be moved to `anyRequest().authenticated()` scope or remain in `/api/auth/` but protected by requiring a valid JWT.

### Q: `GET /api/plans` always returns an empty list. What is the bug?
**A:** `PlanServiceImpl.getAllActivePlans()` never calls `PlanRepository`. The implementation likely creates an empty list or returns `List.of()` without querying the DB. The fix is to call `planRepository.findByActiveTrue()` (or `findAll()` if all plans should be returned) and map the results to `PlanResponse` via a mapper. Since `Plan` has a `Boolean active` field, `findByActiveTrue()` is the semantically correct query.

### Q: Why is the daily token gate commented out, and what are the risks?
**A:** `usageService.checkDailyTokensUsage()` is commented out in `AiGenerationServiceImpl.streamResponse()` with the comment `// usageService.checkDailyTokensUsage()`. Without this check, users can make unlimited AI generation calls regardless of their plan's `maxTokensPerDay` limit. This directly costs money — every LLM call to OpenRouter/Gemini Flash has a per-token cost. A user on the free tier could exhaust the application's OpenRouter budget. `UsageLog` records token usage via `usageService.recordTokenUsage()` (which runs in `finalizeChats()`) and the schema has a `unique_per_user_per_day` constraint ready to support the gate — but the enforcement is not wired up.

### Q: The `ASSISTANT ChatMessage.content` is hardcoded to `"Assistant Message here..."`. What is the architectural intent?
**A:** `ChatMessage` represents a coarse-grained message record (user or assistant), while `ChatEvent` represents fine-grained structured events within an assistant message (thoughts, file edits, text fragments). The design intent is that the actual AI response content is stored in `ChatEvent` records (each chunk/tag parsed from the LLM's XML protocol), while `ChatMessage` is a header/container record. The hardcoded content string is a placeholder — ideally it would either store the full response text or be left null with the actual content fully in `ChatEvent`. This is a TODO left in the code.

### Q: What is the `ProjectFileServiceImpl` bucket name divergence bug?
**A:** `ProjectFileServiceImpl` has:
- `private static final String BUCKET_NAME = "projects";` — used for reading files
- `@Value("${minio.project-bucket}") private String projectBucket;` — used for writing files

If `application.properties` sets `minio.project-bucket=my-projects-prod`, reads always go to the bucket named `"projects"` while writes go to `"my-projects-prod"`. Files written by the application are never found by subsequent reads. The fix is to remove the hardcoded constant and use `@Value("${minio.project-bucket}")` for both reads and writes.

---

*This document was prepared as comprehensive interview preparation for the MorphCode backend. Every answer references actual class names, method signatures, and code-level details from the codebase.*
