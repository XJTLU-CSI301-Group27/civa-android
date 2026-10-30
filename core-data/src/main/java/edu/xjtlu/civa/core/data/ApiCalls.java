package edu.xjtlu.civa.core.data;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.xjtlu.civa.core.ui.ApiError;
import java.io.IOException;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Asynchronous calls with session-isolated read-only snapshots. */
public final class ApiCalls {
    static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final SessionStore sessions;
    private final Executor main;
    private final Runnable unauthorized;
    private final CacheStore cache;
    private final PendingWriteStore pendingWrites;
    private final Map<Call<?>, Long> inFlight = new ConcurrentHashMap<>();

    public ApiCalls(SessionStore sessions) {
        this(sessions, command -> new Handler(Looper.getMainLooper()).post(command), () -> { }, null);
    }

    public ApiCalls(SessionStore sessions, Runnable unauthorized) {
        this(sessions, command -> new Handler(Looper.getMainLooper()).post(command), unauthorized, null);
    }

    public ApiCalls(Context context, SessionStore sessions, Runnable unauthorized) {
        this(sessions, command -> new Handler(Looper.getMainLooper()).post(command), unauthorized, new CacheStore(context));
    }

    ApiCalls(SessionStore sessions, Executor main) {
        this(sessions, main, () -> { }, null);
    }

    ApiCalls(SessionStore sessions, Executor main, Runnable unauthorized) {
        this(sessions, main, unauthorized, null);
    }

    ApiCalls(SessionStore sessions, Executor main, Runnable unauthorized, CacheStore cache) {
        this.sessions = sessions;
        this.main = main;
        this.unauthorized = Objects.requireNonNull(unauthorized);
        this.cache = cache;
        this.pendingWrites = cache == null ? null : new PendingWriteStore(cache.dao, cache.io, main);
        sessions.onInvalidation(event -> {
            cancelGeneration(event.old.generation);
            if (cache != null && event.old.userId != null) {
                if (event.householdOnly && event.old.householdId != null) cache.clearHousehold(event.old.userId, event.old.householdId);
                else if (!event.householdOnly) cache.clearUser(event.old.userId);
            }
        });
    }

    boolean uses(SessionStore store) { return sessions == store; }

    /** Durable pending-write store for writes submitted through this call runner; requires local storage. */
    public PendingWriteStore pendingWrites() {
        if (pendingWrites == null) throw new IllegalStateException("Local storage is not configured");
        return pendingWrites;
    }

    // Revocation must survive invalidation of the old session and must never use a new token.
    <T> void enqueueDetached(Call<T> call, long generation, ApiCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> request, Response<T> response) {
                if (response.isSuccessful()) deliver(generation, () -> callback.onSuccess(response.body()));
                else {
                    ApiError error = parseError(response.errorBody());
                    deliver(generation, () -> callback.onError(error));
                }
            }
            @Override public void onFailure(Call<T> request, Throwable failure) {
                String code = request.isCanceled() ? "CANCELLED" : failure instanceof SocketTimeoutException ? "TIMEOUT" : "NETWORK_ERROR";
                deliver(generation, () -> callback.onError(localError(code)));
            }
        });
    }

    public <T> void enqueue(Call<T> call, ApiCallback<T> callback) {
        synchronized (sessions) {
          long generation = sessions.snapshot().generation;
          SessionStore.Snapshot bound = call.request().tag(SessionStore.Snapshot.class);
          if (bound != null && bound.generation != generation) {
              call.cancel();
              return;
          }
          if (inFlight.putIfAbsent(call, generation) != null) throw new IllegalStateException("Call already registered");
          try {
            call.enqueue(new Callback<T>() {
                @Override public void onResponse(Call<T> request, Response<T> response) {
                    inFlight.remove(request);
                    if (response.isSuccessful()) {
                        T value = response.body();
                        main.execute(() -> {
                            synchronized (sessions) {
                                if (sessions.snapshot().generation == generation) callback.onSuccess(value);
                                else if (value instanceof ResponseBody) ((ResponseBody) value).close();
                            }
                        });
                    } else {
                        ApiError error = parseError(response.errorBody());
                        boolean clearSession = response.code() == 401 && !request.request().url().encodedPath().startsWith("/api/v1/auth/");
                        String requestPath = request.request().url().encodedPath();
                        main.execute(() -> {
                          synchronized (sessions) {
                            if (sessions.snapshot().generation != generation) return;
                            if (clearSession) {
                                if (!sessions.clearIfCurrent(generation)) return;
                                if (sessions.snapshot().generation != generation + 1) return;
                                unauthorized.run();
                                if (sessions.snapshot().generation != generation + 1) return;
                            } else if (response.code() == 403 && "HOUSEHOLD_ACCESS_REVOKED".equals(error.code)) {
                                SessionStore.Snapshot current = sessions.snapshot();
                                if (current.householdId != null && belongsToHousehold(requestPath, current.householdId)) {
                                    if (!sessions.revokeHouseholdIfCurrent(generation, current.householdId)) return;
                                    if (sessions.snapshot().generation != generation + 1) return;
                                }
                            }
                            callback.onError(error);
                          }
                        });
                    }
                }

                @Override public void onFailure(Call<T> request, Throwable failure) {
                    inFlight.remove(request);
                    String code = request.isCanceled() ? "CANCELLED" : failure instanceof RawResponseValidation.InvalidResponseException
                            ? "INVALID_RESPONSE" : failure instanceof SocketTimeoutException ? "TIMEOUT" : "NETWORK_ERROR";
                    deliver(generation, () -> callback.onError(localError(code)));
                }
            });
          } catch (RuntimeException ex) {
            inFlight.remove(call);
            throw ex;
          }
        }
    }

    public <T> void enqueueCached(Call<T> call, String cacheKey, Class<T> type, ApiCallback<CachedResult<T>> callback) {
        if (cache == null) throw new IllegalStateException("Cache is not configured");
        Objects.requireNonNull(cacheKey, "cacheKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(callback, "callback");
        if (cacheKey.isEmpty() || !DtoChecks.permitted(type)) throw new IllegalArgumentException("Unsupported cached type or key");
        SessionStore.Snapshot snapshot;
        String key;
        String householdScope;
        synchronized (sessions) {
            snapshot = sessions.snapshot();
            if (snapshot.token == null || snapshot.userId == null) throw new IllegalStateException("Login required for cache");
            var request = call.request();
            if (!request.method().equals("GET")) throw new IllegalArgumentException("Only GET may be cached");
            householdScope = householdScope(request.url().encodedPath(), snapshot.householdId);
            SessionStore.Snapshot bound = request.tag(SessionStore.Snapshot.class);
            if (bound != null && bound.generation != snapshot.generation) { call.cancel(); return; }
            RawResponseValidation validation = request.tag(RawResponseValidation.class);
            if (validation == null) throw new IllegalArgumentException("Cached calls must use the real API provider");
            if (inFlight.putIfAbsent(call, snapshot.generation) != null) throw new IllegalStateException("Call already registered");
            try { validation.configure(type, cache.schemas); }
            catch (RuntimeException ex) { inFlight.remove(call, snapshot.generation); throw ex; }
            key = cacheKey(snapshot, request.url().toString(), cacheKey);
        }
        cache.io.execute(() -> {
            CacheEntry entry = null;
            try {
                synchronized (sessions) {
                    if (sameSession(snapshot)) entry = cache.dao.cache(key, snapshot.userId, householdScope);
                }
            } catch (RuntimeException ignored) { /* The network request still runs if local reads fail. */ }
            if (entry != null) {
                try {
                    T data = GSON.fromJson(entry.payloadJson, type);
                    if (type.isInstance(data) && DtoChecks.valid(data, cache.schemas)) {
                        CachedResult<T> cached = new CachedResult<>(data, true, entry.fetchedAt);
                        main.execute(() -> { synchronized (sessions) { if (sameSession(snapshot)) callback.onSuccess(cached); } });
                    }
                } catch (RuntimeException ignored) { /* Invalid stored data is not displayed. */ }
            }
            synchronized (sessions) {
                inFlight.remove(call, snapshot.generation);
                if (!sameSession(snapshot)) { call.cancel(); return; }
                enqueue(call, new ApiCallback<T>() {
                    @Override public void onSuccess(T data) {
                        if (!sameSession(snapshot)) return;
                        if (!type.isInstance(data) || !DtoChecks.valid(data, cache.schemas)) { callback.onError(localError("INVALID_RESPONSE")); return; }
                        long fetchedAt = System.currentTimeMillis();
                        cache.io.execute(() -> {
                            try {
                                synchronized (sessions) {
                                    if (sameSession(snapshot)) cache.dao.putCache(new CacheEntry(key, snapshot.userId,
                                            householdScope, GSON.toJson(data), fetchedAt));
                                }
                            } catch (RuntimeException ignored) { /* A successful network read remains usable. */ }
                        });
                        callback.onSuccess(new CachedResult<>(data, false, fetchedAt));
                    }
                    @Override public void onError(ApiError error) {
                        SessionStore.Snapshot now = sessions.snapshot();
                        boolean justRevoked = "HOUSEHOLD_ACCESS_REVOKED".equals(error.code)
                                && now.generation == snapshot.generation + 1 && now.householdId == null
                                && Objects.equals(now.userId, snapshot.userId) && Objects.equals(now.token, snapshot.token);
                        boolean justLoggedOut = now.generation == snapshot.generation + 1 && now.token == null && now.userId == null;
                        if (justRevoked || justLoggedOut || sameSession(snapshot)) callback.onError(error);
                    }
                });
            }
        });
    }

    private boolean sameSession(SessionStore.Snapshot snapshot) {
        SessionStore.Snapshot now = sessions.snapshot();
        return now.generation == snapshot.generation && Objects.equals(now.userId, snapshot.userId)
                && Objects.equals(now.householdId, snapshot.householdId);
    }

    private static boolean belongsToHousehold(String path, String householdId) {
        String[] parts = path.split("/", 6);
        return parts.length >= 5 && parts[1].equals("api") && parts[2].equals("v1")
                && parts[3].equals("households") && parts[4].equalsIgnoreCase(householdId);
    }

    private static String householdScope(String path, String householdId) {
        String[] parts = path.split("/", 6);
        if (parts.length >= 5 && parts[1].equals("api") && parts[2].equals("v1") && parts[3].equals("households")) {
            if (householdId == null || !parts[4].equalsIgnoreCase(householdId)) {
                throw new IllegalArgumentException("Cached household must match the current session");
            }
            return householdId;
        }
        return null;
    }

    private static String cacheKey(SessionStore.Snapshot session, String url, String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{"schema-validated-v2", session.userId, session.householdId, url, key}) {
                byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                int length = value == null ? -1 : bytes.length;
                digest.update(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
                digest.update(bytes);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    void cancelGeneration(long generation) {
        inFlight.forEach((call, owner) -> { if (owner == generation) call.cancel(); });
    }

    void deliver(long generation, Runnable callback) {
        main.execute(() -> {
            // ponytail: callbacks hold the session lock; use a serialized session dispatcher if callbacks become slow.
            synchronized (sessions) { if (sessions.snapshot().generation == generation) callback.run(); }
        });
    }

    private static ApiError parseError(ResponseBody body) {
        if (body == null) return localError("INVALID_RESPONSE");
        try (ResponseBody ignored = body; Reader reader = body.charStream()) {
            char[] chars = new char[65537];
            int length = 0;
            int count;
            while (length < chars.length && (count = reader.read(chars, length, chars.length - length)) != -1) length += count;
            if (length == chars.length) return localError("INVALID_RESPONSE");
            WireError wire = GSON.fromJson(new String(chars, 0, length), WireError.class);
            if (wire == null || wire.code == null || wire.message == null || wire.traceId == null || wire.fieldErrors == null) return localError("INVALID_RESPONSE");
            List<ApiError.FieldError> errors = new ArrayList<>();
            for (WireField field : wire.fieldErrors) {
                if (field == null || field.field == null || field.message == null) return localError("INVALID_RESPONSE");
                errors.add(new ApiError.FieldError(field.field, field.message));
            }
            return new ApiError(wire.code, wire.message, wire.traceId, errors);
        } catch (IOException | RuntimeException ex) {
            return localError("INVALID_RESPONSE");
        }
    }

    static ApiError localError(String code) {
        return new ApiError(code, "请求未完成", null, Collections.emptyList());
    }

    private static final class WireError { String code; String message; String traceId; List<WireField> fieldErrors; }
    private static final class WireField { String field; String message; }
}
