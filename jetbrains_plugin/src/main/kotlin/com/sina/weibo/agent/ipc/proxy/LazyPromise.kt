// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import kotlinx.coroutines.CompletableDeferred

/**
 * 지연 평가되는 Promise(Lazy Promise) 구현체입니다.
 * 비동기 작업의 결과를 나타내며, `CompletableDeferred`를 상속받아 코루틴과 통합됩니다.
 * VSCode의 `LazyPromise`에 해당합니다.
 */
open class LazyPromise : CompletableDeferred<Any?> by CompletableDeferred() {
    /**
     * Promise를 성공적으로 해결(resolve)합니다.
     * @param value 비동기 작업의 결과 값
     */
    fun resolveOk(value: Any?) {
        complete(value) // CompletableDeferred의 complete 메소드 호출
    }

    /**
     * Promise를 오류와 함께 거부(reject)합니다.
     * @param err 발생한 오류 객체
     */
    fun resolveErr(err: Throwable) {
        completeExceptionally(err) // CompletableDeferred의 completeExceptionally 메소드 호출
    }
}

/**
 * 취소된 Lazy Promise 구현체입니다.
 * 생성과 동시에 취소 예외(`CanceledException`)로 완료됩니다.
 * VSCode의 `CanceledLazyPromise`에 해당합니다.
 */
class CanceledLazyPromise : LazyPromise() {
    init {
        // 생성과 동시에 취소 예외로 완료됩니다.
        completeExceptionally(CanceledException())
    }
}

/**
 * 작업 취소를 나타내는 예외 클래스입니다.
 */
class CanceledException : Exception("작업이 취소되었습니다.")
