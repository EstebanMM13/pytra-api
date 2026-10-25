#!/bin/sh
# Dumps the Pytra database to the Railway bucket and keeps only the newest KEEP_BACKUPS dumps
# (buckets have no lifecycle rules). Runs as a Railway cron job, so it must exit when done.
set -eu

: "${DATABASE_URL:?DATABASE_URL is required}"
: "${BUCKET:?BUCKET is required}"
: "${ENDPOINT:?ENDPOINT is required}"
KEEP_BACKUPS="${KEEP_BACKUPS:-8}"

name="pytra-$(date -u +%Y-%m-%dT%H%M%SZ).dump"
file="/tmp/$name"

pg_dump --format=custom --file="$file" "$DATABASE_URL"
aws s3 cp "$file" "s3://$BUCKET/$name" --endpoint-url "$ENDPOINT" --only-show-errors
echo "Uploaded $name ($(wc -c < "$file") bytes)"

# Names sort chronologically, so everything after the newest KEEP_BACKUPS is old.
aws s3 ls "s3://$BUCKET/" --endpoint-url "$ENDPOINT" \
    | awk '{print $4}' \
    | grep -E '^pytra-.*\.dump$' \
    | sort -r \
    | tail -n +"$((KEEP_BACKUPS + 1))" \
    | while read -r old; do
        aws s3 rm "s3://$BUCKET/$old" --endpoint-url "$ENDPOINT" --only-show-errors
        echo "Deleted $old"
    done
