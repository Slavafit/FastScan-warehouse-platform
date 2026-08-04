#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${FASTSCAN_PROJECT_DIR:-/opt/fastscan}"
COMPOSE_SERVICE="${FASTSCAN_POSTGRES_SERVICE:-postgres}"

if [[ $# -ne 2 || "$2" != "--confirm" ]]; then
  echo "Usage: $0 /absolute/path/to/backup.dump --confirm" >&2
  echo "WARNING: this replaces the contents of the configured PostgreSQL database." >&2
  exit 2
fi

backup_path="$1"
if [[ ! -r "$backup_path" || ! -s "$backup_path" ]]; then
  echo "Backup does not exist, is unreadable, or is empty: $backup_path" >&2
  exit 2
fi

cd "$PROJECT_DIR"
docker compose exec -T "$COMPOSE_SERVICE" pg_restore --list < "$backup_path" > /dev/null

echo "Stopping API before restoring the database..."
docker compose stop api

restore_api() {
  docker compose start api
}
trap restore_api EXIT

docker compose exec -T "$COMPOSE_SERVICE" \
  pg_restore --username "${POSTGRES_USER:-fastscan}" \
    --dbname "${POSTGRES_DB:-fastscan}" \
    --clean \
    --if-exists \
    --no-owner \
    --no-privileges \
    --exit-on-error < "$backup_path"

restore_api
trap - EXIT
echo "Restore completed successfully."
