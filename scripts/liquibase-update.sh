#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

set -a
source "$ROOT_DIR/.env.dev"
set +a

cd "$ROOT_DIR/backend"
mvn process-resources liquibase:update -P liquibase-diff \
  -Dliquibase.url=jdbc:postgresql://localhost:5433/${POSTGRES_DB} \
  -Dliquibase.username=${POSTGRES_USER} \
  -Dliquibase.password=${POSTGRES_PASSWORD} \
  -Dliquibase.changeLogFile=db/changelog/db.changelog-master.xml
