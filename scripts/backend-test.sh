#!/usr/bin/env bash
# Starts the portable local PostgreSQL cluster (if needed) and runs the backend
# test suite, including the real-database ledger integration tests.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/scripts/dev-db.sh" start

cd "$ROOT/backend"
export MOZIKI_RUN_DB_TESTS=true
export MOZIKI_DB_TEST_URL="${MOZIKI_DB_TEST_URL:-jdbc:postgresql://127.0.0.1:5434/moziki_test}"
export MOZIKI_DB_USER="${MOZIKI_DB_USER:-moziki}"
export MOZIKI_DB_PASSWORD="${MOZIKI_DB_PASSWORD:-moziki}"

exec mvn test "$@"
