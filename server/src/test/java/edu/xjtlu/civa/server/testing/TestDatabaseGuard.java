package edu.xjtlu.civa.server.testing;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;

/** Runs before the server test task starts any application context or migrations. */
public final class TestDatabaseGuard {
    private TestDatabaseGuard() {}

    public static String databaseFromUrl(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("DB_URL must be a PostgreSQL JDBC URL with a test database");
        }
        try {
            URI uri = URI.create(url.substring(5));
            String path = uri.getPath();
            if (uri.getRawAuthority() == null || uri.getRawAuthority().isEmpty() || path == null
                    || !path.startsWith("/") || path.indexOf('/', 1) >= 0 || !path.endsWith("_test")) {
                throw new IllegalArgumentException();
            }
            return path.substring(1);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("DB_URL must name a database ending in _test");
        }
    }

    public static void main(String[] args) {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String expected = databaseFromUrl(url);
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalStateException("DB_USERNAME and DB_PASSWORD are required for test verification");
        }
        try (var connection = DriverManager.getConnection(url, username, password)) {
            verifyConnection(connection, expected);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot connect to or verify the PostgreSQL test database");
        }
    }

    static void verifyConnection(Connection connection, String expected) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            if (!result.next() || !expected.equals(result.getString(1)) || !result.getString(1).endsWith("_test")) {
                throw new IllegalStateException("Connected database does not match the configured test database");
            }
        }
    }
}
