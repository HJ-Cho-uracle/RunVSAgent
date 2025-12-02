// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable

/**
 * 소켓 인터페이스입니다.
 * 하위 통신 메커니즘을 추상화하며, 데이터 전송 및 수신, 연결 종료 등을 정의합니다.
 * VSCode의 `ISocket`에 해당합니다.
 */
interface ISocket : Disposable {
   /**
    * 데이터 수신 리스너를 추가합니다.
    * @param listener 데이터 수신 시 호출될 리스너
    * @return 리스너 등록 해제를 위한 `Disposable` 객체
    */
    fun onData(listener: DataListener): Disposable
    
   /**
    * 연결 종료 이벤트 리스너를 추가합니다.
    * @param listener 연결 종료 시 호출될 리스너
    * @return 리스너 등록 해제를 위한 `Disposable` 객체
    */
    fun onClose(listener: CloseListener): Disposable
    
   /**
    * 연결 종료(end) 이벤트 리스너를 추가합니다.
    * @param listener 연결 종료 시 호출될 리스너 함수
    * @return 리스너 등록 해제를 위한 `Disposable` 객체
    */
    fun onEnd(listener: () -> Unit): Disposable
    
   /**
    * 데이터를 전송합니다.
    * @param buffer 전송할 데이터 (바이트 배열)
    */
    fun write(buffer: ByteArray)
    
   /**
    * 연결을 종료합니다.
    */
    fun end()
    
   /**
    * 모든 데이터가 전송될 때까지 기다립니다.
    * 비동기 작업 완료를 위한 Promise 역할을 합니다.
    */
    suspend fun drain()
    
   /**
    * 소켓 이벤트를 추적합니다. (디버깅용)
    * @param type 이벤트 타입
    * @param data 이벤트 데이터
    */
    fun traceSocketEvent(type: SocketDiagnosticsEventType, data: Any? = null)

   /**
    * 데이터 수신을 시작합니다.
    */
    fun startReceiving()
    
   /**
    * 데이터 수신 리스너를 위한 함수형 인터페이스입니다.
    */
    fun interface DataListener {
        fun onData(data: ByteArray)
    }
    
   /**
    * 연결 종료 리스너를 위한 함수형 인터페이스입니다.
    */
    fun interface CloseListener {
        fun onClose(event: SocketCloseEvent)
    }
}
