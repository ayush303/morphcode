# Known Issues

| # | Summary | Sev | File:line |
|---|---|---|---|
| 1 | `GET /api/auth/me` always returns user 1 | High | `AuthController.java:42` |
| 2 | `GET /api/usage/today` returns null (NPE) | Med | `UsageController.java:21` |
| 3 | `GET /api/plans` returns `[]` | Med | `PlanServiceImpl.java:13` |
| 4 | Daily token gate disabled | Med | `AiGenerationServiceImpl.java:65` |
| 5 | `tokensUsedThisCycle` always null | Low | `SubscriptionMapper.java:14` |
| 6 | `avatarUrl` always null | Low | `UserMapper`/`UserServiceImpl` |
| 7 | Preview feature unimplemented | Info | `Preview.java` |
| 8 | `ChatSessionId` missing `@Embeddable` | High | `ChatSessionId.java` |
| 9 | ASSISTANT `ChatMessage.content` is placeholder | Med | `AiGenerationServiceImpl.java:140` |
| 10 | `ProjectFileServiceImpl` dual bucket name | Med | `ProjectFileServiceImpl.java:41,58,85` |

---

**#1** `AuthController.java:42`: `Long userId = 1L` — fix: `authUtil.getCurrentUserId()`. Inject `AuthUtil`.

**#2** `UsageController.java:21`: service call commented out, returns `null` ResponseEntity → NPE. Fix: uncomment + use `authUtil.getCurrentUserId()`.

**#3** `PlanServiceImpl.java:13`: `return List.of()` — inject `PlanRepository`+`PlanMapper`, return `planRepository.findByActiveTrue()` mapped.

**#4** `AiGenerationServiceImpl.java:65`: `// usageService.checkDailyTokensUsage()` — limits defined in `Plan.maxTokensPerDay` never enforced. `UsageLog` writes still work.

**#5** `SubscriptionMapper.java:14`: `@Mapping(target="tokensUsedThisCycle", ignore=true)` — remove ignore; populate from `UsageService.getTodayUsage()`.

**#6** No avatar storage; `User` entity has no avatar field. Fix: remove field from DTO, or add MinIO avatar bucket + mapper population.

**#7** `Preview.java`: no `@Entity`, `@Table`, repository. `PreviewStatus` enum unused. Fix: add `@Entity`, create `PreviewRepository`, implement service+controller.

**#8** `ChatSessionId.java`: missing `@Embeddable` — Hibernate behavior with non-`@Embeddable` class as `@EmbeddedId` is undefined; may fail at schema gen or query time. Fix: add `@Embeddable` + `@Getter`/`@Setter` (match `ProjectMemberId`).

**#9** `AiGenerationServiceImpl.java:140`: `ChatMessage.builder().content("Assistant Message here...")` — placeholder. Actual LLM output in `ChatEvent` records. Fix: derive content from MESSAGE-type events, or enforce that clients read `.events[]`.

**#10** `ProjectFileServiceImpl.java`: `getFileContent()` uses `BUCKET_NAME = "projects"` (hardcoded); `saveFile()` uses `@Value("${minio.project-bucket}")`. Silently diverges if property changes. Fix: remove constant; use `projectBucket` in both methods.
