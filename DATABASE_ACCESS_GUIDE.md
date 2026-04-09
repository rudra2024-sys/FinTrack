# How to Access & View FinTrack Database

**Database:** PostgreSQL 16  
**Container:** fintrack-postgres  
**Port:** 5432  
**Database Name:** fintrack_db  
**Username:** postgres  
**Password:** postgres  

---

## 🎯 QUICK ACCESS OPTIONS

### Option 1: Using Command Line (Fastest) 🚀

```bash
# Connect to database and list tables
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db

# Once connected, useful commands:
\dt                    # List all tables
\d table_name         # Show table schema
SELECT * FROM users;  # View data
\q                    # Quit
```

---

## 📊 VIEW DATABASE DATA

### View Users Table

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c \
  "SELECT id, email, full_name, currency, created_at FROM users;"
```

**Sample Output:**
```
 id |              email              |    full_name    | currency |         created_at
----+---------------------------------+-----------------+----------+----------------------------
  1 | test_user@fintrack.local        | Test User       | USD      | 2026-04-08 15:33:56.951121
  2 | another_user@fintrack.local     | Another User    | USD      | 2026-04-08 15:38:47.003828
  3 | prod_test@fintrack.local        | Prod Test User  | USD      | 2026-04-08 15:45:52.101946
```

### View Accounts Table

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c \
  "SELECT id, user_id, name, type, balance, currency FROM accounts;"
```

**Sample Output:**
```
 id | user_id |     name      |   type    |   balance   | currency
----+---------+---------------+-----------+-------------+----------
  1 |       1 | Checking      | CHECKING  |  10500.50   | USD
  2 |       1 | Savings       | SAVINGS   |  25000.00   | USD
  3 |       2 | Credit Card   | CREDIT_CARD |  5000.00   | USD
```

### View Transactions Table

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c \
  "SELECT id, account_id, amount, category, description, type FROM transactions LIMIT 10;"
```

### View All Tables and Counts

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c \
  "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;" && \
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c \
  "SELECT 'users' as table_name, COUNT(*) FROM users UNION ALL \
   SELECT 'accounts', COUNT(*) FROM accounts UNION ALL \
   SELECT 'transactions', COUNT(*) FROM transactions UNION ALL \
   SELECT 'budgets', COUNT(*) FROM budgets UNION ALL \
   SELECT 'categories', COUNT(*) FROM categories;"
```

---

## 📋 DATABASE SCHEMA

### Tables in Database

| Table | Purpose | Records |
|-------|---------|---------|
| **users** | User accounts | 5+ |
| **accounts** | Bank/financial accounts | 3+ |
| **transactions** | Financial transactions | Growing |
| **categories** | Transaction categories | System |
| **budgets** | User budgets | Optional |
| **financial_goals** | Savings goals | Optional |
| **statements** | Bank statements | Optional |
| **recurring_transactions** | Recurring bills | Optional |
| **savings_goals** | Savings targets | Optional |
| **savings_contributions** | Savings tracking | Optional |
| **refresh_tokens** | JWT refresh tokens | System |

---

## 🔧 Option 2: Using pgAdmin Web Interface

### Setup pgAdmin (Optional)

Add to `docker-compose.yml`:

```yaml
pgadmin:
  image: dpage/pgadmin4
  container_name: fintrack-pgadmin
  environment:
    PGADMIN_DEFAULT_EMAIL: admin@fintrack.local
    PGADMIN_DEFAULT_PASSWORD: admin
  ports:
    - "5050:80"
  depends_on:
    - postgres
  restart: unless-stopped
```

Then:
```bash
docker-compose up -d pgadmin
```

Access: http://localhost:5050
- Username: admin@fintrack.local
- Password: admin

Add server:
- Hostname: postgres
- Port: 5432
- Username: postgres
- Password: postgres
- Database: fintrack_db

---

## 💻 Option 3: Using DBeaver (Desktop)

### Download & Install

1. Download: https://dbeaver.io/download/
2. Install and open DBeaver
3. Create new connection:
   - Database: PostgreSQL
   - Server: localhost
   - Port: 5432
   - Database: fintrack_db
   - Username: postgres
   - Password: postgres
4. Click "Test Connection"
5. View all tables and data through GUI

**Benefits:**
- Visual database browser
- Easy queries
- Schema visualization
- Data export

---

## 📱 Option 4: Using API Endpoints

View database data through the REST API:

### Get All Users

```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/users
```

### Get User Accounts

```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/accounts
```

### Get Transactions

```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/transactions
```

### Get Analytics/Dashboard

```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/analytics/dashboard
```

---

## 🔍 USEFUL SQL QUERIES

### Get Count of Each Table

```sql
SELECT 'Users' as type, COUNT(*) as count FROM users
UNION ALL
SELECT 'Accounts', COUNT(*) FROM accounts
UNION ALL
SELECT 'Transactions', COUNT(*) FROM transactions
UNION ALL
SELECT 'Budgets', COUNT(*) FROM budgets
UNION ALL
SELECT 'Categories', COUNT(*) FROM categories;
```

### Show All User Data

```sql
SELECT 
  id, 
  email, 
  full_name, 
  currency, 
  created_at, 
  updated_at 
FROM users 
ORDER BY created_at DESC;
```

### Show User Accounts with Balance

```sql
SELECT 
  a.id,
  u.email,
  a.name,
  a.type,
  a.balance,
  a.currency,
  a.created_at
FROM accounts a
JOIN users u ON a.user_id = u.id
ORDER BY a.user_id, a.created_at;
```

### Show Recent Transactions

```sql
SELECT 
  t.id,
  a.name,
  t.amount,
  t.category,
  t.description,
  t.type,
  t.transaction_date
FROM transactions t
JOIN accounts a ON t.account_id = a.id
ORDER BY t.transaction_date DESC
LIMIT 20;
```

### Get Total Balance Per User

```sql
SELECT 
  u.email,
  u.full_name,
  a.currency,
  SUM(a.balance) as total_balance
FROM users u
LEFT JOIN accounts a ON u.id = a.user_id
GROUP BY u.id, u.email, u.full_name, a.currency
ORDER BY u.created_at;
```

### Show Database Size

```sql
SELECT 
  datname,
  pg_size_pretty(pg_database_size(datname)) AS size
FROM pg_database
WHERE datname = 'fintrack_db';
```

---

## 🛠 INTERACTIVE DATABASE ACCESS

### Full Interactive Mode

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db
```

Then type SQL commands:

```sql
-- List all users
SELECT * FROM users;

-- Show table structure
\d users

-- Show databases
\l

-- Show tables with sizes
\dt+ 

-- Exit
\q
```

### Run Single Command

```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c "SELECT COUNT(*) FROM users;"
```

---

## 📊 CURRENT DATABASE STATE

### Users (Test Data)

```
ID | Email | Full Name | Currency | Created
1  | test_whcqwxkr@fintrack.local | Integration Test User | USD | 2026-04-08
2  | debug_test@fintrack.local | Debug User | USD | 2026-04-08
3  | prod_test_1775663149@fintrack.local | Prod Test User | USD | 2026-04-08
4  | prod_test_1775663542@fintrack.local | Prod Test User | USD | 2026-04-08
5  | prod_test_1775663728@fintrack.local | Prod Test User | USD | 2026-04-08
```

### Schema Structure (Accounts Table Example)

```
Column      | Type                      | Nullable | Default
-----------+---------------------------+----------+---------
id          | bigint                    | NO       | sequence
name        | varchar(255)              | NO       | 
type        | varchar(255)              | NO       | CHECK
balance     | numeric(15,2)             | NO       | 
currency    | varchar(255)              | NO       | 
user_id     | bigint                    | NO       | FK
created_at  | timestamp with timezone   | YES      | 
updated_at  | timestamp with timezone   | YES      | 
```

---

## 🔐 DATABASE CREDENTIALS

```
Host:     localhost
Port:     5432
Database: fintrack_db
Username: postgres
Password: postgres
```

**Connection String:**
```
postgresql://postgres:postgres@localhost:5432/fintrack_db
```

---

## 🆘 TROUBLESHOOTING

### Can't Connect to Database?

```bash
# Check if PostgreSQL container is running
docker ps | grep postgres

# Check logs
docker logs fintrack-postgres

# Restart PostgreSQL
docker restart fintrack-postgres
```

### Permission Denied?

```bash
# Make sure you're using correct credentials
# Username: postgres (not fintrack)
# Password: postgres
```

### Port 5432 Already in Use?

```bash
# Check what's using port 5432
netstat -ano | findstr 5432

# Or change port in docker-compose.yml
# "5433:5432"  (change first number)
```

---

## 📈 NEXT STEPS

1. **View Data**: Use Option 1 (CLI) - fastest way
2. **Browse GUI**: Use Option 3 (DBeaver) - most visual
3. **Automate**: Use API endpoints - programmatic access
4. **Manage**: Use pgAdmin - web interface management

---

## 🎯 QUICK START COMMAND

**View Recent Users:**
```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c "SELECT id, email, full_name FROM users ORDER BY created_at DESC LIMIT 10;"
```

**View User Accounts:**
```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c "SELECT name, type, balance, currency FROM accounts WHERE user_id = 1;"
```

**View All Transactions:**
```bash
docker exec -it fintrack-postgres psql -U postgres -d fintrack_db -c "SELECT * FROM transactions LIMIT 20;"
```

---

**Database is live and populated!** Choose your preferred access method above.
