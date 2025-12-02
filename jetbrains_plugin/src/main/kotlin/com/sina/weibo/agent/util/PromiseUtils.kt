// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import com.sina.weibo.agent.ipc.proxy.LazyPromise
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `LazyPromise`를 `CompletableFuture`로 변환하는 확장 함수입니다.
 * 코루틴 기반의 `LazyPromise`를 Java의 `CompletableFuture`를 사용하는 코드와 통합할 때 유용합니다.
 *
 * @param T 결과 타입
 * @return `LazyPromise`의 결과를 담는 `CompletableFuture`
 */
fun <T> LazyPromise.toCompletableFuture(): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    
    // `LazyPromise`가 완료될 때 호출될 콜백을 등록합니다.
    this.invokeOnCompletion { throwable ->
        if (throwable != null) {
            future.completeExceptionally(throwable) // 예외 발생 시 `CompletableFuture`를 예외로 완료
        } else {
            try {
                @Suppress("UNCHECKED_CAST")
                val result = this.getCompleted() as T // `LazyPromise`의 완료된 결과 가져오기
                future.complete(result) // `CompletableFuture`를 결과로 완료
            } catch (e: Exception) {
                future.completeExceptionally(e) // 결과 처리 중 예외 발생 시 `CompletableFuture`를 예외로 완료
            }
        }
    }
    
    return future
}

/**
 * `LazyPromise`의 완료를 기다리고 결과를 반환하는 확장 함수입니다.
 * `suspend` 함수이므로 코루틴 내에서 호출되어야 합니다.
 *
 * 사용 예시:
 * ```
 * val result = lazyPromise.await()
 * ```
 *
 * @param T 결과 타입
 * @return `LazyPromise`의 결과 값
 */
suspend fun <T> LazyPromise.await(): T {
    return suspendCancellableCoroutine { continuation ->
        // `LazyPromise`가 완료될 때 호출될 콜백을 등록합니다.
        this.invokeOnCompletion { throwable ->
            if (throwable != null) {
                continuation.resumeWithException(throwable) // 예외 발생 시 코루틴을 예외로 재개
            } else {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result = this.getCompleted() as T
                    continuation.resume(result) // 결과가 있으면 코루틴을 결과로 재개
                } catch (e: Exception) {
                    continuation.resumeWithException(e) // 결과 처리 중 예외 발생 시 코루틴을 예외로 재개
                }
            }
        }
        
        // 코루틴이 취소될 때 호출될 콜백을 등록합니다.
        continuation.invokeOnCancellation {
            // `LazyPromise`가 취소를 지원하는 경우 여기에 취소 로직을 추가할 수 있습니다.
        }
    }
}

/**
 * `LazyPromise`의 결과를 처리하는 확장 함수입니다.
 * 성공 또는 실패 콜백을 등록하여 결과를 비동기적으로 처리합니다.
 *
 * 사용 예시:
 * ```
 * lazyPromise.handle { result ->
 *    // 결과 처리
 * }
 * ```
 *
 * @param T 결과 타입
 * @param onSuccess 성공 시 호출될 콜백 함수
 * @param onError 오류 발생 시 호출될 콜백 함수 (기본값은 예외를 다시 던짐)
 */
fun <T> LazyPromise.handle(
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit = { throw it }
) {
    this.invokeOnCompletion { throwable ->
        if (throwable != null) {
            onError(throwable) // 예외 발생 시 오류 콜백 호출
        } else {
            try {
                @Suppress("UNCHECKED_CAST")
                val result = this.getCompleted() as T
                onSuccess(result) // 성공 시 성공 콜백 호출
            } catch (e: Exception) {
                onError(e) // 결과 처리 중 예외 발생 시 오류 콜백 호출
            }
        }
    }
}

/**
 * `LazyPromise`의 결과를 다른 타입으로 변환하는 확장 함수입니다.
 *
 * 사용 예시:
 * ```
 * val boolPromise = lazyPromise.thenMap { result ->
 *    // 결과 변환
 *    result is Boolean && result
 * }
 * ```
 *
 * @param T 원본 결과 타입
 * @param R 변환될 결과 타입
 * @param mapper 변환 함수
 * @return 변환된 결과를 담는 새로운 `LazyPromise`
 */
fun <T, R> LazyPromise.thenMap(mapper: (T) -> R): LazyPromise {
    val result = LazyPromise() // 변환된 결과를 담을 새로운 `LazyPromise`
    
    this.invokeOnCompletion { throwable ->
        if (throwable != null) {
            result.resolveErr(throwable) // 예외 발생 시 새로운 Promise를 예외로 완료
        } else {
            try {
                @Suppress("UNCHECKED_CAST")
                val value = this.getCompleted() as T // 원본 Promise의 결과 가져오기
                val mapped = mapper(value) // 변환 함수 적용
                result.resolveOk(mapped) // 새로운 Promise를 변환된 결과로 완료
            } catch (e: Exception) {
                result.resolveErr(e) // 변환 중 예외 발생 시 새로운 Promise를 예외로 완료
            }
        }
    }
    
    return result
}
