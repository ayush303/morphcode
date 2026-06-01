# Billing & Stripe

## Config

```properties
stripe.api.secret=sk_test_...
stripe.webhook.secret=whsec_...
client.url=http://localhost:8080   # success/cancel redirect base
```

`PaymentConfig` sets `Stripe.apiKey` on startup.

## Checkout Flow

```
POST /api/payments/checkout {planId}
  → StripePaymentProcessor.createCheckoutSessionUrl()
      ├─ load Plan → stripePriceId
      ├─ load User → if stripeCustomerId null: setCustomerEmail(username)
      │              else: setCustomer(stripeCustomerId)
      ├─ SessionCreateParams: mode=SUBSCRIPTION, billing=FLEXIBLE
      │   metadata: {user_id, plan_id}
      │   successUrl: {client.url}/success.html?session_id={CHECKOUT_SESSION_ID}
      │   cancelUrl:  {client.url}/cancel.html
      └─ Session.create(params) → return session.getUrl()
```

## Webhook Events (`POST /webhooks/payment` — public, Stripe-Signature verified)

| Stripe Event | Action |
|---|---|
| `checkout.session.completed` | Create Subscription (INCOMPLETE); save `stripeCustomerId` on User if new |
| `customer.subscription.updated` | Update status, period dates, `cancelAtPeriodEnd`, plan |
| `customer.subscription.deleted` | Set status = CANCELED |
| `invoice.paid` | Fetch period dates from Stripe; call `renewSubscriptionPeriod()`; if PAST_DUE/INCOMPLETE → ACTIVE |
| `invoice.payment_failed` | Set status = PAST_DUE |
| anything else | Logged and ignored |

File: `service/impl/StripePaymentProcessor.java`

## Subscription Lifecycle

```
checkout.session.completed → Subscription(INCOMPLETE)
customer.subscription.updated (active/trialing) → ACTIVE/TRIALING, period dates set
invoice.paid → advance period dates; PAST_DUE/INCOMPLETE → ACTIVE
invoice.payment_failed → PAST_DUE
customer.subscription.updated (canceled) / .deleted → CANCELED
```

Race condition: if `customer.subscription.updated` fires before `checkout.session.completed` fully processes, subscription status may be set before the row exists.

## Stripe → Internal Status Map

| Stripe | Internal |
|---|---|
| `active` | ACTIVE |
| `trialing` | TRIALING |
| `past_due`, `unpaid`, `paused`, `incomplete_expired` | PAST_DUE |
| `canceled` | CANCELED |
| `incomplete` | INCOMPLETE |
| unknown | null (logged, update skipped) |

## Subscription Query

`SubscriptionServiceImpl.getCurrentSubscription()`:
```java
subscriptionRepository.findByUserIdAndStatusIn(userId, Set.of(ACTIVE, PAST_DUE, TRIALING))
// Returns empty Subscription() if none found
```

## Project Limit

`SubscriptionService.canCreateNewProject()`:
- No active sub: `ownerMembershipCount < 100` (FREE_TIER_PROJECTS_ALLOWED)
- Active sub: `ownerMembershipCount < plan.maxProjects`

Counts `ProjectMember` rows where `role=OWNER` for the user.

## Token Gating

**Not enforced.** `usageService.checkDailyTokensUsage()` is commented out in `AiGenerationServiceImpl.streamResponse()`. Tokens are still recorded in `UsageLog` after each LLM call.

## Customer Portal

```
POST /api/payments/portal
  → StripePaymentProcessor.openCustomerPortal()
      ├─ load User → throws BadRequestException if stripeCustomerId null
      └─ billingportal.Session.create({customer, returnUrl: client.url}) → portalUrl
```

## Known Billing Issues

- `tokensUsedThisCycle` in `SubscriptionResponse` always null — `@Mapping(target="tokensUsedThisCycle", ignore=true)` in `SubscriptionMapper.java:14`
- `GET /api/plans` returns `[]` — `PlanRepository` never called in `PlanServiceImpl.java:13`
