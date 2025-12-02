// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.sina.weibo.agent.actors.MainThreadClipboard
import com.sina.weibo.agent.commands.CommandRegistry
import com.sina.weibo.agent.commands.ICommand
import org.jetbrains.plugins.terminal.TerminalToolWindowManager


/**
 * 터미널 API 관련 명령들을 등록합니다.
 * 현재는 터미널 출력을 클립보드에 복사하는 `workbench.action.terminal.copySelection` 명령을 등록합니다.
 *
 * @param project 현재 IntelliJ 프로젝트
 * @param registry 명령을 등록할 `CommandRegistry` 인스턴스
 */
fun registerTerminalAPICommands(project: Project, registry: CommandRegistry) {
    registry.registerCommand(
        object : ICommand {
            override fun getId(): String {
                return "workbench.action.terminal.copySelection" // 커맨드의 고유 ID
            }
            override fun getMethod(): String {
                return "workbench_action_terminal_copySelection" // 이 커맨드가 실행될 때 호출될 메소드 이름
            }

            override fun handler(): Any {
                return TerminalAPICommands(project) // 커맨드 로직을 담고 있는 핸들러 객체
            }

            override fun returns(): String? {
                return "void" // 반환 타입
            }
        }
    )
}

/**
 * 터미널 API 명령(예: 터미널 출력 클립보드 복사)을 처리하는 클래스입니다.
 */
class TerminalAPICommands(val project: Project) {
    private val logger = Logger.getInstance(TerminalAPICommands::class.java)
    private val clipboard = MainThreadClipboard() // 클립보드 접근을 위한 서비스

    /**
     * 현재 터미널의 마지막 명령어 출력을 클립보드에 복사합니다.
     * `workbench.action.terminal.copySelection` 커맨드가 호출될 때 실행되는 실제 로직입니다.
     *
     * @return 작업 완료 후 null
     */
    suspend fun workbench_action_terminal_copySelection(): Any? {
        logger.info("터미널 출력을 클립보드에 복사 중")
        
        val textToCopy = try {
            getTerminalText() ?: "" // 터미널 텍스트 가져오기
        } catch (e: Exception) {
            logger.error("터미널 출력을 클립보드에 복사 실패", e)
            ""
        }
        
        clipboard.writeText(textToCopy) // 클립보드에 텍스트 쓰기
        if (textToCopy.isNotEmpty()) {
            logger.info("터미널 출력을 클립보드에 성공적으로 복사했습니다.")
        } else {
            logger.info("빈 터미널 출력을 클립보드에 복사했습니다.")
        }
        
        return null
    }
    
    /**
     * 현재 활성화된 터미널의 텍스트 내용을 가져옵니다.
     *
     * @return 터미널 텍스트 내용, 가져오기 실패 시 null
     */
    private fun getTerminalText(): String? {
        // "Terminal" 툴 윈도우를 가져옵니다.
        val window = ToolWindowManager.getInstance(project)
            .getToolWindow("Terminal")
            ?: return null
            
        // 현재 선택된 콘텐츠(터미널 탭)를 가져옵니다.
        val selected = window.getContentManager().getSelectedContent()
            ?: return null
            
        // 선택된 콘텐츠로부터 터미널 위젯을 가져옵니다.
        val widget = TerminalToolWindowManager.getWidgetByContent(selected)
            ?: return null
            
        // 위젯의 텍스트 내용을 반환합니다. 내용이 비어있지 않은 경우에만 반환합니다.
        return widget.text.takeIf { it.isNotEmpty() }
    }
    
}
