// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

/**
 * RPC 메시지 타입(Message Type)을 정의하는 열거형입니다.
 * VSCode의 `MessageType` 열거형에 해당하며, RPC 통신에서 메시지의 종류를 식별하는 데 사용됩니다.
 */
enum class MessageType(val value: Int) {
    /**
     * JSON 인자를 포함하는 요청 메시지입니다.
     */
    RequestJSONArgs(1),

    /**
     * JSON 인자와 취소 토큰을 포함하는 요청 메시지입니다.
     */
    RequestJSONArgsWithCancellation(2),

    /**
     * 혼합된 인자(문자열, 버퍼 등)를 포함하는 요청 메시지입니다.
     */
    RequestMixedArgs(3),

    /**
     * 혼합된 인자와 취소 토큰을 포함하는 요청 메시지입니다.
     */
    RequestMixedArgsWithCancellation(4),

    /**
     * 메시지 수신 확인 응답(Acknowledged) 메시지입니다.
     */
    Acknowledged(5),

    /**
     * 작업 취소 메시지입니다.
     */
    Cancel(6),

    /**
     * 빈 성공 응답 메시지입니다.
     */
    ReplyOKEmpty(7),

    /**
     * 바이너리 버퍼를 포함하는 성공 응답 메시지입니다.
     */
    ReplyOKVSBuffer(8),

    /**
     * JSON 형식의 성공 응답 메시지입니다.
     */
    ReplyOKJSON(9),

    /**
     * JSON 형식과 버퍼를 포함하는 성공 응답 메시지입니다.
     */
    ReplyOKJSONWithBuffers(10),

    /**
     * 오류 정보를 포함하는 오류 응답 메시지입니다.
     */
    ReplyErrError(11),

    /**
     * 빈 오류 응답 메시지입니다.
     */
    ReplyErrEmpty(12);
    
    companion object {
        /**
         * 정수 값으로부터 해당하는 `MessageType`을 가져옵니다.
         * @param value 정수 값
         * @return 해당하는 `MessageType` 또는 찾지 못하면 null
         */
        fun fromValue(value: Int): MessageType? = values().find { it.value == value }
    }
}
