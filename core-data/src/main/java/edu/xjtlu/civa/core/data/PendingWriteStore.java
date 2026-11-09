package edu.xjtlu.civa.core.data;

import androidx.annotation.Nullable;
import edu.xjtlu.civa.core.ui.ApiError;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** Durable record of submitted-but-unconfirmed writes; never an automatic offline write queue. */
public final class PendingWriteStore {
    private final LocalDataDao dao;
    private final Executor io;
    private final Executor main;

    PendingWriteStore(LocalDataDao dao, Executor io, Executor main) {
        this.dao = dao;
        this.io = io;
        this.main = main;
    }

    /**
     * Persists the intent before its first network attempt, so a process death cannot turn a retry
     * into a duplicate. The row stores no token or password: auth stays inside core-data and
     * idempotent bodies carry only business fields. Callers must wait for {@code onSuccess} before
     * submitting the network request.
     */
    public void record(String key, String userId, @Nullable String householdId, String operationId,
                       String canonicalPayload, @Nullable String mediaPrivatePath, @Nullable String mediaSha256,
                       ApiCallback<Void> callback) {
        require(key, "key");
        require(userId, "userId");
        require(operationId, "operationId");
        require(canonicalPayload, "canonicalPayload");
        PendingWrite intent = new PendingWrite(key, userId, householdId, operationId, canonicalPayload,
                System.currentTimeMillis(), false, mediaPrivatePath, mediaSha256);
        io.execute(() -> {
            try {
                dao.insertPending(intent);
                deliver(() -> callback.onSuccess(null));
            } catch (RuntimeException ex) {
                deliver(() -> callback.onError(storageError()));
            }
        });
    }

    /** Account-scoped recovery list. Callers re-check permission via getMe before acting; never auto-replay. */
    public void listPending(String userId, ApiCallback<List<PendingWrite>> callback) {
        require(userId, "userId");
        io.execute(() -> {
            try {
                List<PendingWrite> items = dao.pendingForUser(userId);
                deliver(() -> callback.onSuccess(items));
            } catch (RuntimeException ex) {
                deliver(() -> callback.onError(storageError()));
            }
        });
    }

    /** Deletes on confirmed success or explicit user abandon. Abandon does not roll back the server. */
    public void confirm(String userId, String key, ApiCallback<Void> callback) {
        require(userId, "userId");
        require(key, "key");
        io.execute(() -> {
            try {
                dao.deletePending(userId, key);
                deliver(() -> callback.onSuccess(null));
            } catch (RuntimeException ex) {
                deliver(() -> callback.onError(storageError()));
            }
        });
    }

    private static void require(String value, String name) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
    }

    private void deliver(Runnable task) { main.execute(task); }

    private static ApiError storageError() {
        return new ApiError("LOCAL_STORAGE_ERROR", "本地存储不可用", null, Collections.emptyList());
    }
}
