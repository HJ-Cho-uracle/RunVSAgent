// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.cline

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.sina.weibo.agent.actions.executeCommand
import com.sina.weibo.agent.extensions.ui.buttons.ButtonConfiguration
import com.sina.weibo.agent.extensions.ui.buttons.ButtonType
import com.sina.weibo.agent.extensions.ui.buttons.ExtensionButtonProvider
import com.sina.weibo.agent.webview.WebViewManager

/**
 * Cline 확장 버튼 제공자입니다.
 * Cline AI 확장에 특화된 버튼 구성을 제공합니다.
 */
class ClineButtonProvider : ExtensionButtonProvider {

    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "cline"

    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Cline AI"

    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "Cline AI를 사용한 AI 기반 코드 완성 및 채팅"

    /**
     * Cline 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: API 키, 네트워크 연결 등 Cline 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }

    /**
     * Cline 확장을 위한 버튼 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트 (향후 확장성을 위해 유지)
     * @return `AnAction` 객체 리스트 형태의 버튼 목록
     */
    override fun getButtons(project: Project): List<AnAction> {
        return listOf(
            createPlusButton(),
            createMcpButton(),
            createHistoryButton(),
            createAccountButton(),
            createSettingsButton(),
        )
    }

    /**
     * "새 작업" 버튼을 생성합니다.
     * 클릭 시 `cline.plusButtonClicked` 명령을 실행합니다.
     */
    private fun createPlusButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.General.Add // 아이콘 설정
                templatePresentation.text = "새 작업" // 버튼 텍스트
                templatePresentation.description = "새 작업" // 툴팁 설명
            }

            override fun actionPerformed(e: AnActionEvent) {
                val logger = Logger.getInstance(this::class.java)
                logger.info("🔍 Cline Plus 버튼 클릭됨, 명령: cline.plusButtonClicked")
                logger.info("🔍 프로젝트: ${e.project?.name}")

                val project = e.project
                if (project != null) {
                    try {
                        val webViewManager = project.getService(WebViewManager::class.java)
                        if (webViewManager != null) {
                            val latestWebView = webViewManager.getLatestWebView()

                            if (latestWebView != null) {
                                logger.info("✅ WebView 인스턴스 사용 가능, 명령 실행 중...")
                                // `executeCommand` 헬퍼 함수를 사용하여 Extension Host에 명령을 보냅니다.
                                executeCommand("cline.plusButtonClicked", project, hasArgs = false)
                                logger.info("✅ 명령 실행 성공")
                            } else {
                                logger.warn("⚠️ WebView 인스턴스를 사용할 수 없습니다.")
                                // 사용자에게 친화적인 경고 메시지를 표시합니다.
                                Messages.showWarningDialog(
                                    project,
                                    "활성화된 WebView를 찾을 수 없습니다. Cline 확장이 제대로 초기화되었는지 확인해주세요.",
                                    "WebView 사용 불가",
                                )
                            }
                        } else {
                            logger.warn("⚠️ WebView Manager를 사용할 수 없습니다.")
                        }
                    } catch (e: Exception) {
                        logger.error("❌ WebView 상태 확인 중 오류 발생", e)
                    }
                } else {
                    logger.warn("⚠️ 프로젝트가 null입니다.")
                }
            }
        }
    }

    /**
     * "MCP" 버튼을 생성합니다.
     * 클릭 시 `cline.mcpButtonClicked` 명령을 실행합니다.
     */
    private fun createMcpButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.Webreferences.Server
                templatePresentation.text = "MCP"
                templatePresentation.description = "MCP"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("Mcp 버튼 클릭됨")
                executeCommand("cline.mcpButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "기록" 버튼을 생성합니다.
     * 클릭 시 `cline.historyButtonClicked` 명령을 실행합니다.
     */
    private fun createHistoryButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.Vcs.History
                templatePresentation.text = "기록"
                templatePresentation.description = "기록"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("기록 버튼 클릭됨")
                executeCommand("cline.historyButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "계정" 버튼을 생성합니다.
     * 클릭 시 `cline.accountButtonClicked` 명령을 실행합니다.
     */
    private fun createAccountButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.General.User
                templatePresentation.text = "계정"
                templatePresentation.description = "계정"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("계정 버튼 클릭됨")
                executeCommand("cline.accountButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "설정" 버튼을 생성합니다.
     * 클릭 시 `cline.settingsButtonClicked` 명령을 실행합니다.
     */
    private fun createSettingsButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.General.Settings
                templatePresentation.text = "설정"
                templatePresentation.description = "설정"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("설정 버튼 클릭됨")
                executeCommand("cline.settingsButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * Cline 확장을 위한 버튼 구성 정보를 반환합니다.
     */
    override fun getButtonConfiguration(): ButtonConfiguration {
        return ClineButtonConfiguration()
    }

    /**
     * Cline AI 버튼 구성 클래스입니다.
     * 어떤 버튼 타입이 표시되어야 하는지 정의합니다.
     */
    private class ClineButtonConfiguration : ButtonConfiguration {
        /**
         * 특정 버튼 타입이 표시되어야 하는지 여부를 반환합니다.
         */
        override fun isButtonVisible(buttonType: ButtonType): Boolean {
            return when (buttonType) {
                ButtonType.PLUS,
                ButtonType.PROMPTS,
                ButtonType.HISTORY,
                ButtonType.SETTINGS,
                -> true // 이 버튼들은 표시
                ButtonType.MCP,
                ButtonType.MARKETPLACE,
                -> false // 이 버튼들은 숨김
            }
        }

        /**
         * 표시될 버튼 타입 목록을 반환합니다.
         */
        override fun getVisibleButtons(): List<ButtonType> {
            return listOf(
                ButtonType.PLUS,
                ButtonType.PROMPTS,
                ButtonType.HISTORY,
                ButtonType.SETTINGS,
            )
        }
    }
}
