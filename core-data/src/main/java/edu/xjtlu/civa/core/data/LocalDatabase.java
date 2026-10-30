package edu.xjtlu.civa.core.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Private app-local data; no destructive migration fallback. */
@Database(entities = {CacheEntry.class, PendingWrite.class}, version = 1, exportSchema = true)
abstract class LocalDatabase extends RoomDatabase {
    abstract LocalDataDao data();
}
