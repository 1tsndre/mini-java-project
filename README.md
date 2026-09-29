# Mini Java E-Commerce API

An E-Commerce REST API built with Java and Spring Boot, following a layered architecture. It is a one-to-one port of [mini-go-project](https://github.com/1tsndre/mini-go-project): the same endpoints, request and response JSON, status codes, error messages, database schema, Redis keys, NSQ topics and gRPC contract, so either implementation can serve the same clients. Like the Go version, it is a monorepo with two services that talk through asynchronous messaging and gRPC.

## Tech Stack

| Technology | Purpose |
|------------|---------|
| **Java 21** + **Spring Boot 4** (Spring MVC) | HTTP server and routing, on virtual threads |
| **PostgreSQL** | Primary data store |
| **Redis** (Spring Data Redis, Lettuce) | Cart storage, product caching, distributed locking, rate limiting |
| **NSQ** (nsq-j) | Asynchronous order-to-payment pipeline |
| **gRPC** (grpc-java) | Synchronous payment-status query (store-service → payment-service) |
| **JdbcClient** | Plain SQL queries mapped to models (no ORM, no JPA) |
| **Flyway** | Versioned SQL schema migrations, applied on startup |
| **JWT** (Auth0 java-jwt) | Authentication with access/refresh token pair |
| **Jackson 3** | JSON, configured to read and write exactly like Go's `encoding/json` |
| **Logback** | Structured logging (JSON in production) with request tracing |
| **Lombok** | Less boilerplate in models and services |
| **Maven** (wrapper included) | Multi-module build |

## Architecture

```
┌───────────────────────────────────────────────────────┐
│                    store-service                      │
│                                                       │
│  Controller ──▶ Service ──▶ Repository                │
│     │              │            ├── JdbcClient (SQL)  │
│     │              │            └── cache (Redis)     │
│  Filters +         │                                  │
│  RouteGuard        │         NSQ Publisher            │
│  (auth, rate       │              │                   │
│   limit, log)      │              │                   │
└────────────────────┼──────────────┼───────────────────┘
                     │              │
                     │              ▼
                     │     ┌────────────────┐
                     │     │      NSQ       │
                     │     └───────┬────────┘
                     │             │
                     │             ▼
                ┌────┴─────────────────────────┐
                │      payment-service         │
                │                              │
                │  gRPC Server ◀── Service     │
                │       NSQ Consumer/Publisher │
                └──────────────────────────────┘
```

Each layer depends on interfaces, so every part can be tested on its own. Spring wires the beans by constructor injection.

Repositories run plain SQL through Spring's `JdbcClient`; there is no ORM. The schema is defined only by the SQL files in `store-service/src/main/resources/db/migration/` (the same SQL as the Go project's `migrations/`), which Flyway applies when the service starts. Missing rows come back as an empty `Optional`, and constraint violations are translated into `UniqueViolationException` and `ForeignKeyViolationException`, so services never depend on the database driver.

Services report failures with typed exceptions (`NotFoundException`, `ConflictException`, `InsufficientStockException`, ...). One `@RestControllerAdvice` turns each type into the HTTP status and error code the Go handlers use, so the status of a response never depends on the wording of its message.

## Features

- **Auth** — JWT access/refresh tokens, role-based access control (Admin, Buyer, Seller)
- **Products** — Full CRUD, search, filter by category/store/price, image upload
- **Cart** — Redis-first with PostgreSQL fallback, persists across sessions, always shows current product prices (the price checkout charges)
- **Orders** — Checkout reserves stock atomically in a single database transaction (conditional decrement, so stock can never go negative), split into one order per store (a single checkout may create multiple orders), status flow: `pending → paid → processing → shipping → shipped → completed`, cancellation up to `processing` (stock is returned in the same transaction)
- **Payment Pipeline** — Async via NSQ: order created → payment processed (mock) → status updated. Orders still pending after 2 minutes are republished, and the payment service answers duplicate deliveries for an order with its recorded result
- **Reviews** — One review per purchased product, rating 1–5 with optional comment
- **Rate Limiting** — Sliding window using Redis Sorted Sets
- **Observability** — Structured logging with request ID propagation, SQL statement logging in development, graceful shutdown

## Project Structure

<details>
<summary>Click to expand</summary>

```
mini-java-project/
├── pom.xml                         # Parent build: modules and dependency versions
├── proto/                          # gRPC protobuf definitions, compiled into Java classes
├── common/                         # Shared code: response envelope, JWT, uploads, NSQ publisher
│
├── store-service/                  # Main REST API service
│   └── src/main/
│       ├── java/.../store/
│       │   ├── StoreServiceApplication.java
│       │   ├── config/             # AppConfig (env variables), Jackson, web pipeline, infrastructure beans
│       │   ├── constant/           # Redis keys, roles, statuses, error codes, NSQ topics, rate limit key types
│       │   ├── model/              # Entities
│       │   ├── dto/                # Request and response bodies
│       │   ├── repository/         # Data access layer (plain SQL via JdbcClient)
│       │   │   └── cache/          # Cache interface + Redis implementation
│       │   ├── service/            # Business logic layer
│       │   │   └── exception/      # Typed business errors
│       │   ├── controller/         # REST controllers
│       │   ├── web/                # Route guard (auth, roles, rate limit), error handling
│       │   │   ├── bind/           # Argument resolvers: JSON body, path UUIDs, current user
│       │   │   └── filter/         # request ID, access log, body size limit, timeout
│       │   ├── messaging/          # NSQ publisher and consumer (payment results)
│       │   ├── grpc/               # Payment service client
│       │   ├── worker/             # Background jobs (republish stale pending orders)
│       │   ├── pagination/         # Page handling
│       │   └── util/               # Go-compatible UUID, decimal and string rules
│       └── resources/
│           ├── application*.properties
│           └── db/migration/       # SQL schema migrations (Flyway)
│
├── payment-service/                # gRPC + NSQ payment processor
│   └── src/main/java/.../payment/
│       ├── config/
│       ├── grpc/                   # gRPC server
│       ├── service/                # Mock payment logic
│       └── messaging/              # NSQ consumer/publisher
│
├── docs/                           # Static OpenAPI spec
├── qa/                             # End-to-end QA script and test cases
└── .env.example
```

</details>

## Getting Started

### Prerequisites

- JDK 21 (Maven itself comes with the wrapper, `./mvnw`)
- PostgreSQL 16+
- Redis 7+
- NSQ

> Alternatively, skip all of the above and use [Docker](#run-with-docker) instead.

### Setup

**1. Clone and configure**

```bash
git clone https://github.com/1tsndre/mini-java-project.git
cd mini-java-project
cp .env.example .env
# Fill in your PostgreSQL, Redis, and JWT credentials
```

The services read their settings from environment variables, with `.env` in the working directory as a fallback, under the same names and defaults as the Go services.

**2. Run NSQ**

Download the NSQ binary from https://nsq.io/deployment/installing.html, then run in separate terminals:

```bash
nsqlookupd
nsqd --lookupd-tcp-address=localhost:4160
```

**3. Create the database**

```bash
createdb mini_java_ecommerce
```

The tables are created by the store-service itself: Flyway applies any migration the database does not have yet every time the service starts.

**4. Build and run**

```bash
./mvnw package -DskipTests

java -jar payment-service/target/payment-service.jar
java -jar store-service/target/store-service.jar
```

API available at `http://localhost:8080`. OpenAPI spec at `http://localhost:8080/docs/swagger.json`.

### Run with Docker

Spins up PostgreSQL, Redis, NSQ, and both services in one go — no need to install Java, PostgreSQL, Redis, or NSQ locally.

**Prerequisites:** Docker and Docker Compose.

```bash
git clone https://github.com/1tsndre/mini-java-project.git
cd mini-java-project
docker-compose up -d
```

This pulls the `store-service` and `payment-service` images from Docker Hub and starts everything; the store-service creates the tables on its first start. API available at `http://localhost:8080`, NSQ admin at `http://localhost:4171`.

To rebuild locally instead of pulling from Docker Hub, replace the `image:` field with a `build:` block in `docker-compose.yml` (see `Dockerfile.store-service` / `Dockerfile.payment-service`).

```bash
docker-compose logs -f store-service   # tail logs
docker-compose down                    # stop everything
```

## API Endpoints

<details>
<summary>Click to expand</summary>

### Health
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/health` | Service health check | - |

### Auth
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/auth/register` | Register new user | - |
| POST | `/api/v1/auth/login` | Login | - |
| POST | `/api/v1/auth/refresh` | Refresh token (refresh token in request body) | - |

### Store
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/stores` | Create store (become seller) | Buyer |
| GET | `/api/v1/stores/:id` | Get store details | - |
| PUT | `/api/v1/stores/:id` | Update store | Seller |
| POST | `/api/v1/stores/:id/logo` | Upload store logo | Seller |

### Category
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/categories` | Create category | Admin |
| GET | `/api/v1/categories` | List categories | - |
| PUT | `/api/v1/categories/:id` | Update category | Admin |
| DELETE | `/api/v1/categories/:id` | Delete category | Admin |

### Product
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/products` | Create product | Seller |
| GET | `/api/v1/products` | List/search/filter products | - |
| GET | `/api/v1/products/:id` | Get product detail | - |
| PUT | `/api/v1/products/:id` | Update product | Seller |
| DELETE | `/api/v1/products/:id` | Delete product | Seller |
| POST | `/api/v1/products/:id/image` | Upload product image | Seller |

### Review
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/products/:id/reviews` | Create review | Buyer |
| GET | `/api/v1/products/:id/reviews` | List reviews | - |

### Cart
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/cart` | Get cart | Buyer |
| POST | `/api/v1/cart/items` | Add item to cart | Buyer |
| PUT | `/api/v1/cart/items/:product_id` | Update item quantity | Buyer |
| DELETE | `/api/v1/cart/items/:product_id` | Remove item from cart | Buyer |

### Order
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/orders` | Checkout (creates one order per store) | Buyer |
| GET | `/api/v1/orders` | List buyer orders | Buyer |
| GET | `/api/v1/orders/:id` | Get order detail | Buyer |
| PUT | `/api/v1/orders/:id/cancel` | Cancel order | Buyer |
| GET | `/api/v1/orders/:id/payment` | Get payment status (via gRPC to payment-service) | Buyer |
| GET | `/api/v1/seller/orders` | List seller orders | Seller |
| PUT | `/api/v1/orders/:id/status` | Update order status | Seller |

</details>

## Response Format

<details>
<summary>Click to expand</summary>

```json
// Success
{
  "data": { ... },
  "meta": {
    "request_id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-02-20T10:00:00Z",
    "pagination": { "current_page": 1, "per_page": 10, "total_items": 100, "total_pages": 10 }
  }
}

// Error
{
  "errors": [{ "code": "VALIDATION_ERROR", "field": "email", "message": "is required" }],
  "meta": { "request_id": "550e8400-e29b-41d4-a716-446655440000", "timestamp": "2026-02-20T10:00:00Z" }
}
```

Error codes: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INVALID_STATUS`, `INSUFFICIENT_STOCK`, `RATE_LIMITED`, `REQUEST_TIMEOUT`, `INTERNAL_ERROR`. The status codes each endpoint returns are listed in `docs/swagger.json`.

Request bodies are read the way the Go service reads them: the `Content-Type` header is not checked, unknown fields are ignored, field names match case-insensitively, and a value of the wrong JSON type (for example a number where a string belongs) is answered with 400 `invalid request body`.

</details>

## Environment Variables

<details>
<summary>Click to expand</summary>

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_PORT` | 8080 | Application port |
| `APP_ENV` | development (store-service), production (payment-service) | Environment; also the Spring profile, `production` logs JSON |
| `APP_REQUEST_TIMEOUT` | 30s | Per-request timeout (responds 504 when exceeded) |
| `APP_READ_TIMEOUT` | 15s | Max time to wait for a request |
| `APP_WRITE_TIMEOUT` | 35s | Kept for compatibility with the Go service's settings; must be greater than `APP_REQUEST_TIMEOUT` |
| `APP_IDLE_TIMEOUT` | 60s | Keep-alive idle timeout |
| `APP_SHUTDOWN_TIMEOUT` | 30s | Grace period for in-flight requests on shutdown |
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_USER` | postgres | PostgreSQL user |
| `DB_PASSWORD` | - | PostgreSQL password |
| `DB_NAME` | mini_java_ecommerce | Database name |
| `DB_SSLMODE` | disable | PostgreSQL SSL mode |
| `REDIS_HOST` | localhost | Redis host |
| `REDIS_PORT` | 6379 | Redis port |
| `REDIS_PASSWORD` | - | Redis password |
| `REDIS_DB` | 0 | Redis database number |
| `NSQ_LOOKUPD_ADDR` | localhost:4161 | NSQ Lookupd address |
| `NSQD_ADDR` | localhost:4150 | NSQd address |
| `JWT_SECRET` | - | JWT signing secret |
| `JWT_ACCESS_EXPIRY` | 15m | Access token expiry |
| `JWT_REFRESH_EXPIRY` | 168h | Refresh token expiry |
| `RATE_LIMIT_PUBLIC` | 60 | Req/min for public endpoints |
| `RATE_LIMIT_AUTH` | 120 | Req/min for authenticated endpoints |
| `RATE_LIMIT_LOGIN` | 10 | Req/min for login endpoint |
| `UPLOAD_MAX_SIZE` | 5242880 | Max upload size (bytes) |
| `UPLOAD_DIR` | ./uploads | Upload directory |
| `PAYMENT_GRPC_PORT` | 50051 | Payment service gRPC port (payment-service server) |
| `PAYMENT_GRPC_ADDR` | localhost:50051 | Payment service gRPC address (store-service client) |

Durations use Go's format (`15m`, `1h30m`, `500ms`), so the same `.env` works for both projects.

</details>

## Testing

```bash
./mvnw test
```

The tests mirror the Go project's: services with mocked repositories (JUnit 5 + Mockito), the HTTP layer through MockMvc with the real filters, route guard and error handling, the JSON format, the request timeout, and the configuration.

Without a database, the repository tests still check the selected columns and the row mappers against the migrations. With `TEST_DATABASE_URL` set, they also run every query against a real PostgreSQL (those tests are skipped otherwise). They migrate a temporary schema and drop it afterwards, so the database's own data is left untouched — for example, with the Docker Compose database:

```bash
TEST_DATABASE_URL="postgres://postgres:postgres@localhost:5432/mini_java_ecommerce?sslmode=disable" \
  ./mvnw -pl store-service -am test -Dtest=RepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

The end-to-end QA script runs the same 84 cases as the Go project against a running service:

```bash
bash qa/qa_test.sh            # against http://localhost:8080
```

## License

This project is a personal portfolio project.
