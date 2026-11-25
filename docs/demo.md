# Disposable demonstration environment

The `demo` Spring profile adds sample content and request limits. It is disabled in the normal application. Use a separate PostgreSQL database; all visitor data in that database is disposable.

## Configure and start

1. Create an empty PostgreSQL database whose name ends in `_demo`, for example `conduit_demo`.
2. Copy `.env.example` to `.env` if you do not already have local configuration. Preserve any existing development configuration separately.
3. Set these properties in `.env`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/conduit_demo
spring.datasource.username=conduit
spring.datasource.password=your-database-password
CONDUIT_JWT_SECRET=your-base64-signing-key
CONDUIT_DEMO_DATABASE=conduit_demo
CONDUIT_DEMO_PASSWORD=your-demo-only-password-of-at-least-15-characters
```

The password must contain 15–128 Unicode code points. Generate the JWT key with `openssl rand -base64 32`. These values belong only in local/environment configuration.

Start the backend with the explicit profile:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Start the frontend normally from `frontend/` with `pnpm dev`. `GET /api/demo` tells the client whether to show the demo instructions. In demo mode it returns HTTP 503 until seeding commits, then HTTP 200. This endpoint never returns credentials. Visitors should register temporary accounts.

## Dataset

The initial dataset contains four accounts, fifteen articles, fifteen comments, twenty-three favorites and two follows. Article timestamps are spaced across fifteen days. There are enough articles to exercise the client's ten-item pages, tag filters, author filters, favorites and the personal feed.

| Account | Email | Purpose |
| --- | --- | --- |
| `java-author` | `java-author@demo.conduit.test` | Sample author |
| `database-author` | `database-author@demo.conduit.test` | Sample author |
| `vue-author` | `vue-author@demo.conduit.test` | Sample author |
| `demo-reader` | `demo-reader@demo.conduit.test` | Favorites and follows |

All four accounts use the initial `CONDUIT_DEMO_PASSWORD`, hashed through the application's Argon2 encoder. Changing that setting on a normal restart does not rotate existing passwords; a reset recreates them. The operator may use `demo-reader` to explore a populated feed; the password is not published by the app. New visitors can register their own temporary accounts, publish an article, comment, favorite another article, follow its author and inspect Your Feed.

## Startup and reset policy

Normal startup seeds an empty database once. Later restarts preserve edits and visitor accounts. Stable UUIDs identify seeded users and articles. `demo_state` records initialization; an existing marker prevents startup from restoring deleted seed content or duplicating relationships.

Initialization and reset run inside one Spring transaction. A PostgreSQL transaction advisory lock serializes concurrent initializers. The service compares `current_database()` with `CONDUIT_DEMO_DATABASE` and requires the `_demo` suffix. It refuses an unmarked database that already contains users, including when reset was requested.

Reset is manual. Stop every application instance using this demo database, build the JAR with `./mvnw package`, then run:

```sh
java -jar target/conduit-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=demo \
  --spring.main.web-application-type=none \
  --conduit.demo.reset=true
```

Use the same datasource and confirmation configuration as the demo server. This command deletes all accounts and publishing data in the confirmed demo database, recreates the sample dataset, closes the application context and exits. It does not expose an HTTP server. Passing reset to a web-server startup is rejected before initialization.

Restart the server normally afterwards. There is no public reset endpoint or automatic reset schedule. Existing visitor JWTs should be discarded; their accounts no longer exist. The confirmation and marker prevent common configuration mistakes, but are not a substitute for stopping other instances and selecting the intended database.

## Request limits

The demo profile enables a servlet filter inside Spring Security, after bearer-token authentication and before controllers.

| Budget | Default | Key |
| --- | --- | --- |
| Login attempts | 10 per 60 seconds | Connection IP |
| Registrations | 5 per 3,600 seconds | Connection IP |
| Other API writes | 30 per 60 seconds | Verified JWT subject, otherwise connection IP |
| Write request body | 524,288 bytes (512 KiB) | Per request |

The budgets use fixed windows starting with the first request. Rejected requests return HTTP `429`, `Retry-After` and the usual `errors.body` envelope. Oversize bodies return `413`. A bounded stream read also rejects chunked bodies without trusting `Content-Length`; the buffered bytes remain available to Spring MVC's JSON converter. Public GET requests do not spend the write budget.

Configuration properties are under `conduit.demo.limits` in `application-demo.yaml`. The map is capped at 10,000 live keys and expired windows are removed. Limits are process-local and reset on restart; this is a small demo safeguard, not a distributed quota system.

The filter uses the connection address and deliberately ignores forwarded headers. Before deployment through the existing Cloudflare Tunnel, configure and verify trusted proxy handling; otherwise login and registration budgets may be shared by visitors behind that connection. Network-level timeouts and proxy configuration belong to the deployment work.

