# MorphCode — Backend Interview Preparation

**Stack:** Java 17, Spring Boot 3.3.0, PostgreSQL 9010, MinIO, Spring AI 1.0.0, JJWT 0.12.6, MapStruct 1.5.2, Stripe SDK 31.1.0  
**Package root:** `com.morphcode.ai`  
**Entry point:** `MorphcodeApplication` — `@SpringBootApplication`, port 8082

---

## Table of Contents

1. [Spring Boot Fundamentals](#1-spring-boot-fundamentals)
2. [JPA and Hibernate](#2-jpa-and-hibernate)
3. [Composite Primary Keys and Embedded IDs](#3-composite-primary-keys-and-embedded-ids)
4. [Soft Delete Pattern](#4-soft-delete-pattern)
5. [Repository Layer and JPQL](#5-repository-layer-and-jpql)
6. [MapStruct](#6-mapstruct)
7. [Configuration Beans](#7-configuration-beans)
8. [Security — JWT and Spring Security](#8-security--jwt-and-spring-security)
9. [Error Handling](#9-error-handling)
10. [DTOs and Bean Validation](#10-dtos-and-bean-validation)
11. [Service Layer and Transactions](#11-service-layer-and-transactions)
12. [AI Integration and Streaming](#12-ai-integration-and-streaming)
13. [MinIO File Storage](#13-minio-file-storage)
14. [Billing and Stripe Integration](#14-billing-and-stripe-integration)
15. [Enums and RBAC](#15-enums-and-rbac)
16. [Project Structure and Design Decisions](#16-project-structure-and-design-decisions)
17. [Known Bugs and Limitations](#17-known-bugs-and-limitations)
18. [General Java Questions](#18-general-java-questions)

---

## 1. Spring Boot Fundamentals

### Q: What does `@SpringBootApplication` do on `MorphcodeApplication`? What three annotations does it replace?
**A:** `@SpringBootApplication` is a convenience meta-annotation that combines `@Configuration` (marks the class as a source of bean definitions), `@EnableAutoConfiguration` (tells Spring Boot to automatically configure beans based on classpath dependencies), and `@ComponentScan` (scans `com.morphcode.ai` and all sub-packages for components). In `MorphcodeApplication`, `main()` simply delegates to `SpringApplication.run(MorphcodeApplication.class, args)`, which bootstraps the entire application context, starts the embedded Tomcat server on port 8082, and processes all auto-configurations.

### Q: How is the server port configured, and how does Spring Boot resolve property values?
**A:** `server.port=8082` is set in `src/main/resources/application.properties`. Spring Boot reads this file automatically. This project also uses the `spring-dotenv` library (`me.paulschwarz:spring-dotenv:4.0.0`), which loads a `.env` file from the project root and makes those values available as Spring Environment properties. This means four secrets — `OPENROUTER_API_KEY`, `JWT_SECRET_KEY`, `STRIPE_API_SECRET`, `STRIPE_WEBHOOK_SECRET` — are kept out of source control in `.env` and referenced with `${...}` syntax in `application.properties`.

### Q: What is `spring-dotenv` and why is it used here instead of OS environment variables?
**A:** `spring-dotenv` is a community library that reads a `.env` file and registers its entries as a `PropertySource` in the Spring `Environment`, making them available via `@Value` or `@ConfigurationProperties`. It is used here so that a developer can keep secrets in a local `.env` file that is `.gitignore`d rather than setting OS-level environment variables on each machine. The library integrates early enough in the startup lifecycle that `@Value("${JWT_SECRET_KEY}")` resolves correctly without any extra wiring.

### Q: Walk through the entire request lifecycle from HTTP request to response in MorphCode.
**A:** A request arrives at Tomcat on port 8082. The Spring Security `FilterChain` processes it first: `JwtAuthFilter` (registered before `UsernamePasswordAuthenticationFilter`) reads the `Authorization` header, parses the JWT, and sets a `UsernamePasswordAuthenticationToken` on the `SecurityContextHolder`. If the path matches `/api/auth/**` or `/webhooks/**`, the filter chain permits the request without authentication. After security, the request reaches the `DispatcherServlet`, which routes it to the matching `@RestController` method based on `@RequestMapping` / `@GetMapping` / `@PostMapping` etc. The controller delegates to a `@Service` (usually via an interface), which performs business logic using `@Repository` beans backed by Spring Data JPA. Responses are serialised to JSON by Jackson. Exceptions thrown at any layer are caught by `GlobalExceptionHandler` (`@RestControllerAdvice`) which builds an `ApiError` record and writes an appropriate HTTP status.

### Q: How is CORS configured and what are the allowed origins?
**A:** CORS is configured via `CorsConfig`, a `@Configuration` class that declares a `WebMvcConfigurer` bean. Inside `addCorsMappings`, the pattern `/**` is mapped to allow origins `http://localhost:5173` and `http://localhost:5174` (the Vite dev server ports), all HTTP methods, all headers, and `allowCredentials(true)`. In `WebSecurityConfig`, `.cors(Customizer.withDefaults())` enables Spring Security to delegate CORS preflight handling to this `WebMvcConfigurer` definition. The comment in the file incorrectly says all origins are allowed — in reality only the two Vite ports are permitted.

### Q: Why is CSRF disabled in `WebSecurityConfig`?
**A:** CSRF protection is relevant for session-based (cookie) authentication because an attacker can exploit a browser's automatic cookie sending to forge requests on behalf of a logged-in user. MorphCode uses stateless JWT authentication (`SessionCreationPolicy.STATELESS`) — there are no server-side sessions and the JWT is sent in the `Authorization` header, not in a cookie. Since the browser never automatically attaches the JWT, CSRF attacks are not applicable, so `.csrf(csrfConfig -> csrfConfig.disable())` is safe here.

### Q: What is `@EnableMethodSecurity` and what does it unlock?
**A:** `@EnableMethodSecurity` (on `WebSecurityConfig`) activates Spring Security's method-level security. It enables `@PreAuthorize`, `@PostAuthorize`, `@Secured`, and related annotations on service and controller methods. In this project `@PreAuthorize("@security.canEditProject(#projectId)")` is used on service methods like `streamResponse`, `updateProject`, `softDelete`, `inviteMember`, etc. The `@security` in the SpEL expression refers to the `SecurityExpressions` bean registered with `@Component("security")`.

### Q: What is the role of `HandlerExceptionResolver` being injected into `JwtAuthFilter` and `WebSecurityConfig`?
**A:** `HandlerExceptionResolver` is a Spring MVC component that knows how to translate an exception into an HTTP response by finding an appropriate `@ExceptionHandler`. By injecting it into the security filter, exceptions thrown inside the filter (such as a `JwtException` from a malformed token) can be routed to `GlobalExceptionHandler` rather than generating a generic 500 response or a Spring Security default error page. `handlerExceptionResolver.resolveException(request, response, null, e)` effectively delegates the exception to the `@RestControllerAdvice` layer even though the code is executing inside a servlet filter before MVC has started.

### Q: How does `DispatcherType.ASYNC` and `DispatcherType.ERROR` relate to the SSE streaming endpoint?
**A:** The security config explicitly `permitAll()` for both `DispatcherType.ASYNC` and `DispatcherType.ERROR`. The SSE/streaming chat endpoint (`/api/chat/stream`) returns a `Flux<ServerSentEvent<StreamResponse>>` which causes the servlet container to use async dispatch to push multiple responses. Without `DispatcherType.ASYNC` being permitted, the security filter chain would block the subsequent async dispatches. `DispatcherType.ERROR` is also permitted so that error forwarding from the container to the `BasicErrorController` is not intercepted.

---

## 2. JPA and Hibernate

### Q: Walk through the `User` entity and explain every annotation.
**A:** `User` is annotated with `@Entity` (marks it as a JPA-managed class) and `@Table(name="users")` (maps to the `users` table — required because the default would be `user` which is a reserved word in PostgreSQL). `@Id` + `@GeneratedValue(strategy=GenerationType.IDENTITY)` on the `Long id` field means the database auto-increments the primary key column and Hibernate retrieves the generated value after an INSERT. `@Column(unique=true)` on `stripeCustomerId` creates a unique constraint. `@CreationTimestamp` and `@UpdateTimestamp` (Hibernate-specific, from `org.hibernate.annotations`) automatically populate `createdAt` and `updatedAt` as `Instant` values on INSERT and UPDATE respectively. `deletedAt Instant` has no annotation — it is a plain nullable column used for soft delete. The class implements `UserDetails` which Spring Security's `DaoAuthenticationProvider` requires; `getAuthorities()` returns an empty list since there are no roles.

### Q: What is `@CreationTimestamp` vs `@UpdateTimestamp`? Why use Hibernate-specific annotations rather than JPA lifecycle callbacks?
**A:** `@CreationTimestamp` tells Hibernate to set the field to the current timestamp only on the first INSERT and never update it again. `@UpdateTimestamp` sets the field on every INSERT and UPDATE. The JPA alternative is `@PrePersist` / `@PreUpdate` callback methods on the entity, which requires boilerplate. `@Column(updatable=false)` is also used on `ChatSession.createdAt` alongside `@CreationTimestamp` as an extra safeguard to prevent the column being included in UPDATE statements at the SQL level.

### Q: Explain `@ManyToOne` with `FetchType.LAZY` and why it matters for `ChatSession`.
**A:** `FetchType.LAZY` means Hibernate does not load the associated entity (`Project` or `User`) when the owning entity is fetched. Instead it creates a proxy object, and the actual SQL SELECT is triggered only when a getter that touches a field of the associated entity is called. In `ChatSession`, both `project` and `user` are `LAZY`. This is the safe default for `@ManyToOne` in Spring applications; without it (`FetchType.EAGER`), loading a single `ChatSession` would immediately JOIN to both `projects` and `users` tables even if those associations are never needed, causing unnecessary database load. The tradeoff is that the entity must be accessed within an open Hibernate session, otherwise a `LazyInitializationException` is thrown — this is guarded by keeping service calls within `@Transactional` boundaries.

### Q: What does `@Enumerated(EnumType.STRING)` do, and why is it preferred over `EnumType.ORDINAL`?
**A:** `@Enumerated(EnumType.STRING)` tells Hibernate to store the enum name as a VARCHAR in the database (e.g., `"OWNER"`, `"EDITOR"`, `"VIEWER"`). `EnumType.ORDINAL` stores the integer index (0, 1, 2, ...) instead. `STRING` is strongly preferred because adding or reordering enum constants does not corrupt existing data. With `ORDINAL`, inserting a new value in the middle of the enum breaks all existing rows. Used on `ProjectMember.projectRole`, `ChatMessage.role`, `ChatEvent.type`, `Subscription.status`, and `ChatSession`-related enums.

### Q: How does the `ChatMessage` entity handle its relationship with `ChatSession` which has a composite key?
**A:** `ChatSession` has an `@EmbeddedId ChatSessionId` composed of `projectId` and `userId`. `ChatMessage.chatSession` is a `@ManyToOne` with `@JoinColumns` specifying two `@JoinColumn` entries: `project_id` referencing `project_id` in `chat_sessions`, and `user_id` referencing `user_id` in `chat_sessions`. This correctly maps the foreign key to a composite primary key in the parent table.

### Q: What is `ddl-auto=update` and what are its risks in production?
**A:** `spring.jpa.hibernate.ddl-auto=update` instructs Hibernate to compare the current entity model against the database schema at startup and apply ALTER TABLE / CREATE TABLE statements to bring the schema in sync. It is convenient during development since you never have to write migration scripts manually. However it is dangerous in production: it cannot drop columns (data loss risk), it cannot handle complex schema changes (like splitting a table), it may make irreversible changes, and it lacks the auditability of explicit migration scripts. The production-grade alternative is `ddl-auto=validate` combined with a migration tool like Flyway or Liquibase.

### Q: How is the N+1 query problem handled in `ChatMessageRepository`?
**A:** Without special handling, fetching a list of `ChatMessage` entities would issue one query for messages and then N separate queries to load each message's `events` collection (`@OneToMany`). `ChatMessageRepository` solves this with a JPQL `JOIN FETCH`:
```sql
SELECT DISTINCT m FROM ChatMessage m
LEFT JOIN FETCH m.events e
WHERE m.chatSession = :chatSession
ORDER BY m.createdAt ASC, e.sequenceOrder ASC
```
`JOIN FETCH` tells Hibernate to use a single SQL JOIN to load both `chat_messages` and `chat_events` in one query. `DISTINCT` is required because the JOIN multiplies rows (one per event) and without it the same `ChatMessage` appears multiple times in the result list. The comment in the file explicitly documents the N+1 problem and the fix.

### Q: What is `@Column(columnDefinition = "text")` and when is it used?
**A:** By default Hibernate maps a `String` field to a `VARCHAR(255)` column in PostgreSQL. For fields that can hold arbitrarily large text — like `ChatMessage.content` (the user's full message), `ChatEvent.content` (a file's entire source code), and `ChatEvent.metadata` — `@Column(columnDefinition = "text")` overrides the DDL to generate a `TEXT` column which has no length limit in PostgreSQL. This is critical for storing file contents which can easily exceed 255 characters.

### Q: Why does `UsageLog` use `@UniqueConstraint` at the table level instead of `@Column(unique=true)` on each field?
**A:** `@Column(unique=true)` creates a single-column unique constraint. `UsageLog` needs a composite unique constraint across two columns (`user_id` and `date`) — meaning one log row per user per day. This requires `@Table(uniqueConstraints = { @UniqueConstraint(columnNames = {"user_id", "date"}) })` at the class level. A single-column unique constraint on `user_id` would prevent a user from ever having more than one log entry (wrong), and a constraint on `date` alone would allow only one user to have a log on any given day (also wrong).

### Q: Explain `@OneToMany(mappedBy="chatMessage", cascade=CascadeType.ALL, fetch=FetchType.LAZY)` on `ChatMessage.events`.
**A:** `mappedBy="chatMessage"` tells JPA that the `ChatEvent` entity owns this relationship (the foreign key column `chat_message_id` lives in `chat_events`). `CascadeType.ALL` means any persistence operation (persist, merge, remove, refresh, detach) on a `ChatMessage` is cascaded to its `events` collection — saving a `ChatMessage` automatically saves its events. `FetchType.LAZY` means events are not loaded unless accessed. `@OrderBy("sequenceOrder ASC")` ensures that when events are loaded they come back in the correct sequence order.

### Q: What is `getReferenceById` and how is it different from `findById`? Where is it used?
**A:** `getReferenceById(id)` returns a Hibernate proxy without hitting the database. The proxy only contains the ID. If you need the entity for a foreign key association (e.g., assigning an owner to a `Project`), you only need the ID anyway — you do not need the full entity. This avoids a SELECT query. `findById(id)` issues an immediate SELECT and returns `Optional<T>`. In `ProjectServiceImpl.createProject()`, `userRepository.getReferenceById(userId)` is used to get the owner `User` reference just to set it on `ProjectMember`, avoiding an unnecessary DB round trip. The risk is that if you access a field other than the ID on the proxy outside a transaction, you get `LazyInitializationException`.

---

## 3. Composite Primary Keys and Embedded IDs

### Q: What is `@EmbeddedId` and why is it used for `ProjectMember`?
**A:** `@EmbeddedId` marks a field as a composite primary key whose definition is in a separate `@Embeddable` class. `ProjectMember` is keyed by `(projectId, userId)` together — neither alone is unique. `ProjectMemberId` is an `@Embeddable` class with two `Long` fields. JPA requires that an `@Embeddable` class implement `Serializable`, have a no-arg constructor, and override `equals`/`hashCode` consistently (though in this codebase Lombok's `@Getter`/`@Setter` are used and `equals`/`hashCode` are not explicitly defined — a potential issue for cache-based operations).

### Q: What is `@MapsId` and how is it used on `ProjectMember`?
**A:** When you have an `@EmbeddedId` and also want `@ManyToOne` associations to the same tables whose PKs form the composite key, you use `@MapsId` to tell JPA which field of the embeddable ID corresponds to which association. In `ProjectMember`:
- `@MapsId("projectId")` on `Project project` means Hibernate derives the `projectId` field of `ProjectMemberId` from `project.getId()`.
- `@MapsId("userId")` on `User user` means `userId` comes from `user.getId()`.

This avoids having to set the `ProjectMemberId` fields separately from the associated entities. You can build a `ProjectMemberId`, assign it to `.id()`, and the join columns are consistent.

### Q: What is the bug in `ChatSessionId` and what are its consequences?
**A:** `ChatSessionId` is missing the `@Embeddable` annotation. `ProjectMemberId` correctly has `@Embeddable`. Without `@Embeddable`, Hibernate does not know that `ChatSessionId` is an embeddable type and the mapping for `ChatSession`'s `@EmbeddedId private ChatSessionId id` is undefined. At runtime, Hibernate may throw a mapping exception on startup, or silently ignore the composite key structure, leading to incorrect DDL generation and unpredictable query behaviour. The correct fix is to add `@Embeddable` to `ChatSessionId`. Additionally, `ChatSessionId` implements `Serializable` (which is required for embeddable IDs) but does not override `equals` and `hashCode` — without these, the entity cannot be correctly used as a map key or in the second-level cache.

### Q: Why must an `@Embeddable` class implement `Serializable`?
**A:** JPA mandates that embeddable IDs and entity IDs implement `Serializable` because they may be stored in the second-level cache (which serializes them), used as map keys, or detached and reattached across different contexts. `ProjectMemberId` does not explicitly declare `implements Serializable`, which is a subtle bug — though Hibernate may work without it in most cases, it violates the JPA spec. `ChatSessionId` does declare `implements Serializable`.

### Q: How is `ChatSessionRepository` able to use `findById(ChatSessionId)` given the `@Embeddable` bug?
**A:** At the repository level, `ChatSessionRepository extends JpaRepository<ChatSession, ChatSessionId>` simply uses the ID type as declared. In `AiGenerationServiceImpl`, `chatSessionRepository.findById(chatSessionId)` passes a `ChatSessionId` instance. Whether Hibernate can actually execute this depends on whether it correctly resolved the `@EmbeddedId` mapping at startup. Because `@Embeddable` is missing, the application may start only with a warning (Hibernate is lenient), and the query may or may not work correctly depending on the Hibernate version. This is a documented known bug.

---

## 4. Soft Delete Pattern

### Q: How is soft delete implemented in MorphCode and why?
**A:** Three entities — `User`, `Project`, and `ChatSession` — have a nullable `Instant deletedAt` field. When a project is deleted, `ProjectServiceImpl.softDelete()` sets `project.setDeletedAt(Instant.now())` and saves. The record remains in the database, preserving audit history, related chat messages, and billing records. Hard delete would break foreign key constraints and lose data.

### Q: What is the downside of the current soft delete implementation compared to Hibernate's `@Where`?
**A:** The current approach requires every query that reads projects to manually filter `WHERE deleted_at IS NULL`. If any developer forgets this filter in a JPQL query, they will see deleted projects. Hibernate offers `@Where(clause = "deleted_at IS NULL")` as a class-level annotation that automatically appends the condition to every query for that entity, enforcing the filter as a cross-cutting concern. The codebase deliberately does not use `@Where` (noted in CLAUDE.md) — the JPQL queries in `ProjectRepository` manually include `AND p.deletedAt IS NULL`. The risk is that a future query could accidentally expose deleted projects.

### Q: Show how the soft delete filter is applied in `ProjectRepository`.
**A:** In `findAllAccessibleByUser`:
```jpql
WHERE pm.user.id = :userId AND p.deletedAt IS NULL ORDER BY p.updatedAt DESC
```
In `findAccessibleProjectById`:
```jpql
WHERE p.id = :projectId AND p.deletedAt IS NULL AND EXISTS (SELECT 1 FROM ProjectMember pm WHERE ...)
```
Both queries explicitly check `p.deletedAt IS NULL`. The `@Index` annotations on the `Project` entity include `deletedAt` in several indexes specifically to make these filter queries efficient.

### Q: Why are three `@Index` annotations defined on `Project`?
**A:** The `Project` table is expected to be queried with soft delete filter and ordered by recency:
1. `idx_projects_updated_at_desc` on `(updated_at DESC, deleted_at)` — optimises the common "get my projects, newest first, not deleted" query.
2. `idx_projects_deleted_at_updated_at_desc` on `(deleted_at, updated_at DESC)` — a variant for filtering deleted/non-deleted with ordering.
3. `idx_project_deleted_at` on `(deleted_at)` — a simple index for filtering by soft delete status.

These are DDL-only hints to the database generated by Hibernate when `ddl-auto=update` runs.

---

## 5. Repository Layer and JPQL

### Q: What is Spring Data JPA and what does extending `JpaRepository<T, ID>` give you?
**A:** Spring Data JPA is a Spring abstraction over JPA repositories. Extending `JpaRepository<T, ID>` automatically provides implementations for: `save(entity)`, `findById(id)`, `findAll()`, `deleteById(id)`, `existsById(id)`, `saveAll(entities)`, `getReferenceById(id)`, pagination via `PagingAndSortingRepository`, and more. Spring generates a proxy implementation at runtime — no SQL or boilerplate code is needed for standard CRUD. The ID type for `ProjectMemberRepository` is `ProjectMemberId` (the composite key class), and for `ChatSessionRepository` it is `ChatSessionId`.

### Q: Explain the `ProjectWithRole` interface projection and how it works.
**A:** `ProjectRepository.findAllAccessibleByUser` returns `List<ProjectWithRole>` and `findAccessibleProjectByIdWithRole` returns `Optional<ProjectWithRole>`. `ProjectWithRole` is a nested interface inside `ProjectRepository` with two getter methods: `getProject()` returning `Project` and `getRole()` returning `ProjectRole`. The JPQL selects `p as project, pm.projectRole as role` — Spring Data JPA's interface-based projection maps the `project` alias to `getProject()` and the `role` alias to `getRole()` using Hibernate's tuple result mechanism. This is efficient because it avoids loading extra associations and lets the caller get both the entity and the role in a single query.

### Q: What is `findByIdProjectId(Long projectId)` in `ProjectMemberRepository` and how does Spring Data JPA parse it?
**A:** Spring Data JPA uses a method name parsing algorithm. `findBy` indicates a query method. `IdProjectId` is parsed as navigating the `id` field (of type `ProjectMemberId`) and then the `projectId` field within it — i.e., `WHERE entity.id.projectId = :projectId`. This works because `ProjectMember` has `@EmbeddedId ProjectMemberId id` and `ProjectMemberId` has a `projectId` field. The generated query is `SELECT pm FROM ProjectMember pm WHERE pm.id.projectId = ?1`.

### Q: Why does `SubscriptionRepository` use `findByUserIdAndStatusIn` with a `Set<SubscriptionStatus>`?
**A:** A user can have at most one active subscription, but the definition of "active" spans three statuses: `ACTIVE`, `PAST_DUE` (still has access but payment failed), and `TRIALING`. `findByUserIdAndStatusIn(userId, Set.of(ACTIVE, PAST_DUE, TRIALING))` generates `WHERE user_id = ? AND status IN (?, ?, ?)`, returning the most recent qualifying subscription. This is preferred over multiple separate queries or a complex JPQL with OR conditions. Note that `Subscription` entity has `@JoinColumn(name="user_id")` but the repository uses `findByUserId` — Spring Data navigates through the `user` association to compare `user.id`.

### Q: How does `countProjectOwnedByUser` work and why is `'OWNER'` a string literal in JPQL?
**A:** The query is:
```jpql
SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.id.userId = :userId AND pm.projectRole = 'OWNER'
```
Using a string literal `'OWNER'` in JPQL when the field is an enum stored as `EnumType.STRING` is valid — Hibernate translates the string literal to the enum's string representation in the comparison. The alternative would be to pass the enum as a named parameter. This counts only `ProjectMember` rows where the user is the `OWNER`, used in `SubscriptionServiceImpl.canCreateNewProject()` to enforce project limits.

---

## 6. MapStruct

### Q: What is MapStruct and how is it configured in this project?
**A:** MapStruct is an annotation processor that generates type-safe mapping code between Java types at compile time — no reflection at runtime. It is added in `pom.xml` as `mapstruct:1.5.2.Final` and the `mapstruct-processor` is listed as an annotation processor path in the `maven-compiler-plugin`. The `lombok-mapstruct-binding:0.2.0` artifact is also needed to ensure Lombok processes its annotations (generating getters/setters) before MapStruct reads them. All mappers use `@Mapper(componentModel="spring")` which makes MapStruct generate a `@Component`-annotated implementation, so Spring can inject them via `@Autowired` or constructor injection.

### Q: Why is `lombok-mapstruct-binding` needed and what breaks without it?
**A:** MapStruct needs to read the getter and setter methods of Lombok-annotated classes. During annotation processing, order matters — Lombok must run first to generate those methods, then MapStruct reads them. Without `lombok-mapstruct-binding`, the annotation processors may run in the wrong order, causing MapStruct to see classes without getters/setters and generate incorrect mappings or fail to compile. `lombok-mapstruct-binding` is a zero-code artifact that simply controls annotation processor ordering via the compiler plugin.

### Q: Explain the `@Mapping` annotations in `SubscriptionMapper`.
**A:** `SubscriptionMapper.toSubscriptionResponse(Subscription)` maps a `Subscription` entity to a `SubscriptionResponse` record. Two `@Mapping` annotations are applied:
- `@Mapping(target="periodEnd", source="currentPeriodEnd")` — the entity field is named `currentPeriodEnd` but the DTO field is `periodEnd`. MapStruct would fail to map these by name match, so the explicit mapping is required.
- `@Mapping(target="tokensUsedThisCycle", ignore=true)` — `tokensUsedThisCycle` in `SubscriptionResponse` cannot be derived from `Subscription` alone (it would require a DB query against `UsageLog`). Marking it `ignore=true` tells MapStruct to leave it null; the calling code is responsible for populating it separately if needed.

### Q: How does `ProjectMemberMapper` handle mapping from two different source types (`User` and `ProjectMember`) for the same target DTO?
**A:** `ProjectMemberMapper` declares two separate mapping methods: `toProjectMemberResponseFromOwner(User owner)` and `toProjectMemberResponseFromMember(ProjectMember projectMember)`. The owner method maps a `User` directly (with `@Mapping(target="role", constant="OWNER")` to hard-code the role), while the member method navigates through the `ProjectMember` associations (`@Mapping(target="userId", source="user.id")`, `@Mapping(target="name", source="user.name")`, etc.). MapStruct fully supports multi-level path navigation in `source` expressions. Both return the same `MemberResponse` record.

### Q: How does `ProjectMapper.toProjectSummaryResponse(Project, ProjectRole)` work with two source parameters?
**A:** MapStruct supports mapping methods with multiple source parameters. `toProjectSummaryResponse(Project project, ProjectRole role)` takes both a `Project` entity and a `ProjectRole` enum value. MapStruct generates code that reads fields from `project` (like `id`, `name`, `isPublic`, timestamps) and maps the `role` parameter directly to the corresponding field in `ProjectSummaryResponse`. This is used in `ProjectServiceImpl.getUserProjects()` which maps each `ProjectWithRole` projection tuple by calling `projectMapper.toProjectSummaryResponse(p.getProject(), p.getRole())`.

### Q: What would happen if MapStruct could not find a mapping for a field and no `@Mapping(ignore=true)` was specified?
**A:** By default MapStruct generates a compilation warning for unmapped target properties. If the unmapped property is in the target and has no source equivalent, MapStruct will leave it at its default value (null for objects, 0 for primitives). The exact behaviour depends on the `unmappedTargetPolicy` setting — the default is `WARN`. Setting it to `ERROR` would cause the build to fail on any unmapped field, which is safer. In this project the default (warn) is used.

---

## 7. Configuration Beans

### Q: Explain `StorageConfig` — why does it use both `@Configuration` and `@ConfigurationProperties`?
**A:** `StorageConfig` is annotated with both. `@Configuration` registers it as a Spring configuration class so the `@Bean` method `minioClient()` is processed. `@ConfigurationProperties(prefix="minio")` tells Spring Boot to bind all properties under the `minio.*` prefix to the fields of this class (`url`, `accessKey`, `secretKey`). Lombok's `@Data` generates the getters/setters that Spring Boot's property binder requires. This is a clean pattern: the class itself holds the configuration values and also uses them to construct the `MinioClient` bean, so there is no need for separate `@Value` injections in a different class. The `MinioClient` is built with the endpoint URL and credentials from the bound properties.

### Q: How does `PaymentConfig` initialise the Stripe SDK and why is `@PostConstruct` used?
**A:** `PaymentConfig` uses `@Value("${stripe.api.secret}")` to inject the Stripe secret key. The `@PostConstruct` method `init()` runs after dependency injection is complete but before the bean is made available to the context. Inside it, `Stripe.apiKey = stripeSecretKey` sets the global static API key on the Stripe SDK. This is a global side effect — once set, all Stripe SDK calls in the process use this key automatically. `@PostConstruct` is used rather than a constructor because the `@Value` injection happens between construction and `@PostConstruct`.

### Q: How is the `ChatClient` bean created and what is `SimpleLoggerAdvisor`?
**A:** `AiConfig` creates the `ChatClient` bean by accepting a `ChatClient.Builder` (provided by Spring AI's auto-configuration based on the `spring.ai.openai.*` properties). `SimpleLoggerAdvisor` is a Spring AI built-in advisor that logs request and response details at DEBUG level. It is attached as a `defaultAdvisor`, meaning it runs on every request made through this `ChatClient`. The `ChatClient.Builder` pattern allows chaining default system prompts, advisors, and tools before building the immutable `ChatClient`.

### Q: What is the `spring.ai.openai.base-url` property and why is it set to OpenRouter?
**A:** Spring AI's OpenAI integration uses `spring.ai.openai.base-url` to override the API endpoint. The default is `https://api.openai.com`. Setting it to `https://openrouter.ai/api` makes the Spring AI OpenAI client route requests to OpenRouter, which is a gateway supporting many models. The model is set to `google/gemini-3-flash-preview` via `spring.ai.openai.chat.options.model`. This is a common pattern to use Spring AI (which has a first-class OpenAI integration) with alternative providers that expose an OpenAI-compatible API.

### Q: What is `WebSecurityConfig` creating with the `AuthenticationManager` bean and why is it needed explicitly?
**A:** In Spring Security 6 (used with Spring Boot 3), the `AuthenticationManager` is not automatically exposed as a bean. `AuthServiceImpl.login()` needs to authenticate a username/password directly by calling `authenticationManager.authenticate(...)`. To get the `AuthenticationManager`, `WebSecurityConfig` declares a `@Bean` that delegates to `authenticationConfiguration.getAuthenticationManager()`. This retrieves the `AuthenticationManager` that Spring Security has already built (backed by `UserDetailsService` + `PasswordEncoder`), making it available for injection into `AuthServiceImpl`.

---

## 8. Security — JWT and Spring Security

### Q: Walk through the JWT authentication flow end to end.
**A:** On signup/login, `AuthServiceImpl` calls `authUtil.generateAccessToken(user)`. `AuthUtil.generateAccessToken()` builds a JWT using JJWT: the subject is `user.getUsername()` (the email), a custom claim `userId` holds `user.getId().toString()`, issuedAt is `new Date()`, expiry is 100 minutes (`1000 * 60 * 100` ms), and it is signed with an HMAC-SHA key derived from `jwt.secret-key` using `Keys.hmacShaKeyFor(jwtSecretKey.getBytes(UTF_8))`. The token is returned in `AuthResponse.token`. On subsequent requests, the client sends the token as `Authorization: Bearer <token>`. `JwtAuthFilter` extracts it, calls `authUtil.verifyAccessToken(token)` which parses the JWT, validates the signature, and returns a `JwtUserPrincipal(userId, username, authorities)`. This principal is wrapped in a `UsernamePasswordAuthenticationToken` and set on the `SecurityContextHolder`.

### Q: What is `JwtUserPrincipal` and why is it a `record`?
**A:** `JwtUserPrincipal` is a Java record with three components: `Long userId`, `String username`, `List<GrantedAuthority> authorities`. Records are immutable value types — ideal for a security principal that should not be mutated once created from a verified JWT. Using a custom record rather than loading a `User` entity from the DB on every request is a significant performance optimisation: `JwtAuthFilter` never hits the database. The `userId` is extracted from the JWT claim, so any subsequent service call can use `authUtil.getCurrentUserId()` to get the authenticated user's ID without a DB lookup.

### Q: How does `SecurityExpressions` integrate with `@PreAuthorize`? What is `@Component("security")`?
**A:** `@Component("security")` registers the `SecurityExpressions` bean with the name `"security"` in the Spring context. In SpEL expressions like `@PreAuthorize("@security.canEditProject(#projectId)")`, `@security` refers to the bean by name. `#projectId` binds to the method parameter named `projectId` (Spring method security resolves parameter names through reflection or debug info). When `canEditProject(projectId)` is called, it retrieves the current user's ID via `authUtil.getCurrentUserId()`, queries `ProjectMemberRepository.findRoleByProjectIdAndUserId(projectId, userId)`, and checks whether the returned `ProjectRole` contains `ProjectPermission.EDIT` in its permissions set.

### Q: What is `OncePerRequestFilter` and why does `JwtAuthFilter` extend it?
**A:** `OncePerRequestFilter` guarantees that `doFilterInternal()` is called exactly once per request, even if the filter is mapped multiple times (e.g., through different servlet dispatchers). This is important because without this guarantee, a request that is forwarded internally (e.g., for error handling) could trigger the JWT parsing twice. Extending `OncePerRequestFilter` is standard practice for security filters in Spring Boot applications.

### Q: What happens when `JwtAuthFilter` encounters a request without an `Authorization` header?
**A:** The filter checks `if (requestHeaderToken == null || !requestHeaderToken.startsWith("Bearer "))` and if true, it calls `filterChain.doFilter(request, response)` and returns immediately — without setting any authentication on the `SecurityContextHolder`. The request continues down the chain. If it hits a protected endpoint, Spring Security's `AuthorizationFilter` will find no authentication and reject with 401. If it hits a public endpoint (`/api/auth/**` or `/webhooks/**`), the request proceeds normally.

### Q: Why is `AuthUtil.generateAccessToken` storing `userId` as a `String` claim rather than a `Long`?
**A:** JWT claims are JSON values. JJWT's `.claim("userId", user.getId().toString())` stores the ID as a string because JSON's numeric types can lose precision for large `Long` values in some parsers. By storing as `String` and parsing back with `Long.parseLong(claims.get("userId", String.class))`, the conversion is explicit and safe. This avoids potential precision issues when the JWT is consumed by JavaScript clients (which use IEEE 754 doubles with 53-bit integer precision).

### Q: What is the token expiry and what is missing from the JWT implementation?
**A:** The token expiry is `System.currentTimeMillis() + 1000 * 60 * 100` = 100 minutes. The JWT implementation has several gaps worth noting in an interview: (1) There is no refresh token — once the access token expires, the user must log in again. (2) There is no token revocation mechanism — a logged-out user's token remains valid until expiry. (3) There is no token blacklist. (4) The 100-minute window may be intentionally short for a developer IDE tool but is not configurable.

---

## 9. Error Handling

### Q: What is `@RestControllerAdvice` and how does it work?
**A:** `@RestControllerAdvice` is a combination of `@ControllerAdvice` (which applies to all controllers) and `@ResponseBody` (which serialises the return value to JSON). `GlobalExceptionHandler` uses this annotation so that its `@ExceptionHandler` methods apply globally across all controllers and return JSON error responses rather than HTML. Each `@ExceptionHandler` method declares the exception type it handles and returns a `ResponseEntity<ApiError>`.

### Q: Describe `ApiError`. Why is it a `record`? What does `@JsonInclude(NON_NULL)` do on the `errors` field?
**A:** `ApiError` is a Java record with four components: `HttpStatus status`, `String message`, `Instant timestamp`, `List<ApiFieldError> errors`. Records provide compact, immutable value objects — ideal for error payloads. Two compact constructor overloads are defined for convenience (one with errors, one without, both calling `Instant.now()` for timestamp). `@JsonInclude(JsonInclude.Include.NON_NULL)` on `errors` means Jackson omits the `errors` field from the JSON output when it is null — so a 404 response does not include `"errors": null`, keeping the response clean. It is only present for 400 validation errors.

### Q: Walk through what happens when `MethodArgumentNotValidException` is thrown.
**A:** This exception is thrown by Spring MVC when a `@RequestBody` parameter annotated with `@Valid` fails Bean Validation. `GlobalExceptionHandler.handleInputValidationError` catches it, iterates `ex.getBindingResult().getFieldErrors()`, and maps each to an `ApiFieldError(field, defaultMessage)` record. The field is the property name (e.g., `username`) and the message is the constraint annotation message (e.g., "must be a well-formed email address"). The resulting `ApiError` has status `400 BAD_REQUEST`, message `"Input Validation Failed"`, and a non-null `errors` list. Note: `SignupRequest` and `LoginRequest` do not use `@Valid` in the `AuthController` — there is no `@Valid` annotation on the `@RequestBody` parameters in `AuthController`, so validation is not triggered there. `ProjectController` and `ProjectMemberController` do use `@Valid`.

### Q: How are exceptions from the security filter layer (e.g., `JwtException`) handled by `GlobalExceptionHandler`?
**A:** Normally, exceptions thrown inside a servlet filter cannot be caught by `@RestControllerAdvice` because they occur outside the Spring MVC dispatcher. This project handles it by injecting `HandlerExceptionResolver` into `JwtAuthFilter`. When any exception is caught, `handlerExceptionResolver.resolveException(request, response, null, e)` is called, which routes the exception to `GlobalExceptionHandler` through the MVC exception resolution mechanism. The `GlobalExceptionHandler` has a `@ExceptionHandler(JwtException.class)` that returns a 401 response. A similar pattern is used in `WebSecurityConfig` for `AccessDeniedException` in the custom `.accessDeniedHandler`.

### Q: What is `ResourceNotFoundException` and why does it have both `resourceName` and `resourceId` fields?
**A:** `ResourceNotFoundException extends RuntimeException` and carries `String resourceName` (e.g., `"Project"`) and `String resourceId` (e.g., `"42"`). The `GlobalExceptionHandler` constructs the message as `resourceName + " with id " + resourceId + " not found"`, producing clear messages like `"Project with id 42 not found"`. The constructor also calls `super(resourceName + " with id " + resourceId + " not found")` which sets the standard `RuntimeException` message for logging. Using two fields instead of a single pre-formatted message makes the fields independently accessible (e.g., for i18n).

### Q: `ApiFieldError` is declared without `public` visibility — what accessibility does it have?
**A:** `ApiFieldError` is declared as a package-private record in `ApiError.java` (same file). Without an access modifier, Java defaults to package-private, meaning it is accessible within the `com.morphcode.ai.error` package but not from outside it. This is intentional — `ApiFieldError` is only an implementation detail of `ApiError` and does not need to be a public API. It is used in `GlobalExceptionHandler` (same package) and serialised to JSON by Jackson through `ApiError.errors`.

---

## 10. DTOs and Bean Validation

### Q: Why are all DTOs defined as Java `record`s?
**A:** Java records (introduced in Java 16, available since Spring Boot 3's Java 17 baseline) are immutable, compact data carriers. They auto-generate `equals()`, `hashCode()`, `toString()`, and a canonical constructor. For DTOs, immutability is desirable — a request body should not be mutated after parsing. Records eliminate boilerplate compared to a regular class with `@Getter`, `@Setter`, `@NoArgsConstructor`, and `@AllArgsConstructor`. Bean Validation constraints (`@NotBlank`, `@Email`, `@Size`) are placed directly on the record components.

### Q: Explain the constraints on `SignupRequest`.
**A:** `SignupRequest` has three components: `@Email @NotBlank String username` — must be non-blank and a valid email format; `@Size(min=1, max=30) String name` — between 1 and 30 characters; `@Size(min=4) String password` — minimum 4 characters. `@Email` uses Hibernate Validator's email validation which checks for the presence of `@` and a domain part. `@NotBlank` checks that the string is not null and not only whitespace. Note: `@NotBlank` makes `@NotNull` redundant (it implies non-null).

### Q: Why does `AuthController.signup` NOT have `@Valid` on its `@RequestBody` but `ProjectController.createProject` does?
**A:** This is a bug in `AuthController`. Without `@Valid`, Spring MVC does not trigger Bean Validation on `SignupRequest` or `LoginRequest`. A user can sign up with a blank username or a too-short password and no validation error is returned. `ProjectController` correctly uses `@RequestBody @Valid ProjectRequest request`, which triggers `MethodArgumentNotValidException` on constraint violations. The fix is to add `@Valid` to `AuthController.signup` and `AuthController.login` parameters.

### Q: What is the `@NotNull` vs `@NotBlank` distinction? Where is `@NotNull` used?
**A:** `@NotNull` only checks that the value is not null — an empty string `""` passes `@NotNull`. `@NotBlank` checks non-null AND non-empty AND not all whitespace. `@NotNull` is used on `InviteMemberRequest.role` (a `ProjectRole` enum) — since enums cannot be blank (they are not strings), `@NotNull` is the correct constraint here. Using `@NotBlank` on an enum would be meaningless.

### Q: Describe `FileNode` and `FileTreeResponse`. What design choice was made?
**A:** `FileTreeResponse` is a record wrapping `List<FileNode>`. `FileNode` is a DTO representing a file metadata entry mapped from the `ProjectFile` entity. `ProjectFileMapper.toListOfFileNode(List<ProjectFile>)` handles the conversion. This wrapping in `FileTreeResponse` rather than returning a raw `List<FileNode>` from the controller is good practice — it allows the API response shape to be extended later (e.g., adding total count, pagination) without breaking clients.

---

## 11. Service Layer and Transactions

### Q: What is the inconsistency in `@Transactional` imports across the codebase?
**A:** `ProjectServiceImpl` uses `org.springframework.transaction.annotation.Transactional` while `ProjectMemberServiceImpl` and `SubscriptionServiceImpl.updateSubscription` use `jakarta.transaction.Transactional`. Both work correctly — Spring detects both annotations and applies its transaction management. However, they have subtle differences: Spring's `@Transactional` supports additional attributes like `propagation`, `isolation`, `timeout`, `readOnly`, and `rollbackFor` that the Jakarta annotation does not. Using both in the same codebase is inconsistent and can confuse developers. The convention should be to use Spring's annotation throughout.

### Q: `ProjectServiceImpl` is annotated `@Transactional` at the class level. What does this mean for all its methods?
**A:** Class-level `@Transactional` applies the default transaction semantics to every public method in the class: propagation `REQUIRED` (join an existing transaction or create a new one), isolation `DEFAULT` (database default), rollback on any unchecked exception. This means that `createProject()`, which calls `projectRepository.save(project)`, `projectMemberRepository.save(projectMember)`, and `projectTemplateService.initializeProjectFromTemplate(project.getId())`, all execute within a single transaction. If any of those calls throws an exception, the entire operation rolls back — the project row, the member row, and the MinIO operations (though MinIO is not transactional) are all handled atomically at the DB level.

### Q: `ProjectServiceImpl.createProject()` uses `userRepository.getReferenceById(userId)` instead of `findById`. Explain the trade-off.
**A:** `getReferenceById(userId)` returns a Hibernate proxy without issuing a SELECT. Since the only use of `owner` here is to set it as a foreign key on `ProjectMember`, only the ID is needed — Hibernate extracts the ID from the proxy for the INSERT. This avoids one unnecessary SELECT per project creation. The risk: if `userId` does not exist in the database, no exception is thrown at the `getReferenceById` call — the exception would only surface when Hibernate attempts to flush the INSERT for `ProjectMember` and the foreign key constraint fails.

### Q: Why does `finalizeChats()` in `AiGenerationServiceImpl` run on `Schedulers.boundedElastic()`, and what risk does this introduce?
**A:** `streamResponse()` returns a `Flux` (reactive stream). The `.doOnComplete()` callback fires when the LLM finishes streaming. Performing blocking database operations (via Spring Data JPA) directly inside a reactive callback is illegal — it would block a non-blocking scheduler thread. `Schedulers.boundedElastic()` is Project Reactor's thread pool for blocking work; scheduling the DB save there moves it off the reactive scheduler. The risk: `Schedulers.boundedElastic()` has a bounded queue. Under heavy load, the queue can fill up, causing `finalizeChats()` to be silently delayed or dropped, meaning chat messages and file writes to MinIO are never persisted. This is documented as a known issue.

### Q: What is `canCreateNewProject()` doing in `SubscriptionServiceImpl` and how is the free tier enforced?
**A:** `canCreateNewProject()` gets the current user's subscription, counts their owned projects using `projectMemberRepository.countProjectOwnedByUser(userId)`, and compares against the plan's `maxProjects`. If no subscription exists (empty `Subscription` object returned), `currentSubscription.plan()` is null, so the free tier limit of `FREE_TIER_PROJECTS_ALLOWED = 100` is applied. If a paid plan exists, `currentSubscription.plan().maxProjects()` is used. This is called in `createProject()` and throws `BadRequestException` if the limit is reached.

### Q: `ProjectMemberServiceImpl.inviteMember()` calls `orElseThrow()` without an argument. What exception is thrown?
**A:** `userRepository.findByUsername(request.username()).orElseThrow()` calls the no-argument form of `orElseThrow()` which throws `NoSuchElementException` (a runtime exception from `java.util`). This is not caught by `GlobalExceptionHandler` (which handles `ResourceNotFoundException`, `BadRequestException`, `UsernameNotFoundException`, etc.), so it would propagate as a 500 Internal Server Error. The correct implementation would be `orElseThrow(() -> new ResourceNotFoundException("User", request.username()))`. Similarly, `projectMemberRepository.findById(projectMemberId).orElseThrow()` in `updateMemberRole` has the same issue.

---

## 12. AI Integration and Streaming

### Q: What is Spring AI and how is it configured in this project?
**A:** Spring AI is a Spring project providing a portable abstraction over LLM providers. It is configured via `spring.ai.openai.*` properties (because the OpenAI provider is used) — but the `base-url` is overridden to `https://openrouter.ai/api` to route to OpenRouter. The model is `google/gemini-3-flash-preview` and temperature is `0.0` (deterministic, no randomness). The dependency `spring-ai-starter-model-openai` pulls in the OpenAI chat model implementation. Spring AI's BOM (`spring-ai-bom:1.0.0`) manages version alignment for all Spring AI artifacts.

### Q: Explain the streaming flow in `AiGenerationServiceImpl.streamResponse()`.
**A:** The method returns `Flux<StreamResponse>`. It builds a `chatClient.prompt()` with a system prompt from `PromptUtils.CODE_GENERATION_SYSTEM_PROMPT`, the user message, `CodeGenerationTools` as a tool, and `FileTreeContextAdvisor` as a streaming advisor. `.stream().chatResponse()` returns a `Flux<ChatResponse>` from Spring AI. `.doOnNext()` accumulates each chunk into `fullResponseBuffer` and captures token usage metadata. `.doOnComplete()` schedules `finalizeChats()` on `boundedElastic`. `.map()` converts each `ChatResponse` to a `StreamResponse(text)`. The controller wraps these in `ServerSentEvent` objects and returns them with `MediaType.TEXT_EVENT_STREAM_VALUE`.

### Q: What is a Spring AI `StreamAdvisor` and how does `FileTreeContextAdvisor` work?
**A:** A `StreamAdvisor` intercepts the `Flux`-based chat call chain. `FileTreeContextAdvisor implements StreamAdvisor` and overrides `adviseStream(ChatClientRequest, StreamAdvisorChain)`. It reads `projectId` from the advisor context (`Map<String, Object>` passed via `advisorSpec.params(advisorParams)`), fetches the file tree from `ProjectFileService.getFileTree(projectId)`, converts it to a string, and injects it as an additional `SystemMessage` into the prompt before forwarding the augmented request to the next advisor in the chain. This is the RAG (Retrieval Augmented Generation) pattern — context injection at prompt time. The known issue is that it queries the DB on every LLM call with no caching.

### Q: Why is `CodeGenerationTools` NOT a `@Component`? What is its lifecycle?
**A:** `CodeGenerationTools` needs `projectId` as a constructor argument, and `projectId` is only known at request time — it comes from the HTTP request body. Spring beans are typically singletons and cannot take request-time constructor arguments. Therefore `CodeGenerationTools` is instantiated manually: `new CodeGenerationTools(projectFileService, projectId)` inside `streamResponse()` for each request. This is a per-request object. Spring AI's `ChatClient.prompt().tools(codeGenerationTools)` registers the instance as a tool for that specific call. The `@Tool` annotation on `readFiles` uses Spring AI's reflection-based tool registration.

### Q: What is the XML protocol between the LLM and MorphCode? How does `LlmResponseParser` work?
**A:** The system prompt instructs the LLM to wrap all output in XML tags: `<message>` for conversational text, `<file path="...">` for file content, `<tool args="...">` for tool call annotations. After the stream completes, `LlmResponseParser.parseChatEvents(fullText, parentMessage)` applies a regex `GENERIC_TAG_PATTERN` to the full buffered response. The pattern matches opening tag + tag name (`message|file|tool`) + attributes + content + closing tag. For each match, it creates a `ChatEvent` with the appropriate `ChatEventType` (MESSAGE, FILE_EDIT, TOOL_LOG). For `file` tags, `filePath` is extracted from the `path="..."` attribute. For `tool` tags, the `args="..."` value is stored in `metadata`.

### Q: How are AI-generated file edits persisted? Trace the full path.
**A:** In `finalizeChats()`, after all `ChatEvent` records are built by `LlmResponseParser`, the method iterates over events filtered by `ChatEventType.FILE_EDIT` and calls `projectFileService.saveFile(projectId, e.getFilePath(), e.getContent())` for each. `ProjectFileServiceImpl.saveFile()` writes the file to MinIO under the `projects` bucket with key `{projectId}/{path}`, upserts a `ProjectFile` row in PostgreSQL (with path and MinIO object key metadata), and logs success. Then `chatEventRepository.saveAll(chatEventList)` persists all events.

---

## 13. MinIO File Storage

### Q: What is MinIO and why is it used instead of a local filesystem?
**A:** MinIO is an S3-compatible object storage server. It is used so that file storage is decoupled from the application server — the backend can be scaled horizontally (multiple instances) without files being tied to a single machine's local disk. MinIO is run as a Docker container locally (`:9000`). In production, this could be swapped for AWS S3 with minimal code changes since the MinIO SDK is S3-compatible. Files are organised with the key pattern `{projectId}/{relativePath}`.

### Q: What is the bucket naming inconsistency bug in `ProjectFileServiceImpl`?
**A:** `ProjectFileServiceImpl` has two bucket references: a hardcoded `private static final String BUCKET_NAME = "projects"` used in `getFileContent()` via `GetObjectArgs`, and `@Value("${minio.project-bucket}") private String projectBucket` used in `saveFile()` via `PutObjectArgs`. Both `application.properties` and the hardcoded constant resolve to `"projects"` today. But if someone changes `minio.project-bucket=projects-v2` in properties, `saveFile()` writes to `projects-v2` while `getFileContent()` still reads from `projects`. Files would be written but never readable. The fix is to use the `@Value` field everywhere.

### Q: How does `ProjectTemplateServiceImpl` initialise a new project?
**A:** When a project is created, `projectTemplateService.initializeProjectFromTemplate(projectId)` is called inside the same `@Transactional` method in `ProjectServiceImpl`. It uses `MinioClient.listObjects()` with `bucket=starter-projects`, `prefix=react-vite-tailwind-daisyui-starter/`, `recursive=true` to enumerate all template files. For each file, it calls `MinioClient.copyObject()` to copy the object from the template bucket to the project bucket with key `{projectId}/{cleanPath}`. It also creates `ProjectFile` records in batch with `projectFileRepository.saveAll()`. However, MinIO operations are not transactional — if the DB transaction rolls back, the MinIO files remain. This is a consistency gap.

### Q: How does `determineContentType` work for TypeScript files?
**A:** `URLConnection.guessContentTypeFromName(path)` uses Java's built-in MIME type detection. It does not recognise `.tsx` or `.ts` files since these are not in the standard MIME database. The fallback checks `path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")` and returns `"text/javascript"`. This is a sensible approximation — TypeScript files are transpiled to JavaScript, so `text/javascript` is a reasonable content type for storage purposes.

---

## 14. Billing and Stripe Integration

### Q: How does Stripe webhook verification work in `BillingController`?
**A:** `POST /webhooks/payment` receives the raw request body as `String payload` and the `Stripe-Signature` header. `Webhook.constructEvent(payload, sigHeader, webhookSecret)` verifies the signature using HMAC-SHA256 with the `stripe.webhook.secret` property — if tampered or replayed, Stripe throws `SignatureVerificationException`. This is critical for security: without signature verification, anyone could POST fake events. The raw `String payload` (not `@RequestBody` deserialized JSON) is required because the signature is computed over the exact bytes of the HTTP body.

### Q: What events does `StripePaymentProcessor` handle and what does each do?
**A:** Five events are handled via a `switch` statement:
- `checkout.session.completed` — user completed checkout; saves `stripeCustomerId` to `User`, calls `subscriptionService.activateSubscription()` to create the `Subscription` record.
- `customer.subscription.updated` — subscription changed (cancellation scheduled, plan upgrade); calls `subscriptionService.updateSubscription()` with new status, periods, and plan.
- `customer.subscription.deleted` — subscription fully cancelled; calls `subscriptionService.cancelSubscription()`.
- `invoice.paid` — successful invoice payment; calls `subscriptionService.renewSubscriptionPeriod()` with new billing period dates fetched from Stripe.
- `invoice.payment_failed` — payment failed; calls `subscriptionService.markSubscriptionPastDue()`.

### Q: What is the idempotency guard in `activateSubscription()`?
**A:** `subscriptionRepository.existsByStripeSubscriptionId(subscriptionId)` checks if a `Subscription` with that Stripe ID already exists. If yes, the method returns immediately. This prevents duplicate subscriptions if Stripe sends `checkout.session.completed` more than once (which can happen with webhook retries). Idempotency in webhook handlers is essential.

### Q: Why is `StripePaymentProcessor` the implementation of `PaymentProcessor` interface?
**A:** The `PaymentProcessor` interface decouples the billing abstraction from the Stripe implementation. If the payment provider were switched (e.g., to Paddle or LemonSqueezy), a new implementation of `PaymentProcessor` could be provided without changing controllers, service calls, or business logic. `BillingController` depends on `PaymentProcessor`, not `StripePaymentProcessor`, following the Dependency Inversion Principle.

---

## 15. Enums and RBAC

### Q: How is role-based access control (RBAC) modelled using enums?
**A:** `ProjectRole` is an enum with three values: `OWNER`, `EDITOR`, `VIEWER`. Each value carries a `Set<ProjectPermission>` via a final field in the constructor:
- `OWNER` has: VIEW, EDIT, DELETE, VIEW_MEMBERS, MANAGE_MEMBERS
- `EDITOR` has: VIEW, EDIT, VIEW_MEMBERS
- `VIEWER` has: VIEW, VIEW_MEMBERS

`ProjectPermission` is another enum with string values (`"project:view"`, `"project:edit"`, etc.). `SecurityExpressions.hasPermission(projectId, permission)` fetches the role from DB and calls `role.getPermissions().contains(permission)`. This is an enum-based permissions model — roles statically define their permission sets, which is simple and efficient but inflexible (changing permissions requires a code change).

### Q: Why use `Set<ProjectPermission>` inside the enum constant rather than a database permissions table?
**A:** A database-driven permissions model (Role → Permission join table) is more flexible but adds complexity: extra DB queries on every permission check, additional entities, and the risk of inconsistent states. For an application with a small, well-defined permission set that is unlikely to change dynamically, encoding permissions directly in the enum is simpler, faster (no DB query for permissions, only for role), and less error-prone. The trade-off is that changing the `EDITOR` permission set requires a code change and redeployment.

### Q: What is `ChatEventType` and what are its four values?
**A:** `ChatEventType` is a simple enum (no fields) with values:
- `THOUGHT` — a "Thought for Ns" marker event always prepended at sequence 0
- `MESSAGE` — the conversational response text from the LLM (from `<message>` tags)
- `FILE_EDIT` — a file modification event (from `<file path="...">` tags)
- `TOOL_LOG` — a tool call annotation (from `<tool args="...">` tags)

These are stored as strings in `chat_events.type` via `@Enumerated(EnumType.STRING)` and represent the structured event stream of an LLM response.

---

## 16. Project Structure and Design Decisions

### Q: Why is the service layer split into interface + implementation?
**A:** Every service has a separate interface (e.g., `ProjectService`) and an implementation class in `service/impl/` (e.g., `ProjectServiceImpl`). This is the standard Spring pattern enabling: (1) dependency on abstractions rather than concretions for testability (mock the interface in tests); (2) multiple implementations if needed (e.g., `StripePaymentProcessor implements PaymentProcessor`); (3) Spring AOP proxying for `@Transactional`, `@PreAuthorize`, and other aspects — Spring creates a JDK dynamic proxy around the interface, so the interface is required for these mechanisms to work cleanly.

### Q: Why is the LLM layer isolated in the `llm/` package?
**A:** The `llm/` package (`LlmResponseParser`, `PromptUtils`, `advisors/`, `tools/`) encapsulates all AI-specific concerns. `LlmResponseParser` handles XML parsing of AI responses; `PromptUtils` holds the system prompt as a constant; `FileTreeContextAdvisor` implements RAG; `CodeGenerationTools` wraps tool functions the LLM can call. Keeping these separate from `service/` means the LLM integration can be changed (e.g., switching parsers, prompt formats, or AI frameworks) without touching core business services.

### Q: Why does `PromptUtils.CODE_GENERATION_SYSTEM_PROMPT` embed `LocalDateTime.now()` at class load time?
**A:** The system prompt is a `static final String` initialised with `LocalDateTime.now()`. This means the time is baked in at the first time the class is loaded by the JVM (at startup), not at each request. The LLM always sees the startup time, not the current time. This is a subtle bug — the prompt says "Time now:" but the value is stale after the first request. The fix would be to compute the timestamp at call time, either by making it a method rather than a constant, or by using a placeholder replaced in `streamResponse()`.

### Q: What is the pattern used for `ProjectRepository.ProjectWithRole`?
**A:** `ProjectWithRole` is a nested interface inside `ProjectRepository` — a Spring Data JPA interface projection. JPQL queries that return multiple columns (projections of different entity types) use this pattern. The JPQL `SELECT p as project, pm.projectRole as role` returns a tuple; Spring Data maps the tuple to the interface based on alias names matching getter method names. This avoids creating a separate DTO class for what is essentially a query-specific result type, keeping it close to the repository where it is used.

---

## 17. Known Bugs and Limitations

### Q: Explain the hardcoded `userId = 1L` bug in `AuthController.getProfile()`.
**A:** `AuthController.getProfile()` has `Long userId = 1L;` hardcoded instead of calling `authUtil.getCurrentUserId()`. This means `GET /api/auth/me` always returns the profile of the user with ID 1, regardless of which user's JWT token is in the request. Every authenticated user sees user 1's profile. The fix is trivial: replace with `Long userId = authUtil.getCurrentUserId();` and resolve the user via `userService.getProfile(userId)`. Note also that `UserServiceImpl.getProfile()` returns `null` — it is unimplemented.

### Q: Explain the `GET /api/plans` returning an empty list bug.
**A:** `PlanServiceImpl.getAllActivePlans()` has `return List.of()` hardcoded. The `PlanRepository` bean is available in the context but is never injected into `PlanServiceImpl`. The correct implementation would be to inject `PlanRepository`, call `planRepository.findAll()` (or `findByActiveTrue()` to filter active plans), and map results through `SubscriptionMapper.toPlanResponse()`. This means the billing UI that calls `/api/plans` can never show actual plans.

### Q: What is the consequence of `Preview` entity missing `@Entity`?
**A:** Without `@Entity`, Hibernate does not scan `Preview` as a managed entity. No `previews` table is created, no repository can be defined for it (`JpaRepository<Preview, Long>` would fail to compile since `Preview` is not a JPA entity), and the `PreviewStatus` enum is unused. The `Preview` class is a plain Java class with no ORM integration. This means the "live preview" feature is entirely unimplemented at the persistence layer.

### Q: Why is `usageService.checkDailyTokensUsage()` commented out in `streamResponse()`?
**A:** The method `checkDailyTokensUsage()` throws `ResponseStatusException(TOO_MANY_REQUESTS)` when daily token limits are exceeded. It calls `subscriptionService.getCurrentSubscription()` which calls `subscriptionRepository.findByUserIdAndStatusIn()`. If a user has no subscription, `getCurrentSubscription()` returns a default empty `Subscription` object and the mapping via `SubscriptionMapper` produces a `SubscriptionResponse` with a null `plan`. Then `checkDailyTokensUsage()` calls `plan.unlimitedAi()` on a null `plan` — NPE. The gate is commented out because it crashes rather than gracefully handling the no-plan case.

### Q: The `ASSISTANT` ChatMessage has content hardcoded to `"Assistant Message here..."` — what is the actual content and where is it?
**A:** The actual LLM response text is not stored in `ChatMessage.content` for assistant messages. Instead, it is broken down into structured `ChatEvent` records (MESSAGE events hold conversational text, FILE_EDIT events hold file content). The hardcoded `"Assistant Message here..."` is a placeholder. The `ChatResponse` DTO includes both `content` and `List<ChatEventResponse> events` — the events are the real payload for assistant messages. This means any client that reads `ChatMessage.content` for assistant messages gets garbage.

### Q: `UsageController.getTodayUsage()` returns `null` — what is wrong?
**A:** The method has `return null` where it should return `ResponseEntity.ok(...)`. A `null` return from a controller method causes Spring MVC to throw a `NullPointerException` or write nothing, resulting in a 500 or empty response. `usageService.getTodayUsageOfUser(userId)` is also commented out — the method does not exist on `UsageService`. This endpoint is entirely unimplemented.

---

## 18. General Java Questions

### Q: What Lombok annotations are used most heavily and what does each do?
**A:** 
- `@Getter`/`@Setter` — generates getter/setter methods for all fields
- `@NoArgsConstructor`/`@AllArgsConstructor` — generates constructors
- `@Builder` — generates a builder pattern (critical for JPA entities since `@AllArgsConstructor` alone is not flexible enough)
- `@RequiredArgsConstructor` — generates a constructor for all `final` fields; combined with `@FieldDefaults(makeFinal=true, level=PRIVATE)` in service classes, this is the Lombok idiom for constructor injection without writing explicit constructors
- `@FieldDefaults(level=AccessLevel.PRIVATE)` — makes all fields private without needing the `private` keyword on each
- `@Slf4j` — injects a `private static final Logger log = LoggerFactory.getLogger(...)` field
- `@Data` — shorthand for `@Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor` (used only on `StorageConfig`)

### Q: Why is `@Builder` used on JPA entities alongside `@NoArgsConstructor` and `@AllArgsConstructor`?
**A:** JPA requires a no-argument constructor (for Hibernate to instantiate entities via reflection during queries). `@AllArgsConstructor` is needed by Lombok's `@Builder` implementation — it generates `build()` by calling the all-args constructor. Without `@NoArgsConstructor` explicitly, Lombok's `@Builder` would generate only the all-args constructor and JPA would fail at startup. So all three annotations must coexist on JPA entities.

### Q: What is `@FieldDefaults(makeFinal=true, level=AccessLevel.PRIVATE)` used for in service classes and what is the benefit?
**A:** Applied on `ProjectServiceImpl`, `AuthServiceImpl`, and others, this annotation makes all declared fields `private final` implicitly. Combined with `@RequiredArgsConstructor`, Spring Boot's constructor injection works without a single explicit `@Autowired` or constructor definition. The benefit is that all dependencies are immutable after construction (a best practice for Spring beans), the code is concise, and there is no risk of field injection's hidden dependency problem. The implicit `private final` also prevents accidental reassignment in methods.

### Q: What is a Java record and what are its limitations compared to a regular class?
**A:** Records (Java 16+) are transparent, immutable data classes. The compiler generates `equals()`, `hashCode()`, `toString()`, and a canonical constructor automatically. Limitations relevant to this codebase: records cannot extend other classes (only implement interfaces); they cannot have non-final instance fields (all fields are final); they require a canonical constructor. For DTOs, these limitations are features. For JPA entities, records are not suitable because Hibernate requires mutable state and a no-arg constructor.

### Q: Why is `AtomicReference<Long>` used for `startTime`, `endTime`, and `usageRef` in `streamResponse()`?
**A:** The `Flux` callbacks (`.doOnNext()`, `.doOnComplete()`) execute on different threads from the thread that created `streamResponse()`. Java's lambda capture rules require that captured local variables be effectively final. `AtomicReference` provides a thread-safe mutable container that can be captured as a final reference but whose value can be changed across threads. `AtomicReference<Long> endTime = new AtomicReference<>(0L)` is captured by the lambda, and `endTime.set(System.currentTimeMillis())` updates it from a different thread safely.

### Q: How does `Pattern.compile` with `Pattern.DOTALL` affect the LLM response parser?
**A:** Without `Pattern.DOTALL`, the `.` metacharacter in a Java regex does not match newline characters. The LLM-generated file contents inside `<file path="...">` tags span multiple lines. Without `Pattern.DOTALL`, the content group `([\\s\\S]*?)` (which explicitly uses character class `[\s\S]` to match any character including newlines) would still work — but the outer tag pattern would fail to match multi-line content with `.*`. The parser uses `[\\s\\S]*?` (any character including newlines, non-greedy) which is correct regardless of `DOTALL`. `DOTALL` is specified for safety. `CASE_INSENSITIVE` handles LLM output that might capitalise tag names.

### Q: What is the difference between `Instant` and `LocalDate` used in this codebase?
**A:** `Instant` represents a point in time on the UTC timeline, with nanosecond precision — used for entity timestamps (`createdAt`, `updatedAt`, `deletedAt`), subscription periods, and JWT expiry. It is timezone-agnostic. `LocalDate` is a date-only type without timezone or time-of-day information — used in `UsageLog.date` because daily token tracking only needs the date, not the precise time. Using `LocalDate` for daily logs avoids timezone issues: `LocalDate.now()` gives the server's local date. If the server runs in UTC, this is consistent.

### Q: The `ProjectRole` enum uses `Lombok @RequiredArgsConstructor` and `@Getter`. Explain how this interacts with the enum.
**A:** Java enums support constructors and fields. `@RequiredArgsConstructor` on an enum generates a constructor for all `final` fields — in this case, `private final Set<ProjectPermission> permissions`. Each enum constant `OWNER(Set.of(...))`, `EDITOR(Set.of(...))`, `VIEWER(Set.of(...))` calls this constructor. `@Getter` generates `getPermissions()`. This allows `projectRole.getPermissions().contains(permission)` to work without boilerplate. The `Set.of(...)` creates an immutable set, which is appropriate since permissions for a given role should never be mutated at runtime.

### Q: What is `Schedulers.boundedElastic()` in Project Reactor?
**A:** `Schedulers.boundedElastic()` is a Project Reactor thread pool scheduler designed for blocking I/O operations. It creates threads on demand (up to `10 * CPU cores`), keeps them alive for 60 seconds, and has a bounded task queue (default 100,000 tasks). It is contrasted with `Schedulers.parallel()` (for CPU-bound, non-blocking) and `Schedulers.single()` (single-threaded). In `AiGenerationServiceImpl`, database operations in `finalizeChats()` are blocking JPA calls that would block a Reactor event loop thread — they must be offloaded to `boundedElastic`. The call `Schedulers.boundedElastic().schedule(() -> finalizeChats(...))` is a fire-and-forget — the calling Flux does not wait for it.

### Q: How does `UserServiceImpl` implement both `UserService` and `UserDetailsService`?
**A:** `UserDetailsService` is a Spring Security interface with a single method: `loadUserByUsername(String username)`. Spring Security's `DaoAuthenticationProvider` calls this method during `authenticationManager.authenticate(...)` in `AuthServiceImpl.login()`. `UserServiceImpl.loadUserByUsername()` calls `userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException(...))`. The `User` entity implements `UserDetails`, so it is returned directly. This is the standard Spring Security pattern. `UserService` is the application-specific service interface. Implementing both in one class avoids creating a separate `UserDetailsService` bean.

### Q: What Java version features are actively used beyond Java 8 in this codebase?
**A:** The codebase targets Java 17 and uses:
- **Records** (Java 16): All DTOs and `JwtUserPrincipal`, `ApiError`, `ApiFieldError`
- **Sealed text blocks** (Java 15, stable 17): JPQL queries use `""" ... """` text blocks in repositories and services
- **Pattern matching for instanceof** (Java 16): `if (stripeObject instanceof Session session)` in `BillingController`
- **Switch expressions** (Java 14): `switch (type) { case "..." -> ... }` in `StripePaymentProcessor` and `LlmResponseParser`
- **`List.of()`, `Set.of()`, `Map.of()`** (Java 9): Used throughout for immutable collections
- **`var`** (Java 10): Used in some service methods (e.g., `var projectsWithRoles = ...`)

---

*This document covers the complete MorphCode backend codebase including all known bugs, design patterns, and architectural decisions. Each answer references specific class names, annotation details, and line-level behaviour observed in the source code.*
