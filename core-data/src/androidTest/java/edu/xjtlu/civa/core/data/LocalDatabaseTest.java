package edu.xjtlu.civa.core.data;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalDatabaseTest {
    @Test public void packagedContractContainsCacheSchemas() throws Exception {
        try (var reader = new InputStreamReader(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getAssets().open("civa-cache-contract.json"), StandardCharsets.UTF_8)) {
            var schemas = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("components").getAsJsonObject("schemas");
            assertTrue(schemas.has("User"));
            assertTrue(schemas.has("RecipePage"));
        }
    }

    @Test public void separatesUsersAndKeepsPendingWritesWhenCacheExpires() {
        LocalDatabase database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalDatabase.class)
                .allowMainThreadQueries().build();
        try {
            LocalDataDao dao = database.data();
            dao.putCache(new CacheEntry("a-home-page-0", "a", "home", "{}", 100));
            dao.putCache(new CacheEntry("b-personal", "b", null, "{}", 200));
            assertNotNull(dao.cache("a-home-page-0", "a", "home"));
            assertNull(dao.cache("a-home-page-0", "b", "home"));
            assertNull(dao.cache("a-home-page-0", "a", null));
            dao.insertPending(new PendingWrite("key-a", "a", "home", "confirmCooking", "{}", 100, false, null, null));
            assertThrows(RuntimeException.class, () -> dao.insertPending(
                    new PendingWrite("key-a", "a", "home", "confirmCooking", "{\"changed\":true}", 101, false, null, null)));
            assertEquals(1, dao.deleteCacheBefore(150));
            assertEquals(1, dao.pendingForUser("a").size());
            assertTrue(dao.pendingForUser("b").isEmpty());
            assertEquals(0, dao.deletePending("b", "key-a"));
            assertEquals(1, dao.markHouseholdForReview("a", "home"));
            assertTrue(dao.pendingForUser("a").get(0).needsReview);
            assertEquals(1, dao.deletePending("a", "key-a"));
            assertNotNull(dao.cache("b-personal", "b", null));
        } finally {
            database.close();
        }
    }
}
