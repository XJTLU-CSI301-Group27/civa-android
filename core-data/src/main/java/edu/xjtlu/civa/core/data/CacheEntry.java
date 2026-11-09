package edu.xjtlu.civa.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A read-only snapshot; never an authoritative business entity. */
@Entity(tableName = "cache_entries", indices = @Index(value = {"userId", "householdId"}))
final class CacheEntry {
    @PrimaryKey @NonNull public final String cacheKey;
    @NonNull public final String userId;
    @Nullable public final String householdId;
    @NonNull public final String payloadJson;
    public final long fetchedAt;

    CacheEntry(@NonNull String cacheKey, @NonNull String userId, @Nullable String householdId,
               @NonNull String payloadJson, long fetchedAt) {
        this.cacheKey = cacheKey; this.userId = userId; this.householdId = householdId;
        this.payloadJson = payloadJson; this.fetchedAt = fetchedAt;
    }
}
