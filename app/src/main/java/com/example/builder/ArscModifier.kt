package com.example.builder

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ArscModifier {

    fun replaceStrings(arscBytes: ByteArray, replacements: Map<String, String>): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(arscBytes).order(ByteOrder.LITTLE_ENDIAN)
            val rootTag = buffer.short.toInt() and 0xFFFF
            val rootHeaderSize = buffer.short.toInt() and 0xFFFF
            val originalRootSize = buffer.int
            val packageCount = buffer.int

            if (rootTag != 0x0002) {
                return arscBytes
            }

            val spTag = buffer.short.toInt() and 0xFFFF
            val spHeaderSize = buffer.short.toInt() and 0xFFFF
            val originalSpSize = buffer.int

            if (spTag != 0x0001) {
                return arscBytes
            }

            val stringCount = buffer.int
            val styleCount = buffer.int
            val flags = buffer.int
            val stringsStart = buffer.int
            val stylesStart = buffer.int
            val isUtf8 = (flags and (1 shl 8)) != 0

            val offsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                offsets[i] = buffer.int
            }

            val strings = ArrayList<String>(stringCount)
            val spContentOffset = 12

            for (i in 0 until stringCount) {
                val sStart = spContentOffset + stringsStart + offsets[i]
                if (isUtf8) {
                    var cur = sStart
                    val len1 = arscBytes[cur].toInt() and 0xFF
                    cur++
                    if ((len1 and 0x80) != 0) cur++
                    val bLen1 = arscBytes[cur].toInt() and 0xFF
                    cur++
                    val bLen = if ((bLen1 and 0x80) != 0) {
                        val bLen2 = arscBytes[cur].toInt() and 0xFF
                        cur++
                        ((bLen1 and 0x7F) shl 8) or bLen2
                    } else bLen1
                    strings.add(String(arscBytes, cur, bLen, Charsets.UTF_8))
                } else {
                    val charLen = (arscBytes[sStart].toInt() and 0xFF) or ((arscBytes[sStart + 1].toInt() and 0xFF) shl 8)
                    val sBytes = arscBytes.copyOfRange(sStart + 2, sStart + 2 + charLen * 2)
                    strings.add(String(sBytes, Charsets.UTF_16LE))
                }
            }

            var hasMatch = false
            for (i in strings.indices) {
                val orig = strings[i]
                if (replacements.containsKey(orig)) {
                    strings[i] = replacements[orig] ?: orig
                    hasMatch = true
                }
            }

            if (!hasMatch) {
                return arscBytes
            }

            val newStringsData = ArrayList<Byte>()
            val newOffsets = IntArray(stringCount)

            for (i in 0 until stringCount) {
                newOffsets[i] = newStringsData.size
                val s = strings[i]
                val sBytes = s.toByteArray(Charsets.UTF_16LE)
                val charLen = s.length

                newStringsData.add((charLen and 0xFF).toByte())
                newStringsData.add(((charLen shr 8) and 0xFF).toByte())
                for (b in sBytes) {
                    newStringsData.add(b)
                }
                newStringsData.add(0.toByte())
                newStringsData.add(0.toByte())
            }

            while (newStringsData.size % 4 != 0) {
                newStringsData.add(0.toByte())
            }

            val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
            val newSpChunkSize = newStringsStart + newStringsData.size

            val spBuf = ByteBuffer.allocate(newSpChunkSize).order(ByteOrder.LITTLE_ENDIAN)
            spBuf.putShort(spTag.toShort())
            spBuf.putShort(spHeaderSize.toShort())
            spBuf.putInt(newSpChunkSize)
            spBuf.putInt(stringCount)
            spBuf.putInt(styleCount)
            spBuf.putInt(flags and (1 shl 8).inv())
            spBuf.putInt(newStringsStart)
            spBuf.putInt(stylesStart)

            for (off in newOffsets) {
                spBuf.putInt(off)
            }
            for (b in newStringsData) {
                spBuf.put(b)
            }

            val remainderOffset = 12 + originalSpSize
            val remainderLength = arscBytes.size - remainderOffset
            val newTotalSize = 12 + newSpChunkSize + remainderLength

            val resultBuf = ByteBuffer.allocate(newTotalSize).order(ByteOrder.LITTLE_ENDIAN)
            resultBuf.putShort(rootTag.toShort())
            resultBuf.putShort(rootHeaderSize.toShort())
            resultBuf.putInt(newTotalSize)
            resultBuf.putInt(packageCount)
            resultBuf.put(spBuf.array())
            if (remainderLength > 0) {
                resultBuf.put(arscBytes, remainderOffset, remainderLength)
            }

            return resultBuf.array()
        } catch (e: Exception) {
            return arscBytes
        }
    }
}
