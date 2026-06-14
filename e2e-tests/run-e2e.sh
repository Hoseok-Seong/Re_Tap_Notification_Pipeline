#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

set -a
source "$PROJECT_ROOT/.env"
set +a

docker run --rm \
  --network re_tap_notification_pipeline_retap-network \
  -v "$SCRIPT_DIR:/app" \
  -w /app \
  -e E2E_DB_PASSWORD="$MARIADB_PASSWORD" \
  -e E2E_DB_USER="${MARIADB_USER:-retap}" \
  -e E2E_DB_URL="jdbc:mariadb://mariadb:3306/${MARIADB_DATABASE:-retap}" \
  -e E2E_PRODUCER_BASE_URL="http://notification-producer:8081" \
  -e E2E_CONSUMER_BASE_URL="http://notification-consumer:8082" \
  -e E2E_FCM_MOCK_BASE_URL="http://fcm-mock-server:8080" \
  -e E2E_KAFKA_BOOTSTRAP_SERVERS="kafka:29092" \
  gradle:8.10.2-jdk17 gradle test --no-daemon
