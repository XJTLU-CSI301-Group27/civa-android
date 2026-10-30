package edu.xjtlu.civa.core.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
interface LocalDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putCache(CacheEntry entry);
    @Nullable @Query("SELECT * FROM cache_entries WHERE cacheKey=:key AND userId=:userId AND householdId IS :householdId")
    CacheEntry cache(String key, String userId, @Nullable String householdId);
    @Query("DELETE FROM cache_entries WHERE fetchedAt < :cutoff") int deleteCacheBefore(long cutoff);
    @Query("DELETE FROM cache_entries WHERE userId=:userId") int deleteUserCache(String userId);
    @Query("DELETE FROM cache_entries WHERE userId=:userId AND householdId=:householdId") int deleteHouseholdCache(String userId, String householdId);

    @Insert(onConflict = OnConflictStrategy.ABORT) void insertPending(PendingWrite intent);
    @Query("SELECT * FROM pending_writes WHERE userId=:userId ORDER BY createdAt ASC, `key` ASC") List<PendingWrite> pendingForUser(String userId);
    @Query("UPDATE pending_writes SET needsReview=1 WHERE userId=:userId AND householdId=:householdId") int markHouseholdForReview(String userId, String householdId);
    @Query("DELETE FROM pending_writes WHERE `key`=:key AND userId=:userId") int deletePending(String userId, String key);
}
