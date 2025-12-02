// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * 원격(Extension Host)에서 전달된 콘솔 로그 메시지를 담는 데이터 클래스입니다.
 * TypeScript의 `IRemoteConsoleLog` 인터페이스에 해당합니다.
 */
data class RemoteConsoleLog(
    /** 로그의 종류: "log", "warn", "error", "info", "debug" 등 */
    val type: String,
    /** 로그의 심각도 수준 (숫자) */
    val severity: Int,
    /** 로그 메시지의 본문. 여러 인자를 가질 수 있습니다. */
    val args: List<Any?>,
    /** 로그가 발생한 소스 파일 위치 (선택 사항) */
    val source: String? = null,
    /** 소스 파일의 라인 번호 (선택 사항) */
    val line: Int? = null,
    /** 소스 파일의 컬럼 번호 (선택 사항) */
    val columnNumber: Int? = null,
    /** 로그가 발생한 시간의 타임스탬프 */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * IntelliJ 메인 스레드에서 콘솔 관련 작업을 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadConsoleShape`에 해당하며,
 * RPC를 통해 Extension Host의 로그 메시지를 수신하는 기능을 정의합니다.
 */
interface MainThreadConsoleShape : Disposable {
    /**
     * Extension Host로부터 받은 로그 메시지를 처리합니다.
     * @param msg 로그 메시지 정보를 담고 있는 Map 객체
     */
    fun logExtensionHostMessage(msg: Map<String, Any>)
    
    /**
     * 리소스를 해제합니다. (Disposable 인터페이스 구현)
     */
    override fun dispose()
}

/**
 * `MainThreadConsoleShape` 인터페이스의 구현 클래스입니다.
 * Extension Host로부터 받은 로그를 IntelliJ의 로거(Logger)를 사용하여 기록합니다.
 */
class MainThreadConsole : MainThreadConsoleShape {
    private val logger = Logger.getInstance(MainThreadConsole::class.java)

    /**
     * 전달받은 Map 형태의 로그 메시지를 파싱하여 IntelliJ 로그 시스템에 기록합니다.
     * @param msg RPC를 통해 전달된 로그 데이터
     */
    override fun logExtensionHostMessage(msg: Map<String, Any>) {
        // 메시지 Map에서 타입, 심각도, 인자 등을 추출합니다.
        val type = msg["type"]
        val severity = msg["severity"]
        val arguments = msg["arguments"]?.let { args ->
            // 인자가 리스트 형태이면 쉼표로 구분된 문자열로 합칩니다.
            if (args is List<*>) {
                args.joinToString(", ") { it.toString() }
            } else {
                args.toString()
            }
        } ?: return // 인자가 없으면 아무것도 하지 않습니다.

        try {
            // 로그의 심각도(severity)에 따라 다른 레벨의 로그를 기록합니다.
            // 현재는 'warn'과 'error'만 처리하고 나머지는 주석 처리되어 있습니다.
            when (severity) {
//                "log", "info" -> logger.info("[Extension Host] $arguments")
                "warn" -> logger.warn("[Extension Host] $arguments")
                "error" -> logger.warn("[Extension Host] ERROR: $arguments") // error도 warn으로 기록 중
//                "debug" -> logger.debug("[Extension Host] $arguments")
//                else -> logger.info("[Extension Host] $arguments")
            }
        } catch (e: Exception) {
            logger.error("Extension Host 로그 메시지 처리 실패", e)
        }
    }

    /**
     * 리소스를 해제할 때 호출됩니다.
     */
    override fun dispose() {
        logger.info("Disposing MainThreadConsole")
    }
}
