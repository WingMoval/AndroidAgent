package com.androidagent.provider.openai

import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelRequest
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * AGENT-0.PREFLIGHT — T2, T3, Phase E, Phase G (Provider wire audit).
 *
 * Proves the OpenAI-compatible mapping by inspecting the exact JSON body the
 * Provider builds. No network, no live endpoint, no real key. The instructions
 * content is the same frozen string the Host uses; here it is referenced as a
 * local constant so the test does not couple the provider module to :app.
 */
class WireAuditTest {

    // The frozen AGENT_INSTRUCTIONS_V0 content, mirrored so this module stays
    // free of any :app dependency. A test in :app pins that the Host uses the
    // same value (see AgentInstructions identity).
    private val instructions =
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
            "If the goal cannot be completed, report the actual blocking condition."

    private val shellSpec = ToolSpec(
        name = "android_shell",
        description =
            "WHAT: Execute one non-interactive shell command on the connected Android " +
                "device and return its result.\n" +
                "WHEN: Use when the task depends on actual Android device state or a " +
                "shell-accessible Android operation, and the answer is not already known " +
                "from earlier observations.\n" +
                "INPUT: A single JSON object with one string field \"command\", " +
                "for example {\"command\":\"id\"}. No other fields are supported.\n" +
                "OUTPUT: A JSON object with fields \"outcome\", \"exitCode\", \"stdout\", " +
                "\"stderr\", \"hasError\", and \"error\" (only on a mechanism failure). " +
                "A completed command reports success even when exitCode is non-zero; " +
                "the exitCode, stdout and stderr carry the real result. Only a tool " +
                "mechanism failure (transport / protocol / unsupported / timeout / " +
                "cancelled) reports success = false with an \"error\" field.",
    )

    private fun request(
        history: List<com.androidagent.core.Message> = emptyList(),
        tools: List<ToolSpec> = emptyList(),
        instructions: String = "",
    ) = ModelRequest(sessionId = "s1", instructions = instructions, history = history, tools = tools)

    // ------------------------------------------------------------ instructions

    @Test
    fun E1_instructions_become_leading_system_message() {
        val body = ChatCompletionWire.buildRequestBody("model-a", request(instructions = instructions))
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(instructions, messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun E1b_blank_instructions_produce_no_system_message() {
        val body = ChatCompletionWire.buildRequestBody("model-a", request(instructions = "", history = listOf(UserMessage("hi"))))
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    // ----------------------------------------------------------------- tools

    @Test
    fun T2_model_request_tools_contain_exactly_android_shell() {
        val body = ChatCompletionWire.buildRequestBody("model-a", request(tools = listOf(shellSpec)))
        val tools = body["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val fn = tools[0].jsonObject["function"]!!.jsonObject
        assertEquals("android_shell", fn["name"]!!.jsonPrimitive.content)
        // The parameters schema is the permissive object projection (no typed schema).
        val params = fn["parameters"]!!.jsonObject
        assertEquals("object", params["type"]!!.jsonPrimitive.content)
        assertEquals("true", params["additionalProperties"]!!.jsonPrimitive.content)
    }

    @Test
    fun T3_tool_description_is_the_audited_definition() {
        val body = ChatCompletionWire.buildRequestBody("model-a", request(tools = listOf(shellSpec)))
        val fn = body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject
        val description = fn["description"]!!.jsonPrimitive.content

        // WHAT / WHEN / INPUT / OUTPUT all present and unambiguous.
        assertTrue(description.contains("WHAT:"))
        assertTrue(description.contains("WHEN:"))
        assertTrue(description.contains("INPUT:"))
        assertTrue(description.contains("OUTPUT:"))
        // The success-semantics disambiguation is explicit.
        assertTrue(description.contains("success even when exitCode is non-zero"))
        // It does not hardcode a specific command or workflow.
        assertFalse(description.contains("dumpsys"))
        assertFalse(description.contains("foreground"))
        assertFalse(description.contains("pm list"))
    }

    // ---------------------------------------------------------------- history

    @Test
    fun E2_user_message_maps_to_user_role() {
        val body = ChatCompletionWire.buildRequestBody("model-a", request(history = listOf(UserMessage("hello"))))
        val messages = body["messages"]!!.jsonArray
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("hello", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun E3_model_tool_call_turn_maps_to_assistant_with_structured_tool_calls() {
        val turn = ModelMessage(
            content = "let me check",
            toolCalls = listOf(ToolCall(id = "c1", name = "android_shell", arguments = """{"command":"id"}""")),
        )
        val body = ChatCompletionWire.buildRequestBody("model-a", request(history = listOf(turn)))
        val msg = body["messages"]!!.jsonArray[0].jsonObject
        assertEquals("assistant", msg["role"]!!.jsonPrimitive.content)
        val toolCalls = msg["tool_calls"]!!.jsonArray
        assertEquals(1, toolCalls.size)
        val fn = toolCalls[0].jsonObject["function"]!!.jsonObject
        assertEquals("android_shell", fn["name"]!!.jsonPrimitive.content)
        assertEquals("""{"command":"id"}""", fn["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun E4_tool_result_maps_to_tool_role_with_call_id() {
        val turn = ModelMessage("", toolCalls = listOf(ToolCall(id = "c1", name = "android_shell", arguments = "x")))
        val result = ToolResultMessage(
            toolCallId = "c1",
            toolName = "android_shell",
            result = ToolResult(success = true, output = "{\"outcome\":\"COMPLETED\",\"exitCode\":0}"),
        )
        val body = ChatCompletionWire.buildRequestBody("model-a", request(history = listOf(turn, result)))
        val messages = body["messages"]!!.jsonArray
        val toolMsg = messages[1].jsonObject
        assertEquals("tool", toolMsg["role"]!!.jsonPrimitive.content)
        assertEquals("c1", toolMsg["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun E5_tool_result_mechanism_failure_appends_error_for_model() {
        val turn = ModelMessage("", toolCalls = listOf(ToolCall(id = "c1", name = "android_shell", arguments = "x")))
        val result = ToolResultMessage(
            toolCallId = "c1",
            toolName = "android_shell",
            result = ToolResult(success = false, output = "", error = "transport error"),
        )
        val body = ChatCompletionWire.buildRequestBody("model-a", request(history = listOf(turn, result)))
        val content = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(content.contains("transport error"))
    }

    // ---------------------------------------------------------------- secret

    @Test
    fun G1_authorization_header_never_in_body_or_endpoint() {
        val sentinel = "SECRET_SENTINEL"
        val config = OpenAiCompatibleConfig(
            baseUrl = "https://example.test/v1",
            apiKey = sentinel,
            model = "model-a",
            requestTimeout = Duration.ofSeconds(30),
        )
        val body = ChatCompletionWire.buildRequestBody("model-a", request(instructions = instructions))
        assertFalse(body.toString().contains(sentinel), "request body must not embed the key")
        assertFalse(config.chatCompletionsEndpoint.contains(sentinel))
        // The endpoint identity is scheme + host + path only.
        assertEquals("https://example.test/v1/chat/completions", config.chatCompletionsEndpoint)
    }

    @Test
    fun G2_config_tostring_never_reveals_key() {
        val config = OpenAiCompatibleConfig(
            baseUrl = "https://example.test/v1",
            apiKey = "SECRET_SENTINEL",
            model = "model-a",
        )
        assertFalse(config.toString().contains("SECRET_SENTINEL"))
        assertTrue(config.toString().contains("apiKey=<redacted>"))
    }
}
