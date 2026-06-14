# E2E Tests

These tests verify the running Docker Compose pipeline:

- Producer API reads MariaDB rows and publishes Kafka messages.
- Consumer receives Kafka messages and calls FCM Mock.
- DLT retry consumer receives a failed message and retries it through FCM Mock.

Start the stack first:

```bash
docker compose up -d
```

Run:

```bash
./e2e-tests/run-e2e.sh
```

The script reads the local `.env` file and passes the database password as an environment variable to the Gradle test container.
The Gradle container joins the Compose network, so it uses service names such as `kafka:29092` and `mariadb:3306`.
