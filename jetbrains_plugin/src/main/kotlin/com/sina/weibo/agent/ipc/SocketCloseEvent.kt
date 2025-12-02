// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 소켓 연결 종료 이벤트를 나타내는 봉인된(sealed) 인터페이스입니다.
 * VSCode의 `SocketCloseEvent` 타입에 해당합니다.
 * `sealed interface`를 사용하여 모든 가능한 구현체가 이 파일 내에 정의되도록 강제합니다.
 */
sealed interface SocketCloseEvent {
    /**
     * 연결 종료 이벤트의 타입을 가져옵니다.
     * @return 연결 종료 이벤트 타입
     */
    val type: SocketCloseEventType
    
    /**
     * 소켓 연결 종료 이벤트의 종류를 정의하는 열거형입니다.
     */
    enum class SocketCloseEventType {
        /**
         * Node.js 소켓(TCP 또는 UDS) 연결 종료 이벤트입니다.
         */
        NODE_SOCKET_CLOSE_EVENT,
        
        /**
         * WebSocket 연결 종료 이벤트입니다.
         */
        WEB_SOCKET_CLOSE_EVENT
    }
    
    /**
     * Node.js 소켓 연결 종료 이벤트를 나타내는 데이터 클래스입니다.
     */
    data class NodeSocketCloseEvent(
        /**
         * 소켓 통신 중 전송 오류가 있었는지 여부입니다.
         */
        val hadError: Boolean,
        
        /**
         * 발생한 기본 오류 (예외 객체)입니다.
         */
        val error: Throwable?
    ) : SocketCloseEvent {
        override val type: SocketCloseEventType = SocketCloseEventType.NODE_SOCKET_CLOSE_EVENT
    }
    
    /**
     * WebSocket 연결 종료 이벤트를 나타내는 데이터 클래스입니다.
     */
    data class WebSocketCloseEvent(
        /**
         * WebSocket 종료 코드입니다.
         */
        val code: Int,
        
        /**
         * WebSocket 종료 이유 메시지입니다.
         */
        val reason: String,
        
        /**
         * 연결이 깨끗하게(오류 없이) 종료되었는지 여부입니다.
         */
        val wasClean: Boolean,
        
        /**
         * 기본 이벤트 객체입니다.
         */
        val event: Any?
    ) : SocketCloseEvent {
        override val type: SocketCloseEventType = SocketCloseEventType.WEB_SOCKET_CLOSE_EVENT
    }
}
