package com.androidagent.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidagent.app.provider.AndroidOpenAiHttpTransport
import com.androidagent.core.ModelRequest
import com.androidagent.core.UserMessage
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiHttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * AGA.PROVIDER-AB-HEADLESS-0 — headless real-device Provider A/B probe.
 *
 * No UI, no chat clicks. Runs inside the target package via `am instrument` and
 * reads `filesDir/provider.local.json` with the existing
 * [DeviceLocalProviderConfigLoader] (same parser as the repo config). Which
 * config file is present is controlled by provisioning (OpenCode pushes it); the
 * at/noat flavor controls whether the AT Engine is wired at all.
 *
 * Three layers per case:
 *   P1 raw Android HttpsURLConnection POST (stage-by-stage timing)
 *   P2 AndroidOpenAiHttpTransport (the AGA production transport)
 *   P3 OpenAiCompatibleProvider.complete() (wire + parser + ModelTurn)
 *
 * Secret policy: no key/header/body is ever printed; only PRESENT/ABSENT and
 * structural sizes.
 */
@RunWith(AndroidJUnit4::class)
class ProviderRealDeviceProbeTest {

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext get() = targetContext.applicationContext

    private val loadedConfig: OpenAiCompatibleConfig by lazy {
        val result = DeviceLocalProviderConfigLoader.load(appContext)
        check(result is DeviceLocalProviderConfig.Loaded) { "config not loaded: $result" }
        result.config
    }

    private fun safeEndpoint(cfg: OpenAiCompatibleConfig): String = runCatching {
        val uri = java.net.URI.create(cfg.chatCompletionsEndpoint)
        val authority = uri.rawAuthority?.substringAfter('@').orEmpty()
        "${uri.scheme}://$authority${uri.rawPath}"
    }.getOrElse { "endpoint" }

    /** Minimal OpenAI-compatible request body, serialized by hand (no key inside). */
    private fun minimalBody(model: String): String =
        """{"model":"$model","messages":[{"role":"user","content":"Reply with exactly: OK"}],"stream":false}"""

    // ------------------------------------------------------------------ helpers

    private fun logNetwork() {
        val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
        Log.i(TAG, "NETWORK: CELLULAR=$cellular WIFI=$wifi VPN=$vpn VALIDATED=$validated")
    }

    private fun parseChoiceOk(body: String): Boolean = runCatching {
        // Structural check only: must contain choices array with a message.
        body.contains("\"choices\"") && body.contains("\"message\"")
    }.getOrDefault(false)

    // ------------------------------------------------------ P1 raw Android POST

    @Test
    fun P1_raw_android_post() {
        val cfg = loadedConfig
        val endpoint = cfg.chatCompletionsEndpoint
        val body = minimalBody(cfg.model)
        logNetwork()

        Log.i(TAG, "CASE=P1 CONFIG_SOURCE=DEVICE_LOCAL MODEL=${cfg.model} SAFE_ENDPOINT=${safeEndpoint(cfg)} API_KEY=${if (cfg.apiKey.isBlank()) "ABSENT" else "PRESENT"}")
        Log.i(TAG, "TARGET_PACKAGE=${appContext.packageName}")

        var stage = "DNS_OR_OPEN_CONNECTION"
        val start = System.currentTimeMillis()
        var status = -1
        var bytes = 0
        var errorClass = "NONE"
        try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            stage = "CONNECT"
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            stage = "RESPONSE_CODE"
            status = conn.responseCode
            stage = "RESPONSE_READ"
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            bytes = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }?.length ?: 0
            conn.disconnect()
        } catch (e: Exception) {
            errorClass = e::class.java.simpleName
            Log.i(TAG, "P1 FAILED_STAGE=$stage EXCEPTION_CLASS=$errorClass SAFE_MESSAGE=${e.message?.take(120)}")
        }
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "P1 RESULT=${if (status in 200..299) "PASS" else "FAIL"} HTTP_STATUS=$status ELAPSED_MS=$elapsed RESPONSE_BYTES=$bytes FAILED_STAGE=$stage EXCEPTION_CLASS=$errorClass")
        assertTrue("P1 must get 2xx, got status=$status stage=$stage", status in 200..299)
    }

    // -------------------------------------------------- P2 Android transport

    @Test
    fun P2_android_transport() {
        val cfg = loadedConfig
        val body = minimalBody(cfg.model)
        logNetwork()

        Log.i(TAG, "CASE=P2 MODEL=${cfg.model} SAFE_ENDPOINT=${safeEndpoint(cfg)}")
        val transport = AndroidOpenAiHttpTransport()
        val request = OpenAiHttpRequest(
            endpoint = cfg.chatCompletionsEndpoint,
            headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer ${cfg.apiKey}"),
            body = body,
            timeout = cfg.requestTimeout,
        )
        val start = System.currentTimeMillis()
        var result = "FAIL"
        var errorClass = "NONE"
        var status = -1
        var bytes = 0
        try {
            val resp: OpenAiHttpResponse = runBlocking { transport.post(request) }
            status = resp.statusCode
            bytes = resp.body.length
            result = if (status in 200..299 && parseChoiceOk(resp.body)) "PASS" else "FAIL"
        } catch (e: Exception) {
            errorClass = e::class.java.simpleName
        }
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "P2 RESULT=$result HTTP_STATUS=$status ELAPSED_MS=$elapsed RESPONSE_BYTES=$bytes FAILURE_CATEGORY=$errorClass")
        assertTrue("P2 must be PASS, got result=$result status=$status", result == "PASS")
    }

    // ------------------------------------------------------------- P3 provider

    @Test
    fun P3_provider() {
        val cfg = loadedConfig
        logNetwork()

        Log.i(TAG, "CASE=P3 MODEL=${cfg.model} SAFE_ENDPOINT=${safeEndpoint(cfg)}")
        val provider = OpenAiCompatibleProvider(cfg, AndroidOpenAiHttpTransport())
        val request = ModelRequest(
            sessionId = "headless-p3",
            instructions = "",
            history = listOf(UserMessage("Reply briefly with OK.")),
            tools = emptyList(),
        )
        val start = System.currentTimeMillis()
        var result = "FAIL"
        var turnType = "NONE"
        var contentLen = -1
        var errorClass = "NONE"
        try {
            val turn = runBlocking { provider.complete(request) }
            turnType = turn::class.java.simpleName
            if (turn is com.androidagent.core.ModelTurn.FinalAnswer) {
                contentLen = turn.content.length
                result = if (contentLen > 0) "PASS" else "FAIL"
            }
        } catch (e: Exception) {
            errorClass = e::class.java.simpleName
        }
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "P3 RESULT=$result TURN=$turnType CONTENT_LENGTH=$contentLen ELAPSED_MS=$elapsed FAILURE_CATEGORY=$errorClass")
        assertTrue("P3 must be PASS, got result=$result turn=$turnType", result == "PASS")
    }

    private companion object {
        const val TAG: String = "AG.PROVIDER-AB"
    }
}