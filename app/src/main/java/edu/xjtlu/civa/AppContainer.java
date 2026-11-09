package edu.xjtlu.civa;

import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.AuthRepository;
import edu.xjtlu.civa.core.data.PendingWriteStore;
import edu.xjtlu.civa.core.data.RealApiProvider;
import edu.xjtlu.civa.core.data.SessionStore;
import androidx.lifecycle.MutableLiveData;
import android.content.Context;

final class AppContainer {
    final SessionStore sessions = new SessionStore();
    final RealApiProvider api;
    final MutableLiveData<Boolean> sessionExpired = new MutableLiveData<>();
    final ApiCalls calls;
    final AuthRepository auth;
    final PendingWriteStore pendingWrites;

    AppContainer(Context context, String baseUrl) {
        calls = new ApiCalls(context, sessions, () -> sessionExpired.postValue(true));
        api = new RealApiProvider(baseUrl, sessions);
        auth = new AuthRepository(api, sessions, calls);
        pendingWrites = calls.pendingWrites();
    }
}
