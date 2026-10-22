#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Moziki · local PostgreSQL helper
#
# Creates a *portable* PostgreSQL cluster in .data/postgres so development and
# tests never touch an existing system database. Trust auth is limited to
# 127.0.0.1 on a non-standard port; this is for local development only.
#
#   ./scripts/dev-db.sh start     initialise (if needed) and start on :5434
#   ./scripts/dev-db.sh stop
#   ./scripts/dev-db.sh status
#   ./scripts/dev-db.sh reset      destroy local data and recreate empty DBs
#   ./scripts/dev-db.sh url        print the JDBC URL
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$ROOT/.data/postgres"
LOG_FILE="$ROOT/.data/postgres.log"
PORT="${MOZIKI_DB_PORT:-5434}"
DB_USER="moziki"
DB_NAME="moziki"
TEST_DB_NAME="moziki_test"

find_pgbin() {
  if [[ -n "${PGBIN:-}" && -x "$PGBIN/pg_ctl" ]]; then
    echo "$PGBIN"; return
  fi
  if command -v pg_ctl >/dev/null 2>&1; then
    dirname "$(command -v pg_ctl)"; return
  fi
  for candidate in \
    "/c/Program Files/PostgreSQL"/*/bin \
    "/usr/lib/postgresql"/*/bin \
    "/opt/homebrew/opt/postgresql"*/bin \
    "/usr/local/opt/postgresql"*/bin; do
    if [[ -x "$candidate/pg_ctl" ]]; then echo "$candidate"; return; fi
  done
  echo "ERROR: PostgreSQL server binaries (pg_ctl/initdb) not found." >&2
  echo "Install PostgreSQL 14+ or run PostgreSQL with Docker instead." >&2
  exit 1
}

PGBIN="$(find_pgbin)"
mkdir -p "$ROOT/.data"

db_exists() {
  "$PGBIN/psql" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = '$1'" 2>/dev/null | grep -q 1
}

ensure_databases() {
  if ! db_exists "$DB_NAME"; then
    "$PGBIN/createdb" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" "$DB_NAME" >/dev/null 2>&1
    echo "Created database $DB_NAME"
  fi
  if ! db_exists "$TEST_DB_NAME"; then
    "$PGBIN/createdb" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" "$TEST_DB_NAME" >/dev/null 2>&1
    echo "Created database $TEST_DB_NAME"
  fi
}

is_running() {
  "$PGBIN/pg_ctl" -D "$DATA_DIR" status >/dev/null 2>&1
}

start() {
  if is_running; then
    echo "PostgreSQL already running on 127.0.0.1:$PORT"
    ensure_databases
    return
  fi
  if [[ ! -f "$DATA_DIR/PG_VERSION" ]]; then
    echo "Initialising local cluster in $DATA_DIR ..."
    "$PGBIN/initdb" -D "$DATA_DIR" -U "$DB_USER" -A trust -E UTF8 --locale=C >/dev/null 2>&1
  fi
  echo "Starting PostgreSQL on 127.0.0.1:$PORT ..."
  # Fully detach output: a database server inheriting our stdout would keep the
  # calling shell (and any pipe) open forever.
  "$PGBIN/pg_ctl" -D "$DATA_DIR" -l "$LOG_FILE" \
    -o "-p $PORT -c listen_addresses=127.0.0.1" -w start >/dev/null 2>&1
  ensure_databases
  echo "Ready:  postgresql://$DB_USER@127.0.0.1:$PORT/$DB_NAME"
}

stop() {
  if is_running; then
    "$PGBIN/pg_ctl" -D "$DATA_DIR" -m fast -w stop >/dev/null 2>&1
    echo "PostgreSQL stopped."
  else
    echo "PostgreSQL is not running."
  fi
}

status() {
  if is_running; then
    echo "PostgreSQL is running on 127.0.0.1:$PORT (data: $DATA_DIR)"
  else
    echo "PostgreSQL is not running (data: $DATA_DIR)"
  fi
}

case "${1:-start}" in
  start)  start ;;
  stop)   stop ;;
  status) status ;;
  url)    echo "jdbc:postgresql://127.0.0.1:$PORT/$DB_NAME" ;;
  reset)
    stop || true
    rm -rf "$DATA_DIR"
    start
    ;;
  *)
    echo "Usage: $0 {start|stop|status|url|reset}" >&2
    exit 2
    ;;
esac
