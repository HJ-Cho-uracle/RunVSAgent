// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * 버퍼링된 이벤트 이미터(Buffered Event Emitter) 클래스입니다.
 * 이벤트 리스너가 등록되기 전에 발생한 메시지를 버퍼링하여, 리스너가 등록된 후 해당 메시지들을 전달합니다.
 * VSCode의 `BufferedEmitter`에 해당합니다.
 * @param T 이벤트 데이터의 타입
 */
class BufferedEmitter<T> {
    // 이벤트를 수신하는 리스너 목록
    private val listeners = mutableListOf<(T) -> Unit>()
    // 리스너가 없을 때 이벤트를 임시로 저장하는 버퍼
    private val bufferedMessages = ConcurrentLinkedQueue<T>()
    // 리스너가 등록되어 있는지 여부
    private var hasListeners = false
    // 버퍼링된 메시지를 전달 중인지 여부
    private var isDeliveringMessages = false
    
    // 코루틴 스코프 (IO 디스패처 사용)
    private val coroutineContext = Dispatchers.IO
    private val scope = CoroutineScope(coroutineContext)
    
    companion object {
        private val LOG = Logger.getInstance(BufferedEmitter::class.java)
    }

    /**
     * 이벤트 리스너를 추가하기 위한 속성입니다.
     * TypeScript 버전의 `event` 속성과 유사합니다.
     */
    val event: EventListener<T> = this::onEvent
    
    /**
     * 이벤트 리스너를 추가합니다.
     * 리스너가 추가되면 버퍼링된 메시지들을 전달하기 시작합니다.
     * @param listener 추가할 이벤트 리스너 함수
     * @return 리스너를 제거하기 위한 `Disposable` 객체
     */
    fun onEvent(listener: (T) -> Unit): Disposable {
        val wasEmpty = listeners.isEmpty()
        listeners.add(listener)
        
        if (wasEmpty) {
            hasListeners = true
            // 마이크로태스크 큐를 사용하여 다른 메시지가 수신되기 전에 버퍼링된 메시지가 전달되도록 합니다.
            scope.launch { deliverMessages() }
        }
        
        return Disposable {
            // 리스너 제거 시 동기화 처리
            synchronized(listeners) {
                listeners.remove(listener)
                if (listeners.isEmpty()) {
                    hasListeners = false
                }
            }
        }
    }
    
    /**
     * 이벤트를 발생시킵니다.
     * 리스너가 있으면 즉시 전달하고, 없으면 버퍼에 저장합니다.
     * @param event 발생시킬 이벤트 데이터
     */
    fun fire(event: T) {
        if (hasListeners) {
            // 버퍼에 메시지가 있으면 현재 이벤트를 버퍼에 추가합니다.
            if (bufferedMessages.isNotEmpty()) {
                bufferedMessages.offer(event)
            } else {
                // 버퍼가 비어 있으면 리스너들에게 즉시 전달합니다.
                synchronized(listeners) {
                    ArrayList(listeners).forEach { listener ->
                        try {
                            listener(event)
                        } catch (e: Exception) {
                            LOG.warn("이벤트 리스너 처리 중 오류 발생", e)
                        }
                    }
                }
            }
        } else {
            // 리스너가 없으면 이벤트를 버퍼에 저장합니다.
            bufferedMessages.offer(event)
        }
    }
    
    /**
     * 버퍼를 비웁니다.
     */
    fun flushBuffer() {
        bufferedMessages.clear()
    }
    
    /**
     * 버퍼링된 메시지들을 리스너들에게 전달합니다.
     */
    private fun deliverMessages() {
        if (isDeliveringMessages) {
            return
        }
        
        isDeliveringMessages = true
        try {
            // 리스너가 있고 버퍼에 메시지가 있는 동안 메시지를 전달합니다.
            while (hasListeners && bufferedMessages.isNotEmpty()) {
                val event = bufferedMessages.poll() ?: break
                synchronized(listeners) {
                    ArrayList(listeners).forEach { listener ->
                        try {
                            listener(event)
                        } catch (e: Exception) {
                            LOG.warn("이벤트 리스너 처리 중 오류 발생", e)
                        }
                    }
                }
            }
        } finally {
            isDeliveringMessages = false
        }
    }
}

/**
 * 이벤트 리스너 함수 타입을 위한 타입 별칭입니다.
 */
typealias EventListener<T> = ((T) -> Unit) -> Disposable
