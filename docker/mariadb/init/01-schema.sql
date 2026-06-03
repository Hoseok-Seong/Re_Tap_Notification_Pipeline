CREATE DATABASE IF NOT EXISTS retap DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE retap;

CREATE TABLE IF NOT EXISTS user_tb (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    nickname VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    profile_image_url VARCHAR(255),
    fcm_token VARCHAR(255),
    is_blocked BOOLEAN,
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY uk_user_email_provider (username, provider),
    KEY idx_user_tb_fcm_token (fcm_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS letter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(255),
    is_locked BOOLEAN,
    arrival_date DATETIME,
    status VARCHAR(20) NOT NULL,
    read_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_letter_arrival_user (arrival_date, user_id),
    KEY idx_letter_user_arrival (user_id, arrival_date),
    CONSTRAINT fk_letter_user FOREIGN KEY (user_id) REFERENCES user_tb(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_failures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    fcm_token VARCHAR(255),
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_failures_message_id (message_id),
    KEY idx_notification_failures_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
