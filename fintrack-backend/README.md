# FinTrack Backend — Spring Boot REST API

A secure, production-ready financial management backend built with Spring Boot 3.2, PostgreSQL, and JWT authentication.

---

## Tech Stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Framework    | Spring Boot 3.2 (Java 17)           |
| Security     | Spring Security + JWT (jjwt 0.12)  |
| Database     | PostgreSQL 16                       |
| Migrations   | Flyway                              |
| ORM          | Spring Data JPA / Hibernate         |
| Docs         | SpringDoc OpenAPI (Swagger UI)      |
| Build        | Maven 3.9                           |
| Container    | Docker + Docker Compose             |

---

## Project Structure

```
src/main/java/com/fintrack/
├── FinTrackApplication.java
├── config/
│   ├── SecurityConfig.java        # CORS, JWT filter chain, password encoder
│   └── JpaConfig.java             # Auditing
├── controller/
│   ├── AuthController.java
│   ├── AccountController.java
│   ├── CategoryController.java
│   ├── TransactionController.java
│   ├── BudgetController.java
│   ├── SavingsGoalController.java
│   ├── RecurringTransactionController.java
│   └── AnalyticsController.java
├── service/
│   ├── AuthService.java
│   ├── TransactionService.java
│   ├── BudgetService.java
│   ├── SavingsGoalService.java
│   ├── RecurringTransactionService.java  # includes @Scheduled job
│   └── AnalyticsService.java
├── entity/                        # JPA entities
├── dto/                           # Request / Response records
├── repository/                    # Spring Data JPA repos
├── security/
│   ├── JwtUtils.java
│   ├── JwtAuthFilter.java
│   ├── UserDetailsServiceImpl.java
│   └── SecurityUtils.java
└── exception/
    ├── ApiException.java
    └── GlobalExceptionHandler.java
```

---

## Quick Start

### Option 1 — Docker Compose (recommended)

```bash
docker compose up -d
```

API available at: `http://localhost:8080/api`
Swagger UI: `http://localhost:8080/api/swagger-ui.html`

### Option 2 — Local Development

**Prerequisites:** Java 17+, Maven 3.9+, PostgreSQL 16 running locally

```bash
# 1. Create database
psql -U postgres -c "CREATE DATABASE fintrack_db;"

# 2. Set environment variables (or edit application.yml)
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-256-bit-secret

# 3. Run
./mvnw spring-boot:run
```

---

## Environment Variables

| Variable       | Default                          | Description                   |
|----------------|----------------------------------|-------------------------------|
| `DB_USERNAME`  | `postgres`                       | PostgreSQL username            |
| `DB_PASSWORD`  | `postgres`                       | PostgreSQL password            |
| `JWT_SECRET`   | (dev default in yml)             | 256-bit Base64 secret          |
| `CORS_ORIGINS` | `http://localhost:5173,...`      | Comma-separated allowed origins|

---

## API Reference

All endpoints are prefixed with `/api`. Authenticated endpoints require:
```
Authorization: Bearer <access_token>
```

### Auth — `/api/auth`

| Method | Path        | Auth | Description              |
|--------|-------------|------|--------------------------|
| POST   | `/register` | ❌   | Register new user        |
| POST   | `/login`    | ❌   | Login, get tokens        |
| POST   | `/refresh`  | ❌   | Refresh access token     |
| POST   | `/logout`   | ✅   | Invalidate refresh token |

**Register / Login Response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid-string",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "Jane Doe",
    "currency": "INR"
  }
}
```

---

### Accounts — `/api/accounts`

| Method | Path    | Description               |
|--------|---------|---------------------------|
| GET    | `/`     | List all active accounts  |
| POST   | `/`     | Create account            |
| GET    | `/{id}` | Get account               |
| PATCH  | `/{id}` | Update account            |
| DELETE | `/{id}` | Deactivate account        |

**Account types:** `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `WALLET`, `INVESTMENT`, `CASH`

---

### Transactions — `/api/transactions`

| Method | Path    | Description                      |
|--------|---------|----------------------------------|
| GET    | `/`     | List with filters (paginated)    |
| POST   | `/`     | Create transaction               |
| GET    | `/{id}` | Get by ID                        |
| PATCH  | `/{id}` | Update transaction               |
| DELETE | `/{id}` | Delete transaction               |

**Query params for GET /:**
- `type` — `INCOME` | `EXPENSE` | `TRANSFER`
- `categoryId`, `accountId`
- `startDate`, `endDate` — ISO format `YYYY-MM-DD`
- `search` — searches description & merchant
- `page` (default 0), `size` (default 20)

---

### Categories — `/api/categories`

| Method | Path    | Description                             |
|--------|---------|-----------------------------------------|
| GET    | `/`     | List all (system + user's custom)       |
| POST   | `/`     | Create custom category                  |
| DELETE | `/{id}` | Delete custom category (own only)       |

---

### Budgets — `/api/budgets`

| Method | Path    | Description                            |
|--------|---------|----------------------------------------|
| GET    | `/`     | List active budgets with spend data    |
| POST   | `/`     | Create budget                          |
| GET    | `/{id}` | Get budget with current spend          |
| PATCH  | `/{id}` | Update budget                          |
| DELETE | `/{id}` | Delete budget                          |

**Budget periods:** `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`

Budget response includes real-time `spent`, `remaining`, `percentUsed`, and `alertTriggered` fields.

---

### Savings Goals — `/api/savings-goals`

| Method | Path               | Description                    |
|--------|--------------------|--------------------------------|
| GET    | `/`                | List all goals                 |
| POST   | `/`                | Create goal                    |
| GET    | `/{id}`            | Get goal with progress         |
| PATCH  | `/{id}`            | Update goal                    |
| POST   | `/{id}/contribute` | Add a contribution             |
| DELETE | `/{id}`            | Delete goal                    |

Goal response includes `percentComplete`, `remaining`, and `monthsToGoal` (projected).

---

### Recurring Transactions — `/api/recurring-transactions`

| Method | Path    | Description                              |
|--------|---------|------------------------------------------|
| GET    | `/`     | List active recurring transactions       |
| POST   | `/`     | Set up recurring income/expense          |
| PATCH  | `/{id}` | Update recurring transaction             |
| DELETE | `/{id}` | Delete recurring transaction             |

**Frequencies:** `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`

The scheduler runs daily at midnight (`0 0 0 * * *`) and auto-creates transactions for all due entries.

---

### Analytics — `/api/analytics`

| Method | Path                  | Description                               |
|--------|-----------------------|-------------------------------------------|
| GET    | `/dashboard`          | Full dashboard (income, expenses, charts) |
| GET    | `/monthly-trend`      | 12-month income vs expense trend          |
| GET    | `/category-breakdown` | Spending by category (`startDate`, `endDate` required) |

---

## Error Format

All errors return a consistent shape:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2025-03-15T10:30:00Z",
  "fieldErrors": {
    "amount": "must be greater than 0",
    "transactionDate": "must not be null"
  }
}
```

---

## Database Schema Overview

```
users
 └── accounts (1:N)
 └── categories (1:N, custom only; system categories have user_id = NULL)
 └── transactions (1:N) → account, category
 └── recurring_transactions (1:N) → account, category
 └── budgets (1:N) → category
 └── savings_goals (1:N) → account
      └── savings_contributions (1:N)
 └── refresh_tokens (1:N)
```

---

## Phase 2 Roadmap (Next Steps)

- [ ] PDF/CSV report export (Apache POI / iText)
- [ ] Email notifications for budget alerts (Spring Mail)
- [ ] Redis caching for dashboard aggregates
- [ ] Multi-currency support with FX rates
- [ ] Frontend: React + TypeScript + Recharts dashboard
