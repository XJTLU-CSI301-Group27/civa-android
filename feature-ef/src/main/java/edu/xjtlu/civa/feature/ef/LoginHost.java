package edu.xjtlu.civa.feature.ef;

import edu.xjtlu.civa.core.data.AuthRepository;

/** Implemented by the app container host; credentials are not exposed to the host. */
public interface LoginHost {
    AuthRepository authRepository();
    void onAuthenticated();
}
