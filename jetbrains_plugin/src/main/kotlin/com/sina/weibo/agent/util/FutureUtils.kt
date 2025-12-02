// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * 코루틴 작업을 `CompletableFuture`로 변환하는 헬퍼 함수입니다.
 * 코루틴 기반의 비동기 작업을 Java의 `CompletableFuture`를 사용하는 코드와 통합할 때 유용합니다.
 *
 * @param scope 코루틴이 실행될 `CoroutineScope`
 * @param block 코루틴 코드 블록 (`suspend` 함수)
 * @return 코루틴 결과(`T`)를 담는 `CompletableFuture<T>`
 */
fun <T> toCompletableFuture(scope: CoroutineScope, block: suspend () -> T): CompletableFuture<T> {
    // `scope.future { ... }`는 코루틴을 실행하고 그 결과를 `CompletableFuture`로 래핑합니다.
    return scope.future { block() }
}
