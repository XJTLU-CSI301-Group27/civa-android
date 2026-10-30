package edu.xjtlu.civa.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Submitted, uncertain intent; never an automatic offline write queue. Stores no token or password. */
@Entity(tableName = "pending_writes", indices = @Index(value = {"userId", "createdAt"}))
public final class PendingWrite {
    @PrimaryKey @NonNull public final String key;
    @NonNull public final String userId;
    @Nullable public final String householdId;
    @NonNull public final String operationId;
    @NonNull public final String canonicalPayload;
    public final long createdAt;
    public final boolean needsReview;
    @Nullable public final String mediaPrivatePath;
    @Nullable public final String mediaSha256;

    PendingWrite(@NonNull String key, @NonNull String userId, @Nullable String householdId,
                 @NonNull String operationId, @NonNull String canonicalPayload, long createdAt,
                 boolean needsReview, @Nullable String mediaPrivatePath, @Nullable String mediaSha256) {
        this.key = key; this.userId = userId; this.householdId = householdId; this.operationId = operationId;
        this.canonicalPayload = canonicalPayload; this.createdAt = createdAt; this.needsReview = needsReview;
        this.mediaPrivatePath = mediaPrivatePath; this.mediaSha256 = mediaSha256;
    }
}
