# Security

## JWT

- Library: JJWT 0.12.6 | Algorithm: HMAC-SHA (`jwt.secret-key` property) | Expiry: 100 min
- Claims: `sub`=username (email), `userId`=Long (stored as String claim)
- Generation: `AuthUtil.generateAccessToken(User)` | Verification: `AuthUtil.verifyAccessToken(String) → JwtUserPrincipal`

`JwtUserPrincipal`: `record(Long userId, String username, List<GrantedAuthority> authorities)`
Retrieved via `AuthUtil.getCurrentUserId()` from `SecurityContextHolder`.

## JwtAuthFilter (`OncePerRequestFilter`)

```
No/invalid "Bearer " header → pass through unauthenticated (or exception → HandlerExceptionResolver)
Valid JWT → verifyAccessToken() → JwtUserPrincipal → SecurityContextHolder → pass through authenticated
```

File: `security/JwtAuthFilter.java`

## WebSecurityConfig Rules

| Matcher | Rule |
|---|---|
| `DispatcherType.ASYNC` | `permitAll()` — required for SSE |
| `DispatcherType.ERROR` | `permitAll()` |
| `/api/auth/**` | `permitAll()` |
| `/webhooks/**` | `permitAll()` |
| Any other | `authenticated()` |

CSRF: disabled | Session: STATELESS | CORS: `Customizer.withDefaults()` (→ `CorsConfig`) | `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`

## Method Security (`@EnableMethodSecurity`)

`SecurityExpressions` (`@Component("security")`) — implementation: `ProjectMemberRepository.findRoleByProjectIdAndUserId()` → `ProjectRole.getPermissions().contains(permission)`

## Permission Matrix

| Permission | OWNER | EDITOR | VIEWER |
|---|---|---|---|
| VIEW | Y | Y | Y |
| EDIT | Y | Y | N |
| DELETE | Y | N | N |
| VIEW_MEMBERS | Y | Y | Y |
| MANAGE_MEMBERS | Y | N | N |

SpEL methods: `@security.can{View,Edit,Delete}Project(#id)`, `@security.can{View,Manage}Members(#projectId)`

Defined in: `enums/ProjectRole.java` | `security/SecurityExpressions.java`

## Other

- **Password**: `BCryptPasswordEncoder` (bean in `WebSecurityConfig`)
- **CORS**: `http://localhost:5173`, `http://localhost:5174` (`config/CorsConfig.java`)
- **Stripe webhook**: `POST /webhooks/payment` verifies `Stripe-Signature` via `Webhook.constructEvent(payload, sigHeader, webhookSecret)` (property: `stripe.webhook.secret`). Signature mismatch → `SignatureVerificationException` → 500.
