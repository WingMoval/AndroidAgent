package com.androidagent.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Baseline smoke test for AG.BASELINE-0.
 *
 * Scope is intentionally narrow: prove that the JVM unit-test infrastructure
 * is real and executes. It asserts nothing about future Agent behaviour.
 */
class AgCoreBaselineTest {

    @Test
    fun `unit test infrastructure executes`() {
        assertTrue(true, "JUnit 5 is wired and executing on the JVM")
    }

    @Test
    fun `ag core module compiles and is addressable`() {
        assertEquals("ag-core", AgCoreMarker.MODULE)
    }

    @Test
    fun `ag core package contract is frozen`() {
        assertEquals("com.androidagent.core", AgCoreMarker.PACKAGE)
        assertEquals(
            "com.androidagent.core",
            AgCoreMarker::class.java.`package`?.name,
            "AG Core must live in com.androidagent.core, never com.androidterminal.*"
        )
    }
}