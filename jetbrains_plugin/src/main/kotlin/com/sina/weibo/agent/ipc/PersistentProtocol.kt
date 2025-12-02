// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 영속적인 프로토콜(Persistent Protocol) 구현체입니다.
 * VSCode의 `PersistentProtocol`에 해당하며, `ISocket`을 통해 메시지를 안정적으로 주고받습니다.
 * 메시지 순서 보장, 재전송, Keep-Alive, ACK(확인 응답) 메커니즘을 포함합니다.
 */
class PersistentProtocol(opts: PersistentProtocolOptions, msgListener: ((data: ByteArray)->Unit)? = null) : IMessagePassingProtocol {
    companion object {
        private val LOG = Logger.getInstance(PersistentProtocol::class.java)
    }
    
    /**
     * 영속적인 프로토콜의 설정 옵션입니다.
     */
    class PersistentProtocolOptions(
        val socket: ISocket, // 사용할 소켓 구현체
        val initialChunk: ByteArray? = null, // 초기 데이터 청크 (재연결 시 유용)
        val loadEstimator: ILoadEstimator? = null, // 부하 추정기
        val sendKeepAlive: Boolean = true // Keep-Alive 메시지 전송 여부
    )
    
    // --- 상태 변수 ---
    private val _isReconnecting = AtomicBoolean(false) // 재연결 중인지 여부
    private val _didSendDisconnect = AtomicBoolean(false) // 연결 끊기 메시지를 보냈는지 여부
    
    private val _outgoingUnackMsg = LinkedBlockingQueue<ProtocolMessage>() // 아직 확인 응답을 받지 못한 발신 메시지 큐
    private val _outgoingMsgId = AtomicInteger(0) // 발신 메시지 ID 카운터
    private val _outgoingAckId = AtomicInteger(0) // 마지막으로 확인 응답을 받은 발신 메시지 ID
    private var _outgoingAckTimeout: Timer? = null // 발신 ACK 타임아웃 타이머
    
    private val _incomingMsgId = AtomicInteger(0) // 수신 메시지 ID 카운터
    private val _incomingAckId = AtomicInteger(0) // 마지막으로 확인 응답을 보낸 수신 메시지 ID
    private val _incomingMsgLastTime = AtomicLong(0L) // 마지막 수신 메시지 시간
    private var _incomingAckTimeout: Timer? = null // 수신 ACK 타임아웃 타이머
    
    private var _keepAliveInterval: Timer? = null // Keep-Alive 메시지 전송 타이머
    
    private val _lastReplayRequestTime = AtomicLong(0L) // 마지막 재전송 요청 시간
    private val _lastSocketTimeoutTime = AtomicLong(System.currentTimeMillis()) // 마지막 소켓 타임아웃 시간
    
    private var _socket: ISocket // 실제 소켓
    private var _socketWriter: ProtocolWriter // 소켓에 메시지를 쓰는 객체
    private var _socketReader: ProtocolReader // 소켓에서 메시지를 읽는 객체
    private val _socketDisposables = mutableListOf<Disposable>() // 소켓 관련 리스너 해제를 위한 Disposable 목록
    
    private val _loadEstimator: ILoadEstimator // 부하 추정기
    private val _shouldSendKeepAlive: Boolean // Keep-Alive 메시지 전송 여부
    
    // --- 버퍼링된 이벤트 이미터 ---
    private val _onControlMessage = BufferedEmitter<ByteArray>() // 제어 메시지 이벤트
    private val _onMessage = BufferedEmitter<ByteArray>() // 일반 메시지 이벤트
    private val _onDidDispose = BufferedEmitter<Unit>() // 프로토콜 해제 이벤트
    private val _onSocketClose = BufferedEmitter<SocketCloseEvent>() // 소켓 닫힘 이벤트
    private val _onSocketTimeout = BufferedEmitter<SocketTimeoutEvent>() // 소켓 타임아웃 이벤트

    private var _isDisposed = false // 프로토콜 해제 여부
    
    /**
     * 아직 확인 응답을 받지 못한 메시지 개수
     */
    val unacknowledgedCount: Int
        get() = _outgoingMsgId.get() - _outgoingAckId.get()
    
    /**
     * 프로토콜이 해제되었는지 여부
     */
    fun isDisposed(): Boolean {
        return _isDisposed
    }
    
    init {
        _loadEstimator = opts.loadEstimator ?: LoadEstimator.getInstance()
        _shouldSendKeepAlive = opts.sendKeepAlive
        _socket = opts.socket
        
        _socketWriter = ProtocolWriter(_socket)
        _socketReader = ProtocolReader(_socket)
        // 소켓 리더로부터 메시지를 수신하면 _receiveMessage 메소드를 호출하도록 리스너 등록
        _socketDisposables.add(_socketReader.onMessage(this::_receiveMessage))
        // 소켓이 닫히면 _onSocketClose 이벤트 발생
        _socketDisposables.add(_socket.onClose { event -> _onSocketClose.fire(event) })
        
        // 초기 데이터 청크가 있으면 소켓 리더에 전달
        if (opts.initialChunk != null) {
            _socketReader.acceptChunk(opts.initialChunk)
        }

        // 외부에서 제공된 메시지 리스너가 있으면 등록
        if(msgListener != null){
            this._onMessage.event { data ->
                msgListener(data)
            }
        }

        _socket.startReceiving() // 소켓 데이터 수신 시작
        
        // Keep-Alive 메시지 전송이 활성화되어 있으면 주기적으로 전송 타이머 설정
        if (_shouldSendKeepAlive) {
            _keepAliveInterval = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        _sendKeepAlive()
                    }
                }, ProtocolConstants.KEEP_ALIVE_SEND_TIME.toLong(), ProtocolConstants.KEEP_ALIVE_SEND_TIME.toLong())
            }
        }
    }
    
    /**
     * 프로토콜 리소스를 해제합니다.
     */
    override fun dispose() {
        _outgoingAckTimeout?.cancel()
        _outgoingAckTimeout = null
        
        _incomingAckTimeout?.cancel()
        _incomingAckTimeout = null
        
        _keepAliveInterval?.cancel()
        _keepAliveInterval = null
        
        _socketDisposables.forEach { it.dispose() }
        _socketDisposables.clear()

        _isDisposed = true
    }
    
    /**
     * 모든 데이터가 전송될 때까지 기다립니다.
     */
    override suspend fun drain() {
        _socketWriter.drain()
    }
    
    /**
     * 메시지를 전송합니다.
     * 메시지 ID를 할당하고, 확인 응답을 받지 못한 메시지 큐에 추가합니다.
     */
    override fun send(buffer: ByteArray) {
        val myId = _outgoingMsgId.incrementAndGet()
        val currentIncomingAckId = _incomingMsgId.get()
        _incomingAckId.set(currentIncomingAckId)
        val msg = ProtocolMessage(ProtocolMessageType.REGULAR, myId, currentIncomingAckId, buffer)
        _outgoingUnackMsg.add(msg)
        if (!_isReconnecting.get()) {
            _socketWriter.write(msg)
            _recvAckCheck()
        }
    }
    
    /**
     * 메시지 수신 리스너를 등록합니다.
     */
    override fun onMessage(listener: MessageListener): Disposable {
        return _onMessage.event { data ->
            listener.onMessage(data)
        }
    }
    
    /**
     * 프로토콜 해제 리스너를 등록합니다.
     */
    override fun onDidDispose(listener: () -> Unit): Disposable {
        return _onDidDispose.event { listener() }
    }
    
    // --- 기타 공개 메소드 ---
    fun onControlMessage(listener: (ByteArray) -> Unit): Disposable {
        return _onControlMessage.event(listener)
    }
    
    fun onSocketClose(listener: (SocketCloseEvent) -> Unit): Disposable {
        return _onSocketClose.event(listener)
    }
    
    fun onSocketTimeout(listener: (SocketTimeoutEvent) -> Unit): Disposable {
        return _onSocketTimeout.event(listener)
    }
    
    /**
     * 연결 끊기(DISCONNECT) 메시지를 전송합니다.
     */
    fun sendDisconnect() {
        if (_didSendDisconnect.compareAndSet(false, true)) {
            val msg = ProtocolMessage(ProtocolMessageType.DISCONNECT, 0, 0, ByteArray(0))
            _socketWriter.write(msg)
            _socketWriter.flush()
        }
    }
    
    /**
     * 일시 중지(PAUSE) 메시지를 전송합니다.
     */
    fun sendPause() {
        val msg = ProtocolMessage(ProtocolMessageType.PAUSE, 0, 0, ByteArray(0))
        _socketWriter.write(msg)
    }
    
    /**
     * 재개(RESUME) 메시지를 전송합니다.
     */
    fun sendResume() {
        val msg = ProtocolMessage(ProtocolMessageType.RESUME, 0, 0, ByteArray(0))
        _socketWriter.write(msg)
    }
    
    /**
     * 소켓 쓰기 작업을 일시 중지합니다.
     */
    fun pauseSocketWriting() {
        _socketWriter.pause()
    }
    
    fun getSocket(): ISocket {
        return _socket
    }
    
    /**
     * 마지막으로 수신된 데이터 이후 경과된 시간을 밀리초 단위로 가져옵니다.
     */
    fun getMillisSinceLastIncomingData(): Long {
        return System.currentTimeMillis() - _socketReader.getLastReadTime()
    }
    
    /**
     * 재연결을 수락하기 시작합니다.
     * 새로운 소켓으로 프로토콜을 재설정하고, 이전 리소스들을 정리합니다.
     */
    fun beginAcceptReconnection(socket: ISocket, initialDataChunk: ByteArray?) {
        _isReconnecting.set(true)
        
        _socketDisposables.forEach { it.dispose() }
        _socketDisposables.clear()
        _onControlMessage.flushBuffer()
        _onSocketClose.flushBuffer()
        _onSocketTimeout.flushBuffer()
        _socket.dispose()
        
        _lastReplayRequestTime.set(0)
        _lastSocketTimeoutTime.set(System.currentTimeMillis())
        
        _socket = socket
        _socketWriter = ProtocolWriter(_socket)
        _socketReader = ProtocolReader(_socket)
        _socketDisposables.add(_socketReader.onMessage(this::_receiveMessage))
        _socketDisposables.add(_socket.onClose { event -> _onSocketClose.fire(event) })
        
        if (initialDataChunk != null) {
            _socketReader.acceptChunk(initialDataChunk)
        }
    }
    
    /**
     * 재연결 수락을 종료합니다.
     * 재연결 후, 상대방에게 수신된 메시지를 알리고, 확인 응답을 받지 못한 메시지들을 재전송합니다.
     */
    fun endAcceptReconnection() {
        _isReconnecting.set(false)
        
        val currentIncomingMsgId = _incomingMsgId.get()
        _incomingAckId.set(currentIncomingMsgId)
        val msg = ProtocolMessage(ProtocolMessageType.ACK, 0, currentIncomingMsgId, ByteArray(0))
        _socketWriter.write(msg)
        
        // 확인 응답을 받지 못한 모든 메시지를 재전송합니다.
        val toSend = _outgoingUnackMsg.toTypedArray()
        for (message in toSend) {
            _socketWriter.write(message)
        }
        _recvAckCheck()
    }
    
    /**
     * 연결 끊기(DISCONNECT) 메시지를 수락합니다.
     */
    fun acceptDisconnect() {
        _onDidDispose.fire(Unit)
    }
    
    /**
     * 소켓 리더로부터 메시지를 수신했을 때 호출됩니다.
     * 메시지 타입에 따라 적절한 처리를 수행합니다.
     */
    private fun _receiveMessage(msg: ProtocolMessage) {
        // ACK ID 업데이트 및 확인 응답을 받지 못한 메시지 큐 정리
        val currentOutgoingAckId = _outgoingAckId.get()
        if (msg.ack > currentOutgoingAckId) {
            _outgoingAckId.set(msg.ack)
            while (_outgoingUnackMsg.isNotEmpty()) {
                val first = _outgoingUnackMsg.peek()
                if (first != null && first.id <= msg.ack) {
                    _outgoingUnackMsg.poll()
                } else {
                    break
                }
            }
        }
        
        when (msg.type) {
            ProtocolMessageType.NONE -> { /* N/A */ }
            ProtocolMessageType.REGULAR -> { // 일반 메시지
                val currentIncomingMsgId = _incomingMsgId.get()
                if (msg.id > currentIncomingMsgId) {
                    if (msg.id != currentIncomingMsgId + 1) {
                        // 메시지 손실 감지, 재전송 요청
                        val now = System.currentTimeMillis()
                        val lastReplayTime = _lastReplayRequestTime.get()
                        if (now - lastReplayTime > 10000) { // 10초에 한 번만 재전송 요청
                            _lastReplayRequestTime.set(now)
                            _socketWriter.write(ProtocolMessage(ProtocolMessageType.REPLAY_REQUEST, 0, 0, ByteArray(0)))
                        }
                    } else {
                        _incomingMsgId.set(msg.id)
                        _incomingMsgLastTime.set(System.currentTimeMillis())
                        _sendAckCheck() // ACK 전송 확인
                        _onMessage.fire(msg.data) // 메시지 이벤트 발생
                    }
                }
            }
            ProtocolMessageType.CONTROL -> { // 제어 메시지
                _onControlMessage.fire(msg.data)
            }
            ProtocolMessageType.ACK -> { /* 위에서 이미 처리됨 */ }
            ProtocolMessageType.DISCONNECT -> { // 연결 끊기 메시지
                _onDidDispose.fire(Unit)
            }
            ProtocolMessageType.REPLAY_REQUEST -> { // 재전송 요청
                // 확인 응답을 받지 못한 모든 메시지를 재전송합니다.
                val toSend = _outgoingUnackMsg.toTypedArray()
                for (message in toSend) {
                    _socketWriter.write(message)
                }
                _recvAckCheck()
            }
            ProtocolMessageType.PAUSE -> { // 일시 중지 메시지
                _socketWriter.pause()
            }
            ProtocolMessageType.RESUME -> { // 재개 메시지
                _socketWriter.resume()
            }
            ProtocolMessageType.KEEP_ALIVE -> { /* 처리할 필요 없음 */ }
        }
    }
    
    fun readEntireBuffer(): ByteArray {
        return _socketReader.readEntireBuffer()
    }
    
    fun flush() {
        _socketWriter.flush()
    }
    
    /**
     * 일반 확인 응답 흐름에 참여하지 않는 제어 메시지를 전송합니다.
     */
    fun sendControl(buffer: ByteArray) {
        val msg = ProtocolMessage(ProtocolMessageType.CONTROL, 0, 0, buffer)
        _socketWriter.write(msg)
    }
    
    /**
     * 수신 메시지에 대한 확인 응답(ACK) 전송을 확인합니다.
     */
    private fun _sendAckCheck() {
        val currentIncomingMsgId = _incomingMsgId.get()
        val currentIncomingAckId = _incomingAckId.get()
        
        if (currentIncomingMsgId <= currentIncomingAckId) {
            return // 확인 응답이 필요한 메시지가 없습니다.
        }
        
        if (_incomingAckTimeout != null) {
            return // 가까운 미래에 이미 확인 응답 확인이 예정되어 있습니다.
        }
        
        val timeSinceLastIncomingMsg = System.currentTimeMillis() - _incomingMsgLastTime.get()
        if (timeSinceLastIncomingMsg >= ProtocolConstants.ACKNOWLEDGE_TIME) {
            // 메시지 수신 후 충분한 시간이 경과했고, 그동안 보낼 메시지가 없었으므로,
            // 확인 응답만 포함하는 메시지를 전송합니다.
            _sendAck()
            return
        }
        
        // ACK 전송을 위한 타이머를 설정합니다.
        _incomingAckTimeout = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    _incomingAckTimeout = null
                    _sendAckCheck()
                }
            }, ProtocolConstants.ACKNOWLEDGE_TIME - timeSinceLastIncomingMsg + 5)
        }
    }
    
    /**
     * 발신 메시지에 대한 확인 응답(ACK) 수신을 확인하고, 필요한 경우 타임아웃을 처리합니다.
     */
    private fun _recvAckCheck() {
        val currentOutgoingMsgId = _outgoingMsgId.get()
        val currentOutgoingAckId = _outgoingAckId.get()
        
        if (currentOutgoingMsgId <= currentOutgoingAckId) {
            return // 모든 메시지가 확인 응답을 받았습니다.
        }
        
        if (_outgoingAckTimeout != null) {
            return // 가까운 미래에 이미 확인 응답 확인이 예정되어 있습니다.
        }
        
        if (_isReconnecting.get()) {
            return // 재연결 중에는 타임아웃을 트리거하지 않습니다.
        }
        
        val oldestUnacknowledgedMsg = _outgoingUnackMsg.peek()!!
        val timeSinceOldestUnacknowledgedMsg = System.currentTimeMillis() - oldestUnacknowledgedMsg.writtenTime
        val timeSinceLastReceivedSomeData = System.currentTimeMillis() - _socketReader.getLastReadTime()
        val timeSinceLastTimeout = System.currentTimeMillis() - _lastSocketTimeoutTime.get()
        
        if (timeSinceOldestUnacknowledgedMsg >= ProtocolConstants.TIMEOUT_TIME &&
            timeSinceLastReceivedSomeData >= ProtocolConstants.TIMEOUT_TIME &&
            timeSinceLastTimeout >= ProtocolConstants.TIMEOUT_TIME) {
            
            // 보낸 메시지에 대한 확인 응답을 오랫동안 받지 못했고,
            // 데이터 수신도 오랫동안 없었습니다.
            
            // 하지만 이벤트 루프가 바빠서 메시지를 읽지 못하는 경우일 수 있습니다.
            if (!_loadEstimator.hasHighLoad()) {
                // 소켓을 끊고 타임아웃 이벤트를 발생시킵니다.
                _lastSocketTimeoutTime.set(System.currentTimeMillis())
                _onSocketTimeout.fire(SocketTimeoutEvent(
                    _outgoingUnackMsg.size,
                    timeSinceOldestUnacknowledgedMsg,
                    timeSinceLastReceivedSomeData
                ))
                return
            }
        }
        
        // 다음 타임아웃 확인까지의 최소 시간을 계산하여 타이머를 설정합니다.
        val minimumTimeUntilTimeout = maxOf(
            ProtocolConstants.TIMEOUT_TIME - timeSinceOldestUnacknowledgedMsg,
            ProtocolConstants.TIMEOUT_TIME - timeSinceLastReceivedSomeData,
            ProtocolConstants.TIMEOUT_TIME - timeSinceLastTimeout,
            500
        )
        
        _outgoingAckTimeout = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    _outgoingAckTimeout = null
                    _recvAckCheck()
                }
            }, minimumTimeUntilTimeout)
        }
    }
    
    /**
     * 확인 응답(ACK) 메시지를 전송합니다.
     */
    private fun _sendAck() {
        val currentIncomingMsgId = _incomingMsgId.get()
        val currentIncomingAckId = _incomingAckId.get()
        
        if (currentIncomingMsgId <= currentIncomingAckId) {
            return // 확인 응답이 필요한 메시지가 없습니다.
        }
        
        _incomingAckId.set(currentIncomingMsgId)
        val msg = ProtocolMessage(ProtocolMessageType.ACK, 0, currentIncomingMsgId, ByteArray(0))
        _socketWriter.write(msg)
    }
    
    /**
     * Keep-Alive 메시지를 전송합니다.
     */
    private fun _sendKeepAlive() {
        val currentIncomingMsgId = _incomingMsgId.get()
        _incomingAckId.set(currentIncomingMsgId)
        val msg = ProtocolMessage(ProtocolMessageType.KEEP_ALIVE, 0, currentIncomingMsgId, ByteArray(0))
        _socketWriter.write(msg)
    }
}
