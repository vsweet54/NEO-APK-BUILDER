package com.example.builder

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlModifier {

    /**
     * Replaces specific strings in an Android Binary XML (AXML) string pool.
     * Preserves all other XML chunks, resource IDs, namespaces, elements, and attributes.
     */
    fun replaceStrings(axmlBytes: ByteArray, replacements: Map<String, String>): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(axmlBytes).order(ByteOrder.LITTLE_ENDIAN)
            val rootTag = buffer.short.toInt() and 0xFFFF
            val rootHeaderSize = buffer.short.toInt() and 0xFFFF
            val originalRootSize = buffer.int

        if (rootTag != 0x0003) {
            // Not a RES_XML_TYPE chunk, return unmodified
            return axmlBytes
        }

        val spTag = buffer.short.toInt() and 0xFFFF
        val spHeaderSize = buffer.short.toInt() and 0xFFFF
        val originalSpSize = buffer.int

        if (spTag != 0x0001) {
            // Not a string pool
            return axmlBytes
        }

        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int
        val isUtf8 = (flags and (1 shl 8)) != 0

        // Read offsets
        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.int
        }

        // Read strings
        val strings = ArrayList<String>(stringCount)
        val spContentOffset = 8 // start of SP after root header

        for (i in 0 until stringCount) {
            val sStart = spContentOffset + stringsStart + offsets[i]
            if (isUtf8) {
                // UTF-8 string: 1-2 bytes length, then bytes, null-terminated
                var cur = sStart
                val len1 = axmlBytes[cur].toInt() and 0xFF
                cur++
                if ((len1 and 0x80) != 0) cur++ // skip second byte if multi-byte
                val bLen1 = axmlBytes[cur].toInt() and 0xFF
                cur++
                val bLen = if ((bLen1 and 0x80) != 0) {
                    val bLen2 = axmlBytes[cur].toInt() and 0xFF
                    cur++
                    ((bLen1 and 0x7F) shl 8) or bLen2
                } else bLen1
                strings.add(String(axmlBytes, cur, bLen, Charsets.UTF_8))
            } else {
                // UTF-16LE: 2 bytes length in characters
                val charLen = (axmlBytes[sStart].toInt() and 0xFF) or ((axmlBytes[sStart + 1].toInt() and 0xFF) shl 8)
                val sBytes = axmlBytes.copyOfRange(sStart + 2, sStart + 2 + charLen * 2)
                strings.add(String(sBytes, Charsets.UTF_16LE))
            }
        }

        // Apply string replacements
        for (i in strings.indices) {
            val original = strings[i]
            if (replacements.containsKey(original)) {
                strings[i] = replacements[original] ?: original
            }
        }

        // Re-encode string pool as UTF-16LE (matching AAPT2 default)
        val newStringsData = ArrayList<Byte>()
        val newOffsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            newOffsets[i] = newStringsData.size
            val s = strings[i]
            val sBytes = s.toByteArray(Charsets.UTF_16LE)
            val charLen = s.length

            // 2 bytes character length
            newStringsData.add((charLen and 0xFF).toByte())
            newStringsData.add(((charLen shr 8) and 0xFF).toByte())
            // UTF-16LE characters
            for (b in sBytes) {
                newStringsData.add(b)
            }
            // 2 bytes null terminator
            newStringsData.add(0.toByte())
            newStringsData.add(0.toByte())
        }

        // Pad strings data to 4-byte boundary
        while (newStringsData.size % 4 != 0) {
            newStringsData.add(0.toByte())
        }

        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newSpChunkSize = newStringsStart + newStringsData.size

        // Build new SP chunk
        val spBuf = ByteBuffer.allocate(newSpChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        spBuf.putShort(spTag.toShort())
        spBuf.putShort(spHeaderSize.toShort())
        spBuf.putInt(newSpChunkSize)
        spBuf.putInt(stringCount)
        spBuf.putInt(styleCount)
        spBuf.putInt(flags and (1 shl 8).inv()) // ensure UTF-16LE flag
        spBuf.putInt(newStringsStart)
        spBuf.putInt(stylesStart)

        for (off in newOffsets) {
            spBuf.putInt(off)
        }
        for (b in newStringsData) {
            spBuf.put(b)
        }

        val remainderOffset = 8 + originalSpSize
        val remainderLength = axmlBytes.size - remainderOffset
        val newTotalSize = 8 + newSpChunkSize + remainderLength

        val resultBuf = ByteBuffer.allocate(newTotalSize).order(ByteOrder.LITTLE_ENDIAN)
        resultBuf.putShort(rootTag.toShort())
        resultBuf.putShort(rootHeaderSize.toShort())
        resultBuf.putInt(newTotalSize)
        resultBuf.put(spBuf.array())
        if (remainderLength > 0) {
            resultBuf.put(axmlBytes, remainderOffset, remainderLength)
        }

        return resultBuf.array()
        } catch (e: Exception) {
            return axmlBytes
        }
    }
}
