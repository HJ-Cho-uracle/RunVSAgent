// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 메시지 리스너(Message Listener) 인터페이스입니다.
 * 메시지를 수신했을 때 호출될 콜백 메소드를 정의하는 함수형 인터페이스입니다.
 */
fun interface MessageListener {
    /**
     * 수신된 메시지를 처리합니다.
     * @param data 수신된 메시지 데이터 (바이트 배열)
     */
    fun onMessage(data: ByteArray)
}
