#!/bin/sh
# Proves a dump restores: loads it into a throwaway local Postgres and checks it.
# Usage: verify-restore.sh <dump-file> <source-database-url> <flyway-version-before-dump>
#        verify-restore.sh --flyway-version <database-url>   (prints the newest versioned migration)
# Fails (non-zero) when the restore errors, when the dump's newest versioned migration is not between the
# source's version from before pg_dump and its version now (a deploy may migrate it in between), or when a
# core table the source has rows in comes back empty. Row counts are printed for the log; they are not
# compared exactly, because the source may change between the dump and the check.
set -eu

FLYWAY_VERSION_SQL="SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"

if [ "${1:-}" = "--flyway-version" ]; then
    version="$(psql "${2:?database url required}" -v ON_ERROR_STOP=1 -tAc "$FLYWAY_VERSION_SQL")"
    [ -n "$version" ] || { echo "No versioned Flyway migration found" >&2; exit 1; }
    echo "$version"
    exit 0
fi

dump="${1:?dump file required}"
source_url="${2:?source database url required}"
version_before="${3:?flyway version from before the dump required}"

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
    status=$?
    as_pg pg_ctl -D "$work/data" -m immediate stop >/dev/null 2>&1 || true
    # The container is gone after the run: on failure, keep the throwaway server's side in the job log
    # (terse verbosity: no CONTEXT lines, so no row data ends up there).
    if [ "$status" -ne 0 ] && [ -f "$work/server.log" ]; then
        echo "--- restore-check server log ---" >&2
        cat "$work/server.log" >&2
    fi
    rm -rf "$work"
    exit "$status"
}
trap cleanup EXIT

as_pg initdb -D "$work/data" -A trust -U postgres >/dev/null
as_pg pg_ctl -D "$work/data" -w -l "$work/server.log" \
    -o "-c listen_addresses=$host -p $port -c log_error_verbosity=terse" start >/dev/null

# Results go through assignments, so a failing query aborts the script (set -e) instead of comparing "".
restored() {
    as_pg psql -h "$host" -p "$port" -U postgres -d restore_check -v ON_ERROR_STOP=1 -tAc "$1"
}
original() {
    psql "$source_url" -v ON_ERROR_STOP=1 -tAc "$1"
}

as_pg createdb -h "$host" -p "$port" -U postgres restore_check
as_pg pg_restore -h "$host" -p "$port" -U postgres -d restore_check --no-owner --no-acl --exit-on-error "$dump"

restored_version="$(restored "$FLYWAY_VERSION_SQL")"
version_now="$(original "$FLYWAY_VERSION_SQL")"
# Pytra's migrations are plain integers (V1..Vn): the dump must sit between the version before it and now.
if ! [ "$restored_version" -ge "$version_before" ] 2>/dev/null || ! [ "$restored_version" -le "$version_now" ] 2>/dev/null; then
    echo "Restore check FAILED: dump is at Flyway version '$restored_version'," \
        "source was '$version_before' before the dump and is '$version_now' now" >&2
    exit 1
fi
echo "Restore check: Flyway version $restored_version"

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
