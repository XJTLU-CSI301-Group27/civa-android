package edu.xjtlu.civa.core.data;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** In-memory session. The token and generation are accessible only inside core-data. */
public final class SessionStore {
    private String token;
    private String userId;
    private String householdId;
    private long generation;
    private final List<Consumer<Invalidation>> invalidationListeners = new ArrayList<>();

    @Nullable public synchronized String currentUserId() { return userId; }
    @Nullable public synchronized String currentHouseholdId() { return householdId; }
    public synchronized boolean isLoggedIn() { return token != null; }

    public synchronized void clear() {
        clearAndGetToken();
    }

    @Nullable synchronized String clearAndGetToken() {
        Snapshot old = snapshot();
        token = null;
        userId = null;
        householdId = null;
        generation++;
        notifyInvalidated(old, false);
        return old.token;
    }

    synchronized Snapshot snapshot() { return new Snapshot(token, userId, householdId, generation); }

    synchronized void onInvalidation(Consumer<Invalidation> listener) { invalidationListeners.add(listener); }

    private void notifyInvalidated(Snapshot old, boolean householdOnly) {
        Invalidation event = new Invalidation(old, householdOnly);
        for (Consumer<Invalidation> listener : new ArrayList<>(invalidationListeners)) listener.accept(event);
    }

    synchronized boolean clearIfCurrent(long expectedGeneration) {
        if (generation != expectedGeneration) return false;
        clearAndGetToken();
        return true;
    }

    static final class Snapshot {
        @Nullable final String token;
        @Nullable final String userId;
        @Nullable final String householdId;
        final long generation;

        private Snapshot(@Nullable String token, @Nullable String userId, @Nullable String householdId, long generation) {
            this.token = token;
            this.userId = userId;
            this.householdId = householdId;
            this.generation = generation;
        }
    }

    static final class Invalidation {
        final Snapshot old;
        final boolean householdOnly;
        Invalidation(Snapshot old, boolean householdOnly) { this.old = old; this.householdOnly = householdOnly; }
    }

    synchronized long installIfCurrent(long expectedGeneration, String newToken, String newUserId,
                                           @Nullable String newHouseholdId) {
        if (generation != expectedGeneration) return -1;
        Objects.requireNonNull(newToken, "token");
        Objects.requireNonNull(newUserId, "userId");
        if (newToken.isEmpty() || newUserId.isEmpty()) throw new IllegalArgumentException("Empty session field");
        Snapshot old = snapshot();
        token = newToken;
        userId = newUserId;
        householdId = newHouseholdId;
        generation++;
        notifyInvalidated(old, false);
        return generation;
    }

    synchronized boolean updateHouseholdIfCurrent(long expectedGeneration, @Nullable String newHouseholdId) {
        if (generation != expectedGeneration || token == null) return false;
        householdId = newHouseholdId;
        return true;
    }

    synchronized boolean revokeHouseholdIfCurrent(long expectedGeneration, String expectedHouseholdId) {
        if (generation != expectedGeneration || token == null || !expectedHouseholdId.equalsIgnoreCase(householdId)) return false;
        Snapshot old = snapshot();
        householdId = null;
        generation++;
        notifyInvalidated(old, true);
        return true;
    }
}
