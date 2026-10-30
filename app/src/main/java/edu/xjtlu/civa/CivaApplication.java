package edu.xjtlu.civa;

import android.app.Application;

public final class CivaApplication extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        container = new AppContainer(this, BuildConfig.API_BASE_URL);
    }

    AppContainer container() { return container; }
}
