package com.androidagent.core

/**
 * The verdict on whether one tool call may run.
 */
sealed interface ToolApproval {

    /** Run the tool. */
    data object Allow : ToolApproval

    /**
     * Do not run the tool.
     *
     * [reason] is surfaced to the model so it can adapt; it is diagnostic text,
     * not a machine-readable code.
     */
    data class Deny(val reason: String) : ToolApproval
}

/**
 * Decides whether a tool call may execute.
 *
 * This is the seam where a future human-approval flow would attach. AG.0
 * deliberately supports only Allow/Deny: there is no frontend lifecycle to
 * pause and resume a Run against, so a pause/resume state machine would be
 * unreachable code today.
 */
fun interface ToolApprovalPolicy {
    fun decide(call: ToolCall): ToolApproval

    companion object {
        /** Default policy: every registered tool may run. */
        val AllowAll: ToolApprovalPolicy = ToolApprovalPolicy { ToolApproval.Allow }
    }
}