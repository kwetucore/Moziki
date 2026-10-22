# Moziki database

The schema is managed by **Flyway** and lives in
[`src/main/resources/db/migration`](../src/main/resources/db/migration).
This folder is the human entry point for database design notes.

```
V1__init.sql
```

## Tables

| Table | Purpose | Key integrity rules |
| --- | --- | --- |
| `cooperatives` | One row per savings group / cooperative | unique `code`; CDF currency; `en`, `fr`, `sw` default locale |
| `users` | Members, leaders, auditors | unique E.164 `phone`; role check; unique member number per cooperative; locale check |
| `accounts` | Chart of accounts + per-member subledgers | unique code per cooperative; one account per member/category; member targets limited to savings/shares/loans |
| `transactions` | Journal headers | immutable after posting except reversal metadata; unique reference; idempotency and mobile-money external refs unique; reversal must reference its original |
| `ledger_entries` | Journal lines | immutable; one line per account per transaction; positive amounts; debits = credits enforced by a deferred constraint trigger |
| `audit_logs` | Append-only leader accountability | no updates/deletes |
| `ussd_sessions` | Ready for the feature-phone phase | resumable JSON context, locale, expiry |

## Second line of defence

`trg_ledger_entries_balanced` is a **deferrable constraint trigger** on
`ledger_entries`. At commit it verifies, per transaction:

1. the transaction is `POSTED`;
2. every account belongs to the transaction's cooperative and currency;
3. at least two lines exist;
4. total debits = total credits > 0;
5. the header `amount_minor` equals the debit total.

Any failure aborts the whole commit with SQLSTATE `23514` (check violation).
`trg_ledger_entries_immutable`, `trg_transactions_immutable` and
`trg_audit_logs_immutable` reject edits and deletions.

The reconciliation view `v_account_ledger_balances` sums the journal per
account; `GET /api/accounts/reconciliation` compares it with the cached
`accounts.balance_minor`.

## Money

All amounts are `BIGINT` **minor units** (centimes) in CDF. The application
accepts decimal strings/JSON numbers with up to two decimals and rejects finer
precision rather than rounding silently. Never use floating point for money.

## Applying migrations

Flyway runs automatically at API startup. To inspect the applied history:

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

For local resets, prefer `./scripts/dev-db.sh reset` (destroys the portable
cluster only) or recreate the `moziki_test` schema for tests. Do not run Flyway
`clean` against production.
