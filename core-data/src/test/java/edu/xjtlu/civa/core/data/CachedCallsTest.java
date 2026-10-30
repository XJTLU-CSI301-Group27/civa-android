package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.api.model.AuthSession;
import edu.xjtlu.civa.api.model.User;
import edu.xjtlu.civa.api.model.Preference;
import edu.xjtlu.civa.core.ui.ApiError;
import java.lang.reflect.Proxy;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import static org.junit.Assert.*;

public class CachedCallsTest {
    private static final String HOUSEHOLD = "7e0dd3fb-30d6-4df5-b495-fd067610db79";
    private static final String USER = "{\"id\":\"5e0dd3fb-30d6-4df5-b495-fd067610db79\","
            + "\"createdAt\":\"2026-01-01T00:00:00.000Z\",\"updatedAt\":\"2026-01-01T00:00:00.000Z\","
            + "\"username\":\"alice\",\"displayName\":\"Alice\",\"zoneId\":\"UTC\",\"householdId\":null}";

    @Test public void offlineSnapshotPrecedesErrorAndOldWritesCannotCrossSessions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            sessions.installIfCurrent(0, "token-a", "user-a", HOUSEHOLD);
            Queue<Runnable> io = new ArrayDeque<>();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            Map<String, CacheEntry> entries = new HashMap<>();
            ApiCalls calls = new ApiCalls(sessions, main::add, () -> fail("unexpected 401"), new CacheStore(dao(entries), io::add, schemas()));
            RealApiProvider api = new RealApiProvider(server.url("/").toString(), sessions);
            AtomicReference<CachedResult<User>> result = new AtomicReference<>();
            AtomicReference<ApiError> error = new AtomicReference<>();
            ApiCallback<CachedResult<User>> callback = new ApiCallback<CachedResult<User>>() {
                @Override public void onSuccess(CachedResult<User> value) { result.set(value); }
                @Override public void onError(ApiError value) { error.set(value); }
            };

            server.enqueue(new MockResponse().setBody(USER));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main);
            assertFalse(result.get().stale);
            assertEquals("alice", result.get().data.getUsername());
            runIo(io);
            assertEquals(1, entries.size());

            result.set(null);
            server.enqueue(new MockResponse().setBody("{\"username\":\"missing-required-fields\"}"));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main);
            assertTrue(result.get().stale);
            runMain(main);
            assertEquals("INVALID_RESPONSE", error.get().code);
            assertTrue(io.isEmpty());
            assertEquals(1, entries.size());

            result.set(null);
            error.set(null);
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main);
            assertTrue(result.get().stale);
            runMain(main);
            assertEquals("NETWORK_ERROR", error.get().code);
            assertEquals(1, entries.size());

            server.enqueue(new MockResponse().setBody(USER));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main); // Cached snapshot.
            runMain(main); // Fresh response queues an asynchronous cache write.
            sessions.clear();
            sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "user-b", null);
            runIo(io); // Old write sees the changed session and is discarded.
            runIo(io); // Logout removes only the old user's snapshots.
            assertTrue(entries.isEmpty());
            assertEquals("user-b", sessions.currentUserId());
            assertThrows(IllegalArgumentException.class, () -> calls.enqueueCached(
                    api.auth().login(new edu.xjtlu.civa.api.model.LoginRequest().username("a").password("password")),
                    "token", AuthSession.class, new ApiCallback<CachedResult<AuthSession>>() {
                        @Override public void onSuccess(CachedResult<AuthSession> value) { fail(); }
                        @Override public void onError(ApiError value) { fail(); }
                    }));
        }
    }

    @Test public void revokedHouseholdClearsOnlyItsCacheAndKeepsLogin() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            sessions.installIfCurrent(0, "token-a", "user-a", HOUSEHOLD);
            Queue<Runnable> io = new ArrayDeque<>();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            Map<String, CacheEntry> entries = new HashMap<>();
            entries.put("house", new CacheEntry("house", "user-a", HOUSEHOLD, "{}", 1));
            entries.put("personal", new CacheEntry("personal", "user-a", null, "{}", 1));
            entries.put("other", new CacheEntry("other", "user-b", HOUSEHOLD, "{}", 1));
            AtomicInteger unauthorized = new AtomicInteger();
            AtomicInteger reviewMarked = new AtomicInteger();
            ApiCalls calls = new ApiCalls(sessions, main::add, unauthorized::incrementAndGet, new CacheStore(dao(entries, reviewMarked), io::add, schemas()));
            RealApiProvider api = new RealApiProvider(server.url("/").toString(), sessions);
            server.enqueue(new MockResponse().setResponseCode(403).setBody(
                    "{\"code\":\"HOUSEHOLD_ACCESS_REVOKED\",\"message\":\"revoked\",\"traceId\":\"t\",\"fieldErrors\":[]}"));
            AtomicReference<ApiError> error = new AtomicReference<>();
            calls.enqueueCached(api.household().getHousehold(UUID.fromString(HOUSEHOLD)), "home",
                    edu.xjtlu.civa.api.model.Household.class, new ApiCallback<CachedResult<edu.xjtlu.civa.api.model.Household>>() {
                @Override public void onSuccess(CachedResult<edu.xjtlu.civa.api.model.Household> value) { fail(); }
                @Override public void onError(ApiError value) { error.set(value); }
            });
            runIo(io);
            runMain(main);
            assertEquals("HOUSEHOLD_ACCESS_REVOKED", error.get().code);
            assertTrue(sessions.isLoggedIn());
            assertNull(sessions.currentHouseholdId());
            assertEquals(0, unauthorized.get());
            runIo(io);
            assertFalse(entries.containsKey("house"));
            assertTrue(entries.containsKey("personal"));
            assertTrue(entries.containsKey("other"));
            assertEquals(1, reviewMarked.get());
        }
    }

    @Test public void unauthorizedCachedRequestReportsErrorAndClearsSession() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            sessions.installIfCurrent(0, "token-a", "user-a", null);
            Queue<Runnable> io = new ArrayDeque<>();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            AtomicInteger unauthorized = new AtomicInteger();
            AtomicReference<ApiError> error = new AtomicReference<>();
            ApiCalls calls = new ApiCalls(sessions, main::add, unauthorized::incrementAndGet, new CacheStore(dao(new HashMap<>()), io::add, schemas()));
            RealApiProvider api = new RealApiProvider(server.url("/").toString(), sessions);
            server.enqueue(new MockResponse().setResponseCode(401).setBody(
                    "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Login\",\"traceId\":\"t\",\"fieldErrors\":[]}"));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, new ApiCallback<CachedResult<User>>() {
                @Override public void onSuccess(CachedResult<User> value) { fail(); }
                @Override public void onError(ApiError value) { error.set(value); }
            });
            runIo(io);
            runMain(main);
            assertFalse(sessions.isLoggedIn());
            assertEquals(1, unauthorized.get());
            assertEquals("UNAUTHENTICATED", error.get().code);
            runIo(io);
        }
    }

    @Test public void rawCachedResponsesRejectMissingNullableDuplicatesAndBadUuid() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            sessions.installIfCurrent(0, "token-a", "user-a", null);
            Queue<Runnable> io = new ArrayDeque<>();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            Map<String, CacheEntry> entries = new HashMap<>();
            ApiCalls calls = new ApiCalls(sessions, main::add, () -> fail("unexpected 401"), new CacheStore(dao(entries), io::add, schemas()));
            RealApiProvider api = new RealApiProvider(server.url("/").toString(), sessions);
            AtomicReference<ApiError> error = new AtomicReference<>();
            AtomicReference<CachedResult<User>> result = new AtomicReference<>();
            ApiCallback<CachedResult<User>> callback = new ApiCallback<CachedResult<User>>() {
                @Override public void onSuccess(CachedResult<User> value) { result.set(value); }
                @Override public void onError(ApiError value) { error.set(value); }
            };
            String[] invalid = {
                    USER.replace(",\"householdId\":null", ""),
                    USER.replace("5e0dd3fb-30d6-4df5-b495-fd067610db79", "1-1-1-1-1"),
                    USER.replace("\"username\":\"alice\"", "\"username\":\"alice\",\"username\":\"other\""),
                    USER + "{}"
            };
            for (String body : invalid) {
                error.set(null);
                server.enqueue(new MockResponse().setBody(body));
                calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
                runIo(io);
                runMain(main);
                assertEquals("INVALID_RESPONSE", error.get().code);
                assertNull(result.get());
                assertTrue(entries.isEmpty());
            }

            int nameOffset = USER.indexOf("Alice");
            Buffer malformedUtf8 = new Buffer().writeUtf8(USER.substring(0, nameOffset))
                    .writeByte(0xc3).writeByte(0x28).writeUtf8(USER.substring(nameOffset + "Alice".length()));
            error.set(null);
            server.enqueue(new MockResponse().setBody(malformedUtf8));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main);
            assertEquals("INVALID_RESPONSE", error.get().code);
            assertTrue(entries.isEmpty());

            String preference = "{\"userId\":\"" + HOUSEHOLD + "\",\"likedTags\":[\"soup\",\"soup\"],\"excludedIngredientIds\":[],\"version\":1}";
            AtomicReference<ApiError> duplicateError = new AtomicReference<>();
            server.enqueue(new MockResponse().setBody(preference));
            calls.enqueueCached(api.profile().getPreferences(), "preferences", Preference.class, new ApiCallback<CachedResult<Preference>>() {
                @Override public void onSuccess(CachedResult<Preference> value) { fail("duplicate list accepted"); }
                @Override public void onError(ApiError value) { duplicateError.set(value); }
            });
            runIo(io);
            runMain(main);
            assertEquals("INVALID_RESPONSE", duplicateError.get().code);

            server.enqueue(new MockResponse().setBody(USER.replaceFirst("\\{", "{\"futureField\":true,")));
            calls.enqueueCached(api.profile().getMe(), "me", User.class, callback);
            runIo(io);
            runMain(main);
            assertNotNull(result.get());
            assertEquals("alice", result.get().data.getUsername());
            assertFalse(result.get().stale);
            runIo(io);
            assertEquals(1, entries.size());
        }
    }

    private static JsonObject schemas() throws Exception {
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("civa.contract")))) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("components").getAsJsonObject("schemas");
        }
    }

    private static LocalDataDao dao(Map<String, CacheEntry> entries) {
        return dao(entries, new AtomicInteger());
    }
    private static LocalDataDao dao(Map<String, CacheEntry> entries, AtomicInteger reviewMarked) {
        return (LocalDataDao) Proxy.newProxyInstance(LocalDataDao.class.getClassLoader(), new Class<?>[]{LocalDataDao.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "cache": {
                    CacheEntry item = entries.get(args[0]);
                    return item != null && item.userId.equals(args[1]) && java.util.Objects.equals(item.householdId, args[2]) ? item : null;
                }
                case "putCache": { CacheEntry item = (CacheEntry) args[0]; entries.put(item.cacheKey, item); return null; }
                case "deleteUserCache": {
                    int before = entries.size(); entries.values().removeIf(item -> item.userId.equals(args[0])); return before - entries.size();
                }
                case "deleteHouseholdCache": {
                    int before = entries.size(); entries.values().removeIf(item -> item.userId.equals(args[0]) && java.util.Objects.equals(item.householdId, args[1])); return before - entries.size();
                }
                case "markHouseholdForReview": { reviewMarked.incrementAndGet(); return 1; }
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    private static void runIo(Queue<Runnable> io) { Runnable task = io.poll(); assertNotNull("No cache task", task); task.run(); }
    private static void runMain(BlockingQueue<Runnable> main) throws Exception { Runnable task = main.poll(10, TimeUnit.SECONDS); assertNotNull("No main callback", task); task.run(); }
}
