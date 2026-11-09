package edu.xjtlu.civa.server.household;

import edu.xjtlu.civa.server.testing.SafeFlywayTestConfiguration;
import edu.xjtlu.civa.server.testing.TestDatabaseGuard;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdReadIntegrationTest {
    @Test void membersCanReadButOutsidersAndRemovedMembersCannot() {
        String url = System.getenv("DB_URL"), username = System.getenv("DB_USERNAME"), password = System.getenv("DB_PASSWORD");
        Flyway flyway = Flyway.configure().dataSource(url, username, password).cleanDisabled(true).locations("classpath:db/migration").load();
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, TestDatabaseGuard.databaseFromUrl(url));
        var source = new DriverManagerDataSource(url, username, password);
        var jdbc = new JdbcTemplate(source);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(status -> {
            try {
                UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), outsider = UUID.randomUUID(), home = UUID.randomUUID();
                for (UUID id : new UUID[]{owner, member, outsider}) {
                    jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
                            id, "u_" + id.toString().replace("-", "").substring(0, 20), "Test", "test-only", "UTC");
                }
                jdbc.update("INSERT INTO households(id,name) VALUES (?,?)", home, "Home");
                jdbc.update("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,?,true)", UUID.randomUUID(), home, owner, "OWNER");
                jdbc.update("INSERT INTO memberships(id,household_id,user_id,role,active) VALUES (?,?,?,?,true)", UUID.randomUUID(), home, member, "MEMBER");
                var service = new HouseholdReadService(jdbc);
                assertEquals("Home", service.getHousehold(member, home).name);
                assertEquals(2, service.listMembers(member, home, 0, 20).total);
                assertEquals(1, service.listMembers(owner, home, 0, 1).items.size());
                assertEquals(0, service.listMembers(owner, home, 2, 1).items.size());
                assertEquals(2, service.listMembers(owner, home, 2, 1).total);
                assertThrows(IllegalArgumentException.class, () -> service.listMembers(owner, home, 0, 101));
                assertEquals("NOT_FOUND", assertThrows(HouseholdReadService.AccessFailure.class,
                        () -> service.getHousehold(outsider, home)).code);
                assertEquals("NOT_FOUND", assertThrows(HouseholdReadService.AccessFailure.class,
                        () -> service.listMembers(outsider, home, 0, 20)).code);
                jdbc.update("UPDATE memberships SET active=false WHERE user_id=? AND household_id=?", member, home);
                assertEquals("HOUSEHOLD_ACCESS_REVOKED", assertThrows(HouseholdReadService.AccessFailure.class,
                        () -> service.getHousehold(member, home)).code);
                assertEquals("HOUSEHOLD_ACCESS_REVOKED", assertThrows(HouseholdReadService.AccessFailure.class,
                        () -> service.listMembers(member, home, 0, 20)).code);
            } finally {
                status.setRollbackOnly();
            }
        });
    }
}
