// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.nio.ByteBuffer

/**
 * 프로토콜 리더(Protocol Reader) 클래스입니다.
 * 소켓에서 바이너리 데이터를 읽어 프로토콜 메시지로 파싱합니다.
 * VSCode의 `ProtocolReader`에 해당합니다.
 *
 * @param socket 데이터를 읽어올 `ISocket` 인스턴스
 */
class ProtocolReader(private val socket: ISocket) : Disposable {
    private var isDisposed = false // 객체 해제 여부
    private val incomingData = ChunkStream() // 수신 데이터를 버퍼링하는 청크 스트림
    private var lastReadTime = System.currentTimeMillis() // 마지막 데이터 읽기 시간
    
    private val messageListeners = mutableListOf<(ProtocolMessage) -> Unit>() // 메시지 리스너 목록
    
    // 메시지 파싱 상태를 관리하는 객체
    private val state = State()
    
    companion object {
        private val LOG = Logger.getInstance(ProtocolReader::class.java)
    }
    
    init {
        // 소켓으로부터 데이터가 수신되면 `acceptChunk` 메소드를 호출하도록 리스너 등록
        socket.onData(this::acceptChunk)
    }
    
    /**
     * 메시지 리스너를 추가합니다.
     * @param listener 메시지 수신 시 호출될 리스너
     * @return 리스너 등록 해제를 위한 `Disposable` 객체
     */
    fun onMessage(listener: (ProtocolMessage) -> Unit): Disposable {
        messageListeners.add(listener)
        return Disposable { messageListeners.remove(listener) }
    }
    
    /**
     * 데이터 청크를 수신하고 파싱을 시도합니다.
     * @param data 수신된 데이터 청크
     */
    fun acceptChunk(data: ByteArray) {
        if (data.isEmpty()) {
            return
        }
        lastReadTime = System.currentTimeMillis() // 마지막 읽기 시간 업데이트
        
        incomingData.acceptChunk(data) // 수신 데이터를 청크 스트림에 추가
        
        // 청크 스트림에 읽을 데이터가 충분히 있는 동안 메시지를 파싱합니다.
        while (incomingData.byteLength >= state.readLen) {
            val buff = incomingData.read(state.readLen) // 필요한 길이만큼 데이터 읽기
            
            if (state.readHead) {
                // `buff`가 메시지 헤더인 경우
                
                // 메시지 헤더 파싱 (타입, ID, ACK, 메시지 크기)
                val buffer = ByteBuffer.wrap(buff)
                val messageTypeByte = buffer.get(0)
                val id = buffer.getInt(1)
                val ack = buffer.getInt(5)
                val messageSize = buffer.getInt(9)
                
                val messageType = ProtocolMessageType.fromValue(messageTypeByte.toInt())
                
                // 다음에는 메시지 본문을 읽도록 상태를 업데이트합니다.
                state.readHead = false
                state.readLen = messageSize
                state.messageType = messageType
                state.id = id
                state.ack = ack
                
                socket.traceSocketEvent(
                    SocketDiagnosticsEventType.PROTOCOL_HEADER_READ, 
                    HeaderReadInfo(
                        messageType.toTypeString(),
                        id,
                        ack,
                        messageSize
                    )
                )
            } else {
                // `buff`가 메시지 본문인 경우
                val messageType = state.messageType
                val id = state.id
                val ack = state.ack
                
                // 다음에는 메시지 헤더를 읽도록 상태를 초기화합니다.
                state.readHead = true
                state.readLen = ProtocolConstants.HEADER_LENGTH
                state.messageType = ProtocolMessageType.NONE
                state.id = 0
                state.ack = 0
                
                socket.traceSocketEvent(SocketDiagnosticsEventType.PROTOCOL_MESSAGE_READ, buff)
                
                val message = ProtocolMessage(messageType, id, ack, buff)
                
                // 모든 메시지 리스너에게 파싱된 메시지를 알립니다.
                ArrayList(messageListeners).forEach { listener ->
                    try {
                        listener(message)
                    } catch (e: Exception) {
                        LOG.warn("메시지 리스너 처리 중 오류 발생: ${e.message}", e)
                    }
                }
                
                if (isDisposed) {
                    // 이벤트 리스너가 객체를 해제했을 수도 있으므로 확인 후 루프 종료
                    break
                }
            }
        }
    }
    
    /**
     * 버퍼에 남아있는 모든 데이터를 읽습니다.
     * @return 버퍼의 모든 데이터를 담은 바이트 배열
     */
    fun readEntireBuffer(): ByteArray {
        return incomingData.read(incomingData.byteLength)
    }
    
    /**
     * 마지막으로 데이터를 읽은 시간을 가져옵니다.
     * @return 마지막 읽기 시간 (밀리초 타임스탬프)
     */
    fun getLastReadTime(): Long {
        return lastReadTime
    }
    
    override fun dispose() {
        isDisposed = true
        messageListeners.clear()
    }
    
    /**
     * 메시지 파싱 상태를 관리하는 내부 클래스입니다.
     */
    private class State {
        var readHead = true // 현재 헤더를 읽을 차례인지 여부
        var readLen = ProtocolConstants.HEADER_LENGTH // 다음으로 읽을 바이트 길이
        var messageType = ProtocolMessageType.NONE // 파싱된 메시지 타입
        var id = 0 // 파싱된 메시지 ID
        var ack = 0 // 파싱된 ACK ID
    }
    
    /**
     * 메시지 헤더 읽기 정보를 담는 데이터 클래스입니다. (디버깅용)
     */
    private data class HeaderReadInfo(
        val messageType: String,
        val id: Int,
        val ack: Int,
        val messageSize: Int
    ) {
        override fun toString(): String {
            return "HeaderReadInfo{messageType='$messageType', id=$id, ack=$ack, messageSize=$messageSize}"
        }
    }
}
