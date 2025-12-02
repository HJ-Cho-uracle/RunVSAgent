// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.events

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap


/**
 * 애플리케이션 레벨의 이벤트 버스 서비스입니다.
 * 플러그인 전체에서 공유되는 이벤트를 발행하고 구독하는 데 사용됩니다.
 * `@Service` 어노테이션을 통해 IntelliJ에 애플리케이션 서비스로 등록됩니다.
 */
@Service
class EventBus : AbsEventBus() {
    companion object {
        /**
         * `EventBus`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun get(): EventBus {
            return service<EventBus>()
        }
    }
}

/**
 * 프로젝트 레벨의 이벤트 버스 서비스입니다.
 * 특정 프로젝트 내에서만 유효한 이벤트를 발행하고 구독하는 데 사용됩니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class ProjectEventBus : AbsEventBus() {
}

/**
 * 이벤트 버스의 추상 기본 클래스입니다.
 * `MutableSharedFlow`를 사용하여 코루틴 기반의 이벤트 스트림을 제공하고,
 * 일반 리스너를 위한 `ConcurrentHashMap`도 관리합니다.
 */
open class AbsEventBus : Disposable {
    // 로깅을 위한 Logger 인스턴스
    private val logger = Logger.getInstance(ProjectEventBus::class.java) // ProjectEventBus 로거를 사용

    // 모든 이벤트가 이 SharedFlow를 통해 전달됩니다. (코루틴 기반)
    private val _events = MutableSharedFlow<Event<*>>(extraBufferCapacity = 64)
    val events: SharedFlow<Event<*>> = _events.asSharedFlow()

    // 이벤트 타입별로 리스너 함수들을 저장하는 맵 (일반 리스너용)
    private val listeners = ConcurrentHashMap<EventType<*>, MutableList<(Any) -> Unit>>()

    /**
     * 이벤트를 발행합니다. (코루틴 스코프 내에서 호출)
     * `SharedFlow`를 통해 이벤트를 발행하고, 등록된 일반 리스너들에게도 알립니다.
     * @param eventType 발행할 이벤트의 타입
     * @param data 이벤트와 함께 전달할 데이터
     */
    suspend fun <T : Any> emit(eventType: EventType<T>, data: T) {
        _events.emit(Event(eventType, data))

        // 일반 리스너들에게도 알립니다.
        @Suppress("UNCHECKED_CAST")
        listeners[eventType]?.forEach { listener ->
            try {
                listener(data)
            } catch (e: Exception) {
                logger.error("이벤트 처리 중 예외 발생", e)
            }
        }
    }

    /**
     * 지정된 코루틴 스코프 내에서 이벤트를 발행합니다.
     * @param scope 이벤트를 발행할 코루틴 스코프
     * @param eventType 발행할 이벤트의 타입
     * @param data 이벤트와 함께 전달할 데이터
     */
    fun <T : Any> emitIn(scope: CoroutineScope, eventType: EventType<T>, data: T) {
        scope.launch {
            emit(eventType, data)
        }
    }

    /**
     * IntelliJ 애플리케이션 컨텍스트(EDT)에서 이벤트를 발행합니다.
     * IntelliJ 플랫폼의 스레드 안전한 메소드를 사용하여 UI 스레드에서 리스너를 호출합니다.
     * @param eventType 발행할 이벤트의 타입
     * @param data 이벤트와 함께 전달할 데이터
     */
    fun <T : Any> emitInApplication(eventType: EventType<T>, data: T) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runReadAction { // 읽기 액션 내에서 리스너 호출
                listeners[eventType]?.forEach { listener ->
                    @Suppress("UNCHECKED_CAST")
                    try {
                        listener(data)
                    } catch (e: Exception) {
                        logger.error("이벤트 처리 중 예외 발생", e)
                    }
                }
            }
        }
    }

    /**
     * 지정된 코루틴 스코프 내에서 특정 이벤트 타입을 구독합니다.
     * `SharedFlow`를 필터링하여 해당 타입의 이벤트만 처리합니다.
     * @param scope 이벤트를 구독할 코루틴 스코프
     * @param eventType 구독할 이벤트의 타입
     * @param handler 이벤트를 처리할 suspend 함수
     */
    inline fun <reified T : Any> on(
        scope: CoroutineScope,
        eventType: EventType<T>,
        crossinline handler: suspend (T) -> Unit
    ) {
        scope.launch {
            events
                .filter { it.type == eventType } // 해당 이벤트 타입만 필터링
                .collect { event ->
                    @Suppress("UNCHECKED_CAST")
                    handler(event.data as T) // 핸들러 호출
                }
        }
    }

    /**
     * 일반 이벤트 리스너를 추가합니다. (코루틴 불필요)
     * IntelliJ 플랫폼 호환 이벤트 리스닝 방식을 제공합니다.
     * @param eventType 리스너를 추가할 이벤트의 타입
     * @param handler 이벤트를 처리할 함수
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> addListener(eventType: EventType<T>, handler: (T) -> Unit) {
        listeners.getOrPut(eventType) { mutableListOf() }.add(handler as (Any) -> Unit)
    }

    /**
     * `Disposable` 객체와 함께 이벤트 리스너를 추가합니다.
     * `Disposable`가 해제될 때 리스너가 자동으로 제거됩니다.
     * @param eventType 리스너를 추가할 이벤트의 타입
     * @param disposable 리스너의 생명주기를 관리할 `Disposable` 객체
     * @param handler 이벤트를 처리할 함수
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> addListener(eventType: EventType<T>, disposable: Disposable, handler: (T) -> Unit) {
        val wrappedHandler = handler as (Any) -> Unit
        listeners.getOrPut(eventType) { mutableListOf() }.add(wrappedHandler)

        // IntelliJ의 Disposer API를 사용하여 리소스 정리 시 리스너를 자동으로 제거합니다.
        Disposer.register(disposable, Disposable {
            removeListener(eventType, wrappedHandler)
        })
    }

    /**
     * 이벤트 리스너를 제거합니다.
     * @param eventType 리스너를 제거할 이벤트의 타입
     * @param handler 제거할 리스너 함수
     */
    fun <T : Any> removeListener(eventType: EventType<T>, handler: (Any) -> Unit) {
        listeners[eventType]?.remove(handler)
    }

    /**
     * 특정 이벤트 타입에 등록된 모든 리스너를 제거합니다.
     * @param eventType 모든 리스너를 제거할 이벤트의 타입
     */
    fun <T : Any> removeAllListeners(eventType: EventType<T>) {
        listeners.remove(eventType)
    }

    /**
     * `AbsEventBus` 인스턴스가 해제될 때 호출됩니다.
     * 현재는 특별한 정리 작업이 필요하지 않습니다.
     */
    override fun dispose() {
    }
}

/**
 * 이벤트 타입을 정의하기 위한 마커 인터페이스입니다.
 * 제네릭 `T`는 이벤트와 함께 전달될 데이터의 타입을 나타냅니다.
 */
interface EventType<T : Any>

/**
 * 이벤트 데이터를 캡슐화하는 데이터 클래스입니다.
 * @param type 이벤트의 타입
 * @param data 이벤트와 함께 전달될 실제 데이터
 */
data class Event<T : Any>(
    val type: EventType<T>,
    val data: T
)
