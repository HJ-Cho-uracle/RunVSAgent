// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.terminal.TerminalInstanceManager

/**
 * IntelliJ 메인 스레드에서 터미널 셸 통합(Shell Integration) 관련 작업을 처리하기 위한 인터페이스입니다.
 * 셸과 더 깊이 연동하여 특정 명령어를 실행하는 등의 기능을 정의합니다.
 */
interface MainThreadTerminalShellIntegrationShape : Disposable {
    /**
     * 지정된 터미널에서 특정 명령어를 실행합니다.
     * @param terminalId 명령어를 실행할 터미널의 숫자 ID
     * @param commandLine 실행할 명령어 라인
     */
    fun executeCommand(terminalId: Int, commandLine: String)
}

/**
 * `MainThreadTerminalShellIntegrationShape` 인터페이스의 구현 클래스입니다.
 * `TerminalInstanceManager`를 통해 터미널 인스턴스를 찾아 명령어를 전송합니다.
 *
 * @property project 현재 IntelliJ 프로젝트 컨텍스트
 */
class MainThreadTerminalShellIntegration(
    private val project: Project,
) : MainThreadTerminalShellIntegrationShape {
    private val logger = Logger.getInstance(MainThreadTerminalShellIntegration::class.java)

    // 터미널 인스턴스를 관리하는 프로젝트 레벨 서비스
    private val terminalManager = project.service<TerminalInstanceManager>()

    /**
     * 전달받은 명령어를 지정된 터미널에서 실행합니다.
     */
    override fun executeCommand(terminalId: Int, commandLine: String) {
        logger.info("🚀 셸 통합 명령어 실행: terminalId=$terminalId, commandLine='$commandLine'")

        try {
            // 숫자 ID로 터미널 인스턴스를 찾습니다.
            val terminalInstance = terminalManager.getTerminalInstance(terminalId)

            if (terminalInstance == null) {
                logger.warn("❌ 터미널 인스턴스를 찾을 수 없음: terminalId=$terminalId")
                return
            }

            logger.info("✅ 터미널 인스턴스 찾음: ${terminalInstance.extHostTerminalId}")

            // 터미널에 텍스트를 보내고 바로 실행하도록 합니다.
            terminalInstance.sendText(commandLine, shouldExecute = true)

            logger.info("✅ 터미널에 명령어 전송 완료: terminalId=$terminalId, command='$commandLine'")
        } catch (e: Exception) {
            logger.error("❌ 셸 통합 명령어 실행 실패: terminalId=$terminalId, command='$commandLine'", e)
        }
    }

    override fun dispose() {
        logger.info("🧹 MainThreadTerminalShellIntegration 해제 중")
    }
}
