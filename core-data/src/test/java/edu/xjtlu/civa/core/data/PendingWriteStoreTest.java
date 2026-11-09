package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.core.ui.ApiError;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class PendingWriteStoreTest {
    @Test public void recordsDurablyAndStaysAccountScoped() throws Exception {
        Queue<Runnable> io = new ArrayDeque<>();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        Map<String, PendingWrite> writes = new HashMap<>();
        PendingWriteStore store = new PendingWriteStore(dao(writes), io::add, main::add);

        assertThrows(IllegalArgumentException.class, () -> store.record("", "a", null, "confirmCooking", "{}", null, null, noop()));
        assertThrows(IllegalArgumentException.class, () -> store.record("key-a", "a", null, "confirmCooking", "", null, null, noop()));

        AtomicReference<Void> recorded = new AtomicReference<>();
        store.record("key-a", "a", "home", "confirmCooking", "{\"recipeId\":\"r\"}", null, null, new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { recorded.set(value); }
            @Override public void onError(ApiError error) { fail("record failed: " + error.code); }
        });
        runIo(io);
        runMain(main);
        assertEquals(1, writes.size());
        assertEquals("confirmCooking", writes.get("key-a").operationId);

        AtomicReference<ApiError> duplicate = new AtomicReference<>();
        store.record("key-a", "a", "home", "confirmCooking", "{\"recipeId\":\"r2\"}", null, null, new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { fail("duplicate key accepted"); }
            @Override public void onError(ApiError error) { duplicate.set(error); }
        });
        runIo(io);
        runMain(main);
        assertEquals("LOCAL_STORAGE_ERROR", duplicate.get().code);

        AtomicReference<List<PendingWrite>> forA = new AtomicReference<>();
        AtomicReference<List<PendingWrite>> forB = new AtomicReference<>();
        store.listPending("a", new ApiCallback<List<PendingWrite>>() {
            @Override public void onSuccess(List<PendingWrite> value) { forA.set(value); }
            @Override public void onError(ApiError error) { fail("list failed"); }
        });
        runIo(io);
        runMain(main);
        store.listPending("b", new ApiCallback<List<PendingWrite>>() {
            @Override public void onSuccess(List<PendingWrite> value) { forB.set(value); }
            @Override public void onError(ApiError error) { fail("list failed"); }
        });
        runIo(io);
        runMain(main);
        assertEquals(1, forA.get().size());
        assertTrue(forB.get().isEmpty());

        AtomicReference<Void> confirmed = new AtomicReference<>();
        store.confirm("b", "key-a", new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { confirmed.set(value); }
            @Override public void onError(ApiError error) { fail("confirm failed"); }
        });
        runIo(io);
        runMain(main);
        assertEquals(1, writes.size());

        store.confirm("a", "key-a", new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { confirmed.set(value); }
            @Override public void onError(ApiError error) { fail("confirm failed"); }
        });
        runIo(io);
        runMain(main);
        assertTrue(writes.isEmpty());
    }

    private static ApiCallback<Void> noop() {
        return new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { }
            @Override public void onError(ApiError error) { }
        };
    }

    private static LocalDataDao dao(Map<String, PendingWrite> writes) {
        return (LocalDataDao) Proxy.newProxyInstance(LocalDataDao.class.getClassLoader(), new Class<?>[]{LocalDataDao.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "insertPending": {
                    PendingWrite item = (PendingWrite) args[0];
                    if (writes.containsKey(item.key)) throw new IllegalStateException("duplicate key");
                    writes.put(item.key, item);
                    return null;
                }
                case "pendingForUser": {
                    List<PendingWrite> result = new ArrayList<>();
                    for (PendingWrite w : writes.values()) if (w.userId.equals(args[0])) result.add(w);
                    return result;
                }
                case "deletePending": {
                    PendingWrite item = writes.get(args[1]);
                    if (item != null && item.userId.equals(args[0])) { writes.remove(args[1]); return 1; }
                    return 0;
                }
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static void runIo(Queue<Runnable> io) { Runnable task = io.poll(); assertNotNull("No io task", task); task.run(); }
    private static void runMain(BlockingQueue<Runnable> main) throws Exception {
        Runnable task = main.poll(10, TimeUnit.SECONDS);
        assertNotNull("No main callback", task);
        task.run();
    }
}
