# Seed Data

Generate 1,000,000 test users and 1,000,000 arrived letters in MariaDB.

Run this against a fresh local database:

```bash
set -a
source .env
set +a

docker compose exec -T mariadb mariadb \
  -u "$MARIADB_USER" \
  -p"$MARIADB_PASSWORD" \
  "$MARIADB_DATABASE" < seed/generate_seed_data.sql
```

Verify the loaded rows:

```bash
docker compose exec mariadb mariadb \
  -u "$MARIADB_USER" \
  -p"$MARIADB_PASSWORD" \
  "$MARIADB_DATABASE" \
  -e "SELECT COUNT(*) FROM user_tb; SELECT COUNT(*) FROM letter;"
```
