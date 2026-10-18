package com.androidagent.tool.file

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Text decode policy (Preflight §17, FROZEN): UTF-8 only + BOM detection.
 *
 * - BOM (EF BB BF) present -> stripped, decode as UTF-8.
 * - No BOM -> decode as UTF-8.
 * - Malformed UTF-8 -> CodingErrorAction.REPLACE (no charset auto-detection, no
 *   heavy detector; consistent with the network tool's decode strategy).
 *
 * Binary refusal is decided by the caller (NUL sniff) BEFORE this is used, so this
 * only ever decodes content that was not already classified as binary.
 */
internal object TextCodec {

    fun decodeUtf8(bytes: ByteArray): String {
        val stripped = stripBom(bytes)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(stripped)).toString()
    }

    private fun stripBom(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }
}
