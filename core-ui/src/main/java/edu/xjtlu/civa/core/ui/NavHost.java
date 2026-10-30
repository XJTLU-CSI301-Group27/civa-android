package edu.xjtlu.civa.core.ui;

import androidx.annotation.Nullable;

public interface NavHost {
    /** Navigate to a route; entityId is null for routes without a selected entity. */
    void navigate(Route route, @Nullable String entityId);
}
