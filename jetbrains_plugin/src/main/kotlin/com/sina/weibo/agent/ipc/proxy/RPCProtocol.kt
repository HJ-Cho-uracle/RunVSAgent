// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.ipc.IMessagePassingProtocol
import com.sina.weibo.agent.ipc.proxy.uri.IURITransformer
import com.sina.weibo.agent.ipc.proxy.uri.UriReplacer
import com.sina.weibo.agent.util.doInvokeMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions

/**
 * 요청 시작자(Request Initiator)를 나타내는 열거형입니다.
 * 요청이 로컬에서 시작되었는지 또는 원격에서 시작되었는지 구분합니다.
 */
enum class RequestInitiator {
    /** 로컬에서 시작됨 */
    LocalSide,

    /** 원격에서 시작됨 */
    OtherSide,
}

/**
 * 응답 상태(Responsive State)를 나타내는 열거형입니다.
 * 프로토콜이 현재 응답 가능한 상태인지 또는 응답하지 않는 상태인지 나타냅니다.
 */
enum class ResponsiveState {
    /** 응답 가능 */
    Responsive,

    /** 응답 없음 */
    Unresponsive,
}

/**
 * RPC 프로토콜 로거 인터페이스입니다.
 * 들어오고 나가는 메시지를 로깅하는 기능을 정의합니다.
 */
interface IRPCProtocolLogger {
    /** 수신 메시지를 로깅합니다. */
    fun logIncoming(msgLength: Int, req: Int, initiator: RequestInitiator, str: String, data: Any? = null)

    /** 발신 메시지를 로깅합니다. */
    fun logOutgoing(msgLength: Int, req: Int, initiator: RequestInitiator, str: String, data: Any? = null)
}

/**
 * RPC 프로토콜 구현체입니다.
 * VSCode의 `RPCProtocol`에 해당하며, `IMessagePassingProtocol`을 통해 메시지를 주고받고,
 * 프록시 객체를 생성하며, 로컬 서비스를 등록하여 원격 호출을 처리합니다.
 */
class RPCProtocol(
    private val protocol: IMessagePassingProtocol, // 하위 메시지 전달 프로토콜
    private val logger: IRPCProtocolLogger? = null, // 로깅을 위한 로거 (선택 사항)
    private val uriTransformer: IURITransformer? = null, // URI 변환기 (선택 사항)
) : IRPCProtocol, Disposable {

    companion object {
        private val LOG = Logger.getInstance(RPCProtocol::class.java)

        /** 응답 없음 상태로 간주하기 위한 시간 임계값 (밀리초) */
        private const val UNRESPONSIVE_TIME = 3 * 1000 // 3초 (TypeScript 구현과 동일)

        /** RPC 프로토콜을 구현하는 객체를 식별하기 위한 심볼 */
        private val RPC_PROTOCOL_SYMBOL = "rpcProtocol"

        /** 프록시 객체를 식별하기 위한 심볼 */
        private val RPC_PROXY_SYMBOL = "rpcProxy"

        /** 달러 기호의 문자 코드 */
        private const val DOLLAR_SIGN_CHAR_CODE = 36 // '$'

        /** 아무 작업도 하지 않는 빈 함수 */
        private val noop: () -> Unit = {}
    }

    /** RPC 관련 비동기 작업을 위한 코루틴 스코프 */
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** URI 변환기 (URITransformer가 제공되면 UriReplacer 생성) */
    private val uriReplacer: ((String, Any?) -> Any?)? = if (uriTransformer != null) UriReplacer(uriTransformer) else null

    private var isDisposed = false // 객체 해제 여부

    // --- RPC 상태 관리 ---
    private val locals = arrayOfNulls<Any?>(ProxyIdentifier.count + 1) // 로컬 서비스 인스턴스 목록
    private val proxies = arrayOfNulls<Any?>(ProxyIdentifier.count + 1) // 프록시 객체 목록
    private var lastMessageId = 0 // 마지막 메시지 ID
    private val cancelInvokedHandlers = ConcurrentHashMap<String, () -> Unit>() // 취소된 핸들러 맵
    private val pendingRPCReplies = ConcurrentHashMap<String, PendingRPCReply>() // 보류 중인 RPC 응답 맵

    /** 현재 프로토콜의 응답 상태 */
    override var responsiveState = ResponsiveState.Responsive
        private set

    private var unacknowledgedCount = 0 // 아직 확인 응답을 받지 못한 요청 수
    private var unresponsiveTime = 0L // 응답 없음 상태로 간주될 시간
    private var asyncCheckUnresponsiveJob: Job? = null // 비동기 응답성 확인 작업

    private val onDidChangeResponsiveStateListeners = mutableListOf<(ResponsiveState) -> Unit>() // 응답 상태 변경 리스너

    init {
        // 하위 프로토콜로부터 메시지를 받으면 `receiveOneMessage`를 호출하도록 리스너 등록
        protocol.onMessage { data -> receiveOneMessage(data) }
    }

    /**
     * 응답 상태 변경 이벤트 리스너를 추가합니다.
     */
    fun onDidChangeResponsiveState(listener: (ResponsiveState) -> Unit): Disposable {
        onDidChangeResponsiveStateListeners.add(listener)
        return Disposable { onDidChangeResponsiveStateListeners.remove(listener) }
    }

    override fun dispose() {
        isDisposed = true

        coroutineScope.cancel() // 모든 코루틴 취소

        // 보류 중인 모든 응답을 취소 오류와 함께 해제
        pendingRPCReplies.keys.forEach { msgId ->
            val pending = pendingRPCReplies[msgId]
            pendingRPCReplies.remove(msgId)
            pending?.resolveErr(CanceledException())
        }
    }

    override suspend fun drain() {
        protocol.drain()
    }

    /**
     * 요청을 보내기 전에 호출됩니다.
     * 응답성 확인을 위한 카운트다운을 시작합니다.
     */
    private fun onWillSendRequest(req: Int) {
        if (unacknowledgedCount == 0) {
            unresponsiveTime = System.currentTimeMillis() + UNRESPONSIVE_TIME
            LOG.debug("초기 응답 없음 확인 시간 설정, 요청 ID: $req, 응답 없음 시간: ${unresponsiveTime}ms")
        }
        unacknowledgedCount++

        // 2초마다 응답성 확인 작업을 시작합니다.
        if (asyncCheckUnresponsiveJob == null || asyncCheckUnresponsiveJob?.isActive == false) {
            LOG.debug("응답 없음 확인 작업 시작")
            asyncCheckUnresponsiveJob = coroutineScope.launch {
                while (isActive) {
                    checkUnresponsive()
                    delay(2000)
                }
            }
        }
    }

    /**
     * 확인 응답을 수신했을 때 호출됩니다.
     * 응답 없음 확인 카운트다운을 재설정하고, 응답 상태를 `Responsive`로 설정합니다.
     */
    private fun onDidReceiveAcknowledge(req: Int) {
        unresponsiveTime = System.currentTimeMillis() + UNRESPONSIVE_TIME
        unacknowledgedCount--

        if (unacknowledgedCount == 0) {
            LOG.debug("미확인 요청 없음, 응답 없음 확인 작업 취소")
            asyncCheckUnresponsiveJob?.cancel()
            asyncCheckUnresponsiveJob = null
        }

        setResponsiveState(ResponsiveState.Responsive)
    }

    /**
     * 응답 없음 상태를 확인합니다.
     * `UNRESPONSIVE_TIME` 이상 응답이 없으면 상태를 `Unresponsive`로 변경합니다.
     */
    private fun checkUnresponsive() {
        if (unacknowledgedCount == 0) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime > unresponsiveTime) {
            LOG.warn("응답 없음 상태 감지: 현재 시간 ${currentTime}ms > 응답 없음 임계값 ${unresponsiveTime}ms, 미확인 요청: $unacknowledgedCount")
            setResponsiveState(ResponsiveState.Unresponsive)
        } else {
            if (LOG.isDebugEnabled) {
                val remainingTime = unresponsiveTime - currentTime
                LOG.debug("연결 응답 가능, 응답 없음 임계값까지 남은 시간: ${remainingTime}ms, 미확인 요청: $unacknowledgedCount")
            }
        }
    }

    /**
     * 응답 상태를 설정하고 리스너들에게 알립니다.
     */
    private fun setResponsiveState(newResponsiveState: ResponsiveState) {
        if (responsiveState == newResponsiveState) {
            return
        }

        LOG.info("응답 상태 변경: $responsiveState -> $newResponsiveState")
        responsiveState = newResponsiveState

        onDidChangeResponsiveStateListeners.forEach { it(responsiveState) }
    }

    /**
     * 수신되는 URI를 변환합니다.
     */
    fun <T> transformIncomingURIs(obj: T): T {
        if (uriTransformer == null) {
            return obj
        }

        @Suppress("UNCHECKED_CAST")
        return when (obj) {
            is java.net.URI -> uriTransformer.transformIncoming(obj) as T
            is String -> {
                try {
                    val uri = java.net.URI(obj)
                    uriTransformer.transformIncoming(uri).toString() as T
                } catch (e: Exception) {
                    obj
                }
            }
            is List<*> -> {
                obj.map { item -> transformIncomingURIs(item) } as T
            }
            is Map<*, *> -> {
                val result = mutableMapOf<Any?, Any?>()
                obj.forEach { (key, value) ->
                    val transformedValue = if (key is String && (
                            key == "uri" || key == "documentUri" || key == "targetUri" || key == "sourceUri" || key.endsWith("Uri")
                            )
                    ) {
                        transformIncomingURIs(value)
                    } else {
                        transformIncomingURIs(value)
                    }
                    result[key] = transformedValue
                }
                result as T
            }
            else -> obj
        }
    }

    override fun <T> getProxy(identifier: ProxyIdentifier<T>): T {
        val rpcId = identifier.nid
        val sid = identifier.sid

        if (proxies[rpcId] == null) {
            proxies[rpcId] = createProxy(rpcId, sid)
        }

        @Suppress("UNCHECKED_CAST")
        return proxies[rpcId] as T
    }

    /**
     * 프록시 객체를 생성합니다.
     * Java의 동적 프록시를 사용하여 원격 메소드 호출을 가로챕니다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> createProxy(rpcId: Int, debugName: String): T {
        val interfaces = mutableListOf<Class<*>>()

        // 제네릭 파라미터로부터 인터페이스 정보를 로드합니다.
        try {
            val classLoader = javaClass.classLoader
            val proxyClass = classLoader.loadClass(debugName)
            if (proxyClass.isInterface) {
                interfaces.add(proxyClass)
            }
        } catch (e: Exception) {
            LOG.warn("인터페이스 클래스 로드 실패 $debugName: ${e.message}")
        }

        // Java 동적 프록시를 사용하여 프록시 객체를 생성합니다.
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            interfaces.toTypedArray(),
        ) { _, method, args ->
            val name = method.name

            // 특수 메소드 처리
            if (name == "toString") {
                return@newProxyInstance "Proxy($debugName)"
            }

            if (name == RPC_PROXY_SYMBOL) {
                return@newProxyInstance debugName
            }

            // 원격 메소드 호출
            if (name.isNotEmpty()) {
                return@newProxyInstance remoteCall(rpcId, "\$$name", args ?: emptyArray())
            }
            null
        } as T
    }

    override fun <T, R : T> set(identifier: ProxyIdentifier<T>, instance: R): R {
        locals[identifier.nid] = instance
        return instance
    }

    override fun assertRegistered(identifiers: List<ProxyIdentifier<*>>) {
        for (identifier in identifiers) {
            if (locals[identifier.nid] == null) {
                throw IllegalStateException("프록시 인스턴스 ${identifier.sid}가 누락되었습니다.")
            }
        }
    }

    /**
     * 원격 메소드를 호출합니다.
     */
    private fun remoteCall(rpcId: Int, methodName: String, args: Array<out Any?>): Any {
        if (isDisposed) {
            throw CanceledException()
        }
        LOG.debug("원격 호출: $rpcId.$methodName.${lastMessageId + 1}")

        // 마지막 인자가 취소 토큰인지 확인합니다.
        var cancellationToken: Any? = null
        val effectiveArgs = if (args.isNotEmpty()) {
            val lastArg = args.last()
            if (lastArg != null && lastArg::class.java.simpleName == "CancellationToken") {
                cancellationToken = lastArg
                args.dropLast(1).toTypedArray()
            } else {
                args
            }
        } else {
            args
        }

        val serializedRequestArguments = MessageIO.serializeRequestArguments(effectiveArgs.toList(), uriReplacer)

        val req = ++lastMessageId
        val callId = req.toString()
        val result = LazyPromise() // 결과를 받을 LazyPromise 생성

        val deferred = LazyPromise() // 내부적으로 사용될 Deferred

        val disposable = Disposable {
            if (!deferred.isCompleted) {
                deferred.cancel()
            }
        }

        pendingRPCReplies[callId] = PendingRPCReply(result, disposable) // 보류 중인 응답에 등록
        onWillSendRequest(req) // 요청 전 처리

        val usesCancellationToken = cancellationToken != null
        val msg = MessageIO.serializeRequest(req, rpcId, methodName, serializedRequestArguments, usesCancellationToken)

        logger?.logOutgoing(
            msg.size,
            req,
            RequestInitiator.LocalSide,
            "요청: ${getStringIdentifierForProxy(rpcId)}.$methodName(",
            effectiveArgs,
        )

        protocol.send(msg) // 메시지 전송

        return result // Promise를 즉시 반환하여 현재 스레드를 블록하지 않습니다.
    }

    /**
     * 메시지를 수신합니다.
     */
    private fun receiveOneMessage(rawmsg: ByteArray) {
        if (isDisposed) {
            return
        }

        val msgLength = rawmsg.size
        val buff = MessageBuffer.read(rawmsg, 0)
        val messageType = MessageType.fromValue(buff.readUInt8()) ?: return
        val req = buff.readUInt32()

        LOG.debug("메시지 수신: $messageType, req: $req, 길이: $msgLength")
        when (messageType) {
            MessageType.RequestJSONArgs, MessageType.RequestJSONArgsWithCancellation -> {
                val (rpcId, method, args) = MessageIO.deserializeRequestJSONArgs(buff)
                val transformedArgs = transformIncomingURIs(args) // URI 변환
                receiveRequest(
                    msgLength,
                    req,
                    rpcId,
                    method,
                    transformedArgs,
                    messageType == MessageType.RequestJSONArgsWithCancellation,
                )
            }
            MessageType.RequestMixedArgs, MessageType.RequestMixedArgsWithCancellation -> {
                val (rpcId, method, args) = MessageIO.deserializeRequestMixedArgs(buff)
                val transformedArgs = transformIncomingURIs(args) // URI 변환
                receiveRequest(
                    msgLength,
                    req,
                    rpcId,
                    method,
                    transformedArgs,
                    messageType == MessageType.RequestMixedArgsWithCancellation,
                )
            }
            MessageType.Acknowledged -> {
                logger?.logIncoming(msgLength, req, RequestInitiator.LocalSide, "ack")
                onDidReceiveAcknowledge(req)
            }
            MessageType.Cancel -> {
                receiveCancel(msgLength, req)
            }
            MessageType.ReplyOKEmpty -> {
                receiveReply(msgLength, req, null)
            }
            MessageType.ReplyOKJSON -> {
                val value = MessageIO.deserializeReplyOKJSON(buff)
                val transformedValue = transformIncomingURIs(value) // URI 변환
                receiveReply(msgLength, req, transformedValue)
            }
            MessageType.ReplyOKJSONWithBuffers -> {
                val value = MessageIO.deserializeReplyOKJSONWithBuffers(buff, uriReplacer)
                receiveReply(msgLength, req, value)
            }
            MessageType.ReplyOKVSBuffer -> {
                val value = MessageIO.deserializeReplyOKVSBuffer(buff)
                receiveReply(msgLength, req, value)
            }
            MessageType.ReplyErrError -> {
                val err = MessageIO.deserializeReplyErrError(buff)
                val transformedErr = transformIncomingURIs(err) // URI 변환
                receiveReplyErr(msgLength, req, transformedErr)
            }
            MessageType.ReplyErrEmpty -> {
                receiveReplyErr(msgLength, req, null)
            }
        }
    }

    /**
     * 요청을 수신하고 처리합니다.
     */
    private fun receiveRequest(
        msgLength: Int,
        req: Int,
        rpcId: Int,
        method: String,
        args: List<Any?>,
        usesCancellationToken: Boolean,
    ) {
        LOG.debug("요청 수신: $req.$rpcId.$method()")
        logger?.logIncoming(
            msgLength,
            req,
            RequestInitiator.OtherSide,
            "요청 수신 ${getStringIdentifierForProxy(rpcId)}.$method(",
            args,
        )

        val callId = req.toString()

        val promise: Deferred<Any?>
        val cancel: () -> Unit

        // 코루틴을 사용하여 요청을 처리합니다.
        if (usesCancellationToken) {
            val job = Job() // 취소 가능한 코루틴 Job 생성
            val context: CoroutineContext = job + Dispatchers.Default // 코루틴 컨텍스트

            promise = coroutineScope.async(context) {
                // Kotlin에서는 코루틴의 취소 메커니즘을 사용합니다.
                invokeHandler(rpcId, method, args)
            }

            cancel = { job.cancel() } // 취소 함수
        } else {
            promise = coroutineScope.async {
                invokeHandler(rpcId, method, args)
            }
            cancel = noop // 취소 불가능
        }

        cancelInvokedHandlers[callId] = cancel // 취소 핸들러 등록

        // 요청 확인 응답 전송
        val msg = MessageIO.serializeAcknowledged(req)
        logger?.logOutgoing(msg.size, req, RequestInitiator.OtherSide, "ack")
        protocol.send(msg)

        // 요청 결과 처리
        coroutineScope.launch {
            try {
                val result = promise.await() // 결과 대기
                cancelInvokedHandlers.remove(callId)
                val msg = MessageIO.serializeReplyOK(req, result, uriReplacer)
                logger?.logOutgoing(msg.size, req, RequestInitiator.OtherSide, "응답:", result)
                protocol.send(msg)
            } catch (err: Throwable) {
                cancelInvokedHandlers.remove(callId)
                val msg = MessageIO.serializeReplyErr(req, err)
                logger?.logOutgoing(msg.size, req, RequestInitiator.OtherSide, "오류 응답:", err)
                protocol.send(msg)
            }
        }
    }

    /**
     * 취소 메시지를 수신합니다.
     */
    private fun receiveCancel(msgLength: Int, req: Int) {
        logger?.logIncoming(msgLength, req, RequestInitiator.OtherSide, "취소 수신")
        val callId = req.toString()
        cancelInvokedHandlers[callId]?.invoke() // 등록된 취소 핸들러 호출
    }

    /**
     * 응답 메시지를 수신합니다.
     */
    private fun receiveReply(msgLength: Int, req: Int, value: Any?) {
        logger?.logIncoming(msgLength, req, RequestInitiator.LocalSide, "응답 수신:", value)
        val callId = req.toString()
        if (!pendingRPCReplies.containsKey(callId)) {
            return
        }

        val pendingReply = pendingRPCReplies[callId] ?: return
        pendingRPCReplies.remove(callId)

        pendingReply.resolveOk(value) // 보류 중인 응답 해결
    }

    /**
     * 오류 응답 메시지를 수신합니다.
     */
    private fun receiveReplyErr(msgLength: Int, req: Int, value: Throwable?) {
        logger?.logIncoming(msgLength, req, RequestInitiator.LocalSide, "오류 응답 수신:", value)

        val callId = req.toString()
        if (!pendingRPCReplies.containsKey(callId)) {
            return
        }

        val pendingReply = pendingRPCReplies[callId] ?: return
        pendingRPCReplies.remove(callId)

        val err = value ?: Exception("알 수 없는 오류")
        pendingReply.resolveErr(err) // 보류 중인 응답을 오류와 함께 해결
    }

    /**
     * 핸들러를 호출합니다.
     */
    private suspend fun invokeHandler(rpcId: Int, methodName: String, args: List<Any?>): Any? {
        return try {
            doInvokeHandler(rpcId, methodName, args)
        } catch (err: Throwable) {
            LOG.error("핸들러 호출 중 오류 발생: $methodName(${args.joinToString(", ")})", err)
            null
        }
    }

    /**
     * 핸들러 호출을 실행합니다.
     */
    private suspend fun doInvokeHandler(rpcId: Int, methodName: String, args: List<Any?>): Any? {
        val actor = locals[rpcId]
        if (actor == null) {
            LOG.error("알 수 없는 액터 ${getStringIdentifierForProxy(rpcId)}")
            return null
        }
        // 메소드 이름과 인자 타입에 가장 잘 맞는 메소드를 리플렉션을 통해 찾습니다.
        val method = try {
            findBestMatchingMethod(actor, methodName, args)
        } catch (e: Exception) {
            throw IllegalStateException("액터 ${getStringIdentifierForProxy(rpcId)}에 메소드 '$methodName'를 찾을 수 없습니다.")
        }

        return doInvokeMethod(method, args, actor) // 메소드 동적 호출
    }

    /**
     * 메소드 이름과 인자 타입에 가장 잘 맞는 메소드를 찾습니다.
     */
    private fun findBestMatchingMethod(actor: Any, methodName: String, args: List<Any?>): KFunction<*> {
        val candidateMethods = actor::class.functions.filter { it.name == methodName }

        if (candidateMethods.isEmpty()) {
            throw NoSuchMethodException("'$methodName'라는 이름의 메소드를 찾을 수 없습니다.")
        }

        if (candidateMethods.size == 1) {
            return candidateMethods.first()
        }

        // 파라미터 개수가 일치하는 메소드를 찾습니다. (리시버 파라미터 제외)
        val methodsWithMatchingParamCount = candidateMethods.filter { method ->
            val paramCount = method.parameters.size - 1
            paramCount == args.size
        }

        if (methodsWithMatchingParamCount.isEmpty()) {
            // 정확히 일치하는 파라미터 개수가 없으면, 더 적은 인자를 받을 수 있는 메소드를 찾습니다.
            val compatibleMethods = candidateMethods.filter { method ->
                val paramCount = method.parameters.size - 1
                paramCount >= args.size // 기본값을 가진 인자를 고려
            }
            if (compatibleMethods.isNotEmpty()) {
                return compatibleMethods.first()
            }
            throw NoSuchMethodException("${args.size}개 파라미터를 가진 메소드 '$methodName'를 찾을 수 없습니다.")
        }

        if (methodsWithMatchingParamCount.size == 1) {
            return methodsWithMatchingParamCount.first()
        }

        // 동일한 파라미터 개수를 가진 메소드가 여러 개이면, 타입으로 일치하는 것을 찾습니다.
        for (method in methodsWithMatchingParamCount) {
            if (isMethodCompatible(method, args)) {
                return method
            }
        }

        // 완벽하게 일치하는 것이 없으면, 파라미터 개수가 일치하는 첫 번째 메소드를 반환합니다.
        return methodsWithMatchingParamCount.first()
    }

    /**
     * 메소드가 주어진 인자들과 호환되는지 확인합니다.
     */
    private fun isMethodCompatible(method: KFunction<*>, args: List<Any?>): Boolean {
        val parameters = method.parameters.drop(1) // 리시버 파라미터 건너뛰기

        if (parameters.size != args.size) {
            return false
        }

        for (i in parameters.indices) {
            val param = parameters[i]
            val arg = args[i]

            if (arg == null) {
                // null 인자는 널러블 파라미터와 호환됩니다.
                if (!param.type.isMarkedNullable) {
                    return false
                }
            } else {
                // 타입 호환성 검사
                val argClass = arg::class.java
                val paramClass = param.type.classifier as? KClass<*>

                if (paramClass != null) {
                    val paramJavaClass = paramClass.java

                    val isCompatible = when {
                        paramJavaClass.isAssignableFrom(argClass) -> true // 직접 할당 가능
                        // Double을 다른 숫자 타입으로 변환 가능
                        arg is Double && (
                            paramJavaClass == Int::class.java ||
                                paramJavaClass == Long::class.java ||
                                paramJavaClass == Float::class.java ||
                                paramJavaClass == Short::class.java ||
                                paramJavaClass == Byte::class.java ||
                                paramJavaClass == Boolean::class.java
                            ) -> true
                        // String 타입 호환성
                        arg is String && paramJavaClass == String::class.java -> true
                        else -> false
                    }

                    if (!isCompatible) {
                        return false
                    }
                }
            }
        }

        return true
    }
}
