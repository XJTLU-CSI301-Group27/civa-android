package edu.xjtlu.civa.core.ui;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ApiError {
    public final String code;
    public final String message;
    @Nullable public final String traceId;
    public final List<FieldError> fieldErrors;

    public ApiError(String code, String message, @Nullable String traceId, List<FieldError> fieldErrors) {
        this.code = Objects.requireNonNull(code);
        this.message = Objects.requireNonNull(message);
        this.traceId = traceId;
        this.fieldErrors = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(fieldErrors)));
    }

    public static final class FieldError {
        public final String field;
        public final String message;

        public FieldError(String field, String message) {
            this.field = Objects.requireNonNull(field);
            this.message = Objects.requireNonNull(message);
        }
    }
}
