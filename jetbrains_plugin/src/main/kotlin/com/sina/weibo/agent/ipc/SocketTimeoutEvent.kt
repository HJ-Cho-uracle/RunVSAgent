// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 소켓 타임아웃 이벤트를 나타내는 데이터 클래스입니다.
 * VSCode의 `SocketTimeoutEvent`에 해당하며, 소켓 통신 중 타임아웃이 발생했을 때
 * 관련 정보를 캡슐화합니다.
 */
data class SocketTimeoutEvent(
    /**
     * 아직 확인 응답(ACK)을 받지 못한 메시지의 개수입니다.
     */
    val unacknowledgedMsgCount: Int,
    
    /**
     * 가장 오래된 미확인 메시지가 전송된 이후 경과된 시간 (밀리초)입니다.
     */
    val timeSinceOldestUnacknowledgedMsg: Long,
    
    /**
     * 마지막으로 데이터를 수신한 이후 경과된 시간 (밀리초)입니다.
     */
    val timeSinceLastReceivedSomeData: Long
)
