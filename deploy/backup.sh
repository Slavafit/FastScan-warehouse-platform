#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${FASTSCAN_PROJECT_DIR:-/opt/fastscan}"
BACKUP_DIR="${FASTSCAN_BACKUP_DIR:-/var/backups/fastscan}"
RETENTION_DAYS="${FASTSCAN_BACKUP_RETENTION_DAYS:-7}"
COMPOSE_SERVICE="${FASTSCAN_POSTGRES_SERVICE:-postgres}"

if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
  echo "FASTSCAN_BACKUP_RETENTION_DAYS must be a non-negative integer" >&2
  exit 2
fi

cd "$PROJECT_DIR"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

timestamp="$(date -u +'%Y-%m-%dT%H-%M-%SZ')"
final_path="$BACKUP_DIR/fastscan-$timestamp.dump"
temporary_path="$final_path.tmp"

cleanup() {
  rm -f -- "$temporary_path"
}
trap cleanup EXIT

echo "Creating PostgreSQL backup: $final_path"
docker compose exec -T "$COMPOSE_SERVICE" \
  pg_dump --username "${POSTGRES_USER:-fastscan}" \
    --dbname "${POSTGRES_DB:-fastscan}" \
    --format custom \
    --no-owner \
    --no-privileges > "$temporary_path"

if [[ ! -s "$temporary_path" ]]; then
  echo "Backup failed: dump is empty" >&2
  exit 1
fi

docker compose exec -T "$COMPOSE_SERVICE" pg_restore --list < "$temporary_path" > /dev/null
chmod 600 "$temporary_path"
mv -- "$temporary_path" "$final_path"
trap - EXIT

# Rotation happens only after a new verified backup has been completed.
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'fastscan-*.dump' \
  -mtime "+$RETENTION_DAYS" -delete

echo "Backup completed: $final_path ($(du -h "$final_path" | cut -f1))"
