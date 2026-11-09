package edu.xjtlu.civa.core.data;

import android.content.Context;
import androidx.room.Room;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Serializes cache reads, writes and invalidations; pending writes use separate queries. */
final class CacheStore {
    final LocalDataDao dao;
    final Executor io;
    final JsonObject schemas;

    CacheStore(Context context) {
        this(Room.databaseBuilder(context.getApplicationContext(), LocalDatabase.class, "civa-local.db").build().data(),
                Executors.newSingleThreadExecutor(), readSchemas(context));
        io.execute(() -> {
            try { dao.deleteCacheBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)); }
            catch (RuntimeException ignored) { /* Network reads remain available when local storage fails. */ }
        });
    }

    CacheStore(LocalDataDao dao, Executor io, JsonObject schemas) { this.dao = dao; this.io = io; this.schemas = schemas; }

    private static JsonObject readSchemas(Context context) {
        try (Reader reader = new InputStreamReader(context.getAssets().open("civa-cache-contract.json"), StandardCharsets.UTF_8)) {
            JsonObject contract = JsonParser.parseReader(reader).getAsJsonObject();
            return contract.getAsJsonObject("components").getAsJsonObject("schemas");
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Cache contract is unavailable", ex);
        }
    }

    void clearUser(String userId) {
        io.execute(() -> { try { dao.deleteUserCache(userId); } catch (RuntimeException ignored) { retryOnce(() -> dao.deleteUserCache(userId)); } });
    }

    void clearHousehold(String userId, String householdId) {
        io.execute(() -> {
            try { dao.deleteHouseholdCache(userId, householdId); } catch (RuntimeException ignored) { retryOnce(() -> dao.deleteHouseholdCache(userId, householdId)); }
            try { dao.markHouseholdForReview(userId, householdId); } catch (RuntimeException ignored) { retryOnce(() -> dao.markHouseholdForReview(userId, householdId)); }
        });
    }

    // ponytail: one retry for a transient local-storage failure; the 7-day TTL sweep stays the final backstop.
    private void retryOnce(Runnable clear) {
        io.execute(() -> { try { clear.run(); } catch (RuntimeException ignored) { } });
    }
}
