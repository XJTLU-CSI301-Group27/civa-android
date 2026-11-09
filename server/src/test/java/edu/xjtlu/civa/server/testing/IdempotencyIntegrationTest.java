package edu.xjtlu.civa.server.testing;

import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import edu.xjtlu.civa.server.application.CanonicalRequestDigest;
import edu.xjtlu.civa.server.household.HouseholdWriteService;
import edu.xjtlu.civa.server.config.JsonbCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyIntegrationTest {
    @Test void concurrentRetryCommitsOnceAndRechecksAuthorization() throws Exception {
        String url = System.getenv("DB_URL"), user = System.getenv("DB_USERNAME"), password = System.getenv("DB_PASSWORD");
        String expectedDatabase = TestDatabaseGuard.databaseFromUrl(url);
        var base = new DriverManagerDataSource(url, user, password);
        String schema = "civa_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = base.getConnection()) {
            TestDatabaseGuard.verifyConnection(connection, expectedDatabase);
            try (var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        }
        DataSource scoped = new DelegatingDataSource(base) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try { connection.setSchema(schema); return connection; }
                catch (SQLException ex) { connection.close(); throw ex; }
            }
        };
        Flyway flyway = Flyway.configure().dataSource(scoped).schemas(schema).defaultSchema(schema)
                .createSchemas(false).cleanDisabled(true).locations("classpath:db/migration").load();
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, expectedDatabase);
        JdbcTemplate jdbc = new JdbcTemplate(scoped);
        var transactions = new IdempotencyExecutor(jdbc, new DataSourceTransactionManager(scoped));
        UUID actor = UUID.randomUUID(), key = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        var schemas = mapper.readTree(Path.of(System.getProperty("civa.contractDraft")).toFile()).path("components").path("schemas");
        var canonicalizer = new CanonicalRequestDigest(mapper, schemas);
        var requestSchema = schemas.get("CreateInventoryBatchRequest");
        String path = "/api/v1/households/" + UUID.randomUUID() + "/inventory-batches";
        String ingredient = UUID.randomUUID().toString();
        String body = "{\"ingredientId\":\"" + ingredient + "\",\"quantity\":\"2.500\",\"unit\":\"G\",\"location\":\"\"}";
        String equivalent = "{\"expiresOn\":null,\"location\":\"\",\"unit\":\"G\",\"quantity\":\"2.5\",\"ingredientId\":\"" + ingredient + "\"}";
        byte[] digest = canonicalizer.digestJson("POST", path, body, requestSchema);
        byte[] retryDigest = canonicalizer.digestJson("POST", path, equivalent, requestSchema);
        var pool = Executors.newFixedThreadPool(2);
        try {
            jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
                    actor, "u_" + actor.toString().replace("-", "").substring(0, 20), "Test", "test-only", "UTC");
            CountDownLatch startedWork = new CountDownLatch(1), releaseWork = new CountDownLatch(1), retryStarted = new CountDownLatch(1);
            AtomicInteger executions = new AtomicInteger(), replayChecks = new AtomicInteger();
            var first = pool.submit(() -> transactions.execute(actor, key, digest, () -> {
                executions.incrementAndGet();
                startedWork.countDown();
                try { if (!releaseWork.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for retry"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                jdbc.update("INSERT INTO households(id,name) VALUES (?,?)", UUID.randomUUID(), "Test home");
                return new IdempotencyExecutor.Result(201, "{\"ok\":true}");
            }, result -> fail("First request must not replay")));
            assertTrue(startedWork.await(10, TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                retryStarted.countDown();
                return transactions.execute(actor, key, retryDigest, () -> { executions.incrementAndGet(); return new IdempotencyExecutor.Result(201, "{}"); },
                        result -> replayChecks.incrementAndGet());
            });
            assertTrue(retryStarted.await(10, TimeUnit.SECONDS));
            try { assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS)); }
            finally { releaseWork.countDown(); }
            assertEquals(201, first.get(10, TimeUnit.SECONDS).status);
            assertEquals(201, second.get(10, TimeUnit.SECONDS).status);
            assertEquals(1, executions.get());
            assertEquals(1, replayChecks.get());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM households", Integer.class));
            byte[] different = canonicalizer.digestJson("POST", path, body.replace("2.500", "2.600"), requestSchema);
            assertThrows(IdempotencyExecutor.KeyReused.class, () -> transactions.execute(actor, key, different,
                    () -> fail("different payload must not execute"), result -> fail("different payload must not replay")));
            assertThrows(SecurityException.class, () -> transactions.execute(actor, key, digest,
                    () -> fail("replay must not execute"), result -> { throw new SecurityException("revoked"); }));
            UUID failedKey = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> transactions.execute(actor, failedKey, digest,
                    () -> { throw new IllegalStateException("failed work"); }, result -> fail("must not replay")));
            assertEquals(204, transactions.execute(actor, failedKey, digest,
                    () -> new IdempotencyExecutor.Result(204, null), result -> fail("must not replay")).status);
            UUID removedKey = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> transactions.execute(actor, removedKey, digest, () -> {
                jdbc.update("DELETE FROM idempotency_requests WHERE actor_id=? AND key=?", actor, removedKey);
                return new IdempotencyExecutor.Result(201, "{}");
            }, result -> fail("must not replay")));

            var households = new HouseholdWriteService(jdbc, transactions, new JsonbCodec(mapper), Clock.systemUTC());
            String createBody = "{\"name\":\"Shared home\"}";
            byte[] createDigest = canonicalizer.digestJson("POST", "/api/v1/households", createBody, schemas.get("CreateHouseholdRequest"));
            UUID createKey = UUID.randomUUID();
            var created = households.createHousehold(actor, createKey, createDigest, "Shared home");
            UUID home = UUID.fromString(mapper.readTree(created.body).path("id").asText());
            assertEquals("Shared home", mapper.readTree(created.body).path("name").asText());
            assertTrue(mapper.readTree(created.body).path("createdAt").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
            assertEquals(home.toString(), mapper.readTree(households.createHousehold(actor, createKey, createDigest, "Shared home").body).path("id").asText());
            assertEquals("ALREADY_IN_HOUSEHOLD", assertThrows(HouseholdWriteService.Failure.class,
                    () -> households.createHousehold(actor, UUID.randomUUID(), createDigest, "Shared home")).code);

            UUID member = UUID.randomUUID(), outsider = UUID.randomUUID();
            for (UUID id : new UUID[]{member, outsider}) {
                jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
                        id, "u_" + id.toString().replace("-", "").substring(0, 20), "Test", "test-only", "UTC");
            }
            String invite = "AAAAAAAAAAAAAAAAAAAAAAAAAA";
            byte[] inviteHash = MessageDigest.getInstance("SHA-256").digest(invite.getBytes(StandardCharsets.US_ASCII));
            jdbc.update("INSERT INTO invitations(id,household_id,created_by,code_hash,expires_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), home, actor, inviteHash, Timestamp.from(Clock.systemUTC().instant().plus(Duration.ofHours(1))));
            String joinBody = "{\"code\":\"" + invite.toLowerCase() + "\"}";
            byte[] joinDigest = canonicalizer.digestJson("POST", "/api/v1/households/join", joinBody, schemas.get("JoinHouseholdRequest"));
            UUID joinKey = UUID.randomUUID();
            var joined = households.joinHousehold(member, joinKey, joinDigest, invite.toLowerCase());
            assertEquals(home.toString(), mapper.readTree(joined.body).path("householdId").asText());
            assertEquals("MEMBER", mapper.readTree(joined.body).path("role").asText());
            assertEquals(mapper.readTree(joined.body), mapper.readTree(households.joinHousehold(member, joinKey, joinDigest, invite.toLowerCase()).body));
            byte[] uppercaseDigest = canonicalizer.digestJson("POST", "/api/v1/households/join", "{\"code\":\"" + invite + "\"}", schemas.get("JoinHouseholdRequest"));
            assertEquals("INVALID_INVITATION", assertThrows(HouseholdWriteService.Failure.class,
                    () -> households.joinHousehold(outsider, UUID.randomUUID(), uppercaseDigest, invite)).code);
            jdbc.update("UPDATE memberships SET active=false WHERE user_id=? AND household_id=?", member, home);
            assertEquals("HOUSEHOLD_ACCESS_REVOKED", assertThrows(HouseholdWriteService.Failure.class,
                    () -> households.joinHousehold(member, joinKey, joinDigest, invite.toLowerCase())).code);

            UUID secondOwner = UUID.randomUUID(), joiner = UUID.randomUUID();
            for (UUID id : new UUID[]{secondOwner, joiner}) {
                jdbc.update("INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES (?,?,?,?,?)",
                        id, "u_" + id.toString().replace("-", "").substring(0, 20), "Test", "test-only", "UTC");
            }
            String secondBody = "{\"name\":\"Second home\"}";
            byte[] secondDigest = canonicalizer.digestJson("POST", "/api/v1/households", secondBody, schemas.get("CreateHouseholdRequest"));
            UUID secondHome = UUID.fromString(mapper.readTree(households.createHousehold(secondOwner, UUID.randomUUID(), secondDigest, "Second home").body).path("id").asText());
            String firstCode = "AAAQEAYEAUDAOCAJBIFQYDIOB4", secondCode = "77777777777777777777777774";
            for (int i = 0; i < 2; i++) {
                String code = i == 0 ? firstCode : secondCode;
                jdbc.update("INSERT INTO invitations(id,household_id,created_by,code_hash,expires_at) VALUES (?,?,?,?,?)",
                        UUID.randomUUID(), i == 0 ? home : secondHome, i == 0 ? actor : secondOwner,
                        MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII)),
                        Timestamp.from(Clock.systemUTC().instant().plus(Duration.ofHours(1))));
            }
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            var joinTasks = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (String code : new String[]{firstCode, secondCode}) {
                byte[] codeDigest = canonicalizer.digestJson("POST", "/api/v1/households/join", "{\"code\":\"" + code + "\"}", schemas.get("JoinHouseholdRequest"));
                joinTasks.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out starting join");
                    try { households.joinHousehold(joiner, UUID.randomUUID(), codeDigest, code); return "JOINED"; }
                    catch (HouseholdWriteService.Failure ex) { return ex.code; }
                }));
            }
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            var outcomes = java.util.Set.of(joinTasks.get(0).get(10, TimeUnit.SECONDS), joinTasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(java.util.Set.of("JOINED", "ALREADY_IN_HOUSEHOLD"), outcomes);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM memberships WHERE user_id=? AND active", Integer.class, joiner));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM invitations WHERE code_hash IN (?,?) AND used_at IS NOT NULL", Integer.class,
                    MessageDigest.getInstance("SHA-256").digest(firstCode.getBytes(StandardCharsets.US_ASCII)),
                    MessageDigest.getInstance("SHA-256").digest(secondCode.getBytes(StandardCharsets.US_ASCII))));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            try (Connection connection = base.getConnection()) {
                TestDatabaseGuard.verifyConnection(connection, expectedDatabase);
                String tables = "users sessions households memberships invitations preferences ingredients ingredient_aliases recipes recipe_ingredients wishlists inventory_batches inventory_movements plan_slots plan_items cooking_events meal_entries media idempotency_requests";
                String qualified = Arrays.stream(tables.split(" ")).map(table -> schema + "." + table).collect(Collectors.joining(","));
                try (var statement = connection.createStatement()) { statement.execute("TRUNCATE " + qualified); }
            }
        }
    }
}
