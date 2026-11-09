package edu.xjtlu.civa.core.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionStoreTest {
    @Test public void oldLoginAndHouseholdResultsCannotOverwriteNewSession() {
        SessionStore store = new SessionStore();
        long initial = store.snapshot().generation;
        long accountA = store.installIfCurrent(initial, "token-a", "user-a", "house-a");
        assertEquals(initial + 1, accountA);
        assertTrue(store.isLoggedIn());
        assertEquals("token-a", store.clearAndGetToken());
        assertFalse(store.isLoggedIn());
        assertNull(store.currentUserId());
        assertNull(store.currentHouseholdId());
        assertNull(store.snapshot().token);
        assertEquals(-1, store.installIfCurrent(initial, "late-token", "old-user", null));
        long beforeB = store.snapshot().generation;
        assertEquals(beforeB + 1, store.installIfCurrent(beforeB, "token-b", "user-b", null));
        assertFalse(store.updateHouseholdIfCurrent(accountA, "house-a"));
        assertEquals("user-b", store.currentUserId());
        assertNull(store.currentHouseholdId());
        assertTrue(store.updateHouseholdIfCurrent(store.snapshot().generation, "house-b"));
        assertEquals("house-b", store.currentHouseholdId());
    }
}
