// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import com.intellij.openapi.Disposable

/**
 * 보류 중인 RPC 응답을 나타내는 클래스입니다.
 * VSCode의 `PendingRPCReply`에 해당하며, 비동기 RPC 호출의 결과를 처리하고
 * 관련 리소스를 정리하는 역할을 합니다.
 */
class PendingRPCReply(
    private val promise: LazyPromise, // RPC 호출의 결과를 담을 `LazyPromise`
    private val disposable: Disposable // 응답 처리 완료 후 해제될 `Disposable`
) {
    /**
     * RPC 응답을 성공적으로 해결(resolve)합니다.
     * @param value RPC 호출의 결과 값
     */
    fun resolveOk(value: Any?) {
        promise.resolveOk(value) // Promise를 성공 상태로 완료
        disposable.dispose() // 관련 리소스 해제
    }

    /**
     * RPC 응답을 오류와 함께 거부(reject)합니다.
     * @param err 발생한 오류 객체
     */
    fun resolveErr(err: Throwable) {
        promise.resolveErr(err) // Promise를 오류 상태로 완료
        disposable.dispose() // 관련 리소스 해제
    }
}
