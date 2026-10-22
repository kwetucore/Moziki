# Moziki

**Transparent finance for community cooperatives, savings groups and Likelemba in the DRC.**

Moziki is a web PWA plus a double-entry ledger API. It records savings, share purchases, loans, fees and mobile money deposits for a cooperative, and gives leaders, auditors and members the same trustworthy view of the books.

> Phase 1 scope: **Spring Boot API + React PWA**. The data model and capture paths already accommodate a future USSD channel; the standalone mobile app is deliberately out of scope.

---

## 1. Stack at a glance

| Layer | Technology |
| --- | --- |
| API | Java 17, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, Bean Validation |
| Database | PostgreSQL 14+ (tested on 17), Flyway migrations |
| Ledger integrity | Deferred PostgreSQL constraint trigger + append-only triggers + service validator |
| Web app | React 18, Vite 5, TypeScript, Tailwind CSS, i18next |
| Offline / PWA | `vite-plugin-pwa` (Workbox), IndexedDB transaction queue, idempotency keys |
| Languages | English, French (default), Swahili — UI and API errors |

```
.
├── backend/
│   ├── src/main/java/cd/moziki/
│   │   ├── auth/          # login, JWT, current user
│   │   ├── ledger/        # DoubleEntryValidator, LedgerService, queries
│   │   ├── account/       # chart of accounts, balances, reconciliation
│   │   ├── member/        # onboarding, member directory
│   │   ├── audit/         # append-only audit trail
│   │   ├── dashboard/     # cooperative aggregates
│   │   ├── ussd/          # session model for the future feature-phone channel
│   │   └── config/        # security, CORS, i18n, locale resolution
│   ├── src/main/resources/
│   │   ├── db/migration/V1__init.sql     # full schema + integrity triggers
│   │   └── i18n/messages_{en,fr,sw}.properties
│   └── src/test/          # unit + real-PostgreSQL integration tests
├── frontend/
│   ├── src/locales/{en,fr,sw}.json       # product dictionaries
│   ├── src/pages/                        # dashboard, transactions, members, accounts, audit
│   ├── src/lib/offlineQueue.ts           # IndexedDB queue + safe replay
│   └── src/lib/api.ts
├── scripts/
│   ├── dev-db.sh          # portable local PostgreSQL on :5434
│   └── backend-test.sh    # starts DB, runs unit + integration suite
├── docker-compose.yml
└── .env.example
```

A schema overview also lives in [`backend/db/README.md`](backend/db/README.md).

---

## 2. Quick start

### Prerequisites

- Java 17+ and Maven 3.9+
- Node.js 20+ and npm
- PostgreSQL 14+ **or** Docker

### Option A — portable PostgreSQL (recommended on a workstation)

```bash
./scripts/dev-db.sh start      # creates .data/postgres, trust auth on 127.0.0.1:5434
```

It creates two databases: `moziki` (app) and `moziki_test` (tests). Nothing touches an existing system PostgreSQL instance.

### Option B — Docker

```bash
docker compose up -d postgres
```

The container exposes port `5434` and creates the test database on first boot.

### Run the API

```bash
cd backend
mvn spring-boot:run
```

Defaults match the local cluster: `jdbc:postgresql://127.0.0.1:5434/moziki`, user/password `moziki/moziki`, API on <http://localhost:8080>. Flyway creates the schema, and in development the demo cooperative is seeded automatically.

### Run the PWA

```bash
cd frontend
npm install
npm run dev
```

Open <http://127.0.0.1:5173>. Vite proxies `/api` to `localhost:8080`, so there is no CORS setup in development. For a production build:

```bash
npm run build
npm run preview
```

Open <http://127.0.0.1:4173>; `vite preview` proxies `/api` to `localhost:8080` too, so the production build can be tested with its service worker enabled. Set `VITE_API_URL` only when the API is on a different origin. Without it, the PWA calls `/api` (same-origin reverse proxy in production).

### Demo accounts

Seeded by `DemoDataSeeder` only when `MOZIKI_DEMO_DATA=true` (default in dev):

| Role | Name | Phone | Password |
| --- | --- | --- | --- |
| Leader | Aline Mwamba | `+243990000001` | `Moziki2026!` |
| Auditor | Jean Kabasele | `+243990000002` | `Moziki2026!` |
| Member | Espérance Nabintu | `+243990000003` | `Moziki2026!` |
| Member | Patrick Bahati | `+243990000004` | `Moziki2026!` |

The demo cooperative (`UMOJA`, *Coopérative Umoja – Bukavu*) starts with 7 posted, balanced transactions: cash and mobile money deposits, a share purchase, a loan disbursement/repayment and a fee. **Never enable demo data in production** (`application-prod.yml` disables it).

### Configuration

Environment variables (see [`.env.example`](.env.example)):

| Variable | Default | Purpose |
| --- | --- | --- |
| `MOZIKI_DB_URL` | `jdbc:postgresql://127.0.0.1:5434/moziki` | JDBC URL |
| `MOZIKI_DB_USER` / `MOZIKI_DB_PASSWORD` | `moziki` / `moziki` | DB credentials |
| `MOZIKI_DB_TEST_URL` | `.../moziki_test` | Integration-test database |
| `MOZIKI_JWT_SECRET` | dev-only value | HS256 signing secret, **min 32 bytes** |
| `MOZIKI_JWT_TTL_MINUTES` | `480` | Token lifetime |
| `MOZIKI_CORS_ORIGINS` | `http://localhost:5173,...` | Allowed browser origins |
| `MOZIKI_DEMO_DATA` | `true` | Seed demo cooperative (dev only) |
| `MOZIKI_DEMO_PASSWORD` | `Moziki2026!` | Demo password |
| `MOZIKI_PORT` | `8080` | API port |

Spring Boot does not load `.env` automatically; export the variables in your shell or use your process manager/secret store. Defaults are intentionally aligned with the local dev cluster.

---

## 3. The accounting model

### Accounts

Every cooperative receives a standard chart of accounts:

| Code | Account | Type | Category |
| --- | --- | --- | --- |
| `1000` | Caisse (espèces) | Asset | Cash |
| `1010` | Airtel Money | Asset | Mobile money |
| `1020` | Orange Money | Asset | Mobile money |
| `1030` | M-Pesa | Asset | Mobile money |
| `1100` | Prêts accordés | Asset | Loans |
| `2000-<member>` | Épargne – member | Liability | Savings |
| `3000-<member>` | Parts sociales – member | Equity | Shares |
| `4000` | Produits – frais | Income | Fees |
| `4100` | Produits – intérêts | Income | Interest |

Balances are stored as **integer minor units (centimes)** in `accounts.balance_minor`, signed using the accounting convention (debit-positive). Liabilities, equity and income are credit-normal, so `displayBalanceMinor()` flips the sign for the UI. Banking-style floats are never used for money.

### Posting rules

| Operation | Debit | Credit |
| --- | --- | --- |
| Deposit (cash / mobile money) | Cash or wallet | Member savings |
| Withdrawal | Member savings | Cash or wallet |
| Share purchase | Cash or wallet | Member shares |
| Loan disbursement | Loans receivable | Cash or wallet |
| Loan repayment | Cash or wallet | Loans receivable |
| Fee | Cash or wallet | Fee income |
| Reversal | Mirrors original lines (sides swapped) | |

Before a posting is saved the service checks:

1. at least two lines;
2. every amount is positive and has at most two decimals;
3. no account appears twice in one transaction;
4. total debits equal total credits and equal the transaction header amount;
5. every account belongs to the cooperative and uses its currency;
6. no withdrawal exceeds the member's savings, and no cash/wallet account goes negative;
7. all involved accounts are locked in a deterministic UUID order (deadlock-safe).

Then PostgreSQL independently verifies the same invariants. The deferred constraint trigger `trg_ledger_entries_balanced` fires at commit and rejects any transaction whose lines do not balance, even if it was inserted by hand or by a future USSD gateway.

### Immutability and corrections

- `ledger_entries` rejects `UPDATE` and `DELETE`.
- `transactions` rows cannot be deleted or have economic fields changed; only `status`, `reversed_at` and `reversal_reason` may move from `POSTED` to `REVERSED`.
- Corrections are **reversals**: a new `REVERSAL` transaction mirrors the original lines, points back via `reverses_transaction_id`, and the original remains visible with status `REVERSED`.
- `audit_logs` is append-only, including for leaders.

### Reconciliation

The cached `accounts.balance_minor` is updated atomically with the ledger lines. Auditors and leaders can verify it against a live SQL aggregate over `ledger_entries` through `GET /api/accounts/reconciliation` (backed by the `v_account_ledger_balances` view). The integration suite asserts every account stays consistent.

---

## 4. Roles and access

| Capability | Member | Leader | Auditor |
| --- | :---: | :---: | :---: |
| View own savings/shares and own transactions | ✅ | ✅ | ✅ |
| View cooperative pool and chart of accounts | ✅ | ✅ | ✅ |
| View all transactions | — | ✅ | ✅ |
| Post transactions / reversals | — | ✅ | — |
| Onboard members | — | ✅ | — |
| View member directory | — | ✅ | ✅ |
| View audit trail / reconciliation | — | ✅ | ✅ |

Authorization is enforced server-side with Spring Security method annotations (`@PreAuthorize`) and additionally scoped in queries (a member can only read their own transaction details; probing another reference returns 404). Roles live in the `users.role` column (`MEMBER`, `LEADER`, `AUDITOR`).

---

## 5. Internationalization

**Web UI:** `frontend/src/locales/{en,fr,sw}.json` with i18next. French is the default; the chosen language is persisted in `localStorage` and the `<html lang>` attribute follows it. The switcher is available on the login screen and in the app header.

**API errors:** `backend/src/main/resources/i18n/messages_{en,fr,sw}.properties`. Send `Accept-Language: en|fr|sw` and errors, including Bean Validation messages, are returned in that language. Unsupported languages fall back to French.

Core terminology used consistently:

| Concept | EN | FR | SW |
| --- | --- | --- | --- |
| Balance | Balance | Solde | Salio |
| Deposit | Deposit | Dépôt | Weka Akiba |
| Withdrawal | Withdrawal | Retrait | Kutoa |
| Savings | Savings | Épargne | Akiba |
| Shares | Shares | Parts sociales | Hisa |
| Loan | Loan | Prêt | Mkopo |
| Leader accountability | Leader accountability | Responsabilité des dirigeants | Uwajibikaji wa viongozi |

Account names in the database keep their French labels because they are business data; the UI translates the account **category** and member names, while showing the stored account name as supporting text.

---

## 6. Offline-first PWA

- The app shell and build assets are precached by Workbox; the app opens without a network.
- Authorized GET responses (`dashboard`, `accounts`, `transactions`, `members`, `audit`) use a `NetworkFirst` cache with a 4-second network timeout and a 24-hour expiry. The API cache is cleared on sign-out and whenever the login screen is shown, so one member's ledger is never served to the next.
- The signed-in profile is cached in `localStorage` (`moziki.session`). Opening the PWA without a network keeps the session and the last cached ledger instead of signing the user out; `/auth/me` refreshes the profile on the next connection.
- A transaction submitted offline — or while the API is unreachable through a gateway (502/503/504) — is written to an **IndexedDB** queue (`moziki-offline`). Each payload is generated with a UUID `idempotencyKey`.
- The queue is replayed in order on app start (when online), when the browser emits `online`, and on an explicit retry. The server treats a repeated idempotency key as the same operation, so a retry after an ambiguous network failure can never double-post. Non-retryable 4xx responses are dropped from the queue; network/5xx failures stop the sync and keep the work.
- Queued counts and sync status are visible in the header. Offline-queued postings do not trigger a dashboard refetch, so cached figures stay on screen until the queue is flushed.

Security note: cached API responses contain cooperative financial data. On shared devices, sign out after use and configure a short browser/device lock. For higher-assurance deployments, disable API response caching in `vite.config.ts`.

---

## 7. API surface

All endpoints are under `/api`. Authentication is `Authorization: Bearer <JWT>`.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | public | Phone + password → JWT |
| `GET` | `/auth/me` | any role | Current user + cooperative |
| `GET` | `/health` | public | Liveness |
| `GET` | `/dashboard` | any role | Pool totals, personal totals, recent activity |
| `GET` | `/accounts` | any role | Accounts (members see their own subledger accounts) |
| `GET` | `/accounts/reconciliation` | Leader, Auditor | Cached vs journal balances |
| `GET` | `/transactions?page=&size=&memberId=` | any role | Paginated journal (members scoped) |
| `GET` | `/transactions/{id}` | any role | Transaction + balanced entries |
| `POST` | `/transactions` | Leader | Post a transaction |
| `POST` | `/transactions/{id}/reverse` | Leader | Post a reversal |
| `GET` | `/members` | Leader, Auditor | Member directory with balances |
| `POST` | `/members` | Leader | Onboard a member and open savings/shares accounts |
| `GET` | `/audit?page=&size=` | Leader, Auditor | Audit trail |

Example posting:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept-Language: fr" \
  -d '{
    "kind": "DEPOSIT",
    "memberId": "<member-uuid>",
    "amount": 25000,
    "channel": "AIRTEL_MONEY",
    "description": "Dépôt via Airtel Money",
    "idempotencyKey": "offline-8f1c…"
  }'
```

Error payloads are localized and stable:

```json
{
  "timestamp": "2026-10-21T14:00:00Z",
  "status": 422,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Solde d'épargne insuffisant pour ce retrait.",
  "path": "/api/transactions"
}
```

Bean Validation failures add a `fieldErrors` map.

---

## 8. Tests

```bash
./scripts/backend-test.sh
```

This starts the portable database and runs:

- **Unit:** strict money conversion, DRC phone normalization, double-entry structural rules.
- **Integration (real PostgreSQL):** Flyway schema, posting a balanced deposit and cached balances, **direct SQL insertion of an unbalanced entry rejected by the deferred trigger**, update/delete attempts rejected by append-only triggers, idempotency replay, insufficient-funds rollback, reversal history and balance restoration, share purchases, JWT/RBAC over HTTP, localized errors in FR/SW/EN, audit trail and reconciliation.

Frontend verification:

```bash
cd frontend
npm run typecheck
npm run build
```

Integration tests are gated by `MOZIKI_RUN_DB_TESTS=true` (set by the script) so `mvn test` alone works without a database. They run against `moziki_test` and clean/re-migrate it.

---

## 9. Prepared for USSD (phase 2)

- `users.phone` is globally unique E.164, the natural USSD identity. `PhoneNumbers` normalizes DRC local formats (`0990…` → `+243990…`).
- `transactions.source` supports `USSD`; the ledger API does not care which channel created an entry.
- `ussd_sessions` stores phone, cooperative/user, locale, state (`IDLE`, `AUTHENTICATING`, `MENU`, `CONFIRMING`, …), a resumable JSON context and expiry. `UssdSessionRepository` provides session lookup groundwork.
- The chart of accounts is code-based (`1000`, `2000-<member>`, …), so menu-driven flows can map directly to the same posting service.
- No USSD gateway, PIN flow or aggregator integration is included yet.

---

## 10. Production checklist

Before a real deployment:

1. Set a strong `MOZIKI_JWT_SECRET` (`openssl rand -base64 48`), real DB credentials, and `MOZIKI_DEMO_DATA=false`.
2. Run with `SPRING_PROFILES_ACTIVE=prod`; serve the PWA and API on the same HTTPS origin (or configure CORS + `VITE_API_URL`).
3. Put the API behind a reverse proxy with TLS, request size limits and rate limiting — the login endpoint is intentionally not rate-limited in this phase.
4. Back up PostgreSQL (including `ledger_entries`/`audit_logs`) and test restores. Never edit posted ledger rows; correct with reversals.
5. Review the account-cache update path under load. Posting locks accounts in UUID order; high-volume cooperatives should move to a journal-only balance view or event-driven projections.
6. Add observability (structured logs, metrics, alert on reconciliation differences) and an incident playbook for suspected leader fraud.
7. Legal/policy review for member data, consent, data retention and cross-border hosting. Financial data is sensitive personal data.

### Known phase-1 limitations

- Single cooperative per user; multi-cooperative membership is a later schema change (`memberships` table).
- Loan schedules, interest accrual and per-member loan subledgers are not modelled yet; disbursements/repayments use the aggregate loans receivable account.
- No statement export (PDF/CSV) or SMS notifications.
- Background Sync API is not enabled; the offline queue flushes while the app is open (or on next launch).
- No login rate limiting / account lockout yet.
