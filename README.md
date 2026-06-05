# Wallet Service

A Spring Boot REST service for managing user wallets, deposits, trades, and transaction history. The service is designed for financial correctness: requests are retry-safe through idempotency keys, trades are protected from concurrent overdrafts with database locks, and wallet metadata is tracked for audit and reporting.

## Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [API](#api)
- [Error Handling](#error-handling)
- [Entity Model](#entity-model)
- [Concurrency and Idempotency](#concurrency-and-idempotency)
- [Database Schema](#database-schema)
- [Build, Run, and Test](#build-run-and-test)
- [Docker Deployment](#docker-deployment)
- [API Request Files](#api-request-files)
- [Testing Strategy](#testing-strategy)
- [Operational Notes](#operational-notes)
- [Enhancement History](#enhancement-history)
- [Future Enhancements](#future-enhancements)
- [License](#license)

## Features

- Wallets are created on demand with a zero balance.
- Deposits add funds and update lifetime deposit metrics.
- Trades debit funds only when sufficient balance exists.
- Trades use pessimistic write locks to prevent overdrafts under concurrent access.
- Idempotency keys prevent duplicate processing when clients retry requests.
- Transaction history records operation type, amount, timestamp, idempotency key, and balance after the operation.
- Wallet responses include audit fields, status, currency, lifetime metrics, description, and transactions.
- `PATCH /wallets/{userId}` updates wallet description metadata.
- Global exception handling returns structured error responses.
- Request validation enforces amounts, UUID v4 identifiers, and description length on all endpoints including path variables.
- Idempotency keys are scoped per wallet — different users may reuse the same key without interference.
- Docker support runs the service with PostgreSQL and health checks.
- H2 is available for local development and tests.

## Technology Stack

- Java 21
- Spring Boot 3.3.6
- Spring WebFlux (Netty — non-blocking, reactive I/O)
- Spring Data R2DBC (reactive database access)
- Spring Validation
- Spring Boot Actuator
- Virtual Threads (`spring.threads.virtual.enabled=true`)
- H2 R2DBC driver for local/test runtime
- PostgreSQL R2DBC driver for Docker deployment
- Lombok
- JUnit 5, Mockito, and Reactor Test (StepVerifier)
- Maven
- Docker and Docker Compose

## Project Structure

```text
.
|-- pom.xml
|-- Dockerfile
|-- docker-compose.yml
|-- api-requests/
|   |-- wallet-service.http
|   `-- wallet-service.ps1
|-- src/main/java/com/wallet/
|   |-- WalletServiceApplication.java
|   |-- controller/
|   |   `-- WalletController.java
|   |-- dto/
|   |   |-- DepositRequest.java
|   |   |-- ErrorResponse.java
|   |   |-- OperationResponse.java
|   |   |-- TradeRequest.java
|   |   |-- UpdateWalletRequest.java
|   |   `-- WalletResponse.java
|   |-- entity/
|   |   |-- CurrencyType.java
|   |   |-- Transaction.java
|   |   |-- Wallet.java
|   |   `-- WalletStatus.java
|   |-- exception/
|   |   |-- GlobalExceptionHandler.java
|   |   |-- InsufficientBalanceException.java
|   |   |-- InvalidAmountException.java
|   |   |-- WalletException.java
|   |   `-- WalletNotFoundException.java
|   |-- repository/
|   |   |-- TransactionRepository.java
|   |   `-- WalletRepository.java
|   `-- service/
|       `-- WalletService.java
|-- src/main/resources/
|   |-- application.properties
|   `-- application-docker.properties
`-- src/test/java/com/wallet/service/
    |-- WalletControllerTest.java
    |-- WalletServiceIntegrationTest.java
    `-- WalletServiceTest.java
```

## API

All path `userId` values must be UUID v4 strings:

```text
xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
```

Idempotency keys use the same UUID v4 format. Amounts must be positive numbers.

### Deposit Funds

```http
POST /wallets/{userId}/deposit
Content-Type: application/json

{
  "amount": 100.00,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "description": "Primary wallet"
}
```

`description` is optional and is applied when a wallet is created by the first deposit.

Successful response:

```json
{
  "status": "success",
  "newBalance": 100.00,
  "message": "Deposit completed"
}
```

If the same idempotency key is retried, the balance is not changed again and the previous result is returned:

```json
{
  "status": "success",
  "newBalance": 100.00,
  "message": "Deposit already processed"
}
```

### Place a Trade

```http
POST /wallets/{userId}/trade
Content-Type: application/json

{
  "amount": 25.00,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440001"
}
```

Successful response:

```json
{
  "status": "success",
  "newBalance": 75.00,
  "message": "Trade completed"
}
```

Insufficient balance response:

```json
{
  "status": "error",
  "newBalance": 50.00,
  "message": "Insufficient balance for trade. Required: 100.00, Available: 50.00"
}
```

### Get Wallet

```http
GET /wallets/{userId}
```

Response:

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440010",
  "balance": 75.00,
  "currency": "EUR",
  "status": "ACTIVE",
  "totalDeposited": 100.00,
  "totalTraded": 25.00,
  "createdAt": "2026-06-01T23:32:00",
  "updatedAt": "2026-06-01T23:33:00",
  "lastTransactionAt": "2026-06-01T23:33:00",
  "description": "Primary wallet",
  "transactions": [
    {
      "transactionId": 1,
      "type": "DEPOSIT",
      "amount": 100.00,
      "timestamp": "2026-06-01T23:32:00",
      "balanceAfter": 100.00
    },
    {
      "transactionId": 2,
      "type": "TRADE",
      "amount": 25.00,
      "timestamp": "2026-06-01T23:33:00",
      "balanceAfter": 75.00
    }
  ]
}
```

If a wallet does not exist, the service returns an empty wallet-style response with zero balance.

### Update Wallet Description

```http
PATCH /wallets/{userId}
Content-Type: application/json

{
  "description": "Travel wallet"
}
```

Rules:

- Maximum description length is 255 characters.
- Empty string or `null` clears the description.
- The wallet is created if it does not already exist.

Response:

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440010",
  "balance": 0.00,
  "currency": "EUR",
  "status": "ACTIVE",
  "totalDeposited": 0.00,
  "totalTraded": 0.00,
  "createdAt": "2026-06-01T23:32:00",
  "updatedAt": "2026-06-01T23:32:00",
  "lastTransactionAt": null,
  "description": "Travel wallet",
  "transactions": []
}
```

## Error Handling

The application uses `@ControllerAdvice` through `GlobalExceptionHandler` to return structured errors for validation failures, business exceptions, and unexpected failures.

Example:

```json
{
  "error": "Insufficient Balance",
  "message": "Insufficient balance for trade. Required: 100.00, Available: 50.00",
  "errorCode": "INSUFFICIENT_BALANCE",
  "path": "/wallets/550e8400-e29b-41d4-a716-446655440010/trade",
  "timestamp": "2026-06-01T10:35:00",
  "status": 402
}
```

Common statuses:

| Status | Meaning |
| --- | --- |
| 400 | Invalid request, invalid amount, invalid UUID v4, or operation response error |
| 402 | Insufficient balance |
| 404 | Wallet not found where applicable |
| 409 | Wallet is not active (frozen or closed) |
| 500 | Unexpected server error |

## Entity Model

### Wallet

| Field | Type | Purpose |
| --- | --- | --- |
| `userId` | `String` | UUID v4 primary key |
| `balance` | `BigDecimal` | Current wallet balance |
| `createdAt` | `LocalDateTime` | Creation timestamp, set by service before insert |
| `updatedAt` | `LocalDateTime` | Last update timestamp, set by service before save |
| `status` | `WalletStatus` | `ACTIVE`, `FROZEN`, or `CLOSED`; default `ACTIVE` |
| `currency` | `CurrencyType` | `USD`, `EUR`, or `GBP`; default `EUR` |
| `totalDeposited` | `BigDecimal` | Lifetime deposit total |
| `totalTraded` | `BigDecimal` | Lifetime trade debit total |
| `lastTransactionAt` | `LocalDateTime` | Last deposit or trade time |
| `description` | `String` | Optional wallet notes, max 255 characters |
| `version` | `Long` | DB-level version column (default 0); not mapped in R2DBC entity |

Indexes:

- `idx_wallet_status`
- `idx_wallet_created_at`
- `idx_wallet_currency`

### Transaction

Transactions record the wallet, type (`DEPOSIT` or `TRADE`), amount, timestamp, idempotency key, and balance after the operation. They provide an audit trail and support safe retries.

## Concurrency and Idempotency

### Trade Safety

Trades use a database-level pessimistic write lock via a native SQL `SELECT ... FOR UPDATE` query:

```java
@Query("SELECT * FROM wallets WHERE user_id = :userId FOR UPDATE")
Mono<Wallet> findByUserIdForUpdate(String userId);
```

When a trade starts, the wallet row is locked for the duration of the `@Transactional` reactive chain. Other trades for the same wallet wait until the lock is released. This makes the balance check and balance update atomic and prevents concurrent overdrafts.

The reactive `@Transactional` uses `R2dbcTransactionManager` (auto-configured by Spring Boot when `spring-boot-starter-data-r2dbc` is on the classpath). The transaction context is propagated through the Reactor `Context` — the entire `flatMap` chain must remain within one `Mono` subscription, and `.block()` must never be called inside a transactional method.

Safe concurrent trade example:

```text
Starting balance: 1000

Thread A locks wallet, trades 500, balance becomes 500, releases lock.
Thread B then locks wallet, trades 500, balance becomes 0, releases lock.

Final balance: 0
```

Without the lock, both threads could read the same starting balance and produce an incorrect final balance.

### Deposit Strategy

Deposits do not use pessimistic locking because adding funds cannot cause an overdraft. They still use idempotency keys to prevent duplicate deposits caused by client retries.

### Idempotency Rules

- Provide a UUID v4 idempotency key for every deposit or trade.
- Reuse the same key only when retrying the exact same operation for the same user.
- Keys are scoped per wallet: two different users may use the same key value independently without interference.
- The service checks for an existing transaction matching `(idempotencyKey, userId)` before applying any change.
- If a match is found, the previous `balanceAfter` is returned immediately without touching the wallet.
- The database enforces this with a composite unique constraint on `(wallet_id, idempotency_key)` as a safety net against concurrent retries racing past the service-layer check.

### Status Enforcement

Wallet status is checked at two points for both deposit and trade:

1. **Early check** (unlocked) — fast path to reject obviously inactive wallets before acquiring any locks.
2. **Post-fetch check** (on the wallet returned from the database) — authoritative check that catches a concurrent freeze or close that committed between the early check and the actual mutation.

For trades the post-fetch check runs on the pessimistically locked wallet, ensuring no status change can slip through while the balance mutation is in progress.

### Locking Trade-Offs

| Scenario | Behavior |
| --- | --- |
| One trade | Runs immediately |
| Multiple trades on the same wallet | Serialized by row lock |
| Trades on different wallets | Can run in parallel |
| High contention on one wallet | Requests may wait or time out depending on database settings |

## Database Schema

The schema is initialized from `src/main/resources/schema.sql` on startup (`spring.sql.init.mode=always`). R2DBC does not use Hibernate DDL auto-generation. H2 runs in `MODE=PostgreSQL` so the same DDL file is used for both local and Docker profiles.

Key tables:

```sql
CREATE TABLE wallets (
    wallet_id   VARCHAR(36)    NOT NULL,  -- UUID PK, client-generated
    user_id     VARCHAR(36)    NOT NULL UNIQUE,  -- FK to users, one wallet per user
    balance     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    status      VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    currency    VARCHAR(10)    NOT NULL DEFAULT 'EUR',
    total_deposited DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_traded    DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    created_at  TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT now(),
    last_transaction_at TIMESTAMP,
    description VARCHAR(255),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_wallets PRIMARY KEY (wallet_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE transactions (
    id              VARCHAR(36)    NOT NULL,  -- UUID PK, client-generated
    wallet_id       VARCHAR(36)    NOT NULL,
    type            VARCHAR(10)    NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    timestamp       TIMESTAMP      NOT NULL,
    idempotency_key VARCHAR(36),
    balance_after   DECIMAL(19, 2) NOT NULL,
    CONSTRAINT pk_transactions       PRIMARY KEY (id),
    CONSTRAINT fk_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id),
    CONSTRAINT uq_tx_idempotency_key UNIQUE (wallet_id, idempotency_key)  -- per-wallet scope
);
```

**Note:** `transactions.id` is a client-generated UUID (`VARCHAR(36)`) in this branch, replacing the previous auto-increment `BIGSERIAL`. This is a breaking change for clients that relied on the integer `transactionId` field in API responses (it is now a UUID string).

The idempotency key uniqueness is enforced **per wallet**, not globally. Two different users may use the same key value without conflict.

H2 is used for local development and tests. PostgreSQL is used by the Docker profile.

## Build, Run, and Test

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher, or use `mvnw.cmd` on Windows

### Local Development

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

The service starts at:

```text
http://localhost:8080
```

### Maven Commands

```powershell
# Compile only
.\mvnw.cmd clean compile -DskipTests

# Run all tests
.\mvnw.cmd test

# Build the jar
.\mvnw.cmd clean package

# Run one test class
.\mvnw.cmd test -Dtest=WalletServiceTest
```

If using a globally installed Maven instead of the wrapper:

```bash
mvn clean package
mvn spring-boot:run
mvn test
```

### H2 Console

H2's web console is a servlet and is not available in the WebFlux stack. Use a standalone H2 client (e.g. DBeaver, IntelliJ database tool) and connect with:

```text
JDBC URL: jdbc:h2:mem:walletdb
Username: sa
Password: leave blank
```

## Docker Deployment

This project includes a Dockerfile and Docker Compose setup for PostgreSQL.

### Quick Start

```bash
mvn clean package
docker-compose up
```

Detached mode:

```bash
docker-compose up -d
docker-compose logs -f
```

Stop services:

```bash
docker-compose down
```

Stop and delete PostgreSQL data:

```bash
docker-compose down -v
```

### Services

| Service | Purpose | Port |
| --- | --- | --- |
| `postgres` / `wallet-db` | PostgreSQL 15 database | `5432` |
| `wallet-service` | Spring Boot API | `8080` |

Default database settings:

| Setting | Value |
| --- | --- |
| Database | `walletdb` |
| Username | `walletuser` |
| Password | `walletpass123` |
| JDBC URL inside Docker | `jdbc:postgresql://postgres:5432/walletdb` |

The services communicate through the `wallet-network` bridge network. PostgreSQL data is stored in the `postgres_data` Docker volume.

### Health Checks

```bash
curl http://localhost:8080/actuator/health
docker-compose ps
docker-compose logs -f wallet-service
docker-compose logs -f postgres
```

### Manual Docker Commands

```bash
docker build -t wallet-service:1.0.0 .

docker run -d \
  -p 8080:8080 \
  -e SPRING_R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/walletdb \
  -e SPRING_R2DBC_USERNAME=walletuser \
  -e SPRING_R2DBC_PASSWORD=walletpass123 \
  --name wallet-service \
  wallet-service:1.0.0
```

Connect to PostgreSQL:

```bash
docker-compose exec postgres psql -U walletuser -d walletdb
```

Backup and restore:

```bash
docker-compose exec postgres pg_dump -U walletuser walletdb > backup.sql
docker-compose exec -T postgres psql -U walletuser walletdb < backup.sql
```

### Docker Environment Variables

| Variable | Default |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://postgres:5432/walletdb` |
| `SPRING_R2DBC_USERNAME` | `walletuser` |
| `SPRING_R2DBC_PASSWORD` | `walletpass123` |
| `POSTGRES_DB` | `walletdb` |
| `POSTGRES_USER` | `walletuser` |
| `POSTGRES_PASSWORD` | `walletpass123` |

For production, change the database credentials, use secret management, apply resource limits, configure backups, and review the schema migration strategy.

### Docker Troubleshooting

```bash
# Container status
docker-compose ps

# Application logs
docker-compose logs wallet-service

# Database logs
docker-compose logs postgres

# Rebuild with fresh data
docker-compose down -v
docker-compose up --build
```

If port 8080 is already in use, change the mapping in `docker-compose.yml`, for example from `8080:8080` to `8081:8080`.

## API Request Files

The `api-requests` folder contains request helpers:

- `api-requests/wallet-service.http` for the VS Code REST Client extension.
- `api-requests/wallet-service.ps1` for running requests from PowerShell.

Run PowerShell requests from the project root:

```powershell
.\api-requests\wallet-service.ps1
```

The wallet service should be running at:

```text
http://localhost:8080
```

## Testing Strategy

The suite is organized into three layers:

| Test Class | Scope |
| --- | --- |
| `WalletServiceTest` | Unit tests for service behavior with Mockito mocks (reactive, uses `.block()`) |
| `WalletServiceIntegrationTest` | Service and persistence workflows with H2 R2DBC (reactive, uses `.block()`) |
| `WalletControllerTest` | HTTP/API behavior through `WebTestClient` (Spring WebFlux) |

Coverage focus:

- New wallets start at zero balance.
- Deposits increase balance and lifetime deposited amount.
- Trades decrease balance and lifetime traded amount.
- Trades are rejected when funds are insufficient.
- Invalid amounts and invalid UUID v4 values are rejected at the controller boundary.
- Idempotency keys prevent duplicate processing on retry.
- Idempotency keys are scoped per user — a key used by User A does not affect User B.
- Transaction history is ordered and accurate.
- Wallet metadata fields are included in responses.
- Pessimistic locking protects trade operations.
- Error responses are structured consistently.

## Operational Notes

### Monitoring

Useful production metrics:

- Trade lock wait time
- Lock timeout count
- Trade success and rejection rates
- Average trade latency
- Transaction throughput
- Wallet creation rate
- Database connection pool usage

### Debug Logging

To inspect R2DBC SQL and locking behavior:

```properties
logging.level.org.springframework.r2dbc=DEBUG
logging.level.io.r2dbc.h2=DEBUG
```

For PostgreSQL lock inspection:

```sql
SELECT * FROM pg_locks;

SELECT pid, query, wait_event_type
FROM pg_stat_activity
WHERE wait_event_type IS NOT NULL;
```

### Development Guidance

- Keep wallet balance changes inside transactions.
- Do not remove the pessimistic lock from trade operations unless replacing it with an equivalent safety mechanism.
- Keep request idempotency behavior stable; clients depend on safe retries.
- Use UUID v4 identifiers in tests and request examples.
- Prefer adding focused tests when changing wallet balance, transaction, validation, or locking logic.

## Reactive Architecture Notes

This service runs on a fully non-blocking reactive stack. Key design points:

- **Netty event loop** handles all I/O without blocking threads. A small number of event loop threads can serve thousands of concurrent requests.
- **Virtual Threads** (`spring.threads.virtual.enabled=true`) are enabled as a safety net. They apply to Spring's blocking thread pools (e.g. `boundedElastic`, security filter chains). On the reactive hot path, the primary concurrency mechanism is the Netty event loop.
- **R2DBC** provides non-blocking database access. There is no connection pool blocking — the connection is released back to the pool as soon as the query completes.
- **R2DBC limitation**: JPA relationship annotations (`@ManyToOne`, `@OneToMany`) do not exist. Wallet-User and Wallet-Transaction relationships are stored as plain FK strings and joined via SQL in repository queries.
- **R2DBC `save()` behavior**: When an entity's `@Id` field is non-null, R2DBC issues `UPDATE` (not `INSERT`). Since `walletId`, `userId`, and `transactionId` are client-generated UUIDs set before save, `R2dbcEntityTemplate.insert()` is used explicitly for all new records.
- **Enum mapping**: R2DBC does not have built-in `@Enumerated(STRING)` support. `R2dbcConfig` registers reading and writing converters for `WalletStatus`, `CurrencyType`, and `Transaction.TransactionType`.
- **Schema**: Initialized from `schema.sql` via `spring.sql.init`. No Hibernate DDL auto.
- **Transactions**: `@Transactional` works with reactive `R2dbcTransactionManager` auto-configured by Spring Boot. The full reactive chain must remain within one `Mono` subscription — never call `.block()` inside a `@Transactional` method.

## Enhancement History

The project documentation was consolidated from the previous markdown files:

- `CHANGES_SUMMARY.md`
- `CONCURRENCY.md`
- `DOCKER.md`
- `ENHANCEMENTS.md`
- `ENHANCEMENT_COMPLETE.md`
- `ENTITY_MODEL_VISUAL.md`
- `PESSIMISTIC_LOCKING.md`
- `WALLET_ENHANCEMENT.md`
- `api-requests/README.md`
- `.github/copilot-instructions.md`

Key completed enhancements:

- Added global exception handling with `@ControllerAdvice`.
- Added structured `ErrorResponse` DTO and custom wallet exceptions.
- Added validation annotations to request DTOs.
- Added Docker and Docker Compose support with PostgreSQL.
- Added actuator health checks.
- Added pessimistic locking for trade safety.
- Added wallet audit and reporting fields.
- Added wallet lifecycle status enum with freeze and close endpoints.
- Added currency enum support.
- Added optional wallet description updates.
- Added indexes for status, creation time, and currency.
- Expanded tests across service, integration, and controller layers.
- Fixed `@Validated` on `WalletController` — path variable `@Pattern` and query param `@Min`/`@Max` constraints were previously silently ignored in Spring Boot 3.1.5.
- Added `ConstraintViolationException` handler to `GlobalExceptionHandler` so path variable violations return HTTP 400 instead of 500.
- Scoped idempotency key lookup to `(idempotencyKey, userId)` — previously any user could match another user's transaction key and receive their balance as a success response.
- Changed idempotency key DB constraint from column-level `UNIQUE` to composite `UNIQUE (wallet_id, idempotency_key)` to match the per-wallet semantic.
- Added authoritative status re-check after pessimistic lock acquisition in `trade()` and after wallet fetch in `deposit()` — previously a concurrent freeze could slip through between the unlocked early check and the mutation.
- Fixed `GET /wallets/{userId}` for unknown users — response no longer contains a null `walletId`.

Key completed enhancements on this branch (`feature/NonBlocking_reactive-support-design`):

- Migrated from Spring MVC + JPA to Spring WebFlux + R2DBC for fully non-blocking I/O.
- Upgraded to Java 21 and Spring Boot 3.3.6.
- Enabled Virtual Threads (`spring.threads.virtual.enabled=true`).
- Replaced `@Lock(PESSIMISTIC_WRITE)` JPA annotation with native SQL `SELECT ... FOR UPDATE` in `WalletRepository`.
- Added `R2dbcConfig` with enum converters for `WalletStatus`, `CurrencyType`, `TransactionType`.
- Replaced Hibernate DDL auto-generation with `schema.sql` initialization.
- Switched `transactions.id` from `BIGSERIAL` to UUID (`VARCHAR(36)`) — **breaking change** for `transactionId` in API responses.
- Removed JPA relationship fields from entities; denormalized to FK strings.
- Migrated `SecurityConfig` to `ServerHttpSecurity` / `SecurityWebFilterChain`.
- Migrated `GlobalExceptionHandler` to use `ServerWebExchange` for path extraction.
- Migrated all tests: `MockMvc` → `WebTestClient`, `@Transactional` rollback → reactive `deleteAll().block()` cleanup.

## Future Enhancements

- Add JWT-based authentication and authorization so callers can only access their own wallet.
- Add rate limiting per user on deposit and trade endpoints.
- Catch `DataIntegrityViolationException` on idempotency key insert and replay the existing transaction — eliminates the check-then-act race window under concurrent retries from multiple nodes.
- Replace Hibernate `ddl-auto` with Flyway migrations for versioned, auditable schema changes.
- Add Testcontainers-based integration tests against real PostgreSQL to close the H2 dialect gap.
- Add reporting endpoints for lifetime deposits, lifetime traded amount, and activity windows.
- Add multi-currency workflows and exchange-rate handling.
- Add webhook notifications for significant operations (large deposits, trade failures, wallet status changes).
- Add distributed tracing and richer operational metrics (trade latency, lock wait time, error rates).

## License

MIT
