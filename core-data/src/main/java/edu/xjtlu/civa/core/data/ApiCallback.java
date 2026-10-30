package edu.xjtlu.civa.core.data;

import androidx.annotation.Nullable;
import edu.xjtlu.civa.core.ui.ApiError;

public interface ApiCallback<T> {
    void onSuccess(@Nullable T value);
    void onError(ApiError error);
}
