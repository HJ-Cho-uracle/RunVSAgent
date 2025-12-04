// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 프로토콜 메시지 타입(Protocol Message Type)을 정의하는 열거형입니다.
 * VSCode의 `ProtocolMessageType`에 해당하며, 메시지의 종류를 식별하는 데 사용됩니다.
 */
enum class ProtocolMessageType(val value: Int) {
    /**
     * 정의되지 않은 메시지 타입입니다.
     */
    NONE(0),

    /**
     * 일반적인 데이터 메시지입니다.
     */
    REGULAR(1),

    /**
     * 프로토콜 제어를 위한 메시지입니다.
     */
    CONTROL(2),

    /**
     * 메시지 수신 확인 응답(Acknowledgment) 메시지입니다.
     */
    ACK(3),

    /**
     * 연결 끊기(Disconnect) 메시지입니다.
     */
    DISCONNECT(5),

    /**
     * 메시지 재전송 요청(Replay Request) 메시지입니다.
     */
    REPLAY_REQUEST(6),

    /**
     * 메시지 쓰기 일시 중지(Pause) 메시지입니다.
     */
    PAUSE(7),

    /**
     * 메시지 쓰기 재개(Resume) 메시지입니다.
     */
    RESUME(8),

    /**
     * 연결 유지(Keep-Alive) 메시지입니다.
     */
    KEEP_ALIVE(9),
    ;

    /**
     * 열거형 타입의 문자열 설명을 반환합니다. (디버깅 및 로깅용)
     * @return 문자열 설명
     */
    fun toTypeString(): String = when (this) {
        NONE -> "None"
        REGULAR -> "Regular"
        CONTROL -> "Control"
        ACK -> "Ack"
        DISCONNECT -> "Disconnect"
        REPLAY_REQUEST -> "ReplayRequest"
        PAUSE -> "PauseWriting"
        RESUME -> "ResumeWriting"
        KEEP_ALIVE -> "KeepAlive"
    }

    companion object {
        /**
         * 정수 값으로부터 해당하는 열거형 상수를 가져옵니다.
         * @param value 정수 값
         * @return 해당하는 열거형 상수, 찾지 못하면 `NONE`을 반환합니다.
         */
        fun fromValue(value: Int): ProtocolMessageType {
            return values().find { it.value == value } ?: NONE
        }
    }
}
