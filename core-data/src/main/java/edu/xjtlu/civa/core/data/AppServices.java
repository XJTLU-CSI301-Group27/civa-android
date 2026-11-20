package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.core.ui.NavHost;

/**
 * App-level services and navigation that every feature screen needs.
 *
 * <p>Implemented exactly once by the application shell, so adding a feature does not add another
 * host interface to the activity. Features cast the activity to this type; feature-specific
 * callbacks stay in the owning feature module.
 */
public interface AppServices extends NavHost {
    ApiProvider api();

    ApiCalls calls();

    SessionStore sessions();

    AuthRepository auth();
}
