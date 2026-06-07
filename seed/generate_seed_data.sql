USE retap;

DELIMITER //

CREATE OR REPLACE PROCEDURE assert_empty_seed_tables()
BEGIN
    DECLARE user_count BIGINT DEFAULT 0;
    DECLARE letter_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO user_count FROM user_tb;
    SELECT COUNT(*) INTO letter_count FROM letter;

    IF user_count > 0 OR letter_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Seed tables must be empty. Run against a fresh database or truncate test data first.';
    END IF;
END//

DELIMITER ;

CALL assert_empty_seed_tables();
DROP PROCEDURE assert_empty_seed_tables;

SET @seed_started_at = NOW(6);

INSERT INTO user_tb (
    id,
    username,
    provider,
    nickname,
    role,
    profile_image_url,
    fcm_token,
    is_blocked,
    created_at,
    updated_at
)
SELECT
    seq AS id,
    CONCAT('user', seq, '@retap.local') AS username,
    'LOCAL' AS provider,
    CONCAT('user', seq) AS nickname,
    'USER' AS role,
    NULL AS profile_image_url,
    CONCAT('mock-token-', seq) AS fcm_token,
    FALSE AS is_blocked,
    NOW() AS created_at,
    NOW() AS updated_at
FROM seq_1_to_1000000;

INSERT INTO letter (
    id,
    user_id,
    content,
    image_url,
    is_locked,
    arrival_date,
    status,
    read_at,
    created_at,
    updated_at
)
SELECT
    seq AS id,
    seq AS user_id,
    CONCAT('Seed letter content ', seq) AS content,
    NULL AS image_url,
    FALSE AS is_locked,
    CURDATE() AS arrival_date,
    'ARRIVED' AS status,
    NULL AS read_at,
    NOW() AS created_at,
    NOW() AS updated_at
FROM seq_1_to_1000000;

SELECT
    (SELECT COUNT(*) FROM user_tb) AS user_count,
    (SELECT COUNT(*) FROM letter) AS letter_count,
    TIMESTAMPDIFF(MICROSECOND, @seed_started_at, NOW(6)) / 1000000 AS elapsed_seconds;
