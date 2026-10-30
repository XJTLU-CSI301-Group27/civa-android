package edu.xjtlu.civa.server.testing;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

/** Test-only guard for the DataSource actually used by Flyway. */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
public class SafeFlywayTestConfiguration {
    @Bean
    FlywayMigrationStrategy testMigrationStrategy() {
        return flyway -> verifyAndMigrate(flyway, TestDatabaseGuard.databaseFromUrl(System.getenv("DB_URL")));
    }

    public static void verifyAndMigrate(Flyway flyway, String expectedDatabase) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            TestDatabaseGuard.verifyConnection(connection, expectedDatabase);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot verify Flyway's PostgreSQL test database");
        }
        flyway.migrate();
    }
}
