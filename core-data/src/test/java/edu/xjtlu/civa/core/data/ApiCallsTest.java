package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.core.ui.ApiError;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Streaming;
import static org.junit.Assert.*;

public class ApiCallsTest {
    private final MockWebServer server = new MockWebServer();
    private final BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    private final SessionStore sessions = new SessionStore();
    private ApiCalls calls;
    private final AtomicInteger unauthorized = new AtomicInteger();
    private Endpoints endpoints;

    interface Endpoints {
        @GET("api/v1/me") Call<Void> me();
        @GET("api/v1/auth/login") Call<Void> login();
        @Streaming @GET("api/v1/media/1/content") Call<ResponseBody> media();
    }

    @Before public void setUp() throws Exception {
        server.start();
        calls = new ApiCalls(sessions, main::add, unauthorized::incrementAndGet);
        endpoints = new Retrofit.Builder().baseUrl(server.url("/")).build().create(Endpoints.class);
    }

    @After public void tearDown() throws Exception { server.shutdown(); }

    @Test public void noContentIsSuccessWithNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        AtomicBoolean success = new AtomicBoolean();
        calls.enqueue(endpoints.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { assertNull(value); success.set(true); }
            @Override public void onError(ApiError error) { fail(error.code); }
        });
        runNext();
        assertTrue(success.get());
    }

    @Test public void queuedOldResultIsDiscardedAfterAccountChange() throws Exception {
        sessions.installIfCurrent(sessions.snapshot().generation, "token-a", "user-a", null);
        server.enqueue(new MockResponse().setResponseCode(204));
        AtomicBoolean delivered = new AtomicBoolean();
        calls.enqueue(endpoints.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { delivered.set(true); }
            @Override public void onError(ApiError error) { delivered.set(true); }
        });
        Runnable pending = main.poll(10, TimeUnit.SECONDS);
        assertNotNull(pending);
        sessions.clear();
        sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "user-b", null);
        pending.run();
        assertFalse(delivered.get());
        assertEquals("user-b", sessions.currentUserId());
    }

    @Test public void unauthorizedClearsOnlyNonAuthRequests() throws Exception {
        sessions.installIfCurrent(sessions.snapshot().generation, "token-a", "user-a", null);
        String body = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Please log in\",\"traceId\":\"t\",\"fieldErrors\":[]}";
        AtomicReference<ApiError> error = new AtomicReference<>();
        ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { fail(); }
            @Override public void onError(ApiError value) { error.set(value); }
        };
        server.enqueue(new MockResponse().setResponseCode(401).setBody(body));
        calls.enqueue(endpoints.login(), callback);
        runNext();
        assertTrue(sessions.isLoggedIn());
        assertEquals(0, unauthorized.get());
        assertEquals("UNAUTHENTICATED", error.get().code);
        server.enqueue(new MockResponse().setResponseCode(401).setBody(body));
        calls.enqueue(endpoints.me(), callback);
        runNext();
        assertFalse(sessions.isLoggedIn());
        assertEquals(1, unauthorized.get());
        assertEquals("UNAUTHENTICATED", error.get().code);
        assertTrue(error.get().fieldErrors.isEmpty());
    }

    @Test public void oldUnauthorizedResponseDoesNotClearNewSessionOrNavigate() throws Exception {
        sessions.installIfCurrent(sessions.snapshot().generation, "token-a", "user-a", null);
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Login\",\"traceId\":\"t\",\"fieldErrors\":[]}"));
        AtomicBoolean delivered = new AtomicBoolean();
        calls.enqueue(endpoints.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { delivered.set(true); }
            @Override public void onError(ApiError error) { delivered.set(true); }
        });
        Runnable old = main.poll(10, TimeUnit.SECONDS);
        assertNotNull(old);
        sessions.clear();
        sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "user-b", null);
        old.run();
        assertFalse(delivered.get());
        assertEquals(0, unauthorized.get());
        assertEquals("user-b", sessions.currentUserId());
    }

    @Test public void staleStreamingResponseIsClosed() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            okhttp3.Response response = chain.proceed(chain.request());
            ResponseBody original = response.body();
            assertNotNull(original);
            ResponseBody tracked = new ResponseBody() {
                @Override public MediaType contentType() { return original.contentType(); }
                @Override public long contentLength() { return original.contentLength(); }
                @Override public BufferedSource source() { return original.source(); }
                @Override public void close() { closed.set(true); original.close(); }
            };
            return response.newBuilder().body(tracked).build();
        }).build();
        Endpoints streaming = new Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(Endpoints.class);
        sessions.installIfCurrent(sessions.snapshot().generation, "token-a", "user-a", null);
        server.enqueue(new MockResponse().setBody("image bytes"));
        calls.enqueue(streaming.media(), new ApiCallback<ResponseBody>() {
            @Override public void onSuccess(ResponseBody value) { fail("stale response delivered"); }
            @Override public void onError(ApiError error) { fail("stale error delivered"); }
        });
        Runnable pending = main.poll(10, TimeUnit.SECONDS);
        assertNotNull(pending);
        sessions.clear();
        pending.run();
        assertTrue(closed.get());
    }

    @Test public void logoutCancelsOldRequestWithoutCancellingNewAccount() throws Exception {
        sessions.installIfCurrent(sessions.snapshot().generation, "token-a", "user-a", null);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Call<Void> oldCall = endpoints.me();
        AtomicBoolean oldDelivered = new AtomicBoolean();
        calls.enqueue(oldCall, new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { oldDelivered.set(true); }
            @Override public void onError(ApiError error) { oldDelivered.set(true); }
        });
        assertNotNull(server.takeRequest(10, TimeUnit.SECONDS));
        sessions.clear();
        assertTrue(oldCall.isCanceled());
        sessions.installIfCurrent(sessions.snapshot().generation, "token-b", "user-b", null);
        server.enqueue(new MockResponse().setResponseCode(204));
        AtomicBoolean newDelivered = new AtomicBoolean();
        calls.enqueue(endpoints.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { newDelivered.set(true); }
            @Override public void onError(ApiError error) { fail(error.code); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!newDelivered.get() && System.nanoTime() < deadline) {
            Runnable next = main.poll(1, TimeUnit.SECONDS);
            if (next != null) next.run();
        }
        assertTrue(newDelivered.get());
        assertFalse(oldDelivered.get());
    }

    @Test public void disconnectReportsNetworkErrorWithoutSuccess() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).build();
        Endpoints offline = new Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(Endpoints.class);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        AtomicReference<ApiError> result = new AtomicReference<>();
        calls.enqueue(offline.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { fail("Disconnected request must not succeed"); }
            @Override public void onError(ApiError error) { result.set(error); }
        });
        runNext();
        assertEquals("NETWORK_ERROR", result.get().code);
        assertTrue(result.get().fieldErrors.isEmpty());
    }

    @Test public void malformedErrorBodyDoesNotBecomeSuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"code\":\"VALIDATION_ERROR\"}"));
        AtomicReference<ApiError> result = new AtomicReference<>();
        calls.enqueue(endpoints.me(), new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { fail("Malformed error must not succeed"); }
            @Override public void onError(ApiError error) { result.set(error); }
        });
        runNext();
        assertEquals("INVALID_RESPONSE", result.get().code);
        assertTrue(result.get().fieldErrors.isEmpty());
    }

    private void runNext() throws Exception {
        Runnable task = main.poll(10, TimeUnit.SECONDS);
        assertNotNull("No callback was posted", task);
        task.run();
    }
}
