package com.androidagent.tool.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * PUBLIC_NETWORK_ONLY SSRF core.
 *
 * Pure classification of resolved addresses: an address is "public" only if it is
 * globally routable and not in any special-use / private / loopback / link-local /
 * multicast / reserved / metadata range. This is the safety floor for the network
 * tool and is independent of approval.
 *
 * The rule is all-or-nothing per hostname: [allPublic] returns false if ANY
 * resolved address is non-public (a public+private mix is blocked).
 *
 * IPv4-mapped / IPv4-compatible IPv6 addresses are unwrapped to the embedded
 * IPv4 and classified as IPv4, so `::ffff:127.0.0.1` is blocked as loopback, not
 * passed as "public v6".
 *
 * This class performs no DNS and no network I/O; it is fully JVM-testable.
 */
object PublicIpGuard {

    /** True if [addresses] is non-empty and every address is globally public. */
    fun allPublic(addresses: Array<out InetAddress>): Boolean {
        if (addresses.isEmpty()) return false
        return addresses.all { isPublic(it) }
    }

    /** Classifies one resolved address. Unwraps IPv4-mapped/compat v6 to v4. */
    fun isPublic(addr: InetAddress): Boolean = when {
        addr is Inet6Address -> isPublicV6(addr)
        addr is Inet4Address -> isPublicV4(addr)
        else -> false
    }

    // --------------------------------------------------------------- IPv4

    /**
     * [n] is the unsigned 32-bit IPv4 address as an Int. Returns false for any
     * non-public (special-use / private / loopback / link-local / multicast /
     * reserved / metadata / CGNAT / TEST-NET / benchmarking) range.
     *
     * Implemented as explicit boundary checks (start..end inclusive) per range to
     * avoid shift-constant arithmetic errors.
     */
    internal fun isPublicV4(n: Int): Boolean {
        // 0.0.0.0/8 "this" network / unspecified
        if (inRange(n, ip4(0, 0, 0, 0), ip4(0, 255, 255, 255))) return false
        // 127.0.0.0/8 loopback
        if (inRange(n, ip4(127, 0, 0, 0), ip4(127, 255, 255, 255))) return false
        // 10.0.0.0/8 private
        if (inRange(n, ip4(10, 0, 0, 0), ip4(10, 255, 255, 255))) return false
        // 172.16.0.0/12 private
        if (inRange(n, ip4(172, 16, 0, 0), ip4(172, 31, 255, 255))) return false
        // 192.168.0.0/16 private
        if (inRange(n, ip4(192, 168, 0, 0), ip4(192, 168, 255, 255))) return false
        // 169.254.0.0/16 link-local (includes cloud metadata 169.254.169.254)
        if (inRange(n, ip4(169, 254, 0, 0), ip4(169, 254, 255, 255))) return false
        // 100.64.0.0/10 carrier-grade NAT / shared (RFC 6598) — not globally routable
        if (inRange(n, ip4(100, 64, 0, 0), ip4(100, 127, 255, 255))) return false
        // 192.0.0.0/24 IETF protocol assignments
        if (inRange(n, ip4(192, 0, 0, 0), ip4(192, 0, 0, 255))) return false
        // 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 TEST-NET documentation
        if (inRange(n, ip4(192, 0, 2, 0), ip4(192, 0, 2, 255))) return false
        if (inRange(n, ip4(198, 51, 100, 0), ip4(198, 51, 100, 255))) return false
        if (inRange(n, ip4(203, 0, 113, 0), ip4(203, 0, 113, 255))) return false
        // 198.18.0.0/15 benchmarking (198.18.0.0 - 198.19.255.255)
        if (inRange(n, ip4(198, 18, 0, 0), ip4(198, 19, 255, 255))) return false
        // 224.0.0.0/4 multicast
        if (inRange(n, ip4(224, 0, 0, 0), ip4(239, 255, 255, 255))) return false
        // 240.0.0.0/4 reserved (includes 255.255.255.255 broadcast)
        if (inRange(n, ip4(240, 0, 0, 0), ip4(255, 255, 255, 255))) return false
        return true
    }

    private fun ip4(a: Int, b: Int, c: Int, d: Int): Int =
        (a shl 24) or (b shl 16) or (c shl 8) or d

    private fun inRange(n: Int, lo: Int, hi: Int): Boolean = n in lo..hi

    private fun isPublicV4(a: Inet4Address): Boolean {
        val b = a.address
        val n = ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
        return isPublicV4(n)
    }

    // --------------------------------------------------------------- IPv6

    private fun isPublicV6(a: Inet6Address): Boolean {
        val b = a.address

        // Unwrap IPv4-mapped (::ffff:0:0/96) and IPv4-compatible (::/96, deprecated)
        // to the embedded IPv4 and classify it as IPv4.
        val first96Zero = (0 until 12).all { b[it] == 0.toByte() }
        val mapped = first96Zero && b[10] == 0xFF.toByte() && b[11] == 0xFF.toByte()
        val compat = first96Zero && b[10] == 0x00.toByte() && b[11] == 0x00.toByte()
        if (mapped || compat) {
            val n = ((b[12].toInt() and 0xFF) shl 24) or
                ((b[13].toInt() and 0xFF) shl 16) or
                ((b[14].toInt() and 0xFF) shl 8) or
                (b[15].toInt() and 0xFF)
            return isPublicV4(n)
        }

        // ::/128 unspecified
        if ((0 until 16).all { b[it] == 0.toByte() }) return false
        // ::1/128 loopback
        if ((0 until 15).all { b[it] == 0.toByte() } && (b[15].toInt() and 0xFF) == 0x01) return false
        // 64:ff9b::/96 NAT64 well-known prefix (translates to IPv4; treat non-public)
        if ((b[0].toInt() and 0xFF) == 0x64 && (b[1].toInt() and 0xFF) == 0xFF &&
            (b[2].toInt() and 0xFF) == 0x9B
        ) return false
        // 100::/64 discard-only
        if ((b[0].toInt() and 0xFF) == 0x01 && (b[1].toInt() and 0xFF) == 0x00 &&
            (b[2].toInt() and 0xFF) == 0x00 && (b[3].toInt() and 0xFF) == 0x00
        ) return false
        // fe80::/10 link-local
        if ((b[0].toInt() and 0xFF) == 0xFE && (b[1].toInt() and 0xC0) == 0x80) return false
        // fc00::/7 unique-local (fd00::/8 included)
        if ((b[0].toInt() and 0xFE) == 0xFC) return false
        // ff00::/8 multicast
        if ((b[0].toInt() and 0xFF) == 0xFF) return false
        // 2001:db8::/32 documentation
        if ((b[0].toInt() and 0xFF) == 0x20 && (b[1].toInt() and 0xFF) == 0x01 &&
            (b[2].toInt() and 0xFF) == 0x0D && (b[3].toInt() and 0xFF) == 0xB8
        ) return false
        // 2001::/23 IETF protocol assignments
        if ((b[0].toInt() and 0xFF) == 0x20 && (b[1].toInt() and 0xFF) == 0x01 &&
            (b[2].toInt() and 0xFE) == 0x00
        ) return false
        // Everything else in 2000::/3 not in a blocked range is public.
        return (b[0].toInt() and 0xE0) == 0x20
    }
}
