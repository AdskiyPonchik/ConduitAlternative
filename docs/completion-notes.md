# Application completion work — steps 1–5

This change covers frontend behavior, session recovery, verification tooling, CI and preparation of a disposable demo. Deployment configuration is outside its scope.

## Loading and request ordering

`frontend/src/composable/use-resource.ts` separates three outcomes that previously overlapped: loading, a successful empty result, and failure. Callers supply a reactive source, a loader and an initial value. Vue's `watch` starts a load when its source changes. Each load captures an increasing generation number; only a matching generation can update data, loading or error state. Disposing the component scope invalidates pending loads too.

This prevents an older page/filter/profile response from replacing a newer result. It does not cancel server execution or claim that requests complete in order. A retry starts another generation and clears the current error. Article detail and comment loaders watch the route slug, so Vue Router can reuse the component for a different article without leaving its previous data on screen. Viewer-token changes also reload personalized fields.

`RequestError.vue` presents failures and retry controls. It is used by article lists, article detail, comments, profiles and tags. A network failure is no longer rendered as an empty feed or an indefinitely missing profile.

## Mutating actions

`use-async.ts` exposes `active` and `error` refs for UI actions. The function checks `active` before invoking a second operation; disabling a button alone would not guard a second submit event. `finally` releases the guard on both success and failure. Rejections become visible state instead of unhandled event promises. Login, registration, settings and the article editor have equivalent form-level guards.

This protects repeated actions in the current UI. It is not an exactly-once delivery protocol: backend idempotence for favorites/follows still comes from PostgreSQL constraints and the existing relation SQL.

The unused Admin page and its tests were removed because they called an endpoint absent from this Java backend. German editor/login text was changed to English. Existing frontend attribution, licensing and Markdown sanitization remain in place.

## Session expiry and drafts

The API wrapper records the Authorization value associated with a request. A `401` clears the session only if that value matches the current token. This preserves a newer login when an older request finishes late. Session restoration likewise applies a successful response only while its original token remains current.

After a rejected authenticated public GET, the wrapper can repeat that request once without Authorization, provided there is no newer session. Public route matching is an explicit allowlist. It never replays a write or the authenticated personal feed. This keeps public content readable when several startup requests all carried the same expired token.

Session invalidation shows a notice. Protected actions open sign in with the original route in `redirect`; login/registration return to a local path afterwards. The old login/register guards were removed because a cached token is not evidence that a session is valid, and canceling initial navigation could prevent an expired user from reaching sign in.

`drafts.ts` stores article content and unsent comments in `sessionStorage`. Keys include the account username and article (or a new-article key). Vue watchers save edits; a successful submission removes the draft. Returning after sign in restores the same account's form. Invalid JSON or unavailable storage does not break editing. Passwords are not draft data. Storage is tab-local and optional, so closing the tab or denying storage can discard a draft.

## Demo persistence and reset

`application-demo.yaml` explicitly enables sample data and limits. `DemoDataService` uses the existing entities, Argon2 password encoder and relationship components. It creates four users, fifteen articles, fifteen comments, twenty-three favorites and two follows. The injected `Clock` supplies timestamps; UUIDs for sample users/articles are deterministic.

Flyway migration V6 creates only the `demo_state` marker table. Ordinary applications do not receive sample records. The seed service checks the actual PostgreSQL database name, requires a matching confirmation with an `_demo` suffix, and refuses unmarked existing user data. A PostgreSQL advisory transaction lock serializes initializers. Spring's transaction interceptor wraps initialization/reset; Hibernate flushes entity inserts and PostgreSQL commits the operation atomically.

The marker makes normal restarts preserve visitor changes. Explicit reset deletes all publishing data and accounts in the confirmed demo database and recreates the dataset. Reset is accepted only by a non-web maintenance startup; the context closes after the service transaction returns. There is no reset HTTP endpoint or scheduled reset. See `demo.md` for the exact procedure and its destructive effect.

`GET /api/demo` supplies public instructions without credentials. In demo mode it returns `503` until the initialization marker is committed. The browser test launcher uses this endpoint for readiness so tests cannot race the seed transaction.

## Demo request limits

`DemoRequestFilter` is a `OncePerRequestFilter` installed after JWT authentication in Spring Security. A disabled servlet-container registration prevents it from also running outside the security chain. Login/registration budgets use the connection IP; other writes use the authenticated subject, falling back to the connection IP. The limiter uses synchronized, bounded, process-local fixed windows and an injected clock. `429` responses include `Retry-After`.

The body check first rejects an excessive declared length, then reads at most the configured maximum plus one byte. The second check also covers chunked or unknown-length bodies. A request wrapper replays accepted bytes to Spring MVC's JSON converter. Oversize requests receive `413` before reaching a controller.

This filter deliberately does not trust forwarded headers. Deployment through Cloudflare Tunnel must establish trusted proxy handling; otherwise visitor login/registration limits may share the tunnel connection's address. Limits reset with the process and are not shared across instances.

## Verification and CI

Vitest regressions cover request ordering, navigation, errors/retry, duplicate actions, session handling and draft persistence. Docker-free JUnit tests exercise request budgets and body buffering. PostgreSQL integration tests cover repeat seeding, reset, readiness, password hashing and database guards.

Playwright's six scenarios use the real Spring Boot application and PostgreSQL 16. A test-only `E2eApplication` launcher starts Testcontainers through `@ServiceConnection`; Vite proxies the browser requests. An expired test token is signed using the test-only key and rejected by the real JWT validator. Successful responses are not mocked. A transport-failure scenario aborts one browser request and then retries against the actual backend. The suite checks Vite routing, not a future production reverse proxy.

GitHub Actions runs backend verification and frontend checks/build. The browser job was removed later; browser tests run locally only. Node 24, Java 21 and pnpm 10.17.1 are explicit. Workflow permissions are read-only and failure reports are retained. CI is configured but has not been executed remotely by this local change.

Local verification on 2026-10-23: frontend type checking, 90 unit tests, build and targeted ESLint passed; all eight Docker-free filter tests passed; Java packaging with tests skipped passed. Playwright discovers six scenarios. Full PostgreSQL/backend and browser execution are blocked here because socket creation and Docker access are denied. Those runs are not reported as passing.
