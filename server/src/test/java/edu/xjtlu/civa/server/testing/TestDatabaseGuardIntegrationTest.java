package edu.xjtlu.civa.server.testing;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDatabaseGuardIntegrationTest {
    @Test void checksTheDatabaseActuallyReturnedByPostgres() throws Exception {
        String url = System.getenv("DB_URL");
        String expected = TestDatabaseGuard.databaseFromUrl(url);
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            TestDatabaseGuard.verifyConnection(connection, expected);
            assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.verifyConnection(connection, expected + "_different_test"));
        }
    }

    @Test void baselineMigratesAndEnforcesCoreDatabaseConstraints() throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String expected = TestDatabaseGuard.databaseFromUrl(url);
        Flyway flyway = Flyway.configure().dataSource(url, user, password).cleanDisabled(true)
                .locations("classpath:db/migration").load();
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, expected);
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, expected);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            TestDatabaseGuard.verifyConnection(connection, expected);
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                UUID actor = UUID.randomUUID(), home = UUID.randomUUID(), other = UUID.randomUUID();
                UUID ingredient = UUID.randomUUID(), batch = UUID.randomUUID();
                sql.executeUpdate("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES ('" + actor + "','u_" + actor.toString().replace("-", "").substring(0, 20) + "','Test','test-only','UTC')");
                sql.executeUpdate("INSERT INTO households(id,name) VALUES ('" + home + "','Home'),('" + other + "','Other')");
                sql.executeUpdate("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES ('" + UUID.randomUUID() + "','" + home + "','" + actor + "','OWNER',true)");
                var savepoint = connection.setSavepoint();
                SQLException duplicate = assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES ('" + UUID.randomUUID() + "','" + other + "','" + actor + "','MEMBER',true)"));
                assertEquals("23505", duplicate.getSQLState());
                connection.rollback(savepoint);
                sql.executeUpdate("INSERT INTO ingredients(id,name,unit,active) VALUES ('" + ingredient + "','Test ingredient','G',true)");
                UUID recipe = UUID.randomUUID();
                sql.executeUpdate("INSERT INTO recipes(id,household_id,author_id,name,steps,yield_servings,duration_minutes) VALUES ('" + recipe + "','" + home + "','" + actor + "','Test recipe','[\"Cook\"]'::jsonb,1,1)");
                sql.executeUpdate("INSERT INTO recipe_ingredients(recipe_id,ingredient_id,position,quantity,unit,track_stock) VALUES ('" + recipe + "','" + ingredient + "',0,0,'G',true)");
                savepoint = connection.setSavepoint();
                SQLException negative = assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO inventory_batches(id,household_id,ingredient_id,quantity,unit,location) VALUES ('" + batch + "','" + home + "','" + ingredient + "',-1,'G','')"));
                assertEquals("23514", negative.getSQLState());
                connection.rollback(savepoint);
                savepoint = connection.setSavepoint();
                sql.executeUpdate("INSERT INTO idempotency_requests(actor_id,key,operation_digest) VALUES ('" + actor + "','" + UUID.randomUUID() + "',decode(repeat('00',32),'hex'))");
                SQLException incomplete = assertThrows(SQLException.class, () -> sql.execute("SET CONSTRAINTS ALL IMMEDIATE"));
                assertEquals("23514", incomplete.getSQLState());
                connection.rollback(savepoint);
                UUID key = UUID.randomUUID();
                sql.executeUpdate("INSERT INTO idempotency_requests(actor_id,key,operation_digest) VALUES ('" + actor + "','" + key + "',decode(repeat('00',32),'hex'))");
                sql.executeUpdate("UPDATE idempotency_requests SET http_status=201,response_body='{}'::jsonb WHERE actor_id='" + actor + "' AND key='" + key + "'");
                sql.execute("SET CONSTRAINTS ALL IMMEDIATE");
            } finally {
                connection.rollback();
            }
        }
    }
}
