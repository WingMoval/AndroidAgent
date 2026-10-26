package com.androidagent.app

import java.security.MessageDigest

/**
 * AGENT_INSTRUCTIONS_V0 — the first formal Agent Instructions for the AGA host.
 *
 * Ownership, frozen:
 *  - The Instructions *contract* (a Run carries an instructions field) is owned
 *    by AG Core (`RunOptions.instructions` / `ModelRequest.instructions`).
 *  - The *content* of AGENT_INSTRUCTIONS_V0 is Host/Composition-owned. It lives
 *    here, in the AGA host, and is injected at the Composition Root. AG Core and
 *    the Agent Runtime stay Android- and task-agnostic: they carry no prompt.
 *
 * Shape, frozen:
 *  - Minimal and behaviour-oriented.
 *  - Not workflow-hardcoded, not command-hardcoded, not task-specific.
 *  - No fixed foreground-app command, no dumpsys command, no A -> B workflow, no
 *    Planner / Todo / Reflection / Memory / Skill / MCP protocol.
 *
 * Version identity:
 *  - [VERSION] is the stable logical identity.
 *  - [HASH] is a deterministic SHA-256 over the exact [CONTENT] bytes, so the
 *    same content always yields the same hash and any content change changes it.
 *    A Trace records [VERSION] + [HASH] only, never the full prompt.
 */
object AgentInstructions {

    const val VERSION: String = "AGENT_INSTRUCTIONS_V1"

    val CONTENT: String =
        "You are an Android device agent.\n" +
            "\n" +
            "When a task depends on the actual state of the connected Android device,\n" +
            "use the available tools to obtain that state instead of guessing.\n" +
            "\n" +
            "Treat tool results as observations.\n" +
            "Base each subsequent action on the information newly observed.\n" +
            "\n" +
            "Continue only as long as needed to satisfy the user's goal.\n" +
            "Do not repeat an action whose result you already know, unless verification\n" +
            "is necessary.\n" +
            "\n" +
            "When the goal is satisfied, provide the final answer.\n" +
            "If the goal cannot be completed, report the actual blocking condition.\n" +
            "\n" +
            "Execution environment: android_shell runs in AndroidAgent's local app shell.\n" +
            "An adb CLI supplied by the AndroidTerminal environment is available there.\n" +
            "Local shell identity and adb shell identity are different execution principals;\n" +
            "do not assume they are the same."

    /**
     * Deterministic SHA-256 over [CONTENT] as UTF-8, lower-case hex. Computed once
     * at class load and pinned by a test, so the frozen content and its identity
     * cannot drift apart.
     */
    val HASH: String = sha256Hex(CONTENT)

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
