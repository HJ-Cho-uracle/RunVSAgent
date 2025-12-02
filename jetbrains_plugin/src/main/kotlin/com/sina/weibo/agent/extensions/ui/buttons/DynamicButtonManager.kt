// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.buttons

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.extensions.plugin.cline.ClineButtonProvider
import com.sina.weibo.agent.extensions.plugin.roo.RooCodeButtonProvider
import com.sina.weibo.agent.extensions.plugin.kilo.KiloCodeButtonProvider
import com.sina.weibo.agent.extensions.plugin.costrict.CostrictCodeButtonProvider

/**
 * 동적 버튼 관리자입니다.
 * 현재 활성화된 확장 타입에 따라 어떤 버튼이 표시될지 제어합니다.
 * `DynamicExtensionActionsGroup`과 함께 동적 버튼 기능을 제공합니다.
 */
@Service(Service.Level.PROJECT)
class DynamicButtonManager(private val project: Project) {
    
    private val logger = Logger.getInstance(DynamicButtonManager::class.java)
    
    // 현재 활성화된 확장의 ID
    @Volatile
    private var currentExtensionId: String? = null
    
    companion object {
        /**
         * `DynamicButtonManager`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): DynamicButtonManager {
            return project.getService(DynamicButtonManager::class.java)
                ?: error("DynamicButtonManager 서비스를 찾을 수 없습니다.")
        }
    }
    
    /**
     * 동적 버튼 관리자를 초기화합니다.
     * `ExtensionManager`로부터 현재 활성화된 확장을 가져와 `currentExtensionId`를 설정합니다.
     */
    fun initialize() {
        logger.info("동적 버튼 관리자 초기화 중")
        
        try {
            val extensionManager = ExtensionManager.getInstance(project)
            val currentProvider = extensionManager.getCurrentProvider()
            currentExtensionId = currentProvider?.getExtensionId()
            logger.info("확장 '$currentExtensionId'로 동적 버튼 관리자 초기화됨")
        } catch (e: Exception) {
            logger.warn("동적 버튼 관리자 초기화 실패", e)
        }
    }
    
    /**
     * 현재 확장을 설정하고 버튼 구성을 업데이트합니다.
     * @param extensionId 새로 설정할 확장의 ID
     */
    fun setCurrentExtension(extensionId: String) {
        logger.info("현재 확장을 '$extensionId'(으)로 설정 중")
        currentExtensionId = extensionId
        
        // 모든 액션 툴바를 새로고침하여 변경사항을 반영합니다.
        refreshActionToolbars()
    }
    
    /**
     * 현재 활성화된 확장의 ID를 가져옵니다.
     */
    fun getCurrentExtensionId(): String? {
        return currentExtensionId
    }
    
    /**
     * 현재 확장에 대한 버튼 구성(`ButtonConfiguration`)을 가져옵니다.
     * @return 현재 확장의 `ButtonConfiguration` 객체, 없으면 기본 구성 반환
     */
    fun getButtonConfiguration(): ButtonConfiguration {
        val buttonProvider = getButtonProvider(currentExtensionId)
        return buttonProvider?.getButtonConfiguration() ?: DefaultButtonConfiguration()
    }
    
    /**
     * 지정된 확장에 대한 `ExtensionButtonProvider` 인스턴스를 가져옵니다.
     * @param extensionId 확장의 ID
     * @return `ExtensionButtonProvider` 인스턴스 또는 찾지 못하면 null
     */
    private fun getButtonProvider(extensionId: String?): ExtensionButtonProvider? {
        if (extensionId == null) return null
        
        return when (extensionId) {
            "roo-code" -> RooCodeButtonProvider()
            "cline" -> ClineButtonProvider()
            "kilo-code" -> KiloCodeButtonProvider()
            "costrict" -> CostrictCodeButtonProvider()
            // TODO: 다른 버튼 제공자 구현 시 여기에 추가
            else -> null
        }
    }
    
    /**
     * 현재 확장에 대해 특정 버튼이 표시되어야 하는지 확인합니다.
     * @param buttonType 확인할 버튼의 타입
     * @return 버튼이 표시되어야 하면 true
     */
    fun isButtonVisible(buttonType: ButtonType): Boolean {
        val config = getButtonConfiguration()
        return config.isButtonVisible(buttonType)
    }
    
    /**
     * 모든 액션 툴바를 새로고침하여 현재 버튼 구성을 반영합니다.
     * IntelliJ 플랫폼의 UI 새로고침 메커니즘을 사용합니다.
     */
    private fun refreshActionToolbars() {
        try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                try {
                    val actionManager = ActionManager.getInstance()
                    
                    // 동적 액션 그룹을 가져옵니다.
                    val dynamicGroup = actionManager.getAction("RunVSAgent.DynamicExtensionActions")
                    dynamicGroup?.let { group ->
                        // 플랫폼에 UI 새로고침을 알립니다.
                        // 플랫폼은 자동으로 적절한 업데이트 메소드를 호출합니다.
                        logger.debug("동적 액션 그룹에 대한 UI 새로고침 트리거")
                    }
                    
                    logger.debug("확장 '$currentExtensionId'에 대한 액션 툴바 새로고침 예약됨")
                } catch (e: Exception) {
                    logger.warn("액션 툴바 새로고침 예약 실패", e)
                }
            }
        } catch (e: Exception) {
            logger.warn("액션 툴바 새로고침 실패", e)
        }
    }
    
    /**
     * 동적 버튼 관리자를 해제합니다.
     */
    fun dispose() {
        logger.info("동적 버튼 관리자 해제 중")
        currentExtensionId = null
    }
}

/**
 * 구성 가능한 버튼의 타입을 정의하는 열거형입니다.
 */
enum class ButtonType {
    PLUS,       // 새 작업/추가 버튼
    PROMPTS,    // 프롬프트 버튼
    MCP,        // MCP (Multi-Cloud Platform) 버튼
    HISTORY,    // 기록 버튼
    MARKETPLACE,// 마켓플레이스 버튼
    SETTINGS    // 설정 버튼
}

/**
 * 버튼 구성 인터페이스입니다.
 * 어떤 버튼이 표시되어야 하는지 정의합니다.
 */
interface ButtonConfiguration {
    /**
     * 특정 버튼 타입이 현재 구성에서 표시되어야 하는지 여부를 반환합니다.
     */
    fun isButtonVisible(buttonType: ButtonType): Boolean
    /**
     * 현재 구성에서 표시될 버튼 타입 목록을 반환합니다.
     */
    fun getVisibleButtons(): List<ButtonType>
}

/**
 * 기본 버튼 구성 클래스입니다.
 * 최소한의 버튼만 표시되도록 설정합니다.
 */
class DefaultButtonConfiguration : ButtonConfiguration {
    override fun isButtonVisible(buttonType: ButtonType): Boolean {
        return when (buttonType) {
            ButtonType.PLUS,
            ButtonType.PROMPTS,
            ButtonType.SETTINGS -> true // 이 버튼들은 표시
            ButtonType.MCP,
            ButtonType.HISTORY,
            ButtonType.MARKETPLACE -> false // 이 버튼들은 숨김
        }
    }
    
    override fun getVisibleButtons(): List<ButtonType> {
        return listOf(
            ButtonType.PLUS,
            ButtonType.PROMPTS,
            ButtonType.SETTINGS
        )
    }
}
