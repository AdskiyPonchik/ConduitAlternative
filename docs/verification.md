# Verification

Use JDK 21, Node.js 24 and pnpm 10.17.1. The root `.node-version` and the frontend `packageManager` field record the intended runtimes. Install frontend dependencies with `pnpm install --frozen-lockfile`.

## Backend

```sh
./mvnw test
./mvnw verify
./mvnw -Dtest=DemoRequestFilterTests test
./mvnw -Dtest=DemoDataTests test
```

Integration tests use a disposable `postgres:16-alpine` database through Testcontainers and `@ServiceConnection`. They ignore the development `.env`. Docker must be reachable. `DemoDataTests` checks repeat initialization, reset, password hashing and refusal to modify unmarked or incorrectly confirmed data. `DemoRequestFilterTests` runs without Docker and checks quotas, window expiry, user isolation, forwarded-header spoofing and bounded request bodies.

## Frontend

From `frontend/`:

```sh
pnpm type-check
pnpm test:unit
pnpm build
```

Type checking includes both Vue source and Playwright configuration/tests. Unit tests use Vitest, Happy DOM, Testing Library and MSW; they do not establish compatibility with a running backend. Regression tests cover same-component article navigation, stale responses, request failures/retry, duplicate actions, public-read recovery and account-scoped drafts.

## Browser integration

From `frontend/`:

```sh
pnpm exec playwright install chromium
pnpm test:e2e
```

On Linux CI, use `pnpm exec playwright install --with-deps chromium` to install browser system dependencies too. JDK 21 and Docker are also required.

Playwright starts two processes and refuses to reuse existing servers:

- The Maven `spring-boot:test-run` launcher starts the real application on port 18080 with a disposable PostgreSQL 16 container. The launcher exists only in the test classpath. `.env` imports are disabled and the signing key/password are deterministic test-only values.
- Vite starts on port 14173 and proxies `/api` to that backend. The demo dataset is enabled explicitly to provide content for reads; demo throttling is disabled for these browser scenarios and tested separately.

The browser suite checks registration/login/logout, article CRUD, comments, favorites, follows, personal feeds, another user's rejected edits/deletes, expired JWTs and editor draft recovery, public reads with an expired token, direct nested URLs, reload, pagination, tag filters and retry after a transport failure. Successful API responses come from Spring Boot and PostgreSQL. The transport-failure test temporarily aborts a browser request; the retry reaches the real API. An expiry test briefly delays `/api/user` without fabricating its response to exercise the startup race.

Traces and screenshots are retained on failure. `pnpm exec playwright test --list` checks test discovery without starting the application or browser; it is not an integration test run. These checks use Vite's development server and do not verify a deployment proxy or production SPA fallback.

## GitHub Actions

`.github/workflows/ci.yml` runs on pushes and pull requests:

1. Backend `verify`, including PostgreSQL integration tests.
2. Frontend type checking, unit tests and build with locked dependencies.
3. Browser integration after both jobs pass, with Chromium installed on the runner.

The workflow grants read-only repository permissions, cancels superseded runs for the same ref and retains failure reports. It does not deploy the application. A workflow file is not evidence of a successful hosted run; inspect its result after the commit is pushed.
