// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import java.lang.Exception

// 직렬화 중 버퍼 참조를 위한 심볼 이름
private const val REF_SYMBOL_NAME = "\$\$ref\$\$"

// 정의되지 않은 참조를 나타내는 맵
private val UNDEFINED_REF = mapOf(REF_SYMBOL_NAME to -1)

/**
 * 버퍼 참조를 포함하는 JSON 문자열을 나타내는 데이터 클래스입니다.
 * @property jsonString 버퍼 참조가 플레이스홀더로 대체된 JSON 문자열
 * @property referencedBuffers JSON 문자열에서 참조된 실제 바이너리 버퍼 목록
 */
data class StringifiedJsonWithBufferRefs(
    val jsonString: String,
    val referencedBuffers: List<ByteArray>
) {
    // data class는 component1(), component2() 함수를 자동으로 생성하여 구조 분해 할당을 지원합니다.
}

/**
 * 객체를 JSON 문자열로 직렬화하고, 포함된 바이너리 버퍼를 별도로 추출합니다.
 * @param obj 직렬화할 객체
 * @param replacer 직렬화 중 값을 대체할 함수 (선택 사항)
 * @param useSafeStringify 안전한 직렬화 사용 여부 (오류 발생 시 "null" 반환)
 * @return `StringifiedJsonWithBufferRefs` 객체
 */
fun stringifyJsonWithBufferRefs(obj: Any?, replacer: ((String, Any?) -> Any?)? = null, useSafeStringify: Boolean = false): StringifiedJsonWithBufferRefs {
    val foundBuffers = mutableListOf<ByteArray>() // 발견된 버퍼들을 저장할 리스트
    
    // 객체를 재귀적으로 처리하여 버퍼를 식별하고 대체합니다.
    fun processObject(value: Any?): Any? {
        return when (value) {
            null -> null
            is ByteArray -> {
                val bufferIndex = foundBuffers.size
                foundBuffers.add(value)
                // 버퍼를 참조 심볼과 인덱스로 대체합니다.
                mapOf(REF_SYMBOL_NAME to bufferIndex)
            }
            is Map<*, *> -> {
                val result = mutableMapOf<String, Any?>()
                value.forEach { (k, v) ->
                    val key = k.toString()
                    val processedValue = processObject(v) // 재귀 호출
                    val finalValue = replacer?.invoke(key, processedValue) ?: processedValue
                    result[key] = finalValue
                }
                result
            }
            is List<*> -> {
                value.map { processObject(it) } // 리스트의 각 요소를 재귀 처리
            }
            is Array<*> -> {
                value.map { processObject(it) } // 배열의 각 요소를 재귀 처리
            }
            is SerializableObjectWithBuffers<*> -> {
                // `SerializableObjectWithBuffers` 객체의 실제 값을 처리합니다.
                processObject(value.value)
            }
            else -> {
                // 다른 기본 타입은 그대로 반환합니다.
                value
            }
        }
    }
    
    // 객체를 처리하고 버퍼를 수집합니다.
    val processedObj = processObject(obj)
    
    // GSON을 사용하여 직렬화합니다.
    val gson = Gson()
    val serialized = try {
        gson.toJson(processedObj)
    } catch (e: Exception) {
        if (useSafeStringify) "null" else throw e // 안전 모드에서는 "null" 반환
    }
    
    return StringifiedJsonWithBufferRefs(serialized, foundBuffers)
}

/**
 * 요청 인자 직렬화 타입입니다.
 */
sealed class SerializedRequestArguments {
    /** 간단한 타입의 인자 */
    data class Simple(val args: String) : SerializedRequestArguments(){
        override fun toString(): String {
            return args
        }
    }
    
    /** 혼합된 타입의 인자 */
    data class Mixed(val args: List<MixedArg>) : SerializedRequestArguments(){
        override fun toString(): String {
            return args.joinToString { "\n" }
        }
    }
}

/**
 * 메시지 IO 유틸리티 클래스입니다.
 * RPC 통신을 위한 메시지 직렬화/역직렬화 기능을 제공합니다.
 * VSCode의 `MessageIO`에 해당합니다.
 */
object MessageIO {
    /**
     * 혼합된 인자 직렬화를 사용할지 여부를 확인합니다.
     * 인자 목록에 `ByteArray`, `SerializableObjectWithBuffers`, `null`이 포함되어 있으면 혼합 직렬화를 사용합니다.
     */
    private fun useMixedArgSerialization(arr: List<Any?>): Boolean {
        for (arg in arr) {
            if (arg is ByteArray || arg is SerializableObjectWithBuffers<*> || arg == null) {
                return true
            }
        }
        return false
    }
    
    /**
     * 요청 인자를 직렬화합니다.
     * @param args 직렬화할 인자 목록
     * @param replacer 직렬화 중 값을 대체할 함수 (선택 사항)
     * @return 직렬화된 요청 인자
     */
    fun serializeRequestArguments(args: List<Any?>, replacer: ((String, Any?) -> Any?)? = null): SerializedRequestArguments {
        if (useMixedArgSerialization(args)) {
            val massagedArgs = mutableListOf<MixedArg>()
            for (i in args.indices) {
                val arg = args[i]
                when {
                    arg is ByteArray -> massagedArgs.add(MixedArg.VSBufferArg(arg))
                    arg == null -> massagedArgs.add(MixedArg.UndefinedArg)
                    arg is SerializableObjectWithBuffers<*> -> {
                        val result = stringifyJsonWithBufferRefs(arg.value, replacer)
                        massagedArgs.add(MixedArg.SerializedObjectWithBuffersArg(
                            result.jsonString.toByteArray(),
                            result.referencedBuffers
                        ))
                    }
                    else -> {
                        val gson = Gson()
                        massagedArgs.add(MixedArg.StringArg(gson.toJson(arg).toByteArray()))
                    }
                }
            }
            return SerializedRequestArguments.Mixed(massagedArgs)
        }
        
        val gson = Gson()
        return SerializedRequestArguments.Simple(gson.toJson(args))
    }
    
    /**
     * 요청 메시지를 직렬화합니다.
     * @param req 요청 ID
     * @param rpcId RPC ID
     * @param method 호출할 메소드 이름
     * @param serializedArgs 직렬화된 인자
     * @param usesCancellationToken 취소 토큰 사용 여부
     * @return 직렬화된 요청 메시지 (바이트 배열)
     */
    fun serializeRequest(
        req: Int,
        rpcId: Int,
        method: String,
        serializedArgs: SerializedRequestArguments,
        usesCancellationToken: Boolean
    ): ByteArray {
        return when (serializedArgs) {
            is SerializedRequestArguments.Simple ->
                requestJSONArgs(req, rpcId, method, serializedArgs.args, usesCancellationToken)
            is SerializedRequestArguments.Mixed ->
                requestMixedArgs(req, rpcId, method, serializedArgs.args, usesCancellationToken)
        }
    }
    
    /**
     * JSON 인자 요청을 직렬화합니다.
     */
    private fun requestJSONArgs(
        req: Int,
        rpcId: Int,
        method: String,
        args: String,
        usesCancellationToken: Boolean
    ): ByteArray {
        val methodBuff = method.toByteArray()
        val argsBuff = args.toByteArray()
        
        var len = 0
        len += MessageBuffer.sizeUInt8 // 메시지 타입
        len += MessageBuffer.sizeShortString(methodBuff) // 메소드 이름
        len += MessageBuffer.sizeLongString(argsBuff) // 인자
        
        val messageType = if (usesCancellationToken)
            MessageType.RequestJSONArgsWithCancellation
        else
            MessageType.RequestJSONArgs
            
        val result = MessageBuffer.alloc(messageType, req, len)
        result.writeUInt8(rpcId)
        result.writeShortString(methodBuff)
        result.writeLongString(argsBuff)
        return result.bytes
    }
    
    /**
     * JSON 인자 요청을 역직렬화합니다.
     */
    fun deserializeRequestJSONArgs(buff: MessageBuffer): Triple<Int, String, List<Any?>> {
        val rpcId = buff.readUInt8()
        var method = buff.readShortString()
        if (method.startsWith("\$")) {
            method = method.substring(1)
        }
        val argsJson = buff.readLongString()
        val gson = Gson()
        val listType = object : TypeToken<List<Any?>>() {}.type
        val args = gson.fromJson<List<Any?>>(argsJson, listType)
        
        return Triple(rpcId, method, args)
    }
    
    /**
     * 혼합된 인자 요청을 직렬화합니다.
     */
    private fun requestMixedArgs(
        req: Int,
        rpcId: Int,
        method: String,
        args: List<MixedArg>,
        usesCancellationToken: Boolean
    ): ByteArray {
        val methodBuff = method.toByteArray()
        
        var len = 0
        len += MessageBuffer.sizeUInt8 // 메시지 타입
        len += MessageBuffer.sizeShortString(methodBuff) // 메소드 이름
        len += MessageBuffer.sizeMixedArray(args) // 혼합 인자
        
        val messageType = if (usesCancellationToken)
            MessageType.RequestMixedArgsWithCancellation
        else
            MessageType.RequestMixedArgs
            
        val result = MessageBuffer.alloc(messageType, req, len)
        result.writeUInt8(rpcId)
        result.writeShortString(methodBuff)
        result.writeMixedArray(args)
        return result.bytes
    }
    
    /**
     * 혼합된 인자 요청을 역직렬화합니다.
     */
    fun deserializeRequestMixedArgs(buff: MessageBuffer): Triple<Int, String, List<Any?>> {
        val rpcId = buff.readUInt8()
        var method = buff.readShortString()
        if (method.startsWith("\$")) {
            method = method.substring(1)
        }
        val rawArgs = buff.readMixedArray()
        val args = rawArgs.mapIndexed { _, rawArg ->
            when (rawArg) {
                is String -> {
                    val gson = Gson()
                    gson.fromJson(rawArg, Any::class.java)
                }
                else -> rawArg
            }
        }
        
        return Triple(rpcId, method, args)
    }
    
    /**
     * 확인 응답(Acknowledged) 메시지를 직렬화합니다.
     */
    fun serializeAcknowledged(req: Int): ByteArray {
        return MessageBuffer.alloc(MessageType.Acknowledged, req, 0).bytes
    }
    
    /**
     * 취소(Cancel) 메시지를 직렬화합니다.
     */
    fun serializeCancel(req: Int): ByteArray {
        return MessageBuffer.alloc(MessageType.Cancel, req, 0).bytes
    }
    
    /**
     * 성공 응답(OK Reply)을 직렬화합니다.
     */
    fun serializeReplyOK(req: Int, res: Any?, replacer: ((String, Any?) -> Any?)? = null): ByteArray {
        return when {
            res == null -> serializeReplyOKEmpty(req)
            res is ByteArray -> serializeReplyOKVSBuffer(req, res)
            res is SerializableObjectWithBuffers<*> -> {
                val result = stringifyJsonWithBufferRefs(res.value, replacer, true)
                serializeReplyOKJSONWithBuffers(req, result.jsonString, result.referencedBuffers)
            }
            else -> {
                val gson = Gson()
                val jsonStr = try {
                    gson.toJson(res)
                } catch (e: Exception) {
                    "null"
                }
                serializeReplyOKJSON(req, jsonStr)
            }
        }
    }
    
    /**
     * 빈 성공 응답을 직렬화합니다.
     */
    private fun serializeReplyOKEmpty(req: Int): ByteArray {
        return MessageBuffer.alloc(MessageType.ReplyOKEmpty, req, 0).bytes
    }
    
    /**
     * 바이너리 버퍼를 포함하는 성공 응답을 직렬화합니다.
     */
    private fun serializeReplyOKVSBuffer(req: Int, res: ByteArray): ByteArray {
        var len = 0
        len += MessageBuffer.sizeVSBuffer(res)
        
        val result = MessageBuffer.alloc(MessageType.ReplyOKVSBuffer, req, len)
        result.writeVSBuffer(res)
        return result.bytes
    }
    
    /**
     * 바이너리 버퍼를 포함하는 성공 응답을 역직렬화합니다.
     */
    fun deserializeReplyOKVSBuffer(buff: MessageBuffer): ByteArray {
        return buff.readVSBuffer()
    }
    
    /**
     * JSON을 포함하는 성공 응답을 직렬화합니다.
     */
    private fun serializeReplyOKJSON(req: Int, res: String): ByteArray {
        val resBuff = res.toByteArray()
        
        var len = 0
        len += MessageBuffer.sizeLongString(resBuff)
        
        val result = MessageBuffer.alloc(MessageType.ReplyOKJSON, req, len)
        result.writeLongString(resBuff)
        return result.bytes
    }
    
    /**
     * JSON과 버퍼를 포함하는 성공 응답을 직렬화합니다.
     */
    private fun serializeReplyOKJSONWithBuffers(req: Int, res: String, buffers: List<ByteArray>): ByteArray {
        val resBuff = res.toByteArray()
        
        var len = 0
        len += MessageBuffer.sizeUInt32 // 버퍼 개수
        len += MessageBuffer.sizeLongString(resBuff) // JSON 문자열
        for (buffer in buffers) {
            len += MessageBuffer.sizeVSBuffer(buffer) // 각 버퍼
        }
        
        val result = MessageBuffer.alloc(MessageType.ReplyOKJSONWithBuffers, req, len)
        result.writeUInt32(buffers.size)
        result.writeLongString(resBuff)
        for (buffer in buffers) {
            result.writeBuffer(buffer)
        }
        
        return result.bytes
    }
    
    /**
     * JSON을 포함하는 성공 응답을 역직렬화합니다.
     */
    fun deserializeReplyOKJSON(buff: MessageBuffer): Any? {
        val res = buff.readLongString()
        val gson = Gson()
        return gson.fromJson(res, Any::class.java)
    }
    
    /**
     * JSON과 버퍼를 포함하는 성공 응답을 역직렬화합니다.
     */
    fun deserializeReplyOKJSONWithBuffers(buff: MessageBuffer, uriTransformer: ((String, Any?) -> Any?)? = null): SerializableObjectWithBuffers<*> {
        val bufferCount = buff.readUInt32()
        val res = buff.readLongString()
        
        val buffers = mutableListOf<ByteArray>()
        for (i in 0 until bufferCount) {
            buffers.add(buff.readVSBuffer())
        }
        
        return SerializableObjectWithBuffers(parseJsonAndRestoreBufferRefs(res, buffers, uriTransformer))
    }
    
    /**
     * 오류 응답을 직렬화합니다.
     */
    fun serializeReplyErr(req: Int, err: Throwable?): ByteArray {
        val errStr = if (err != null) {
            try {
                val gson = Gson()
                gson.toJson(transformErrorForSerialization(err))
            } catch (e: Exception) {
                null
            }
        } else null
        
        return if (errStr != null) {
            val errBuff = errStr.toByteArray()
            
            var len = 0
            len += MessageBuffer.sizeLongString(errBuff)
            
            val result = MessageBuffer.alloc(MessageType.ReplyErrError, req, len)
            result.writeLongString(errBuff)
            result.bytes
        } else {
            serializeReplyErrEmpty(req)
        }
    }
    
    /**
     * 오류 응답을 역직렬화합니다.
     */
    fun deserializeReplyErrError(buff: MessageBuffer): Throwable {
        val err = buff.readLongString()
        val gson = Gson()
        val errorMap = gson.fromJson(err, Map::class.java)
        
        val exception = Exception(errorMap["message"] as? String ?: "알 수 없는 오류")
        
        // 스택 트레이스 등 추가 정보 설정 (Java/Kotlin에서는 직접 스택 설정이 어려움)
        if (errorMap.containsKey("stack")) {
            // 실제 구현에서는 사용자 정의 예외 타입이나 다른 방법을 사용할 수 있습니다.
        }
        
        return exception
    }
    
    /**
     * 빈 오류 응답을 직렬화합니다.
     */
    private fun serializeReplyErrEmpty(req: Int): ByteArray {
        return MessageBuffer.alloc(MessageType.ReplyErrEmpty, req, 0).bytes
    }
    
    /**
     * 직렬화를 위해 오류 객체를 변환합니다.
     */
    private fun transformErrorForSerialization(error: Throwable): Map<String, Any?> {
        return mapOf(
            "\$isError" to true,
            "name" to error.javaClass.simpleName,
            "message" to error.message,
            "stack" to error.stackTraceToString()
        )
    }
}
