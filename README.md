# ConduitAlternative

A Conduit / RealWorld-style publishing app built with Java, Spring Boot and Vue. I use this project to learn Spring by rebuilding the backend of an earlier .NET application and connecting it to a Vue client.

Users can write articles, leave comments, save favorites and follow other authors. The personal feed shows articles from followed authors.

## Stack

- Java 21, Spring Boot 4.1.1, Maven Wrapper
- Spring MVC, Spring Security and JWT authentication
- Spring Data JPA / Hibernate, PostgreSQL 16, Flyway
- Vue 3, TypeScript, Pinia, Vue Router and Vite
- JUnit Jupiter, Testcontainers and Vitest

## Run locally

You need JDK 21, Docker, Node.js 24 or newer, and pnpm 10.17.1. Maven is downloaded by the wrapper. An existing PostgreSQL 16 instance can replace the database container below; backend tests still need Docker.

### 1. Start PostgreSQL

From the repository root:

```sh
docker run --name conduit-postgres \
  -e POSTGRES_DB=conduit \
  -e POSTGRES_USER=conduit \
  -e POSTGRES_PASSWORD=conduit \
  -p 127.0.0.1:5432:5432 \
  -v conduit-postgres-data:/var/lib/postgresql/data \
  -d postgres:16-alpine
```

These credentials are for local development. On later runs, start the existing container with `docker start conduit-postgres`.

### 2. Configure the backend

Create `.env` in the repository root. The backend reads it as a properties file:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/conduit
spring.datasource.username=conduit
spring.datasource.password=conduit
CONDUIT_JWT_SECRET=replace-with-a-base64-key
```

Generate a signing key and paste its output in place of `replace-with-a-base64-key`:

```sh
openssl rand -base64 32
```

`.env` is ignored by Git. Flyway applies the database migrations at startup; Hibernate validates the resulting schema.

### 3. Start the backend

Run from the repository root so the application can find `.env`:

```sh
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080/api`. Browse the endpoints at [Swagger UI](http://localhost:8080/swagger-ui/index.html); the OpenAPI document is at [api-docs](http://localhost:8080/v3/api-docs).

### 4. Start the frontend

In a second terminal:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Open [localhost:4173](http://localhost:4173). Vite forwards `/api` requests to `http://127.0.0.1:8080`, so the default setup needs no frontend environment file.

If the backend runs elsewhere, create `frontend/.env.local` with `VITE_API_PROXY_TARGET=http://host:port` and restart Vite. Leave `VITE_API_HOST` unset for this proxy setup.

## API

| Area | Endpoints |
| --- | --- |
| Accounts | `POST /api/users`, `POST /api/users/login`, `GET/PUT /api/user` |
| Profiles | `GET /api/profiles/{username}`, `POST/DELETE /api/profiles/{username}/follow` |
| Articles | `GET/POST /api/articles`, `GET/PUT/DELETE /api/articles/{slug}` |
| Feed | `GET /api/articles/feed` |
| Favorites | `POST/DELETE /api/articles/{slug}/favorite` |
| Comments | `GET/POST /api/articles/{slug}/comments`, `DELETE /api/articles/{slug}/comments/{id}` |
| Tags | `GET /api/tags` |

Article lists accept `tag`, `author`, `favorited`, `limit` and `offset`. Public reads work without a token; writing content and reading the personal feed require authentication. Only an article's author can edit or delete it. Only a comment's author can delete that comment.

For example, registration expects a `user` wrapper:

```sh
curl -i http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"user":{"username":"reader","email":"reader@example.com","password":"local-test-password-123"}}'
```

The response contains `user.token`. Send it in the `Authorization` header on subsequent requests:

```http
Authorization: Token <JWT>
```

`Bearer <JWT>` also works. In Swagger's **Authorize** dialog, enter the complete header value, including `Token` or `Bearer`. Tokens expire after 15 minutes by default; there is no refresh-token flow.

## Tests and builds

Backend, from the repository root:

```sh
./mvnw test
./mvnw package
```

Database tests start their own PostgreSQL container through Testcontainers. You do not need to start the application or the development database first. Docker must be running; the first test run may download the database image. `package` also runs the tests and produces `target/conduit-backend-0.0.1-SNAPSHOT.jar`.

Frontend, from `frontend/`:

```sh
pnpm test:unit
pnpm type-check
pnpm build
```

The frontend build goes to `frontend/dist`. It is not bundled into the backend JAR. Deployment needs a web server for the frontend and routing for `/api`; the Vite development proxy is not part of the production build.

## Code layout

```text
src/main/java/de/conduit/
  users/          accounts, profiles and follows
  articles/       articles, tags, favorites and feed queries
  comments/       comments and their permissions
  security/       password hashing, JWT and access rules
  config/         shared configuration
src/main/resources/db/migration/
                  versioned SQL migrations
src/test/java/    backend tests
frontend/         Vue client
```

Controllers handle HTTP and request/response models. Services coordinate application rules and transactions. JPA repositories and query components handle persistence. The database schema is maintained through Flyway migrations.
