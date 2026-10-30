package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.api.model.LoginRequest;
import edu.xjtlu.civa.api.model.User;
import edu.xjtlu.civa.core.ui.ApiError;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import retrofit2.Call;
import static org.junit.Assert.*;

public class RealApiProviderTest {
    @Test public void bindsCredentialToRegisteredCallAndDoesNotAuthenticateLogin() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SessionStore sessions = new SessionStore();
            sessions.installIfCurrent(0, "token-a", "a", null);
            RealApiProvider provider = new RealApiProvider(server.url("/").toString(), sessions);
            Call<User> old = provider.profile().getMe();
            sessions.clear();
            sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "b", null);
            assertThrows(IllegalStateException.class, old::clone);
            ApiCalls calls = new ApiCalls(sessions, Runnable::run);
            calls.enqueue(old, new ApiCallback<User>() {
                @Override public void onSuccess(User user) { fail("stale result"); }
                @Override public void onError(ApiError error) { fail("stale result"); }
            });
            assertTrue(old.isCanceled());
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));

            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"username\":\"bob\",\"futureField\":true}"));
            assertEquals("bob", provider.profile().getMe().execute().body().getUsername());
            assertEquals("Bearer token-b", server.takeRequest(5, TimeUnit.SECONDS).getHeader("Authorization"));

            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"username\":\"bob\"}"));
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            calls.enqueue(provider.profile().getMe(), new ApiCallback<User>() {
                @Override public void onSuccess(User user) { result.set(user.getUsername()); done.countDown(); }
                @Override public void onError(ApiError error) { result.set(error.code); done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("bob", result.get());
            assertEquals("Bearer token-b", server.takeRequest(5, TimeUnit.SECONDS).getHeader("Authorization"));

            server.enqueue(new MockResponse().setResponseCode(401));
            provider.auth().login(new LoginRequest().username("bob").password("long-password")).execute();
            assertNull(server.takeRequest(5, TimeUnit.SECONDS).getHeader("Authorization"));
        }
    }
}
