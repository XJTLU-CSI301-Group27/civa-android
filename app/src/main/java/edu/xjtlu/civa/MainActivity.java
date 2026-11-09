package edu.xjtlu.civa;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.navigation.NavigationBarView;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.AuthRepository;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.feature.ef.HouseholdHost;
import edu.xjtlu.civa.feature.ef.LoginFragment;
import edu.xjtlu.civa.feature.ef.LoginHost;

public class MainActivity extends AppCompatActivity implements LoginHost, HouseholdHost {
    private AppContainer container;
    private AppNavigator navigator;
    private NavigationBarView navigation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        container = ((CivaApplication) getApplication()).container();
        boolean restore = container.sessions.isLoggedIn() || getLastCustomNonConfigurationInstance() != null;
        super.onCreate(restore ? savedInstanceState : null);
        setContentView(R.layout.app_activity_main);
        navigator = new AppNavigator(getSupportFragmentManager(), container.sessions);
        navigation = findViewById(R.id.app_navigation);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.app_today) navigator.navigate(Route.TODAY, null);
            else if (id == R.id.app_recipes) navigator.navigate(Route.RECIPES, null);
            else if (id == R.id.app_inventory) navigator.navigate(Route.INVENTORY, null);
            else if (id == R.id.app_diary) navigator.navigate(Route.DIARY, null);
            else if (id == R.id.app_profile) navigator.navigate(Route.PROFILE, null);
            else return false;
            return true;
        });
        navigation.setVisibility(container.sessions.isLoggedIn() ? View.VISIBLE : View.GONE);
        if (savedInstanceState == null || !restore) navigator.navigate(container.sessions.isLoggedIn() ? Route.TODAY : Route.LOGIN, null);
        else if (!container.sessions.isLoggedIn() && !(getSupportFragmentManager().findFragmentById(R.id.app_content) instanceof LoginFragment)) {
            navigator.navigate(Route.LOGIN, null);
        }
        container.sessionExpired.observe(this, ignored -> showLoginIfNeeded());
    }

    @Override public Object onRetainCustomNonConfigurationInstance() { return Boolean.TRUE; }

    @Override protected void onResume() {
        super.onResume();
        showLoginIfNeeded();
    }

    private void showLoginIfNeeded() {
        if (!container.sessions.isLoggedIn()) {
            navigation.setVisibility(View.GONE);
            var current = getSupportFragmentManager().findFragmentById(R.id.app_content);
            if (current != null && !(current instanceof LoginFragment)
                    && !getSupportFragmentManager().isStateSaved()) navigator.navigate(Route.LOGIN, null);
        }
    }

    @Override public AuthRepository authRepository() { return container.auth; }

    @Override public ApiProvider api() { return container.api; }

    @Override public ApiCalls calls() { return container.calls; }

    @Override public SessionStore sessions() { return container.sessions; }

    @Override public void onHouseholdReady() {
        if (!container.sessions.isLoggedIn() || getSupportFragmentManager().isStateSaved()) return;
        navigator.navigate(Route.TODAY, null);
    }

    @Override public void onAuthenticated() {
        if (!container.sessions.isLoggedIn() || getSupportFragmentManager().isStateSaved()) return;
        navigation.setVisibility(View.VISIBLE);
        navigator.navigate(container.sessions.currentHouseholdId() == null ? Route.HOUSEHOLD : Route.TODAY, null);
    }
}
