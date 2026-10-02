package com.androidagent.core

/**
 * Build-baseline marker for the AG Core module.
 *
 * This exists only to prove that `:ag-core` compiles as a pure Kotlin/JVM
 * module and that its package contract is `com.androidagent.core`.
 *
 * It deliberately carries no Agent behaviour. AG.0 added the real runtime
 * alongside it in this same package; this marker remains as a cheap regression
 * guard on the frozen package name.
 */
object AgCoreMarker {

    /** Frozen package identity of AG Core. */
    const val PACKAGE: String = "com.androidagent.core"

    /** Human-readable module identity, used by the baseline smoke test. */
    const val MODULE: String = "ag-core"
}