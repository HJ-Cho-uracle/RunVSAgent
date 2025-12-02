// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.kilo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.sina.weibo.agent.actions.*
import com.sina.weibo.agent.extensions.ui.buttons.ExtensionButtonProvider
import com.sina.weibo.agent.extensions.ui.buttons.ButtonType
import com.sina.weibo.agent.extensions.ui.buttons.ButtonConfiguration
import com.sina.weibo.agent.webview.WebViewManager

/**
 * Kilo Code 확장 버튼 제공자입니다.
 * Kilo Code 확장에 특화된 버튼 구성을 제공합니다.
 */
class KiloCodeButtonProvider : ExtensionButtonProvider {
    
    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "kilo-code"
    
    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Kilo Code"
    
    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "고급 기능을 갖춘 AI 기반 코드 어시스턴트"
    
    /**
     * Kilo Code 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: API 키, 네트워크 연결 등 Kilo Code 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }
    
    /**
     * Kilo Code 확장을 위한 버튼 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트 (향후 확장성을 위해 유지)
     * @return `AnAction` 객체 리스트 형태의 버튼 목록
     */
    override fun getButtons(project: Project): List<AnAction> {
        return listOf(
            createPlusButton(),
            createPromptsButton(),
            createMcpButton(),
            createHistoryButton(),
            createMarketplaceButton(),
            createSettingsButton()
        )
    }
    
    /**
     * "새 작업" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.plusButtonClicked` 명령을 실행합니다.
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
                logger.info("🔍 Kilo Code Plus 버튼 클릭됨, 명령: kilo-code.plusButtonClicked")
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
                                executeCommand("kilo-code.plusButtonClicked", project, hasArgs = false)
                                logger.info("✅ 명령 실행 성공")
                            } else {
                                logger.warn("⚠️ WebView 인스턴스를 사용할 수 없습니다.")
                                // 사용자에게 친화적인 경고 메시지를 표시합니다.
                                Messages.showWarningDialog(
                                    project,
                                    "활성화된 WebView를 찾을 수 없습니다. Kilo Code 확장이 제대로 초기화되었는지 확인해주세요.",
                                    "WebView 사용 불가"
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
     * "프롬프트" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.promptsButtonClicked` 명령을 실행합니다.
     */
    private fun createPromptsButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.General.Information
                templatePresentation.text = "프롬프트"
                templatePresentation.description = "프롬프트"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("프롬프트 버튼 클릭됨")
                executeCommand("kilo-code.promptsButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "MCP" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.mcpButtonClicked` 명령을 실행합니다.
     */
    private fun createMcpButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.Webreferences.Server
                templatePresentation.text = "MCP"
                templatePresentation.description = "MCP"
            }

            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("MCP 버튼 클릭됨")
                executeCommand("kilo-code.mcpButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "기록" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.historyButtonClicked` 명령을 실행합니다.
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
                executeCommand("kilo-code.historyButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "마켓플레이스" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.marketplaceButtonClicked` 명령을 실행합니다.
     */
    private fun createMarketplaceButton(): AnAction {
        return object : AnAction() {
            init {
                templatePresentation.icon = AllIcons.Nodes.ModuleGroup
                templatePresentation.text = "마켓플레이스"
                templatePresentation.description = "마켓플레이스"
            }
            
            override fun actionPerformed(e: AnActionEvent) {
                Logger.getInstance(this::class.java).info("마켓플레이스 버튼 클릭됨")
                executeCommand("kilo-code.marketplaceButtonClicked", e.project, hasArgs = false)
            }
        }
    }

    /**
     * "설정" 버튼을 생성합니다.
     * 클릭 시 `kilo-code.settingsButtonClicked` 명령을 실행합니다.
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
                executeCommand("kilo-code.settingsButtonClicked", e.project, hasArgs = false)
            }
        }
    }
    
    /**
     * Kilo Code 확장을 위한 버튼 구성 정보를 반환합니다.
     */
    override fun getButtonConfiguration(): ButtonConfiguration {
        return KiloCodeButtonConfiguration()
    }
    
    /**
     * Kilo Code 버튼 구성 클래스입니다.
     * 모든 버튼이 표시되도록 설정합니다. (모든 기능을 제공하는 확장)
     */
    private class KiloCodeButtonConfiguration : ButtonConfiguration {
        /**
         * 특정 버튼 타입이 표시되어야 하는지 여부를 반환합니다.
         * Kilo Code의 경우 모든 버튼이 표시됩니다.
         */
        override fun isButtonVisible(buttonType: ButtonType): Boolean {
            return true // 모든 버튼이 Kilo Code에서 표시됩니다.
        }
        
        /**
         * 표시될 버튼 타입 목록을 반환합니다.
         * Kilo Code의 경우 모든 버튼 타입을 반환합니다.
         */
        override fun getVisibleButtons(): List<ButtonType> {
            return ButtonType.values().toList()
        }
    }
}
