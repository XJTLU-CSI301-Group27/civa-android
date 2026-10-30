package edu.xjtlu.civa;

import androidx.fragment.app.FragmentManager;
import edu.xjtlu.civa.core.ui.NavHost;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.feature.ef.HouseholdFragment;
import edu.xjtlu.civa.feature.ef.LoginFragment;

final class AppNavigator implements NavHost {
    private final FragmentManager fragments;
    private final SessionStore sessions;

    AppNavigator(FragmentManager fragments, SessionStore sessions) { this.fragments = fragments; this.sessions = sessions; }

    @Override public void navigate(Route route, String entityId) {
        if (!sessions.isLoggedIn() && route != Route.LOGIN) route = Route.LOGIN;
        int title;
        boolean primary = false;
        switch (route) {
            case TODAY: title = R.string.app_today; primary = true; break;
            case RECIPES: title = R.string.app_recipes; primary = true; break;
            case INVENTORY: title = R.string.app_inventory; primary = true; break;
            case DIARY: title = R.string.app_diary; primary = true; break;
            case PROFILE: title = R.string.app_profile; primary = true; break;
            case RECIPE_DETAIL: title = R.string.app_recipe_detail; break;
            case RECIPE_EDIT: title = R.string.app_recipe_edit; break;
            case INVENTORY_EDIT: title = R.string.app_inventory_edit; break;
            case PLAN: title = R.string.app_plan; break;
            case COOKING_CONFIRM: title = R.string.app_cooking_confirm; break;
            case MEAL_EDIT: title = R.string.app_meal_edit; break;
            case LOGIN: title = R.string.app_login; primary = true; break;
            case HOUSEHOLD: title = R.string.app_household; break;
            case PREFERENCES: title = R.string.app_preferences; break;
            default: throw new IllegalArgumentException("Unknown route: " + route);
        }
        if (primary) fragments.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        var fragment = route == Route.LOGIN ? new LoginFragment()
                : route == Route.HOUSEHOLD ? new HouseholdFragment()
                : PlaceholderFragment.create(title, entityId);
        var transaction = fragments.beginTransaction().replace(R.id.app_content, fragment);
        if (!primary) transaction.addToBackStack(null);
        transaction.commit();
    }
}
