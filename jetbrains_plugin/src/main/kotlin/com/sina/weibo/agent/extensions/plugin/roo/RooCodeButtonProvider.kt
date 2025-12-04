// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.roo

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.actions.executeCommand
import com.sina.weibo.agent.extensions.ui.buttons.ButtonConfiguration
import com.sina.weibo.agent.extensions.ui.buttons.ButtonType
import com.sina.weibo.agent.extensions.ui.buttons.ExtensionButtonProvider

/**
 * Roo Code 확장 버튼 제공자입니다.
 * Roo Code 확장에 특화된 버튼 구성을 제공합니다.
 */
class RooCodeButtonProvider : ExtensionButtonProvider {

    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "roo-code"

    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Roo Code"

    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "모든 기능을 갖춘 AI 기반 코드 어시스턴트"

    /**
     * Roo Code 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: Roo Code 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }

    /**
     * Roo Code 확장을 위한 버튼 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트 (향후 확장성을 위해 유지)
     * @return `AnAction` 객체 리스트 형태의 버튼 목록
     */
    override fun getButtons(project: Project): List<AnAction> {
        return listOf(
            PlusButtonClickAction(),
            PromptsButtonClickAction(),
            MCPButtonClickAction(),
            HistoryButtonClickAction(),
            MarketplaceButtonClickAction(),
            SettingsButtonClickAction(),
        )
    }

    /**
     * Roo Code 확장을 위한 버튼 구성 정보를 반환합니다.
     */
    override fun getButtonConfiguration(): ButtonConfiguration {
        return RooCodeButtonConfiguration()
    }

    /**
     * Roo Code 버튼 구성 클래스입니다.
     * 모든 버튼이 표시되도록 설정합니다. (모든 기능을 제공하는 확장)
     */
    private class RooCodeButtonConfiguration : ButtonConfiguration {
        /**
         * 특정 버튼 타입이 표시되어야 하는지 여부를 반환합니다.
         * Roo Code의 경우 모든 버튼이 표시됩니다.
         */
        override fun isButtonVisible(buttonType: ButtonType): Boolean {
            return true // 모든 버튼이 Roo Code에서 표시됩니다.
        }

        /**
         * 표시될 버튼 타입 목록을 반환합니다.
         * Roo Code의 경우 모든 버튼 타입을 반환합니다.
         */
        override fun getVisibleButtons(): List<ButtonType> {
            return ButtonType.values().toList()
        }
    }

    /**
     * UI의 "새 작업" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class PlusButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(PlusButtonClickAction::class.java)
        private val commandId: String = "roo-cline.plusButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.General.Add // 아이콘 설정
            templatePresentation.text = "새 작업" // 버튼 텍스트
            templatePresentation.description = "새 작업" // 툴팁 설명
        }

        /**
         * "새 작업" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("새 작업 버튼 클릭됨")
            executeCommand(commandId, e.project) // VSCode 명령 실행
        }
    }

    /**
     * UI의 "프롬프트" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class PromptsButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(PromptsButtonClickAction::class.java)
        private val commandId: String = "roo-cline.promptsButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.General.Information
            templatePresentation.text = "프롬프트"
            templatePresentation.description = "프롬프트"
        }

        /**
         * "프롬프트" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("프롬프트 버튼 클릭됨")
            executeCommand(commandId, e.project)
        }
    }

    /**
     * UI의 "MCP 서버" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class MCPButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(MCPButtonClickAction::class.java)
        private val commandId: String = "roo-cline.mcpButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.Webreferences.Server
            templatePresentation.text = "MCP 서버"
            templatePresentation.description = "MCP 서버"
        }

        /**
         * "MCP 서버" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("MCP 버튼 클릭됨")
            executeCommand(commandId, e.project)
        }
    }

    /**
     * UI의 "기록" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class HistoryButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(HistoryButtonClickAction::class.java)
        private val commandId: String = "roo-cline.historyButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.Vcs.History
            templatePresentation.text = "기록"
            templatePresentation.description = "기록"
        }

        /**
         * "기록" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("기록 버튼 클릭됨")
            executeCommand(commandId, e.project)
        }
    }

    /**
     * UI의 "설정" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class SettingsButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(SettingsButtonClickAction::class.java)
        private val commandId: String = "roo-cline.settingsButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.General.Settings
            templatePresentation.text = "설정"
            templatePresentation.description = "설정"
        }

        /**
         * "설정" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("설정 버튼 클릭됨")
            executeCommand(commandId, e.project)
        }
    }

    /**
     * UI의 "마켓플레이스" 버튼 클릭을 처리하는 액션입니다.
     * 트리거될 때 해당 VSCode 명령을 실행합니다.
     */
    class MarketplaceButtonClickAction : AnAction() {
        private val logger: Logger = Logger.getInstance(MarketplaceButtonClickAction::class.java)
        private val commandId: String = "roo-cline.marketplaceButtonClicked" // 실행할 VSCode 명령 ID

        init {
            templatePresentation.icon = AllIcons.Actions.Install
            templatePresentation.text = "MCP 마켓플레이스"
            templatePresentation.description = "마켓플레이스"
        }

        /**
         * "마켓플레이스" 버튼이 클릭되었을 때 액션을 수행합니다.
         * @param e 컨텍스트 정보를 포함하는 액션 이벤트
         */
        override fun actionPerformed(e: AnActionEvent) {
            logger.info("마켓플레이스 버튼 클릭됨")
            executeCommand(commandId, e.project)
        }
    }
}
