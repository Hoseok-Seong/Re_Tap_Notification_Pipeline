package com.retap.e2e;

final class E2eConfig {

    static final String PRODUCER_BASE_URL = env("E2E_PRODUCER_BASE_URL", "http://host.docker.internal:8081");
    static final String CONSUMER_BASE_URL = env("E2E_CONSUMER_BASE_URL", "http://host.docker.internal:8082");
    static final String FCM_MOCK_BASE_URL = env("E2E_FCM_MOCK_BASE_URL", "http://host.docker.internal:8080");
    static final String KAFKA_BOOTSTRAP_SERVERS = env("E2E_KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092");
    static final String DB_URL = env("E2E_DB_URL", "jdbc:mariadb://host.docker.internal:3307/retap");
    static final String DB_USER = env("E2E_DB_USER", "retap");
    static final String DB_PASSWORD = requiredEnv("E2E_DB_PASSWORD");

    private E2eConfig() {
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " environment variable is required");
        }
        return value;
    }
}
