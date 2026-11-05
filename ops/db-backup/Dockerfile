# pg_dump must be the same major version as the server (Postgres 18) or newer.
FROM postgres:18-alpine

# su-exec runs the throwaway restore-check cluster as the postgres user.
RUN apk add --no-cache aws-cli su-exec

COPY backup.sh verify-restore.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/verify-restore.sh

CMD ["/usr/local/bin/backup.sh"]
