// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 프로토콜 상수(Protocol Constants)를 정의하는 객체입니다.
 * VSCode의 `ProtocolConstants`에 해당하며, 메시지 전달 프로토콜의 동작을 제어하는 데 사용됩니다.
 */
object ProtocolConstants {
    /**
     * 메시지 헤더의 길이 (바이트 단위)입니다.
     * 메시지 파싱 시 헤더와 본문을 구분하는 데 사용됩니다.
     */
    const val HEADER_LENGTH = 13
    
    /**
     * 확인 응답(ACK) 메시지를 전송하기 위한 최대 지연 시간 (밀리초)입니다.
     * 이 시간 내에 ACK를 보내지 않으면 재전송 등의 문제가 발생할 수 있습니다.
     */
    const val ACKNOWLEDGE_TIME = 2000 // 2초
    
    /**
     * 보낸 메시지에 대한 확인 응답을 이 시간 동안 받지 못하고,
     * 동시에 서버로부터 어떤 데이터도 수신하지 못하면 연결이 타임아웃된 것으로 간주합니다.
     */
    const val TIMEOUT_TIME = 20000 // 20초
    
    /**
     * 이 시간 범위 내에 재연결이 발생하지 않으면, 연결이 영구적으로 끊긴 것으로 간주합니다.
     */
    const val RECONNECTION_GRACE_TIME = 3 * 60 * 60 * 1000 // 3시간
    
    /**
     * 첫 번째 재연결과 마지막 재연결 사이의 최대 유예 시간입니다.
     */
    const val RECONNECTION_SHORT_GRACE_TIME = 5 * 60 * 1000 // 5분
    
    /**
     * 운영체제에 의해 연결이 끊기는 것을 방지하기 위해 주기적으로 메시지를 전송하는 간격 (밀리초)입니다.
     * (Keep-Alive 메시지)
     */
    const val KEEP_ALIVE_SEND_TIME = 5000 // 5초
}
