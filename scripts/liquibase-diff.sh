#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

set -a
source "$ROOT_DIR/.env.dev"
set +a

cd "$ROOT_DIR/backend"
rm -f src/main/resources/db/changelog/diff/diff-output.sql

mvn compile liquibase:diff -P liquibase-diff \
  -Dliquibase.propertyFile=src/main/resources/liquibase-dev.properties \
  -Dliquibase.url=jdbc:postgresql://localhost:5433/${POSTGRES_DB} \
  -Dliquibase.username=${POSTGRES_USER} \
  -Dliquibase.password=${POSTGRES_PASSWORD}
