package io.github.sk4ndulf.cobblemon.oneblock.diagnostics;

/** Error classes from spec 12 §18. Every admin-facing problem belongs to exactly one class. */
public enum ErrorClass {
    CONFIG,
    STORAGE,
    WORLD,
    COBBLEMON,
    PERMISSIONS,
    RESOURCE,
    UNKNOWN
}
