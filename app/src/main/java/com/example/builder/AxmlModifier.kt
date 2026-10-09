package com.example.builder

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlModifier {

    /**
     * Modifies Android Binary XML (AXML):
     * 1. Replaces specific strings in the string pool (packageName, appName, versionName, etc.)
     * 2. Dynamically filters <uses-permission> tags so only requested permissions appear in final APK
     * 3. Dynamically updates integer attributes in <manifest> and <uses-sdk>:
     *    - targetSdkVersion (0x01010270) -> ensures modern Android (14+) compatibility
     *    - minSdkVersion (0x0101020c) -> ensures minimum SDK requirements
     *    - versionCode (0x0101021b) -> ensures versioning compliance
     *    - compileSdkVersion (0x01010572) -> aligns with targetSdk
     */
    fun modifyManifest(
        axmlBytes: ByteArray,
        replacements: Map<String, String>,
        versionCode: Int = 1,
        minSdkVersion: Int = 21,
        targetSdkVersion: Int = 34,
        enabledPermissions: Set<String>? = null
    ): ByteArray {
        val withReplacedStrings = replaceStrings(axmlBytes, replacements)
        return try {
            val buffer = ByteBuffer.wrap(withReplacedStrings).order(ByteOrder.LITTLE_ENDIAN)
            val rootTag = buffer.short.toInt() and 0xFFFF
            if (rootTag != 0x0003) return withReplacedStrings

            val rootHeaderSize = buffer.short.toInt() and 0xFFFF
            val originalTotalSize = buffer.int

            val spTag = buffer.short.toInt() and 0xFFFF
            val spHeaderSize = buffer.short.toInt() and 0xFFFF
            val spSize = buffer.int
            if (spTag != 0x0001) return withReplacedStrings

            // Read strings in the modified string pool to identify element and attribute names
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
            val spContentOffset = 8
            for (i in 0 until stringCount) {
                val sStart = spContentOffset + stringsStart + offsets[i]
                if (isUtf8) {
                    var cur = sStart
                    val len1 = withReplacedStrings[cur].toInt() and 0xFF
                    cur++
                    if ((len1 and 0x80) != 0) cur++
                    val bLen1 = withReplacedStrings[cur].toInt() and 0xFF
                    cur++
                    val bLen = if ((bLen1 and 0x80) != 0) {
                        val bLen2 = withReplacedStrings[cur].toInt() and 0xFF
                        cur++
                        ((bLen1 and 0x7F) shl 8) or bLen2
                    } else bLen1
                    strings.add(String(withReplacedStrings, cur, bLen, Charsets.UTF_8))
                } else {
                    val charLen = (withReplacedStrings[sStart].toInt() and 0xFF) or
                            ((withReplacedStrings[sStart + 1].toInt() and 0xFF) shl 8)
                    val sBytes = withReplacedStrings.copyOfRange(sStart + 2, sStart + 2 + charLen * 2)
                    strings.add(String(sBytes, Charsets.UTF_16LE))
                }
            }

            var offset = 8 + spSize
            if (offset + 8 > withReplacedStrings.size) return withReplacedStrings

            // Resource map chunk
            val rmtTag = buffer.getShort(offset).toInt() and 0xFFFF
            val rmtHdrSize = buffer.getShort(offset + 2).toInt() and 0xFFFF
            val rmtSize = buffer.getInt(offset + 4)
            if (rmtTag != 0x0180) return withReplacedStrings

            val numResIds = (rmtSize - rmtHdrSize) / 4
            val resIds = IntArray(numResIds) { buffer.getInt(offset + rmtHdrSize + it * 4) }

            val usesPermIdx = strings.indexOf("uses-permission")

            // 1. Filter chunks (filter uses-permission elements)
            val keptChunks = ArrayList<ByteArray>()
            keptChunks.add(withReplacedStrings.copyOfRange(0, offset + rmtSize))
            offset += rmtSize

            while (offset + 8 <= withReplacedStrings.size) {
                val chunkTag = buffer.getShort(offset).toInt() and 0xFFFF
                val chunkHdrSize = buffer.getShort(offset + 2).toInt() and 0xFFFF
                val chunkSize = buffer.getInt(offset + 4)
                if (chunkSize <= 0) break

                var keep = true
                if (enabledPermissions != null && chunkTag == 0x0102 && usesPermIdx >= 0) { // START_ELEMENT
                    val nameIdx = buffer.getInt(offset + 20)
                    if (nameIdx == usesPermIdx) {
                        val attrStart = buffer.getShort(offset + 24).toInt() and 0xFFFF
                        val aOff = offset + 16 + attrStart
                        if (aOff + 20 <= withReplacedStrings.size) {
                            val aRawVal = buffer.getInt(aOff + 8)
                            val aDataVal = buffer.getInt(aOff + 16)
                            val permName = if (aRawVal in strings.indices) {
                                strings[aRawVal]
                            } else if (aDataVal in strings.indices) {
                                strings[aDataVal]
                            } else {
                                ""
                            }
                            if (!enabledPermissions.contains(permName)) {
                                keep = false
                                // Skip this start_element and matching end_element (0x0103)
                                val endElemOffset = offset + chunkSize
                                val endTag = if (endElemOffset + 2 <= withReplacedStrings.size) {
                                    buffer.getShort(endElemOffset).toInt() and 0xFFFF
                                } else 0
                                val endElemSize = if (endTag == 0x0103 && endElemOffset + 8 <= withReplacedStrings.size) {
                                    buffer.getInt(endElemOffset + 4)
                                } else 0
                                offset += chunkSize + if (endElemSize > 0) endElemSize else 0
                                continue
                            }
                        }
                    }
                }

                if (keep) {
                    keptChunks.add(withReplacedStrings.copyOfRange(offset, offset + chunkSize))
                    offset += chunkSize
                }
            }

            // Assemble filtered AXML
            val totalKeptBytes = keptChunks.sumOf { it.size }
            val filteredBytes = ByteArray(totalKeptBytes)
            var destPos = 0
            for (chunk in keptChunks) {
                System.arraycopy(chunk, 0, filteredBytes, destPos, chunk.size)
                destPos += chunk.size
            }

            val finalBuf = ByteBuffer.wrap(filteredBytes).order(ByteOrder.LITTLE_ENDIAN)
            // Update total root chunk size
            finalBuf.putInt(4, totalKeptBytes)

            // 2. Update integer attributes: targetSdkVersion, minSdkVersion, versionCode, compileSdkVersion
            var walkOffset = 8 + spSize + rmtSize
            while (walkOffset + 8 <= filteredBytes.size) {
                val chunkTag = finalBuf.getShort(walkOffset).toInt() and 0xFFFF
                val chunkSize = finalBuf.getInt(walkOffset + 4)
                if (chunkSize <= 0) break

                if (chunkTag == 0x0102) { // START_ELEMENT
                    val attrStart = finalBuf.getShort(walkOffset + 24).toInt() and 0xFFFF
                    val attrSize = finalBuf.getShort(walkOffset + 26).toInt() and 0xFFFF
                    val attrCount = finalBuf.getShort(walkOffset + 28).toInt() and 0xFFFF

                    for (i in 0 until attrCount) {
                        val aOff = walkOffset + 16 + attrStart + (i * attrSize)
                        if (aOff + 20 <= filteredBytes.size) {
                            val aNameIdx = finalBuf.getInt(aOff + 4)
                            val resId = if (aNameIdx in resIds.indices) resIds[aNameIdx] else 0

                            when (resId) {
                                0x01010270 -> { // android:targetSdkVersion
                                    finalBuf.putInt(aOff + 16, targetSdkVersion)
                                }
                                0x0101020c -> { // android:minSdkVersion
                                    finalBuf.putInt(aOff + 16, minSdkVersion)
                                }
                                0x0101021b -> { // android:versionCode
                                    finalBuf.putInt(aOff + 16, versionCode)
                                }
                                0x01010572 -> { // android:compileSdkVersion
                                    finalBuf.putInt(aOff + 16, targetSdkVersion)
                                }
                            }
                        }
                    }
                }
                walkOffset += chunkSize
            }

            filteredBytes
        } catch (e: Exception) {
            withReplacedStrings
        }
    }

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
                    var cur = sStart
                    val len1 = axmlBytes[cur].toInt() and 0xFF
                    cur++
                    if ((len1 and 0x80) != 0) cur++
                    val bLen1 = axmlBytes[cur].toInt() and 0xFF
                    cur++
                    val bLen = if ((bLen1 and 0x80) != 0) {
                        val bLen2 = axmlBytes[cur].toInt() and 0xFF
                        cur++
                        ((bLen1 and 0x7F) shl 8) or bLen2
                    } else bLen1
                    strings.add(String(axmlBytes, cur, bLen, Charsets.UTF_8))
                } else {
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

            // Re-encode string pool as UTF-16LE
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
