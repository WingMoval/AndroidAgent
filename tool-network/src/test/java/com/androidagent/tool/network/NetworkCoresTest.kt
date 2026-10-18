package com.androidagent.tool.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

/**
 * L1 unit tests for the pure SSRF / URL / HTML cores (no tool, no network).
 * These are the deterministic heart of the PUBLIC_NETWORK_ONLY guarantee.
 */
class NetworkCoresTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    private fun v6(hex: String): InetAddress {
        val bytes = ByteArray(16) { (hex.substring(it * 2, it * 2 + 2).toInt(16)).toByte() }
        return InetAddress.getByAddress(bytes)
    }

    // ---------------------------------------------------------- PublicIpGuard IPv4

    @Test
    fun guard_publicV4_isPublic() {
        assertTrue(PublicIpGuard.isPublic(v4(8, 8, 8, 8)))
        assertTrue(PublicIpGuard.isPublic(v4(1, 1, 1, 1)))
        assertTrue(PublicIpGuard.isPublic(v4(93, 184, 216, 34)))
    }

    @Test
    fun guard_V4_blocked_ranges() {
        assertFalse(PublicIpGuard.isPublic(v4(0, 1, 2, 3)))          // 0.0.0.0/8
        assertFalse(PublicIpGuard.isPublic(v4(127, 0, 0, 1)))        // loopback
        assertFalse(PublicIpGuard.isPublic(v4(10, 0, 0, 1)))         // RFC1918
        assertFalse(PublicIpGuard.isPublic(v4(172, 16, 0, 1)))       // RFC1918 /12
        assertFalse(PublicIpGuard.isPublic(v4(172, 31, 255, 255)))   // RFC1918 /12 edge
        assertFalse(PublicIpGuard.isPublic(v4(192, 168, 0, 1)))      // RFC1918 /16
        assertFalse(PublicIpGuard.isPublic(v4(169, 254, 169, 254)))  // link-local / metadata
        assertFalse(PublicIpGuard.isPublic(v4(100, 64, 0, 1)))       // CGNAT
        assertFalse(PublicIpGuard.isPublic(v4(192, 0, 0, 1)))        // IETF 192.0.0.0/24
        assertFalse(PublicIpGuard.isPublic(v4(192, 0, 2, 1)))        // TEST-NET-1
        assertFalse(PublicIpGuard.isPublic(v4(198, 51, 100, 1)))     // TEST-NET-2
        assertFalse(PublicIpGuard.isPublic(v4(203, 0, 113, 1)))      // TEST-NET-3
        assertFalse(PublicIpGuard.isPublic(v4(198, 18, 0, 1)))       // benchmarking
        assertFalse(PublicIpGuard.isPublic(v4(224, 0, 0, 1)))        // multicast
        assertFalse(PublicIpGuard.isPublic(v4(240, 0, 0, 1)))        // reserved
        assertFalse(PublicIpGuard.isPublic(v4(255, 255, 255, 255)))  // broadcast
    }

    // ---------------------------------------------------------- PublicIpGuard IPv6

    @Test
    fun guard_publicV6_isPublic() {
        assertTrue(PublicIpGuard.isPublic(v6("2606280001010001f7df12d623ddd1a7"))) // example public v6
        assertTrue(PublicIpGuard.isPublic(v6("20014860486000010000000000000001"))) // ordinary global unicast
    }

    @Test
    fun guard_V6_blocked_ranges() {
        assertFalse(PublicIpGuard.isPublic(v6("00000000000000000000000000000000"))) // ::
        assertFalse(PublicIpGuard.isPublic(v6("00000000000000000000000000000001"))) // ::1
        assertFalse(PublicIpGuard.isPublic(v6("fe800000000000000000000000000001"))) // link-local
        assertFalse(PublicIpGuard.isPublic(v6("fd000000000000000000000000000001"))) // ULA
        assertFalse(PublicIpGuard.isPublic(v6("fc000000000000000000000000000001"))) // ULA
        assertFalse(PublicIpGuard.isPublic(v6("ff020000000000000000000000000001"))) // multicast
        assertFalse(PublicIpGuard.isPublic(v6("20010db8000000000000000000000001"))) // documentation
        assertFalse(PublicIpGuard.isPublic(v6("0064ff9b000000000000000000000001"))) // NAT64 prefix
    }

    @Test
    fun guard_V6_mapped_unwrap() {
        assertFalse(PublicIpGuard.isPublic(v6("00000000000000000000ffff7f000001"))) // ::ffff:127.0.0.1
        assertFalse(PublicIpGuard.isPublic(v6("00000000000000000000ffff0a000001"))) // ::ffff:10.0.0.1
        assertTrue(PublicIpGuard.isPublic(v6("00000000000000000000ffff08080808")))  // ::ffff:8.8.8.8
    }

    @Test
    fun guard_allPublic_mixed_denies() {
        val mixed = arrayOf(v4(8, 8, 8, 8), v4(127, 0, 0, 1))
        assertFalse(PublicIpGuard.allPublic(mixed))
    }

    @Test
    fun guard_allPublic_allPublic_allows() {
        val pub = arrayOf(v4(8, 8, 8, 8), v4(1, 1, 1, 1))
        assertTrue(PublicIpGuard.allPublic(pub))
    }

    @Test
    fun guard_allPublic_empty_denies() {
        assertFalse(PublicIpGuard.allPublic(emptyArray()))
    }

    // ---------------------------------------------------------- UrlValidator

    @Test
    fun url_valid_https() {
        val r = UrlValidator.validate("https://example.com/path?q=1")
        assertTrue(r is UrlValidator.ParseResult.Ok)
        val t = (r as UrlValidator.ParseResult.Ok).target
        assertEquals("example.com", t.host)
        assertEquals(443, t.port)
        assertEquals("/path?q=1", t.pathAndQuery)
        assertFalse(t.isIpLiteral)
    }

    @Test
    fun url_uppercase_host_is_lowercased() {
        val r = UrlValidator.validate("https://EXAMPLE.com/") as UrlValidator.ParseResult.Ok
        assertEquals("example.com", r.target.host)
    }

    @Test
    fun url_trailing_dot_is_stripped() {
        val r = UrlValidator.validate("https://example.com./") as UrlValidator.ParseResult.Ok
        assertEquals("example.com", r.target.host)
    }

    @Test
    fun url_explicit_port_is_kept() {
        val r = UrlValidator.validate("https://example.com:8443/") as UrlValidator.ParseResult.Ok
        assertEquals(8443, r.target.port)
    }

    @Test
    fun url_http_is_unsupported_scheme() {
        val r = UrlValidator.validate("http://example.com/")
        assertTrue(r is UrlValidator.ParseResult.Error)
        assertEquals(UrlValidator.UrlError.UNSUPPORTED_SCHEME, (r as UrlValidator.ParseResult.Error).error)
    }

    @Test
    fun url_file_is_unsupported_scheme() {
        val r = UrlValidator.validate("file:///etc/passwd")
        assertTrue(r is UrlValidator.ParseResult.Error)
        assertEquals(UrlValidator.UrlError.UNSUPPORTED_SCHEME, (r as UrlValidator.ParseResult.Error).error)
    }

    @Test
    fun url_credentials_are_rejected() {
        val r = UrlValidator.validate("https://user:pass@example.com/")
        assertTrue(r is UrlValidator.ParseResult.Error)
        assertEquals(UrlValidator.UrlError.INVALID_URL, (r as UrlValidator.ParseResult.Error).error)
    }

    @Test
    fun url_ipv4_literal_detected() {
        val r = UrlValidator.validate("https://93.184.216.34/") as UrlValidator.ParseResult.Ok
        assertTrue(r.target.isIpLiteral)
    }

    @Test
    fun url_ipv6_literal_detected() {
        val r = UrlValidator.validate("https://[2606:2800:1::]/") as UrlValidator.ParseResult.Ok
        assertTrue(r.target.isIpLiteral)
        assertTrue(r.target.host.contains(':'), "v6 host must contain ':': ${r.target.host}")
    }

    @Test
    fun url_blank_is_invalid() {
        val r = UrlValidator.validate("   ")
        assertTrue(r is UrlValidator.ParseResult.Error)
        assertEquals(UrlValidator.UrlError.INVALID_URL, (r as UrlValidator.ParseResult.Error).error)
    }

    // ---------------------------------------------------------- HtmlToText

    @Test
    fun html_title_and_body_extracted_script_style_removed() {
        val html = "<html><head><title>T</title><style>.x{color:red}</style></head>" +
            "<body><h1>Head</h1><p>Para</p><script>bad()</script></body></html>"
        val out = HtmlToText.extract(html)
        assertTrue(out.contains("T"))
        assertTrue(out.contains("Head"))
        assertTrue(out.contains("Para"))
        assertFalse(out.contains("bad()"))
        assertFalse(out.contains("color:red"))
    }

    @Test
    fun html_entities_decoded() {
        val out = HtmlToText.extract("<p>A &amp; B &lt;tag&gt; &quot;q&quot;</p>")
        assertTrue(out.contains("A & B"))
        assertTrue(out.contains("<tag>"))
        assertTrue(out.contains("\"q\""))
    }

    @Test
    fun html_whitespace_normalized() {
        val out = HtmlToText.extract("<p>  a    b   </p><div>  c  </div>")
        assertFalse(out.contains("    "), "runs of spaces must collapse: $out")
    }

    @Test
    fun html_comments_and_doctype_removed() {
        val out = HtmlToText.extract("<!DOCTYPE html><!-- a comment --><p>body</p>")
        assertFalse(out.contains("comment"))
        assertFalse(out.contains("DOCTYPE"))
        assertTrue(out.contains("body"))
    }
}
