package edu.xjtlu.civa.server.auth;

import edu.xjtlu.civa.server.testing.SafeFlywayTestConfiguration;
import edu.xjtlu.civa.server.testing.TestDatabaseGuard;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceIntegrationTest {
    @Test void registerLoginSessionLogoutAndRateLimitUsePostgres() throws Exception {
        String url = System.getenv("DB_URL"), username = System.getenv("DB_USERNAME"), password = System.getenv("DB_PASSWORD");
        String dbName = TestDatabaseGuard.databaseFromUrl(url);
        Flyway flyway = Flyway.configure().dataSource(url, username, password).cleanDisabled(true).locations("classpath:db/migration").load();
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, dbName);
        var source = new DriverManagerDataSource(url, username, password);
        var jdbc = new JdbcTemplate(source);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var encoder = new BCryptPasswordEncoder(4);
        tx.executeWithoutResult(status -> {
            try {
                AuthService auth = new AuthService(jdbc, tx, encoder, clock, new SecureRandom(), 2, Duration.ofMinutes(1));
                String loginName = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                AuthService.User user = auth.register(loginName.toUpperCase(Locale.ROOT), " Alice ", "long-password", "Asia/Shanghai");
                assertEquals(loginName, user.username);
                assertEquals("Alice", user.displayName);
                assertNull(user.householdId);
                assertEquals("😀".repeat(120), auth.register(loginName + "b", "😀".repeat(120), "long-password", "UTC").displayName);
                assertThrows(IllegalArgumentException.class, () -> auth.register(loginName + "c", "😀".repeat(121), "long-password", "UTC"));
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM preferences WHERE user_id=?", Integer.class, user.id));
                String storedPassword = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, user.id);
                assertNotEquals("long-password", storedPassword);
                assertTrue(encoder.matches("long-password", storedPassword));

                AuthService.Login login = auth.login(loginName, "long-password");
                byte[] raw = Base64.getUrlDecoder().decode(login.token);
                assertEquals(32, raw.length);
                assertEquals(clock.instant().plus(Duration.ofHours(24)), login.expiresAt);
                byte[] storedTokenHash = jdbc.queryForObject("SELECT token_hash FROM sessions WHERE user_id=?", byte[].class, user.id);
                try { assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(raw), storedTokenHash); }
                catch (Exception ex) { throw new AssertionError(ex); }
                assertEquals(user.id, auth.authenticatedUser(login.token).id);
                assertNull(auth.authenticatedUser("not-a-token"));
                AuthService expired = new AuthService(jdbc, tx, encoder, Clock.offset(clock, Duration.ofHours(25)), new SecureRandom(), 2, Duration.ofMinutes(1));
                assertNull(expired.authenticatedUser(login.token));
                auth.logout(login.token);
                auth.logout(login.token);
                assertNull(auth.authenticatedUser(login.token));

                assertEquals("INVALID_CREDENTIALS", assertThrows(AuthService.Failure.class, () -> auth.login(loginName, "wrong-password")).code);
                assertEquals("INVALID_CREDENTIALS", assertThrows(AuthService.Failure.class, () -> auth.login("unknown_user", "wrong-password")).code);
                AuthService.Failure limited = assertThrows(AuthService.Failure.class, () -> auth.login(loginName, "long-password"));
                assertEquals("RATE_LIMITED", limited.code);
                assertEquals(60, limited.retryAfterSeconds);
                clock.now = clock.now.plusSeconds(61);
                AuthService.Login afterWindow = auth.login(loginName, "long-password");
                assertEquals(user.id, auth.authenticatedUser(afterWindow.token).id);
                auth.logout(afterWindow.token);
                assertEquals("USERNAME_TAKEN", assertThrows(AuthService.Failure.class,
                        () -> auth.register(loginName, "Alice", "long-password", "Asia/Shanghai")).code);
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
