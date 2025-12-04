// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ 메인 스레드에서 오류 처리를 담당하는 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadErrorsShape`에 해당하며,
 * RPC를 통해 예기치 않은 오류 정보를 수신하는 기능을 정의합니다.
 */
interface MainThreadErrorsShape : Disposable {
    /**
     * Extension Host에서 발생한 예기치 않은 오류를 처리합니다.
     * @param err 오류 정보를 담고 있는 객체 (보통 Map 또는 문자열 형태)
     */
    fun onUnexpectedError(err: Any?)

    /**
     * 리소스를 해제합니다. (Disposable 인터페이스 구현)
     */
    override fun dispose()
}

/**
 * `MainThreadErrorsShape` 인터페이스의 구현 클래스입니다.
 * 전달받은 오류 정보를 IntelliJ 로그 시스템에 경고(warn) 레벨로 기록합니다.
 */
class MainThreadErrors : MainThreadErrorsShape {
    private val logger = Logger.getInstance(MainThreadErrors::class.java)

    /**
     * 전달받은 오류 정보를 로그에 기록합니다.
     * @param err Extension Host로부터 받은 오류 정보
     */
    override fun onUnexpectedError(err: Any?) {
        logger.warn("플러그인에서 예기치 않은 오류가 발생했습니다: $err")
    }

    /**
     * 리소스를 해제할 때 호출됩니다.
     */
    override fun dispose() {
        logger.info("Dispose MainThreadErrors")
    }
}
