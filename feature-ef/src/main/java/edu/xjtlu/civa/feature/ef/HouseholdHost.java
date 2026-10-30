package edu.xjtlu.civa.feature.ef;

import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.AuthRepository;
import edu.xjtlu.civa.core.data.SessionStore;

/** Implemented by the app container host; tokens never cross this boundary. */
public interface HouseholdHost {
    ApiProvider api();
    ApiCalls calls();
    AuthRepository authRepository();
    SessionStore sessions();
    void onHouseholdReady();
}
