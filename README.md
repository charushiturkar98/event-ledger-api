# Event Ledger API

A financial transaction event ledger built with Spring Boot. Handles two hard problems in distributed financial systems:

- **Out-of-order events** — events are always stored and returned in business-time order, regardless of when they arrived
- **Duplicate delivery** — submitting the same `eventId` twice is safe; the ledger and balance are never corrupted

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Docker + Compose | (optional, for containerised run) |

Verify your setup:

```bash
java -version
mvn -version
```

---

## Quick Start

### Option A — Maven (recommended for development)

```bash
# 1. Clone the repo
git clone https://github.com/your-username/event-ledger-api.git
cd event-ledger-api

# 2. Start the application
mvn spring-boot:run
```

The API is now running at **http://localhost:8080**

### Option B — Docker Compose

```bash
docker compose up --build
```

---

## Running the Tests

```bash
mvn test
```

This runs all three test classes:

| Class | What it tests |
|-------|--------------|
| `EventLedgerControllerTest` | Full integration — all 4 endpoints, idempotency, out-of-order, balance, validation, pagination |
| `EventLedgerServiceTest` | Service unit tests with Mockito — fast, no Spring context |
| `LedgerEventRepositoryTest` | Repository slice tests — custom JPQL queries against H2 |
| `ConcurrencyTest` | 20 threads fire the same `eventId` simultaneously — verifies exactly one event stored |

---

## API Endpoints

### POST /events — Submit a transaction event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
  }'
```

| Scenario | HTTP Status |
|----------|-------------|
| New event accepted | `201 Created` |
| Duplicate `eventId` | `200 OK` (original event returned) |
| Validation failure | `400 Bad Request` |

---

### GET /events/{id} — Retrieve a single event

```bash
curl http://localhost:8080/events/evt-001
```

Returns `404 Not Found` if the event does not exist.

---

### GET /events?account={accountId} — List events for an account

```bash
# All events, ordered by eventTimestamp ASC
curl "http://localhost:8080/events?account=acct-123"

# Paginated (page 0, 10 per page)
curl "http://localhost:8080/events?account=acct-123&page=0&size=10"
```

Events are **always** returned in `eventTimestamp` order (business time), not arrival time.

---

### GET /accounts/{accountId}/balance — Get net balance

```bash
curl http://localhost:8080/accounts/acct-123/balance
```

```json
{
  "accountId": "acct-123",
  "balance": 850.00,
  "currency": "USD"
}
```

`balance = SUM(CREDIT amounts) − SUM(DEBIT amounts)`

---

## API Documentation (Swagger UI)

With the application running, open:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec:

```
http://localhost:8080/api-docs
```

---

## H2 Console

For direct database inspection during development:

```
http://localhost:8080/h2-console
```

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:ledgerdb` |
| Username | `sa` |
| Password | *(empty)* |

---

## Design Decisions

### Idempotency

`eventId` is the **primary key** of the `ledger_events` table. This means:

1. The database enforces uniqueness at the storage layer — no application-level lock required
2. If two threads race to insert the same `eventId`, one wins and the other gets a `DataIntegrityViolationException` — the PK constraint is the final safety net
3. The service performs an explicit pre-check (`findById`) before saving, returning the stored event cleanly to the caller. The PK constraint acts as the race-condition guard

### Out-of-Order Handling

Two timestamps are stored on every event:

| Field | Meaning | Used for |
|-------|---------|----------|
| `eventTimestamp` | When the event occurred upstream (sent by caller) | Ordering, balance queries |
| `receivedAt` | When this API received the event (set by server) | Audit trail only |

Ordering always uses `eventTimestamp`. A late-arriving event with an earlier timestamp slots into the correct chronological position automatically — no re-sorting or post-processing needed.

### Balance Computation

Balances are computed on the fly from the full ledger:

```
balance = SUM(CREDIT) − SUM(DEBIT)
```

This is intentional. Addition is commutative, so arrival order is irrelevant. There is no cached balance field to keep in sync, no risk of it going stale, and no balance-update logic that could be corrupted by duplicates.

### Amounts as `BigDecimal`

All amounts are stored as `DECIMAL(19,4)` — never `float` or `double`. Floating-point arithmetic on money is a well-known source of bugs (e.g. `0.1 + 0.2 ≠ 0.3` in IEEE 754). `BigDecimal` gives exact decimal arithmetic.

---

## Project Structure

```
src/
├── main/java/com/ledger/
│   ├── EventLedgerApplication.java   # Entry point
│   ├── config/
│   │   └── OpenApiConfig.java        # Swagger setup
│   ├── controller/
│   │   └── EventLedgerController.java
│   ├── dto/
│   │   ├── EventRequest.java         # Inbound payload + validation
│   │   ├── EventResponse.java        # Outbound event representation
│   │   ├── BalanceResponse.java
│   │   └── PagedEventResponse.java
│   ├── exception/
│   │   ├── EventNotFoundException.java
│   │   ├── DuplicateEventException.java
│   │   └── GlobalExceptionHandler.java
│   ├── model/
│   │   ├── LedgerEvent.java          # JPA entity (immutable after insert)
│   │   └── EventType.java            # CREDIT / DEBIT enum
│   ├── repository/
│   │   └── LedgerEventRepository.java
│   └── service/
│       └── EventLedgerService.java   # Core business logic
└── test/java/com/ledger/
    ├── controller/
    │   ├── EventLedgerControllerTest.java  # Integration tests
    │   └── ConcurrencyTest.java            # Race-condition tests
    ├── service/
    │   └── EventLedgerServiceTest.java     # Unit tests (Mockito)
    └── repository/
        └── LedgerEventRepositoryTest.java  # Slice tests (@DataJpaTest)
```
