package edu.xjtlu.civa.core.data;

import androidx.annotation.Nullable;

import edu.xjtlu.civa.core.ui.ApiError;

import java.util.List;
import java.util.Objects;
import retrofit2.Call;

/**
 * Durable idempotent write: record the intent, submit once, confirm on success.
 *
 * <p>Shared by every feature so the "record before the first network attempt, never replay blindly"
 * rule lives in one place. The caller decides the operation name, the key, the canonical payload and
 * the scope; this class never invents a key and never retries without an existing pending record.
 */
public final class IdempotentWriter {
    /** The stored intent does not match this call, so a human must check before anything is sent. */
    public static final String PENDING_NEEDS_REVIEW = "PENDING_WRITE_NEEDS_REVIEW";

    private final ApiCalls calls;
    private final SessionStore sessions;

    public IdempotentWriter(ApiCalls calls, SessionStore sessions) {
        this.calls = Objects.requireNonNull(calls);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** Result callbacks, delivered on the main thread by {@link ApiCalls}. */
    public interface Result<T> {
        void onSuccess(T value);

        void onError(ApiError error);
    }

    /**
     * Persists the intent, then submits it. A process death between the two cannot turn a retry
     * into a duplicate, because the key and payload are already on disk.
     *
     * @param householdId owning household, or {@code null} for a personal write
     */
    public <T> void write(
            String operation,
            String key,
            String payload,
            @Nullable String householdId,
            Call<T> call,
            Result<T> result) {
        String actor = sessions.currentUserId();
        calls.pendingWrites()
                .record(
                        key,
                        actor,
                        householdId,
                        operation,
                        payload,
                        null,
                        null,
                        new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void ignored) {
                                if (!Objects.equals(actor, sessions.currentUserId())) return;
                                submit(operation, key, payload, call, actor, result);
                            }

                            @Override
                            public void onError(ApiError error) {
                                result.onError(error);
                            }
                        });
    }

    /**
     * Submits only if a pending record with the same operation and payload exists; otherwise it
     * refuses with {@link #PENDING_NEEDS_REVIEW} so the user can check the real server state.
     */
    public <T> void retry(
            String operation,
            String key,
            String payload,
            @Nullable String householdId,
            Call<T> call,
            Result<T> result) {
        String actor = sessions.currentUserId();
        calls.pendingWrites()
                .listPending(
                        actor,
                        new ApiCallback<List<PendingWrite>>() {
                            @Override
                            public void onSuccess(List<PendingWrite> values) {
                                if (!Objects.equals(actor, sessions.currentUserId())) return;
                                for (PendingWrite value : values) {
                                    if (!value.key.equals(key)) continue;
                                    if (value.needsReview
                                            || !value.operationId.equals(operation)
                                            || !value.canonicalPayload.equals(payload)) {
                                        result.onError(new ApiError(PENDING_NEEDS_REVIEW, "待确认请求需要人工核对", null,
                                                java.util.Collections.emptyList()));
                                        return;
                                    }
                                    submit(operation, key, payload, call, actor, result);
                                    return;
                                }
                                write(operation, key, payload, householdId, call, result);
                            }

                            @Override
                            public void onError(ApiError error) {
                                result.onError(error);
                            }
                        });
    }

    private <T> void submit(
            String operation,
            String key,
            String payload,
            Call<T> call,
            String actor,
            Result<T> result) {
        calls.enqueue(
                call,
                new ApiCallback<T>() {
                    @Override
                    public void onSuccess(T value) {
                        calls.pendingWrites()
                                .confirm(
                                        actor,
                                        key,
                                        new ApiCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void ignored) {
                                                if (!Objects.equals(actor, sessions.currentUserId())) return;
                                                result.onSuccess(value);
                                            }

                                            @Override
                                            public void onError(ApiError error) {
                                                if (!Objects.equals(actor, sessions.currentUserId())) return;
                                                // The server already saved it; only the local marker is stale.
                                                result.onSuccess(value);
                                            }
                                        });
                    }

                    @Override
                    public void onError(ApiError error) {
                        result.onError(error);
                    }
                });
    }
}
