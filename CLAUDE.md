# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

```powershell
# Build
.\mvnw.cmd clean package

# Run locally (H2 in-memory database)
.\mvnw.cmd spring-boot:run

# Compile only
.\mvnw.cmd clean compile -DskipTests
```

## Tests

```powershell
# All tests
.\mvnw.cmd test

# Single test class
.\mvnw.cmd test -Dtest=WalletServiceTest
```

Three test layers: `WalletServiceTest` (unit, mocked), `WalletServiceIntegrationTest` (service + real H2 DB), `WalletControllerTest` (Spring MVC layer).

## Docker (PostgreSQL)

```bash
mvn clean package
docker-compose up
```

Uses the `docker` Spring profile (`application-docker.properties`), which switches from H2 to PostgreSQL.

## Architecture

Standard Spring Boot layered architecture: `controller` → `service` → `repository` → `entity`.

**Key design decisions:**

- **Idempotency**: Both deposit and trade check `TransactionRepository.findByIdempotencyKey()` before applying changes. If a matching transaction exists, the previous `balanceAfter` is returned immediately. Idempotency keys must be UUID v4.
- **Pessimistic locking for trades**: `WalletRepository.findByUserIdForUpdate()` acquires a `PESSIMISTIC_WRITE` lock for the duration of the transaction, preventing concurrent overdrafts. Deposits do not use this lock (adding funds cannot cause an overdraft).
- **Wallet auto-creation**: All write endpoints (`deposit`, `trade`, `PATCH`) create the wallet if it does not exist. `GET /wallets/{userId}` returns a zeroed-out wallet-shaped response for unknown users without persisting anything.
- **`BigDecimal` for money**: All balance and amount fields use `BigDecimal` throughout — entity, DTOs, and service logic.
- **Security toggle**: Spring Security is included but disabled by default. Set `wallet.security.enabled=true` in `application.properties` to require HTTP Basic auth. The two `SecurityFilterChain` beans in `SecurityConfig` are `@ConditionalOnProperty`-gated.

**Entities:**
- `Wallet` — primary key is `userId` (UUID v4 string). Carries balance, `totalDeposited`, `totalTraded`, `lastTransactionAt`, `status` (`WalletStatus` enum), `currency` (`CurrencyType` enum), `description`, and a `version` field for optimistic locking (used alongside pessimistic locking on trades).
- `Transaction` — belongs to a `Wallet`, records type (`DEPOSIT`/`TRADE`), amount, timestamp, idempotency key, and `balanceAfter`.

**Error handling:** `GlobalExceptionHandler` (`@ControllerAdvice`) converts custom exceptions (`WalletNotFoundException`, `InsufficientBalanceException`, `InvalidAmountException`) and validation errors into structured `ErrorResponse` JSON.

## Local Development Notes

- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:walletdb`, user: `sa`, no password).
- Actuator health: `http://localhost:8080/actuator/health`.
- API examples are in `api-requests/wallet-service.http` (VS Code REST Client) and `api-requests/wallet-service.ps1` (PowerShell).
- To inspect SQL: set `logging.level.org.hibernate.SQL=DEBUG` in `application.properties`.

## Critical Constraints

- Do not remove the pessimistic lock (`findByUserIdForUpdate`) from trade operations — it is the mechanism that prevents concurrent overdrafts.
- All wallet balance mutations must occur inside a `@Transactional` boundary.
- Idempotency key behavior must remain stable; clients rely on safe retries.
