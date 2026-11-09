package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.api.model.LoginRequest;
import edu.xjtlu.civa.api.model.RegisterRequest;
import edu.xjtlu.civa.api.model.User;
import edu.xjtlu.civa.core.ui.ApiError;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthRepositoryTest {
    private static final String USER = "{\"id\":\"7e0dd3fb-30d6-4df5-b495-fd067610db79\","
            + "\"createdAt\":\"2026-01-01T00:00:00.000Z\",\"updatedAt\":\"2026-01-01T00:00:00.000Z\","
            + "\"username\":\"alice\",\"displayName\":\"Alice\",\"zoneId\":\"UTC\",\"householdId\":null}";
    private static final String SESSION = "{\"token\":\"token-a\",\"expiresAt\":\"2099-01-01T00:00:00.000Z\",\"user\":" + USER + "}";

    @Test public void loginInstallsOnlyCurrentSessionAndLogoutRevokesCapturedToken() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            ApiCalls calls = new ApiCalls(sessions, main::add);
            RealApiProvider provider = new RealApiProvider(server.url("/").toString(), sessions);
            AuthRepository auth = new AuthRepository(provider, sessions, calls);
            assertThrows(IllegalArgumentException.class, () -> new AuthRepository(provider, new SessionStore(), calls));
            LoginRequest request = new LoginRequest().username("alice").password("long-password");
            AtomicReference<String> invalidResponse = new AtomicReference<>();
            server.enqueue(new MockResponse().setBody("{\"token\":\"token-x\"}"));
            auth.login(request, new ApiCallback<User>() {
                @Override public void onSuccess(User value) { fail("invalid session accepted"); }
                @Override public void onError(ApiError error) { invalidResponse.set(error.code); }
            });
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable invalid = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(invalid);
            invalid.run();
            assertEquals("INVALID_RESPONSE", invalidResponse.get());
            assertFalse(sessions.isLoggedIn());

            server.enqueue(new MockResponse().setBody(SESSION));
            auth.login(request, new ApiCallback<User>() {
                @Override public void onSuccess(User value) { fail("cancelled login accepted"); }
                @Override public void onError(ApiError error) { fail("cancelled login delivered"); }
            });
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable cancelled = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(cancelled);
            auth.cancelLogin();
            cancelled.run();
            assertFalse(sessions.isLoggedIn());

            server.enqueue(new MockResponse().setResponseCode(201).setBody(USER));
            AtomicReference<User> registered = new AtomicReference<>();
            auth.register(new RegisterRequest().username("alice").displayName("Alice").password("long-password").zoneId("UTC"), new ApiCallback<User>() {
                @Override public void onSuccess(User value) { registered.set(value); }
                @Override public void onError(ApiError error) { fail(error.code); }
            });
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable registration = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(registration);
            registration.run();
            assertEquals("alice", registered.get().getUsername());
            assertFalse(sessions.isLoggedIn());
            AtomicReference<User> user = new AtomicReference<>();
            ApiCallback<User> callback = new ApiCallback<User>() {
                @Override public void onSuccess(User value) { user.set(value); }
                @Override public void onError(ApiError error) { fail(error.code); }
            };
            server.enqueue(new MockResponse().setBody(SESSION));
            auth.login(request, callback);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable login = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(login);
            assertFalse(sessions.isLoggedIn());
            login.run();
            assertEquals("alice", user.get().getUsername());
            assertEquals("token-a", sessions.snapshot().token);

            user.set(null);
            server.enqueue(new MockResponse().setBody(SESSION));
            auth.login(request, callback);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable stale = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(stale);
            sessions.clear();
            sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "user-b", null);
            stale.run();
            assertNull(user.get());
            assertEquals("token-b", sessions.snapshot().token);

            AtomicBoolean logoutDelivered = new AtomicBoolean();
            server.enqueue(new MockResponse().setResponseCode(204));
            auth.logout(new ApiCallback<Void>() {
                @Override public void onSuccess(Void value) { logoutDelivered.set(true); }
                @Override public void onError(ApiError error) { logoutDelivered.set(true); }
            });
            assertFalse(sessions.isLoggedIn());
            assertEquals("Bearer token-b", server.takeRequest(5, TimeUnit.SECONDS).getHeader("Authorization"));
            Runnable loggedOut = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(loggedOut);
            sessions.installIfCurrent(sessions.snapshot().generation, "token-c", "user-c", null);
            loggedOut.run();
            assertFalse(logoutDelivered.get());
            assertEquals("token-c", sessions.snapshot().token);
        }
    }

    @Test public void refreshMeUpdatesSessionHouseholdId() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
            ApiCalls calls = new ApiCalls(sessions, main::add);
            RealApiProvider provider = new RealApiProvider(server.url("/").toString(), sessions);
            AuthRepository auth = new AuthRepository(provider, sessions, calls);
            sessions.installIfCurrent(0, "token-a", "user-a", null);

            String householdId = "8e0dd3fb-30d6-4df5-b495-fd067610db79";
            server.enqueue(new MockResponse().setBody(USER.replace("\"householdId\":null", "\"householdId\":\"" + householdId + "\"")));
            AtomicReference<User> refreshed = new AtomicReference<>();
            auth.refreshMe(new ApiCallback<User>() {
                @Override public void onSuccess(User value) { refreshed.set(value); }
                @Override public void onError(ApiError error) { fail(error.code); }
            });
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            Runnable run = main.poll(5, TimeUnit.SECONDS);
            assertNotNull(run);
            run.run();
            assertEquals(householdId, sessions.currentHouseholdId());
            assertEquals(householdId, refreshed.get().getHouseholdId().toString());
        }
    }
}
