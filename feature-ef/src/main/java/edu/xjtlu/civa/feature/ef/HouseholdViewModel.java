package edu.xjtlu.civa.feature.ef;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import edu.xjtlu.civa.api.model.CreateHouseholdRequest;
import edu.xjtlu.civa.api.model.JoinHouseholdRequest;
import edu.xjtlu.civa.api.model.User;
import edu.xjtlu.civa.core.data.ApiCallback;
import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.AuthRepository;
import edu.xjtlu.civa.core.data.PendingWrite;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.core.ui.ApiError;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import retrofit2.Call;

/** Create or join a household with an idempotency key and a durable pending-write record. */
final class HouseholdViewModel extends ViewModel {
    final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    final MutableLiveData<String> message = new MutableLiveData<>();
    final MutableLiveData<String> recovering = new MutableLiveData<>();
    private final ApiProvider api;
    private final ApiCalls calls;
    private final AuthRepository auth;
    private final SessionStore sessions;
    String name = "", code = "";
    private boolean closed;
    private boolean pending;
    private String pendingKey;

    HouseholdViewModel(ApiProvider api, ApiCalls calls, AuthRepository auth, SessionStore sessions) {
        this.api = api;
        this.calls = calls;
        this.auth = auth;
        this.sessions = sessions;
    }

    /** Restores an unconfirmed create/join intent after a process death; never auto-replays. */
    void recover() {
        String userId = sessions.currentUserId();
        if (userId == null || pendingKey != null) return;
        calls.pendingWrites().listPending(userId, new ApiCallback<List<PendingWrite>>() {
            @Override public void onSuccess(List<PendingWrite> writes) {
                if (closed) return;
                for (PendingWrite write : writes) {
                    if ("createHousehold".equals(write.operationId)) {
                        pendingKey = write.key;
                        name = write.canonicalPayload;
                        recovering.postValue("createHousehold");
                        return;
                    }
                    if ("joinHousehold".equals(write.operationId)) {
                        pendingKey = write.key;
                        code = write.canonicalPayload;
                        recovering.postValue("joinHousehold");
                        return;
                    }
                }
            }
            @Override public void onError(ApiError error) { /* best-effort; a fresh intent is still allowed */ }
        });
    }

    void create() {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) { message.postValue("VALIDATION_ERROR"); return; }
        submit("createHousehold", trimmed,
                () -> api.household().createHousehold(UUID.fromString(pendingKey), new CreateHouseholdRequest().name(trimmed)));
    }

    void join() {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty()) { message.postValue("VALIDATION_ERROR"); return; }
        submit("joinHousehold", trimmed,
                () -> api.household().joinHousehold(UUID.fromString(pendingKey), new JoinHouseholdRequest().code(trimmed)));
    }

    void abandon() {
        if (pendingKey == null) return;
        calls.pendingWrites().confirm(sessions.currentUserId(), pendingKey, new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { if (!closed) clearRecovery(); }
            @Override public void onError(ApiError error) { if (!closed) message.postValue(error.code); }
        });
    }

    private void clearRecovery() {
        pendingKey = null;
        name = "";
        code = "";
        recovering.postValue(null);
    }

    private <T> void submit(String operation, String payload, Supplier<Call<T>> request) {
        if (pending) return;
        pending = true;
        loading.postValue(true);
        message.postValue("");
        if (pendingKey == null) {
            pendingKey = UUID.randomUUID().toString();
            calls.pendingWrites().record(pendingKey, sessions.currentUserId(), null, operation, payload,
                    null, null, new ApiCallback<Void>() {
                        @Override public void onSuccess(Void value) { dispatch(request); }
                        @Override public void onError(ApiError error) { fail(error); }
                    });
        } else {
            dispatch(request);
        }
    }

    private <T> void dispatch(Supplier<Call<T>> request) {
        calls.enqueue(request.get(), new ApiCallback<T>() {
            @Override public void onSuccess(T value) { confirmed(); }
            @Override public void onError(ApiError error) { fail(error); }
        });
    }

    private void confirmed() {
        String key = pendingKey;
        pendingKey = null;
        calls.pendingWrites().confirm(sessions.currentUserId(), key, new ApiCallback<Void>() {
            @Override public void onSuccess(Void value) { refresh(); }
            @Override public void onError(ApiError error) { refresh(); } // confirm failure is non-fatal
        });
    }

    private void refresh() {
        auth.refreshMe(new ApiCallback<User>() {
            @Override public void onSuccess(User user) {
                if (closed) return;
                pending = false;
                loading.postValue(false);
                recovering.postValue(null);
                ready.postValue(true);
            }
            @Override public void onError(ApiError error) { fail(error); }
        });
    }

    private void fail(ApiError error) {
        if (closed) return;
        pending = false;
        loading.postValue(false);
        // Keep the key only when the result is unknown; a rejected request gets a fresh key next time.
        if (!"NETWORK_ERROR".equals(error.code) && !"TIMEOUT".equals(error.code)) pendingKey = null;
        message.postValue(error.code);
    }

    boolean isPending() { return pending; }

    @Override protected void onCleared() { closed = true; }
}
