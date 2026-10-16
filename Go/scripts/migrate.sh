#!/usr/bin/env bash
# Applies every pending SQL migration in internal/db/migrations to
# the database pointed at by $DATABASE_URL.
#
# Usage:
#   DATABASE_URL="postgres://user:pass@host:5432/callin?sslmode=disable" ./scripts/migrate.sh

set -euo pipefail

if [ -z "${DATABASE_URL:-}" ]; then
  echo "error: DATABASE_URL is not set" >&2
  exit 1
fi

MIGRATIONS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/internal/db/migrations"

echo "Applying migrations from $MIGRATIONS_DIR ..."

for file in "$MIGRATIONS_DIR"/*.sql; do
  echo "-> $(basename "$file")"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$file"
done

echo "All migrations applied."
