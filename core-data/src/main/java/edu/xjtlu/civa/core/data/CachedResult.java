package edu.xjtlu.civa.core.data;

/** Cached snapshot or fresh response, with fetch time in Unix milliseconds. */
public final class CachedResult<T> {
    public final T data;
    public final boolean stale;
    public final long fetchedAt;

    CachedResult(T data, boolean stale, long fetchedAt) {
        this.data = data; this.stale = stale; this.fetchedAt = fetchedAt;
    }
}
