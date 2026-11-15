# 🏦 FinanzDaroca · Full-Stack Wealth Management System

English | [Español](README.es.md)

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)
![Python](https://img.shields.io/badge/Python-3.11+-3776AB.svg?logo=python&logoColor=white)
![Textual](https://img.shields.io/badge/Textual-8.x-FFD43B.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)
![Security](https://img.shields.io/badge/Security-Spring%20Security-success.svg)

> **Status: beta / learning project.** Built to explore how an asset-management backend looks in Spring Boot; the REST API is the core and the terminal client is an optional consumer.

Wealth and investment management system for independent financial advisors. It combines an **accounting and compliance engine in Java** with a **professional terminal client in Python** that mimics the workflow of a **Bloomberg Terminal**: NAV queries, trade execution, client CRM and market management, all governed by session and roles.

---

## 🚀 Architecture and Tech Stack

The project follows a **layered architecture** with a clear separation between presentation, API, domain and persistence.

### Backend (Java / Spring Boot)

| Layer | Technology |
|------|------------|
| **Runtime** | Java 21, Spring Boot 4.0.6 |
| **API** | REST (`spring-boot-starter-webmvc`) |
| **Persistence** | Spring Data JPA / Hibernate, PostgreSQL 15 |
| **Security** | Spring Security — stateful authentication with a `JSESSIONID` cookie, BCrypt passwords, RBAC and per-client (row-level) access control |
| **Cache** | Spring Cache (`@EnableCaching`) — in-memory cache of Yahoo Finance quotes |
| **Integration** | Native `HttpClient` → Yahoo Finance Chart API |
| **Seed data** | `schema.sql` + `data.sql` (idempotent on startup) |
| **Configuration** | Variables in `.env` imported via `spring.config.import` |
| **Testing** | JUnit 5, Mockito, AssertJ |

### Frontend (Python / Textual TUI)

| Component | Description |
|------------|-------------|
| **Client** | `terminal-client/` — [Textual](https://textual.textualize.io/) application (TUI) |
| **Style** | **Bloomberg**-style terminal (dark palette, dense tables, keyboard shortcuts) |
| **Communication** | `api_client.py` — HTTP session with `requests`, `JSESSIONID` cookie after login |
| **Adaptability** | Tabs and actions depend on the role returned by the API (`ADMIN` vs `ASESOR`, i.e. advisor) |

```
┌─────────────────────┐     JSESSIONID      ┌──────────────────────────┐
│  Textual TUI        │ ◄─────────────────► │  Spring Boot REST API    │
│  (terminal-client)  │     JSON / REST     │  (sistema-financiero)    │
└─────────────────────┘                     └────────────┬─────────────┘
                                                         │
                                              ┌──────────▼──────────┐
                                              │  PostgreSQL (Docker) │
                                              └─────────────────────┘
```

---

## ✨ Key Features (Business Logic)

1. **Real-time NAV (Net Asset Value) engine**  
   Wealth consolidation: multi-currency cash (EUR, USD, GBP, GBp, etc.), valuation of live assets, FX rates via Yahoo and a breakdown of **cash wallets** (`posicionesEfectivo`) in the TUI.

2. **Multiple asset classes and transaction types**  
   - **Stocks:** `COMPRA` / `VENTA` (buy / sell)  
   - **Asset income:** `DIVIDENDO`, `ALQUILER` (dividend, rent — real estate or other assets linked in the catalogue)  
   - **Treasury:** multi-currency `DEPOSITO` / `RETIRO` (deposit / withdrawal)  

3. **Automatic interest-bearing account**  
   Pro-rated compound interest (2% per year) on uninvested cash.

4. **MiFID II compliance (alerts)**  
   Detects mismatches between a `CONSERVADOR` (conservative) profile and volatile, non-exempt assets.

5. **Quote cache (anti rate-limit)**  
   `@Cacheable("cotizaciones")` on `YahooFinanceChartClient` reduces repeated calls to Yahoo Finance and mitigates IP blocking when calculating NAV or syncing prices.

6. **Scheduled market synchronisation**  
   A `@Scheduled` task updates asset prices in the database from Yahoo Finance.

7. **Session and row-level security (GDPR / RBAC)**  
   - **ADMIN:** global access (clients, users, assets, transactions).  
   - **ASESOR (advisor):** only assigned clients; the TUI **hides or disables** components (e.g. the *Usuarios & Staff* tab, deleting assets) depending on the authenticated role.

8. **Trading Desk with editable history**  
   Create, edit and delete transactions from the TUI (fixing operational mistakes with automatic NAV recalculation).

---

## ⚙️ Prerequisites

| Tool | Recommended version |
|-------------|---------------------|
| **Docker** + Docker Compose | Recent |
| **JDK** | 21+ |
| **Maven** | Included (`./mvnw`) |
| **Python** | 3.11+ |
| **jq** (optional) | To pretty-print `curl` responses |

---

## 📦 Installation and Startup

### Step 0 · Environment variables (required)

Before starting Docker or Spring, create the **`.env` file in the repository root**:

```bash
# .env (project root — DO NOT commit to Git)
DB_PASSWORD=secret123
```

- It is listed in `.gitignore`.
- **Docker Compose** uses it via `env_file` and the interpolation `${DB_PASSWORD:-secret123}`.
- **Spring Boot** loads it with `spring.config.import=optional:file:.env[.properties]` and `spring.datasource.password=${DB_PASSWORD:secret123}`.

> **`password authentication failed` error:** the `.postgres-data` volume may have been created with a different password.  
> - **Without deleting data:**  
>   `docker exec finanzas-db psql -U admin_daroca -d finanz_daroca -c "ALTER USER admin_daroca WITH PASSWORD 'secret123';"`  
> - **Clean database:**  
>   `docker compose down -v && docker compose up -d`

### Step 1 · Database (Docker)

```bash
docker compose up -d
```

PostgreSQL is exposed on `localhost:5432` (DB: `finanz_daroca`, user: `admin_daroca`).

To start from scratch (deletes local data):

```bash
docker compose down -v
docker compose up -d
```

### Step 2 · Backend (Spring Boot)

From the project root:

```bash
./mvnw clean spring-boot:run
```

API available at **`http://localhost:8080`**.

### Step 3 · TUI frontend (Python / Textual)

In another terminal:

```bash
cd terminal-client
python -m venv venv
source venv/bin/activate          # Linux / macOS
# venv\Scripts\activate           # Windows (PowerShell / CMD)
pip install -r requirements.txt
python app.py
```

By default the TUI connects to `http://localhost:8080`. Log in with one of the test users (table below).

#### Main TUI tabs

| Tab | Purpose |
|---------|---------|
| **NAV & Portfolio** | Wealth report, asset positions and cash wallets |
| **Trading Desk** | Transaction history + create/edit/delete transactions |
| **CRM Clientes** | Client management and advisor assignment |
| **Market Activos** | Ticker and price catalogue |
| **Usuarios & Staff** | **ADMIN** only — system user management |

Useful shortcuts: **`q`** quit · **`r`** reload active tab.

---

## ⚖️ System Scope: API vs TUI Client

FinanzDaroca takes an **API-first** approach: the **Spring Boot** backend is the **complete engine and the source of truth** for the domain. It holds 100% of the business logic — **MiFID II** checks, multi-currency **NAV** calculation, the transaction ledger, role-based security and the **CRON** job that syncs with **Yahoo Finance** (with an anti rate-limit cache).

The **Textual TUI** client (`terminal-client/`) is an **optional operational consumer** that mimics a Bloomberg terminal: it covers the daily workflow of an advisor or administrator (entering transactions, viewing portfolios, CRM, asset catalogue) and exposes roughly **80% of the REST endpoints** interactively. Anything not available in the terminal remains accessible via the **plain API** (`curl`, scripts or future web/mobile frontends).

### Capability comparison

| Capability / Resource | Backend API (Spring Boot) | TUI (Python client) |
|---------------------|---------------------------|----------------------|
| Authentication (`JSESSIONID`) | ✅ Supported | ✅ Supported |
| User (staff) management | ✅ Full CRUD (ADMIN only) | ✅ Create and delete (ADMIN only) |
| Client management (CRM) | ✅ Full CRUD + filters by advisor | ✅ Create, edit and delete |
| Asset catalogue | ✅ Full CRUD | ✅ Create and delete (edit via API) |
| Transactions and ledger | ✅ Global (ADMIN) and per client (ASESOR) | ✅ Per-client history |
| NAV and portfolio calculation | ✅ Dynamic multi-currency (`?divisa=`) | ✅ Fixed to base currency (EUR) |
| Interest and accruals | ✅ Breakdown of generated interest | ❌ Grouped into total balance |
| Yahoo Finance sync | ✅ Automatic, asynchronous (`@Scheduled`) | ✅ Transparent to the client |

### TUI operational limitations

The following operations are **reserved for the REST API** (external integrations, automation or `curl`). The TUI does not replace them; the official contract of the system is always the API:

- **Global transaction list** (`GET /api/transacciones`) — **ADMIN** only; the terminal only shows per-client history.
- **Manually changing the date** of a transaction on create or edit — the TUI records the server date (current by default); the API accepts `fecha` in the `PUT`/`POST` body.
- **Editing (`PUT`) existing financial assets** (ticker, name, market price, currency) — **ADMIN** only in the backend; in Market the TUI lets you create and delete, not modify.
- **Changing the base currency** used to consolidate and display NAV (`?divisa=USD`, `GBP`, etc.) — supported dynamically by the API; the TUI consolidates and shows the report in **EUR**.

> **Integrators:** any new channel (web, mobile, regulatory reporting) must be built against the endpoints documented in this guide, without duplicating business rules outside the backend.

---

## 📖 Usage Guide

### Test users

Demo users seeded by `data.sql` for **local testing only** (shared password: **`secret123`**). Change them before exposing the API anywhere.

| User | Role | Typical scope |
|---------|-----|----------------|
| `admin_jefe` | ADMIN | All clients, users and assets |
| `asesor_aleix` | ASESOR | Only clients assigned to Aleix |
| `asesor_maria` | ASESOR | Only clients assigned to María |

### Recommended interaction: TUI terminal

1. Start the backend + database.  
2. Run `python app.py` in `terminal-client/`.  
3. Log in → explore NAV, record trades in *Trading Desk*, manage clients in *CRM*.  
4. The role determines which tabs and buttons are available (e.g. *Staff* is admin-only).

### Alternative: REST API (cURL)

The API remains the official contract of the system. CSRF is disabled to make integrations easier; authentication uses a **session cookie** after `POST /api/login`.

<details>
<summary>Show plain REST API examples with cURL</summary>

#### Login (get `JSESSIONID`)

```bash
curl -i -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username": "asesor_aleix", "password": "secret123"}' \
  -c cookies.txt
```

#### Cash deposit

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "tipoOperacion": "DEPOSITO",
    "precioEjecucion": 5000.0,
    "moneda": "EUR"
  }'
```

#### Buying shares (e.g. Apple / AAPL, asset id 1)

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 1},
    "tipoOperacion": "COMPRA",
    "cantidad": 10,
    "precioEjecucion": 180.0
  }'
```

#### Dividend or rent (income on an asset)

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/transacciones \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 5},
    "tipoOperacion": "DIVIDENDO",
    "cantidad": 1,
    "precioEjecucion": 250.0
  }'
```

#### Consolidated portfolio (NAV)

```bash
curl -b cookies.txt -X GET "http://localhost:8080/api/clientes/1/cartera?divisa=EUR" | jq
```

#### Transaction history per client

```bash
curl -b cookies.txt -X GET "http://localhost:8080/api/transacciones/cliente/1" | jq
```

#### Update / delete a transaction

```bash
# PUT — fix price or type
curl -b cookies.txt -X PUT http://localhost:8080/api/transacciones/42 \
  -H "Content-Type: application/json" \
  -d '{
    "cliente": {"id": 1},
    "activoFinanciero": {"id": 2},
    "tipoOperacion": "COMPRA",
    "cantidad": 10,
    "precioEjecucion": 185.0
  }'

# DELETE
curl -b cookies.txt -X DELETE http://localhost:8080/api/transacciones/42
```

</details>

---

## 🧪 Tests

The core financial logic (`ClienteService`) has unit tests with Mockito (no real database): NAV with cash and stocks, the MiFID II alert for conservative clients, and FX conversion for foreign assets. A Spring context test is also included.

```bash
./mvnw test
```

---

## 🛡️ Audit and Compliance

This system addresses the usual gaps of managing client wealth in isolated spreadsheets:

| Area | Approach |
|------|---------|
| **Confidentiality** | Centralised PostgreSQL, authenticated access and segregation by advisor |
| **Integrity** | Transaction ledger as the source of truth for NAV |
| **Traceability** | Typed operations (`COMPRA`, `VENTA`, `DEPOSITO`, `RETIRO`, `DIVIDENDO`, `ALQUILER`) with access control before every mutation |
| **Regulation** | MiFID II alerts on conservative portfolios |

> **Note:** Transactions can be corrected via the API/TUI under access policies (ADMIN / the client's ASESOR). For regulated production environments, complementing this with an immutable audit trail (change history, reversal entries) is recommended.

---

## 📁 Repository Structure

```
finanzdaroca-api/
├── .env                          # Local secrets (not versioned)
├── docker-compose.yml            # PostgreSQL 15
├── pom.xml                       # Spring Boot 4.0.6, Java 21, Lombok
├── src/main/java/com/daroca/sistema_financiero/
│   ├── config/                   # SecurityConfig, SecurityExceptionHandler
│   ├── controller/               # Auth, Cliente, Transaccion, ActivoFinanciero, Usuario (REST)
│   ├── dto/                      # Portfolio / NAV report / position DTOs
│   ├── entity/                   # Cliente, Transaccion, ActivoFinanciero, Usuario, Rol, TipoOperacion
│   ├── integration/              # YahooFinanceChartClient (cached quotes)
│   ├── repository/               # Spring Data JPA repositories
│   ├── security/                 # CustomUserDetailsService, SecurityAuditService (role + per-client checks)
│   ├── service/                  # ClienteService (NAV, MiFID, FX), TransaccionService, PrecioMercadoSyncService (@Scheduled)…
│   └── util/                     # MonedaFinancieraUtil (currency handling, GBp)
├── src/main/resources/           # application.properties, schema.sql, data.sql (demo data)
├── src/test/java/…               # ClienteServiceTest (JUnit 5 + Mockito), context test
└── terminal-client/              # Textual TUI
    ├── app.py                    # Full TUI (tabs, CRUD, role-aware UI)
    ├── main.py                   # Earlier minimal client (login + one portfolio)
    ├── api_client.py             # requests session with JSESSIONID cookie
    └── app.tcss, styles.tcss     # Textual styles
```

---

**FinanzDaroca** · Wealth engine, professional terminal and REST API unified for the independent advisor's day-to-day work.
