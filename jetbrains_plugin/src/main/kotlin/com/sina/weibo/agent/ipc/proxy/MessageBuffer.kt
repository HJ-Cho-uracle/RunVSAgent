// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 인자 타입(Argument Type) 열거형입니다.
 * VSCode의 `ArgType`에 해당하며, RPC 메시지에서 사용되는 인자의 종류를 정의합니다.
 */
enum class ArgType(val value: Int) {
    /** 문자열 타입 */
    String(1),

    /** 바이너리 버퍼 타입 */
    VSBuffer(2),

    /** 버퍼를 포함하는 직렬화된 객체 타입 */
    SerializedObjectWithBuffers(3),

    /** 정의되지 않은 타입 (null과 유사) */
    Undefined(4),
    ;

    companion object {
        /**
         * 정수 값으로부터 `ArgType`을 가져옵니다.
         */
        fun fromValue(value: Int): ArgType? = values().find { it.value == value }
    }
}

/**
 * 혼합된 인자 타입(Mixed Argument Type)을 나타내는 봉인된(sealed) 클래스입니다.
 * RPC 메시지에서 다양한 형태의 인자를 캡슐화합니다.
 */
sealed class MixedArg {
    /** 문자열 인자 */
    data class StringArg(val value: ByteArray) : MixedArg()

    /** 바이너리 버퍼 인자 */
    data class VSBufferArg(val value: ByteArray) : MixedArg()

    /** 버퍼를 포함하는 직렬화된 객체 인자 */
    data class SerializedObjectWithBuffersArg(val value: ByteArray, val buffers: List<ByteArray>) : MixedArg()

    /** 정의되지 않은 인자 */
    object UndefinedArg : MixedArg()
}

/**
 * 메시지 버퍼 클래스입니다.
 * RPC 메시지를 효율적으로 직렬화(쓰기)하고 역직렬화(읽기)하는 기능을 제공합니다.
 * VSCode의 `MessageBuffer`에 해당합니다.
 */
class MessageBuffer private constructor(
    private val buffer: ByteBuffer,
) {
    companion object {
        /**
         * 지정된 크기의 메시지 버퍼를 할당합니다.
         * @param type 메시지 타입
         * @param req 요청 ID
         * @param messageSize 메시지 본문의 크기
         * @return 할당된 `MessageBuffer` 인스턴스
         */
        fun alloc(type: MessageType, req: Int, messageSize: Int): MessageBuffer {
            val totalSize = messageSize + 1 + 4 // type + req
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN) // 빅 엔디안으로 설정
            val result = MessageBuffer(buffer)
            result.writeUInt8(type.value) // 메시지 타입 쓰기
            result.writeUInt32(req) // 요청 ID 쓰기
            return result
        }

        /**
         * 바이트 배열로부터 메시지 버퍼를 읽습니다.
         * @param buff 읽을 바이트 배열
         * @param offset 읽기 시작할 오프셋
         * @return `MessageBuffer` 인스턴스
         */
        fun read(buff: ByteArray, offset: Int): MessageBuffer {
            val buffer = ByteBuffer.wrap(buff).order(ByteOrder.BIG_ENDIAN)
            buffer.position(offset) // 읽기 시작 위치 설정
            return MessageBuffer(buffer)
        }

        // --- 상수: 각 데이터 타입의 바이트 크기 ---
        const val sizeUInt8: Int = 1
        const val sizeUInt32: Int = 4

        /** 짧은 문자열의 크기를 계산합니다. */
        fun sizeShortString(str: ByteArray): Int {
            return sizeUInt8 + str.size // 문자열 길이 + 실제 문자열
        }

        /** 긴 문자열의 크기를 계산합니다. */
        fun sizeLongString(str: ByteArray): Int {
            return sizeUInt32 + str.size // 문자열 길이 + 실제 문자열
        }

        /** 바이너리 버퍼의 크기를 계산합니다. */
        fun sizeVSBuffer(buff: ByteArray): Int {
            return sizeUInt32 + buff.size // 버퍼 길이 + 실제 버퍼
        }

        /** 혼합된 배열의 크기를 계산합니다. */
        fun sizeMixedArray(arr: List<MixedArg>): Int {
            var size = 0
            size += 1 // 배열 길이 (UInt8)
            for (el in arr) {
                size += 1 // 인자 타입 (UInt8)
                when (el) {
                    is MixedArg.StringArg -> size += sizeLongString(el.value)
                    is MixedArg.VSBufferArg -> size += sizeVSBuffer(el.value)
                    is MixedArg.SerializedObjectWithBuffersArg -> {
                        size += sizeUInt32 // 버퍼 개수 (UInt32)
                        size += sizeLongString(el.value) // 직렬화된 객체 (JSON 문자열)
                        for (buffer in el.buffers) {
                            size += sizeVSBuffer(buffer) // 각 버퍼
                        }
                    }
                    is MixedArg.UndefinedArg -> Unit // Undefined는 추가 데이터 없음
                }
            }
            return size
        }
    }

    /**
     * 내부 `ByteBuffer`의 바이트 배열을 가져옵니다.
     */
    val bytes: ByteArray
        get() = buffer.array()

    /**
     * 버퍼의 총 바이트 길이를 가져옵니다.
     */
    val byteLength: Int
        get() = buffer.array().size

    // --- 쓰기 메소드 ---
    fun writeUInt8(n: Int) { buffer.put(n.toByte()) }
    fun writeUInt32(n: Int) { buffer.putInt(n) }
    fun writeShortString(str: ByteArray) {
        buffer.put(str.size.toByte())
        buffer.put(str)
    }
    fun writeLongString(str: ByteArray) {
        buffer.putInt(str.size)
        buffer.put(str)
    }
    fun writeBuffer(buff: ByteArray) {
        buffer.putInt(buff.size)
        buffer.put(buff)
    }
    fun writeVSBuffer(buff: ByteArray) {
        buffer.putInt(buff.size)
        buffer.put(buff)
    }

    /**
     * 혼합된 인자 배열을 버퍼에 씁니다.
     */
    fun writeMixedArray(arr: List<MixedArg>) {
        buffer.put(arr.size.toByte()) // 배열의 길이 쓰기
        for (el in arr) {
            when (el) {
                is MixedArg.StringArg -> {
                    writeUInt8(ArgType.String.value)
                    writeLongString(el.value)
                }
                is MixedArg.VSBufferArg -> {
                    writeUInt8(ArgType.VSBuffer.value)
                    writeVSBuffer(el.value)
                }
                is MixedArg.SerializedObjectWithBuffersArg -> {
                    writeUInt8(ArgType.SerializedObjectWithBuffers.value)
                    writeUInt32(el.buffers.size) // 버퍼 개수 쓰기
                    writeLongString(el.value) // 직렬화된 객체 (JSON 문자열) 쓰기
                    for (buffer in el.buffers) {
                        writeBuffer(buffer) // 각 버퍼 쓰기
                    }
                }
                is MixedArg.UndefinedArg -> {
                    writeUInt8(ArgType.Undefined.value)
                }
            }
        }
    }

    // --- 읽기 메소드 ---
    fun readUInt8(): Int { return buffer.get().toInt() and 0xFF }
    fun readUInt32(): Int { return buffer.getInt() }
    fun readShortString(): String {
        val strByteLength = buffer.get().toInt() and 0xFF
        val strBuff = ByteArray(strByteLength)
        buffer.get(strBuff)
        return String(strBuff)
    }
    fun readLongString(): String {
        val strByteLength = buffer.getInt()
        val strBuff = ByteArray(strByteLength)
        buffer.get(strBuff)
        return String(strBuff)
    }
    fun readVSBuffer(): ByteArray {
        val buffLength = buffer.getInt()
        val buff = ByteArray(buffLength)
        buffer.get(buff)
        return buff
    }

    /**
     * 버퍼에서 혼합된 인자 배열을 읽습니다.
     */
    fun readMixedArray(): List<Any?> {
        val arrLen = readUInt8() // 배열의 길이 읽기
        val arr = ArrayList<Any?>(arrLen)

        for (i in 0 until arrLen) {
            val argType = ArgType.fromValue(readUInt8()) ?: ArgType.Undefined // 인자 타입 읽기
            when (argType) {
                ArgType.String -> arr.add(readLongString())
                ArgType.VSBuffer -> arr.add(readVSBuffer())
                ArgType.SerializedObjectWithBuffers -> {
                    val bufferCount = readUInt32() // 버퍼 개수 읽기
                    val jsonString = readLongString() // 직렬화된 객체 (JSON 문자열) 읽기
                    val buffers = ArrayList<ByteArray>(bufferCount)
                    for (j in 0 until bufferCount) {
                        buffers.add(readVSBuffer()) // 각 버퍼 읽기
                    }
                    arr.add(SerializableObjectWithBuffers(parseJsonAndRestoreBufferRefs(jsonString, buffers, null)))
                }
                ArgType.Undefined -> arr.add(null)
            }
        }
        return arr
    }
}

/**
 * JSON 문자열을 파싱하고 버퍼 참조를 복원합니다.
 * VSCode의 `parseJsonAndRestoreBufferRefs`에 해당합니다.
 * @param jsonString 파싱할 JSON 문자열
 * @param buffers 복원할 버퍼 목록
 * @param uriTransformer URI 변환기 (선택 사항)
 * @return 파싱되고 버퍼 참조가 복원된 객체
 */
fun parseJsonAndRestoreBufferRefs(
    jsonString: String,
    buffers: List<ByteArray>,
    uriTransformer: ((String, Any?) -> Any?)? = null,
): Any {
    // 실제 프로젝트에서는 더 완전한 기능이 구현되어야 합니다.
    // JSON 문자열을 파싱하고, 버퍼 참조를 복원하며, URI 변환을 적용해야 합니다.
    return jsonString
}
