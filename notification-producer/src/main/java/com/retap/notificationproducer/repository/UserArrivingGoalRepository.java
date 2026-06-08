package com.retap.notificationproducer.repository;

import com.retap.notificationproducer.domain.UserArrivingGoal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserArrivingGoalRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserArrivingGoalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserArrivingGoal> findArrivingGoalsAfterUserId(long lastUserId, int limit) {
        return jdbcTemplate.query("""
                        SELECT
                            u.id AS user_id,
                            u.fcm_token,
                            u.nickname,
                            COUNT(l.id) AS arriving_count
                        FROM letter l FORCE INDEX (idx_letter_user_arrival)
                        JOIN user_tb u ON u.id = l.user_id
                        WHERE l.user_id > ?
                          AND l.arrival_date >= CURDATE()
                          AND l.arrival_date < CURDATE() + INTERVAL 1 DAY
                          AND u.fcm_token IS NOT NULL
                        GROUP BY l.user_id, u.id, u.fcm_token, u.nickname
                        ORDER BY l.user_id
                        LIMIT ?
                        """,
                (rs, rowNum) -> new UserArrivingGoal(
                        rs.getLong("user_id"),
                        rs.getString("fcm_token"),
                        rs.getString("nickname"),
                        rs.getLong("arriving_count")
                ),
                lastUserId,
                limit
        );
    }
}
