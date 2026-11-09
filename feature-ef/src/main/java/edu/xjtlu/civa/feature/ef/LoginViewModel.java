package edu.xjtlu.civa.feature.ef;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import edu.xjtlu.civa.api.model.LoginRequest;
import edu.xjtlu.civa.api.model.RegisterRequest;
import edu.xjtlu.civa.api.model.User;
import edu.xjtlu.civa.core.data.ApiCallback;
import edu.xjtlu.civa.core.data.AuthRepository;
import edu.xjtlu.civa.core.ui.ApiError;
import java.time.ZoneId;

final class LoginViewModel extends ViewModel {
    final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    final MutableLiveData<Boolean> authenticated = new MutableLiveData<>(false);
    final MutableLiveData<String> message = new MutableLiveData<>();
    private final AuthRepository auth;
    String username = "", password = "", displayName = "";
    boolean registerMode;
    private boolean closed;
    private boolean pending;

    LoginViewModel(AuthRepository auth) { this.auth = auth; }

    void submit() {
        if (pending) return;
        pending = true;
        loading.postValue(true);
        message.postValue("");
        ApiCallback<User> callback = new ApiCallback<User>() {
            @Override public void onSuccess(User user) {
                if (closed) return;
                pending = false;
                loading.postValue(false);
                if (registerMode) {
                    registerMode = false;
                    message.postValue("REGISTERED");
                } else {
                    password = "";
                    authenticated.postValue(true);
                }
            }
            @Override public void onError(ApiError error) {
                if (closed) return;
                pending = false;
                loading.postValue(false);
                message.postValue(error.code);
            }
        };
        if (registerMode) {
            auth.register(new RegisterRequest().username(username).displayName(displayName).password(password)
                    .zoneId(ZoneId.systemDefault().getId()), callback);
        } else {
            auth.login(new LoginRequest().username(username).password(password), callback);
        }
    }

    boolean isPending() { return pending; }

    @Override protected void onCleared() {
        closed = true;
        password = "";
        auth.cancelLogin();
    }
}
