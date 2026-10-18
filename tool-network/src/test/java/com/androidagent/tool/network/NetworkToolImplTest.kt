package com.androidagent.tool.network

import com.androidagent.core.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * L1 unit tests for the network tool (AG.NETWORK-0 §32).
 *
 * The tool's pure logic (argument parsing, URL validation, SSRF classification,
 * content-type policy, text bound, result mapping) is tested with a fake [Fetcher]
 * and a fake resolver, so no real network / DNS / TLS is touched. The real
 * transport (AppHttpFetcher) is covered at L2 (device) and by integration.
 */
class NetworkToolImplTest {

    // ------------------------------------------------------------------ helpers

    private fun v4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    private fun v6(hex: String): InetAddress {
        val bytes = ByteArray(16) { (hex.substring(it * 2, it * 2 + 2).toInt(16)).toByte() }
        return InetAddress.getByAddress(bytes)
    }

    /** A fake Fetcher that returns a canned outcome, recording the call count. */
    private class FakeFetcher(
        private val outcome: FetchOutcome? = null,
        private val error: TransportError? = null,
    ) : Fetcher {
        var calls: Int = 0
        override suspend fun fetch(
            target: UrlValidator.Target,
            maxResponseBytes: Int,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
        ): FetchOutcome {
            calls++
            error?.let { throw it }
            return outcome ?: FetchOutcome(
                finalUrl = "https://${target.host}${target.pathAndQuery}",
                httpStatus = 200,
                contentType = "text/plain",
                bodyBytes = "ok".toByteArray(),
                bytesRead = 2,
                redirectCount = 0,
                truncatedBytes = false,
            )
        }
    }

    /** Fake resolver: returns the configured addresses for any host. */
    private fun resolver(varaddrs: Array<InetAddress>): (String) -> Array<InetAddress> =
        { varaddrs }

    private fun tool(fetcher: Fetcher, addrs: Array<InetAddress> = arrayOf(v4(93, 184, 216, 34))): NetworkToolImpl =
        NetworkToolImpl(fetcher, resolver(addrs))

    private fun call(args: String): ToolCall =
        ToolCall(id = "t1", name = NetworkToolImpl.TOOL_NAME, arguments = args)

    private fun root(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun body(outcome: FetchOutcome? = null): FetchOutcome =
        outcome ?: FetchOutcome(
            finalUrl = "https://example.com/",
            httpStatus = 200,
            contentType = "text/plain",
            bodyBytes = "hello".toByteArray(),
            bytesRead = 5,
            redirectCount = 0,
            truncatedBytes = false,
        )

    // ------------------------------------------------------------------ A. Arguments

    @Test
    fun A1_valid_fetch_parses_and_executes_once() = runBlocking {
        val fake = FakeFetcher()
        val t = tool(fake)
        val r = t.execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertEquals(1, fake.calls)
    }

    @Test
    fun A2_blank_arguments_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("   "))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A3_malformed_json_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("{not json"))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A4_missing_action_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"url":"https://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A5_missing_url_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A6_empty_url_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"  "}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A7_unknown_action_is_unsupported_action_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"request","url":"https://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_ARGUMENT.name))
        assertTrue(r.error!!.contains("unsupported action"))
    }

    @Test
    fun A8_non_object_arguments_is_invalid_argument_zero_calls() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("42"))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
    }

    // ------------------------------------------------------------------ B. Schemes

    @Test
    fun B1_http_scheme_is_unsupported_scheme_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"http://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.UNSUPPORTED_SCHEME.name))
    }

    @Test
    fun B2_file_scheme_is_unsupported_scheme_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"file:///etc/passwd"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.UNSUPPORTED_SCHEME.name))
    }

    @Test
    fun B3_ftp_scheme_is_unsupported_scheme_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"ftp://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.UNSUPPORTED_SCHEME.name))
    }

    @Test
    fun B4_content_scheme_is_unsupported_scheme_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"content://media/1"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.UNSUPPORTED_SCHEME.name))
    }

    @Test
    fun B5_https_scheme_passes_url_validation() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertEquals(1, fake.calls)
    }

    @Test
    fun B6_credentials_in_url_are_invalid_url_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"https://user:pass@example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_URL.name))
    }

    @Test
    fun B7_missing_host_is_invalid_url_zero_fetch() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake).execute(call("""{"action":"fetch","url":"https:///path"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.INVALID_URL.name))
    }

    // ------------------------------------------------------------------ C. IPv4 SSRF

    @Test
    fun C1_loopback_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(127, 0, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://127.0.0.1/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C2_rfc1918_10_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(10, 1, 2, 3)))
            .execute(call("""{"action":"fetch","url":"https://10.1.2.3/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C3_rfc1918_172_16_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(172, 16, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://172.16.0.1/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C4_rfc1918_192_168_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(192, 168, 1, 1)))
            .execute(call("""{"action":"fetch","url":"https://192.168.1.1/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C5_link_local_metadata_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(169, 254, 169, 254)))
            .execute(call("""{"action":"fetch","url":"https://169.254.169.254/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C6_unspecified_0_0_0_0_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(0, 0, 0, 0)))
            .execute(call("""{"action":"fetch","url":"https://0.0.0.0/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C7_cgnat_100_64_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(100, 64, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://100.64.0.1/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C8_multicast_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(224, 0, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://224.0.0.1/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C9_broadcast_reserved_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(255, 255, 255, 255)))
            .execute(call("""{"action":"fetch","url":"https://255.255.255.255/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C10_test_net_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v4(192, 0, 2, 5)))
            .execute(call("""{"action":"fetch","url":"https://192.0.2.5/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun C11_public_ipv4_is_allowed() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake, arrayOf(v4(93, 184, 216, 34)))
            .execute(call("""{"action":"fetch","url":"https://93.184.216.34/"}"""))
        assertTrue(r.success)
        assertEquals(1, fake.calls)
    }

    // ------------------------------------------------------------------ D. IPv6 SSRF

    @Test
    fun D1_v6_unspecified_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v6("00000000000000000000000000000000")))
            .execute(call("""{"action":"fetch","url":"https://[::]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D2_v6_loopback_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v6("00000000000000000000000000000001")))
            .execute(call("""{"action":"fetch","url":"https://[::1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D3_v6_link_local_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v6("fe800000000000000000000000000001")))
            .execute(call("""{"action":"fetch","url":"https://[fe80::1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D4_v6_ula_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v6("fd000000000000000000000000000001")))
            .execute(call("""{"action":"fetch","url":"https://[fd00::1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D5_v6_multicast_is_blocked() = runBlocking {
        val r = tool(FakeFetcher(), arrayOf(v6("ff020000000000000000000000000001")))
            .execute(call("""{"action":"fetch","url":"https://[ff02::1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D6_v6_mapped_loopback_is_blocked() = runBlocking {
        // ::ffff:127.0.0.1 -> unwrapped to 127.0.0.1 loopback -> blocked
        val r = tool(FakeFetcher(), arrayOf(v6("00000000000000000000ffff7f000001")))
            .execute(call("""{"action":"fetch","url":"https://[::ffff:127.0.0.1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D7_v6_mapped_private_is_blocked() = runBlocking {
        // ::ffff:10.0.0.1 -> unwrapped to 10.0.0.1 -> blocked
        val r = tool(FakeFetcher(), arrayOf(v6("00000000000000000000ffff0a000001")))
            .execute(call("""{"action":"fetch","url":"https://[::ffff:10.0.0.1]/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun D8_v6_public_is_allowed() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake, arrayOf(v6("2606280001010001f7df12d623ddd1a7")))
            .execute(call("""{"action":"fetch","url":"https://[2606:2800:1::]/"}"""))
        assertTrue(r.success)
        assertEquals(1, fake.calls)
    }

    // ------------------------------------------------------------------ E. DNS

    @Test
    fun E1_all_public_continues() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake, arrayOf(v4(93, 184, 216, 34), v4(151, 101, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertEquals(1, fake.calls)
    }

    @Test
    fun E2_mixed_public_private_is_blocked() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake, arrayOf(v4(93, 184, 216, 34), v4(127, 0, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun E3_all_private_is_blocked() = runBlocking {
        val fake = FakeFetcher()
        val r = tool(fake, arrayOf(v4(10, 0, 0, 1), v4(192, 168, 0, 1)))
            .execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun E4_dns_failure_is_dns_failure() = runBlocking {
        val fake = FakeFetcher()
        val t = NetworkToolImpl(fake) { throw java.net.UnknownHostException("nope") }
        val r = t.execute(call("""{"action":"fetch","url":"https://no-such-host.invalid/"}"""))
        assertFalse(r.success)
        assertEquals(0, fake.calls)
        assertTrue(r.error!!.startsWith(NetworkError.DNS_FAILURE.name))
    }

    // ------------------------------------------------------------------ F. Redirect (via Fetcher outcome)

    @Test
    fun F1_followed_redirect_reports_count_and_final_url() = runBlocking {
        val outcome = FetchOutcome(
            finalUrl = "https://final.example.com/landed",
            httpStatus = 200,
            contentType = "text/plain",
            bodyBytes = "landed".toByteArray(),
            bytesRead = 6,
            redirectCount = 2,
            truncatedBytes = false,
        )
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://start.example.com/"}"""))
        assertTrue(r.success)
        val j = root(r.output)
        assertEquals("https://final.example.com/landed", j["finalUrl"]!!.jsonPrimitive.content)
        assertEquals("2", j["redirectCount"]!!.jsonPrimitive.content)
    }

    @Test
    fun F2_redirect_to_private_is_destination_blocked() = runBlocking {
        val r = tool(FakeFetcher(error = TransportError(NetworkError.DESTINATION_BLOCKED, "redirect target is not public")))
            .execute(call("""{"action":"fetch","url":"https://start.example.com/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun F3_https_to_http_downgrade_is_denied() = runBlocking {
        val r = tool(FakeFetcher(error = TransportError(NetworkError.DESTINATION_BLOCKED, "redirect to non-https scheme is denied")))
            .execute(call("""{"action":"fetch","url":"https://start.example.com/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.DESTINATION_BLOCKED.name))
    }

    @Test
    fun F4_redirect_limit_is_reported() = runBlocking {
        val r = tool(FakeFetcher(error = TransportError(NetworkError.REDIRECT_LIMIT, "too many redirects")))
            .execute(call("""{"action":"fetch","url":"https://start.example.com/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.REDIRECT_LIMIT.name))
    }

    // ------------------------------------------------------------------ G. HTTP status semantics

    @Test
    fun G1_http_200_is_success_with_status() = runBlocking {
        val outcome = body().copy(httpStatus = 200, bodyBytes = "ok".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertEquals("200", root(r.output)["httpStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun G2_http_404_is_success_with_status_not_failure() = runBlocking {
        val outcome = body().copy(httpStatus = 404, bodyBytes = "<html>not found</html>".toByteArray(), contentType = "text/html")
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/missing"}"""))
        assertTrue(r.success)
        assertNull(r.error)
        assertEquals("404", root(r.output)["httpStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun G3_http_500_is_success_with_status_not_mechanism_failure() = runBlocking {
        val outcome = body().copy(httpStatus = 500, bodyBytes = "boom".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertNull(r.error)
        assertEquals("500", root(r.output)["httpStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun G4_http_403_is_success_not_destination_blocked() = runBlocking {
        val outcome = body().copy(httpStatus = 403, bodyBytes = "forbidden".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertTrue(r.success)
        assertEquals("403", root(r.output)["httpStatus"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ H. Content-Type

    @Test
    fun H1_text_plain_is_verbatim() = runBlocking {
        val outcome = body().copy(contentType = "text/plain", bodyBytes = "plain body".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertEquals("plain body", root(r.output)["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun H2_application_json_is_verbatim() = runBlocking {
        val json = """{"a":1}"""
        val outcome = body().copy(contentType = "application/json", bodyBytes = json.toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/api"}"""))
        assertEquals(json, root(r.output)["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun H3_text_html_is_extracted_to_readable_text() = runBlocking {
        val html = "<html><head><title>My Page</title><style>body{color:red}</style></head>" +
            "<body><h1>Hello</h1><p>World</p><script>evil()</script></body></html>"
        val outcome = body().copy(contentType = "text/html", bodyBytes = html.toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        val content = root(r.output)["content"]!!.jsonPrimitive.content
        assertTrue(content.contains("My Page"), "title expected: $content")
        assertTrue(content.contains("Hello"), "body expected: $content")
        assertFalse(content.contains("evil()"), "script must be removed: $content")
        assertFalse(content.contains("color:red"), "style must be removed: $content")
    }

    @Test
    fun H4_unsupported_binary_type_reaches_but_body_not_returned() = runBlocking {
        val outcome = body().copy(contentType = "image/png", bodyBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/x.png"}"""))
        assertTrue(r.success, "a response was reached")
        val j = root(r.output)
        assertEquals("", j["content"]!!.jsonPrimitive.content)
        assertTrue(j.containsKey("note"), "note expected for rejected binary type")
    }

    @Test
    fun H5_pdf_type_reaches_but_body_not_returned() = runBlocking {
        val outcome = body().copy(contentType = "application/pdf", bodyBytes = "%PDF-1.4".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/x.pdf"}"""))
        assertTrue(r.success)
        assertEquals("", root(r.output)["content"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ I. Bounds

    @Test
    fun I1_text_over_max_chars_is_truncated_and_flagged() = runBlocking {
        val big = "x".repeat(NetworkToolImpl.MAX_CONTENT_CHARS + 5000)
        val outcome = body().copy(bodyBytes = big.toByteArray(), bytesRead = big.length)
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        val j = root(r.output)
        assertEquals("true", j["truncated"]!!.jsonPrimitive.content)
        assertEquals(NetworkToolImpl.MAX_CONTENT_CHARS, j["content"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun I2_byte_truncation_flag_propagates() = runBlocking {
        val outcome = body().copy(truncatedBytes = true, bytesRead = NetworkToolImpl.MAX_RESPONSE_BYTES)
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertEquals("true", root(r.output)["truncated"]!!.jsonPrimitive.content)
        assertEquals(NetworkToolImpl.MAX_RESPONSE_BYTES.toString(), root(r.output)["bytesRead"]!!.jsonPrimitive.content)
    }

    @Test
    fun I3_under_bounds_is_not_truncated() = runBlocking {
        val outcome = body().copy(bodyBytes = "small".toByteArray())
        val r = tool(FakeFetcher(outcome)).execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertEquals("false", root(r.output)["truncated"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ J. Cancellation

    @Test
    fun J1_cancellation_propagates_not_converted() = runBlocking {
        val fetcher = Fetcher { _, _, _, _ -> throw CancellationException("run cancelled") }
        val t = tool(fetcher)
        val threw = try {
            t.execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
            false
        } catch (e: CancellationException) {
            true
        }
        assertTrue(threw, "CancellationException must propagate, not be converted to a ToolResult")
    }

    @Test
    fun J2_timeout_is_transport_failure_not_success() = runBlocking {
        val r = tool(FakeFetcher(error = TransportError(NetworkError.READ_TIMEOUT, "request timed out")))
            .execute(call("""{"action":"fetch","url":"https://example.com/"}"""))
        assertFalse(r.success)
        assertTrue(r.error!!.startsWith(NetworkError.READ_TIMEOUT.name))
    }
}
