package com.retap.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

final class DatabaseSupport {

    void prepareArrivalsForToday(int rows) throws SQLException {
        try (Connection connection = DriverManager.getConnection(E2eConfig.DB_URL, E2eConfig.DB_USER, E2eConfig.DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE letter SET arrival_date = CURDATE() WHERE id <= " + rows);
        }
    }
}
