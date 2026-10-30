package edu.xjtlu.civa.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal account service. HTTP mapping and application wiring are not yet present. */
class AuthService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom random;
    private final int failureLimit;
    private final Duration failureWindow;
    private final Deque<Instant> failures = new ArrayDeque<>();
    private final String dummyHash;

    AuthService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder passwords,
                Clock clock, SecureRandom random, int failureLimit, Duration failureWindow) {
        if (failureLimit < 1 || failureWindow.isZero() || failureWindow.isNegative()) throw new IllegalArgumentException("Invalid rate limit");
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.passwords = passwords;
        this.clock = clock;
        this.random = random;
        this.failureLimit = failureLimit;
        this.failureWindow = failureWindow;
        this.dummyHash = passwords.encode("civa-invalid-password");
    }

    User register(String username, String displayName, String password, String zoneId) {
        String normalized = Credentials.normalizeUsername(username);
        Credentials.validateNewPassword(password);
        if (displayName == null) throw new IllegalArgumentException("Invalid display name");
        String name = displayName.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 120) throw new IllegalArgumentException("Invalid display name");
        try { if (zoneId == null || zoneId.length() > 120) throw new IllegalArgumentException(); ZoneId.of(zoneId); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid time zone"); }
        String passwordHash = passwords.encode(password);
        UUID id = UUID.randomUUID();
        return transactions.execute(status -> {
            int inserted = jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?) ON CONFLICT (username) DO NOTHING",
                    id, normalized, name, passwordHash, zoneId);
            if (inserted == 0) throw new Failure("USERNAME_TAKEN", 0);
            jdbc.update("INSERT INTO preferences(user_id) VALUES (?)", id);
            return findUser(id);
        });
    }

    // ponytail: global login lock and shared failure window; use bounded per-account/IP limits if throughput warrants it.
    synchronized Login login(String username, String password) {
        Instant now = clock.instant();
        while (!failures.isEmpty() && !failures.peekFirst().plus(failureWindow).isAfter(now)) failures.removeFirst();
        if (failures.size() >= failureLimit) {
            long seconds = Math.max(1, (Duration.between(now, failures.peekFirst().plus(failureWindow)).toMillis() + 999) / 1000);
            throw new Failure("RATE_LIMITED", seconds);
        }
        String normalized = null;
        boolean validPassword = true;
        try { normalized = Credentials.normalizeUsername(username); } catch (IllegalArgumentException ignored) { }
        try { Credentials.validateNewPassword(password); } catch (IllegalArgumentException ignored) { validPassword = false; }
        List<PasswordRow> rows = normalized == null ? List.of() : jdbc.query(
                "SELECT id,password_hash FROM users WHERE username=?", (rs, n) -> new PasswordRow(rs.getObject(1, UUID.class), rs.getString(2)), normalized);
        String hash = rows.isEmpty() ? dummyHash : rows.get(0).hash;
        boolean matches = passwords.matches(validPassword ? password : "civa-invalid-password", hash);
        if (!validPassword || rows.isEmpty() || !matches) {
            failures.addLast(now);
            throw new Failure("INVALID_CREDENTIALS", 0);
        }
        UUID id = rows.get(0).id;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(Duration.ofHours(24));
        return transactions.execute(status -> {
            jdbc.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES (?,?,?)", hashToken(bytes), id, Timestamp.from(expiresAt));
            return new Login(token, expiresAt, findUser(id));
        });
    }

    User authenticatedUser(String token) {
        byte[] bytes = decodeToken(token);
        if (bytes == null) return null;
        List<UUID> ids = jdbc.query("SELECT user_id FROM sessions WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?",
                (rs, n) -> rs.getObject(1, UUID.class), hashToken(bytes), Timestamp.from(clock.instant()));
        return ids.isEmpty() ? null : findUser(ids.get(0));
    }

    void logout(String token) {
        byte[] bytes = decodeToken(token);
        if (bytes != null) jdbc.update("UPDATE sessions SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                Timestamp.from(clock.instant()), hashToken(bytes));
    }

    User findUser(UUID id) {
        return jdbc.queryForObject("SELECT u.id,u.username,u.display_name,u.zone_id,u.created_at,u.updated_at,m.household_id FROM users u LEFT JOIN memberships m ON m.user_id=u.id AND m.active WHERE u.id=?",
                (rs, n) -> new User(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(), rs.getObject(7, UUID.class)), id);
    }

    private static byte[] decodeToken(String token) {
        if (token == null || token.length() != 43) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token.getBytes(StandardCharsets.US_ASCII));
            return bytes.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token) ? bytes : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static byte[] hashToken(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    static final class Failure extends RuntimeException {
        final String code;
        final long retryAfterSeconds;
        Failure(String code, long retryAfterSeconds) { super(code); this.code = code; this.retryAfterSeconds = retryAfterSeconds; }
    }
    private static final class PasswordRow {
        final UUID id;
        final String hash;
        PasswordRow(UUID id, String hash) { this.id = id; this.hash = hash; }
    }
    static final class User {
        final UUID id;
        final String username, displayName, zoneId;
        final Instant createdAt, updatedAt;
        final UUID householdId;
        User(UUID id, String username, String displayName, String zoneId, Instant createdAt, Instant updatedAt, UUID householdId) {
            this.id = id; this.username = username; this.displayName = displayName; this.zoneId = zoneId;
            this.createdAt = createdAt; this.updatedAt = updatedAt; this.householdId = householdId;
        }
    }
    static final class Login {
        final String token;
        final Instant expiresAt;
        final User user;
        Login(String token, Instant expiresAt, User user) { this.token = token; this.expiresAt = expiresAt; this.user = user; }
    }
}
