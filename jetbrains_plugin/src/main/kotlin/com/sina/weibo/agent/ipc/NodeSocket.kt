// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * NodeSocket 구현체입니다. Java의 `Socket` 또는 `SocketChannel`을 래핑하여 사용합니다.
 * VSCode의 `NodeSocket` 구현에 해당하며, Extension Host와의 저수준 통신을 담당합니다.
 */
class NodeSocket : ISocket {

    private val logger = Logger.getInstance(NodeSocket::class.java)
    // 데이터, 연결 종료, 스트림 종료 이벤트를 위한 리스너 맵
    private val dataListeners = ConcurrentHashMap<ISocket.DataListener, Unit>()
    private val closeListeners = ConcurrentHashMap<ISocket.CloseListener, Unit>()
    private val endListeners = ConcurrentHashMap<() -> Unit, Unit>()
    // 쓰기 가능 상태를 나타내는 플래그
    private val canWrite = AtomicBoolean(true)
    // 데이터 수신을 위한 별도의 스레드
    private var receiveThread: Thread? = null
    // 객체 해제 여부를 나타내는 플래그
    private val isDisposed = AtomicBoolean(false)
    // 스트림 종료 후 지연 종료를 위한 타이머 핸들
    private var endTimeoutHandle: Thread? = null
    // 소켓 종료 타임아웃 (30초)
    private val socketEndTimeoutMs = 30_000L
    private val debugLabel: String // 디버깅을 위한 레이블
    private val input: java.io.InputStream // 입력 스트림
    private val output: java.io.OutputStream // 출력 스트림
    private val closeAction: () -> Unit // 소켓/채널 닫기 액션
    private val isSocket: Boolean // TCP 소켓인지 UDS 채널인지 구분
    private val socket: Socket? // TCP 소켓 인스턴스
    private val channel: SocketChannel? // UDS 채널 인스턴스
    private val writeAction: (ByteArray) -> Unit // 데이터 쓰기 액션

    /**
     * TCP 소켓을 사용하는 생성자입니다.
     * @param socket 래핑할 Java `Socket` 객체
     * @param debugLabel 디버깅을 위한 레이블
     */
    constructor(socket: Socket, debugLabel: String = "") {
        this.input = socket.getInputStream()
        this.output = socket.getOutputStream()
        this.closeAction = { socket.close() }
        this.debugLabel = debugLabel
        this.isSocket = true
        this.socket = socket
        this.channel = null
        this.writeAction = { buffer ->
            output.write(buffer)
            output.flush() // 데이터 전송 후 즉시 플러시
        }
        traceSocketEvent(SocketDiagnosticsEventType.CREATED, mapOf("type" to "NodeSocket-TCP"))
    }

    /**
     * `SocketChannel` (UDS)을 사용하는 생성자입니다.
     * @param channel 래핑할 Java `SocketChannel` 객체
     * @param debugLabel 디버깅을 위한 레이블
     */
    constructor(channel: SocketChannel, debugLabel: String = "") {
        this.input = Channels.newInputStream(channel)
        this.output = Channels.newOutputStream(channel)
        this.closeAction = { channel.close() }
        this.debugLabel = debugLabel
        this.isSocket = false
        this.socket = null
        this.channel = channel
        this.writeAction = { buffer ->
            val byteBuffer = java.nio.ByteBuffer.wrap(buffer)
            while (byteBuffer.hasRemaining()) {
                channel.write(byteBuffer) // 모든 바이트가 쓰여질 때까지 반복
            }
        }
        traceSocketEvent(SocketDiagnosticsEventType.CREATED, mapOf("type" to "NodeSocket-UDS"))
    }

    /**
     * 데이터 수신을 시작합니다.
     * 별도의 스레드에서 입력 스트림을 지속적으로 읽습니다.
     */
    override fun startReceiving() {
        if (receiveThread != null) return // 이미 수신 스레드가 실행 중이면 중복 실행 방지

        receiveThread = thread(start = true, name = "NodeSocket-Receiver-$debugLabel") {
            val buffer = ByteArray(8192) // 8KB 버퍼
            try {
                while (!isDisposed.get() && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) {
                            // 스트림 끝(EOF)에 도달
                            logger.info("Socket[$debugLabel] EOF 읽음, onEndReceived() 트리거")
                            onEndReceived()
                            break
                        } else if (bytesRead > 0) {
                            val data = buffer.copyOfRange(0, bytesRead)
                            traceSocketEvent(SocketDiagnosticsEventType.READ, data)
                            // 모든 데이터 리스너에게 수신된 데이터를 알립니다.
                            dataListeners.keys.forEach { listener ->
                                try {
                                    listener.onData(data)
                                } catch (e: Exception) {
                                    logger.error("Socket[$debugLabel] 데이터 리스너 처리 중 예외 발생", e)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!isDisposed.get()) {
                            logger.error("Socket[$debugLabel] 데이터 읽기 중 IO 예외 발생", e)
                            handleSocketError(e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                if (!isDisposed.get()) {
                    logger.error("Socket[$debugLabel] 수신 스레드에서 처리되지 않은 예외 발생", e)
                    handleSocketError(e)
                }
            } finally {
                // 수신 스레드 종료 시 소켓이 닫히도록 보장합니다.
                closeSocket(false)
            }
        }
    }

    /**
     * 스트림 종료(END) 신호를 수신했을 때 호출됩니다.
     * 쓰기 작업을 비활성화하고 지연 종료 타이머를 설정합니다.
     */
    private fun onEndReceived() {
        traceSocketEvent(SocketDiagnosticsEventType.NODE_END_RECEIVED)
        logger.info("Socket[$debugLabel] END 이벤트 수신, 쓰기 작업 비활성화")
        canWrite.set(false)

        // 모든 end 리스너에게 알립니다.
        endListeners.keys.forEach { listener ->
            try {
                listener.invoke()
            } catch (e: Exception) {
                logger.error("Socket[$debugLabel] END 이벤트 리스너 처리 중 예외 발생", e)
            }
        }

        // 지연 종료 타이머를 설정합니다.
        logger.info("Socket[$debugLabel] ${socketEndTimeoutMs}ms 후 지연 종료 실행 예정")
        endTimeoutHandle = thread(start = true, name = "NodeSocket-EndTimeout-$debugLabel") {
            try {
                Thread.sleep(socketEndTimeoutMs)
                if (!isDisposed.get()) {
                    logger.info("Socket[$debugLabel] 지연 종료 실행")
                    closeAction()
                }
            } catch (e: InterruptedException) {
                logger.info("Socket[$debugLabel] 지연 종료 스레드 중단됨")
            } catch (e: Exception) {
                logger.error("Socket[$debugLabel] 지연 종료 처리 중 예외 발생", e)
            }
        }
    }

    /**
     * 소켓 오류를 처리합니다.
     * @param error 발생한 예외
     */
    private fun handleSocketError(error: Exception) {
        // 일반적인 연결 끊김 오류(EPIPE, ECONNRESET)를 필터링합니다.
        val errorCode = when {
            error.message?.contains("Broken pipe") == true -> "EPIPE"
            error.message?.contains("Connection reset") == true -> "ECONNRESET"
            else -> null
        }

        traceSocketEvent(
            SocketDiagnosticsEventType.ERROR, mapOf(
                "code" to errorCode,
                "message" to error.message
            )
        )

        // EPIPE 오류는 추가적인 처리 없이 소켓이 스스로 닫히도록 합니다.
        if (errorCode != "EPIPE") {
            logger.warn("Socket[$debugLabel] 오류: ${error.message}", error)
        }

        // 소켓을 닫습니다.
        closeSocket(true)
    }

    /**
     * 소켓 연결을 닫습니다.
     * @param hadError 오류로 인해 닫혔는지 여부
     */
    private fun closeSocket(hadError: Boolean) {
        if (isDisposed.get()) return
        logger.info("Socket[$debugLabel] 연결 닫는 중, hadError=$hadError")
        try {
            if (!isClosed()) {
                logger.info("Socket[$debugLabel] 연결 닫기")
                closeAction()
            }
        } catch (e: Exception) {
            logger.warn("Socket[$debugLabel] 연결 닫기 중 예외 발생", e)
        }

        // 지연 종료 스레드를 중단합니다.
        endTimeoutHandle?.interrupt()
        endTimeoutHandle = null

        canWrite.set(false)
        traceSocketEvent(SocketDiagnosticsEventType.CLOSE, mapOf("hadError" to hadError))

        // 모든 close 리스너에게 알립니다.
        val closeEvent = SocketCloseEvent.NodeSocketCloseEvent(hadError, null)
        closeListeners.keys.forEach { listener ->
            try {
                listener.onClose(closeEvent)
            } catch (e: Exception) {
                logger.error("Socket[$debugLabel] Close 리스너 처리 중 예외 발생", e)
            }
        }
    }

    override fun onData(listener: ISocket.DataListener): Disposable {
        dataListeners[listener] = Unit
        return Disposable { dataListeners.remove(listener) }
    }

    override fun onClose(listener: ISocket.CloseListener): Disposable {
        closeListeners[listener] = Unit
        return Disposable { closeListeners.remove(listener) }
    }

    override fun onEnd(listener: () -> Unit): Disposable {
        endListeners[listener] = Unit
        return Disposable { endListeners.remove(listener) }
    }

    /**
     * 데이터를 소켓에 씁니다.
     * @param buffer 쓸 데이터 (바이트 배열)
     */
    override fun write(buffer: ByteArray) {
        if (isDisposed.get()) {
            logger.debug("Socket[$debugLabel] 쓰기 무시됨: 소켓 해제됨")
            return
        }
        if (isClosed()) {
            logger.info("Socket[$debugLabel] 쓰기 무시됨: 소켓 닫힘")
            return
        }
        if (!canWrite.get()) {
            logger.info("Socket[$debugLabel] 쓰기 무시됨: canWrite=false")
            return
        }

        try {
            traceSocketEvent(SocketDiagnosticsEventType.WRITE, buffer)
            writeAction(buffer)
        } catch (e: java.nio.channels.ClosedChannelException) {
            logger.warn("Socket[$debugLabel] 쓰기 중 ClosedChannelException 감지, 연결 닫힘")
            handleSocketError(e)
        } catch (e: IOException) {
            logger.error("Socket[$debugLabel] 쓰기 중 IO 예외 발생", e)
            if (e.message?.contains("Broken pipe") == true) {
                logger.warn("Socket[$debugLabel] 쓰기 중 Broken pipe 감지")
                return
            }
            handleSocketError(e)
        } catch (e: Exception) {
            logger.error("Socket[$debugLabel] 쓰기 중 알 수 없는 예외 발생", e)
            handleSocketError(e)
        }
    }

    /**
     * 소켓 연결의 출력 스트림을 종료합니다.
     */
    override fun end() {
        if (isDisposed.get() || isClosed()) {
            return
        }

        traceSocketEvent(SocketDiagnosticsEventType.NODE_END_SENT)
        logger.info("Socket[$debugLabel] END 신호 전송 중")
        try {
            if (isSocket && socket != null) {
                socket.shutdownOutput()
            } else channel?.shutdownOutput()
        } catch (e: Exception) {
            logger.error("Socket[$debugLabel] END 신호 전송 중 예외 발생", e)
            handleSocketError(e)
        }
    }

    /**
     * 모든 데이터가 전송될 때까지 기다립니다.
     */
    override suspend fun drain(): Unit {
        traceSocketEvent(SocketDiagnosticsEventType.NODE_DRAIN_BEGIN)
        try {
            // 플러시를 트리거하기 위해 빈 패킷을 보냅니다.
            writeAction(ByteArray(0))
        } catch (e: Exception) {
            logger.error("Socket[$debugLabel] drain 실행 중 예외 발생", e)
            handleSocketError(e)
        }
        traceSocketEvent(SocketDiagnosticsEventType.NODE_DRAIN_END)
    }

    /**
     * 소켓 이벤트를 추적하여 디버그 로그에 기록합니다.
     */
    override fun traceSocketEvent(type: SocketDiagnosticsEventType, data: Any?) {
        if (logger.isDebugEnabled) {
            logger.debug("Socket[$debugLabel] 이벤트: $type, 데이터: $data")
        }
    }

    /**
     * 리소스를 해제합니다.
     */
    override fun dispose() {
        if (isDisposed.getAndSet(true)) {
            return
        }

        traceSocketEvent(SocketDiagnosticsEventType.CLOSE)
        logger.info("Socket[$debugLabel] 리소스 해제 중")

        dataListeners.clear()
        closeListeners.clear()
        endListeners.clear()

        try {
            if (!isClosed()) {
                closeAction()
            }
        } catch (e: Exception) {
            logger.warn("Socket[$debugLabel] 리소스 해제 중 연결 닫기 예외 발생", e)
        }

        receiveThread?.interrupt()
        receiveThread = null
        endTimeoutHandle?.interrupt()
        endTimeoutHandle = null

        logger.info("Socket[$debugLabel] 리소스 해제 완료")
    }

    // --- 소켓 상태 확인 메소드 ---
    fun isClosed(): Boolean {
        return when {
            socket != null -> socket.isClosed
            channel != null -> !channel.isOpen
            else -> true
        }
    }

    fun isInputClosed(): Boolean {
        return when {
            socket != null -> socket.isClosed || socket.isInputShutdown
            channel != null -> !channel.isOpen
            else -> true
        }
    }

    fun isOutputClosed(): Boolean {
        return when {
            socket != null -> socket.isClosed || socket.isOutputShutdown
            channel != null -> !channel.isOpen
            else -> true
        }
    }

    fun isDisposed(): Boolean = isDisposed.get()
}
