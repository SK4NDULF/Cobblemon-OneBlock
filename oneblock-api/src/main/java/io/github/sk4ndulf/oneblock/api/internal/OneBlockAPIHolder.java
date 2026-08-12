package io.github.sk4ndulf.oneblock.api.internal;

import io.github.sk4ndulf.oneblock.api.OneBlockAPI;

/**
 * Internal holder for the API singleton.
 *
 * <p><b>Internal API — do not call from addon code.</b> Only the {@code oneblock} core mod
 * may set the instance, exactly once during initialization.</p>
 */
public final class OneBlockAPIHolder {

    private static volatile OneBlockAPI instance;

    private OneBlockAPIHolder() {
    }

    public static OneBlockAPI get() {
        OneBlockAPI api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "OneBlockAPI is not available yet. The oneblock core mod has not finished initializing.");
        }
        return api;
    }

    public static boolean isPresent() {
        return instance != null;
    }

    /**
     * Sets the API singleton. May only be called once, by the core mod.
     *
     * @throws IllegalStateException if an instance is already set
     */
    public static void set(OneBlockAPI api) {
        synchronized (OneBlockAPIHolder.class) {
            if (instance != null) {
                throw new IllegalStateException("OneBlockAPI instance is already set.");
            }
            instance = api;
        }
    }
}
