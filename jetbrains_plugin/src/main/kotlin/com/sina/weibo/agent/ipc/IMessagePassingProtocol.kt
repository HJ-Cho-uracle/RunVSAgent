// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CompletableDeferred

/**
 * 메시지 전달 프로토콜(Message Passing Protocol) 인터페이스입니다.
 * 프로세스 간 통신(IPC)을 위한 메시지 전송 및 수신 기능을 정의합니다.
 * VSCode의 `IMessagePassingProtocol`에 해당합니다.
 */
interface IMessagePassingProtocol : Disposable {
    /**
     * 메시지 데이터를 전송합니다.
     * @param buffer 전송할 메시지 데이터 (바이트 배열)
     */
    fun send(buffer: ByteArray)
    
    /**
     * 메시지 수신 리스너를 추가합니다.
     * @param listener 메시지 수신 시 호출될 리스너
     * @return 리스너 등록 해제를 위한 `Disposable` 객체
     */
    fun onMessage(listener: MessageListener): Disposable
    
    /**
     * 프로토콜이 해제될 때 호출될 리스너를 추가합니다.
     * @param listener 프로토콜 해제 시 호출될 리스너 함수
     * @return 리스너 등록 해제를 위한 `Disposable` 객체
     */
    fun onDidDispose(listener: () -> Unit): Disposable
    
    /**
     * 모든 데이터가 전송될 때까지 기다립니다.
     * 비동기 작업 완료를 위한 Promise 역할을 합니다.
     */
    suspend fun drain(): Unit
}
