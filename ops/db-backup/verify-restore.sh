#!/bin/sh
# Proves a dump restores: loads it into a throwaway local Postgres and checks it against the source.
# Usage: verify-restore.sh <dump-file> <source-database-url>
# Fails (non-zero) when the restore errors, when the newest Flyway migration differs from the source, or
# when a core table the source has rows in comes back empty. Row counts are printed for the log; they are
# not compared exactly, because the source may change between the dump and the check.
set -eu

dump="${1:?dump file required}"
source_url="${2:?source database url required}"

work="$(mktemp -d)"
# Loopback only, on a port nothing else in the job uses.
host=localhost
port=54329

# Postgres refuses to run as root: inside the container, run the throwaway cluster as the postgres user.
as_pg() {
    if [ "$(id -u)" = "0" ]; then su-exec postgres "$@"; else "$@"; fi
}
if [ "$(id -u)" = "0" ]; then chown -R postgres "$work"; fi

cleanup() {
    as_pg pg_ctl -D "$work/data" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$work"
}
trap cleanup EXIT

as_pg initdb -D "$work/data" -A trust -U postgres >/dev/null
as_pg pg_ctl -D "$work/data" -w -l "$work/server.log" \
    -o "-c listen_addresses=$host -p $port" start >/dev/null

restored() {
    as_pg psql -h "$host" -p "$port" -U postgres -d restore_check -v ON_ERROR_STOP=1 -tAc "$1"
}
original() {
    psql "$source_url" -v ON_ERROR_STOP=1 -tAc "$1"
}

as_pg createdb -h "$host" -p "$port" -U postgres restore_check
as_pg pg_restore -h "$host" -p "$port" -U postgres -d restore_check --no-owner --no-acl --exit-on-error "$dump"

latest="SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
if [ "$(restored "$latest")" != "$(original "$latest")" ]; then
    echo "Restore check FAILED: Flyway history differs from the source" >&2
    exit 1
fi

for table in users games experiences diary_entries; do
    restored_rows="$(restored "SELECT count(*) FROM $table")"
    source_rows="$(original "SELECT count(*) FROM $table")"
    echo "Restore check: $table restored=$restored_rows source=$source_rows"
    if [ "$source_rows" -gt 0 ] && [ "$restored_rows" -eq 0 ]; then
        echo "Restore check FAILED: $table came back empty" >&2
        exit 1
    fi
done
echo "Restore check passed"
