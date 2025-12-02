// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.contextmenu

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.extensions.plugin.cline.ClineContextMenuProvider
import com.sina.weibo.agent.extensions.plugin.roo.RooCodeContextMenuProvider
import com.sina.weibo.agent.extensions.plugin.kilo.KiloCodeContextMenuProvider
import com.sina.weibo.agent.extensions.plugin.costrict.CostrictCodeContextMenuProvider

/**
 * 동적 컨텍스트 메뉴 관리자입니다.
 * 현재 활성화된 확장 타입에 따라 어떤 컨텍스트 메뉴 액션을 사용할 수 있는지 제어합니다.
 * `DynamicExtensionContextMenuGroup`과 함께 동적 컨텍스트 메뉴 기능을 제공합니다.
 */
@Service(Service.Level.PROJECT)
class DynamicContextMenuManager(private val project: Project) {
    
    private val logger = Logger.getInstance(DynamicContextMenuManager::class.java)
    private val extensionManager = ExtensionManager.getInstance(project)

    // 현재 활성화된 확장의 ID
    @Volatile
    private var currentExtensionId: String? = null
    
    companion object {
        /**
         * `DynamicContextMenuManager`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): DynamicContextMenuManager {
            return project.getService(DynamicContextMenuManager::class.java)
                ?: error("DynamicContextMenuManager 서비스를 찾을 수 없습니다.")
        }
    }
    
    /**
     * 동적 컨텍스트 메뉴 관리자를 초기화합니다.
     * `ExtensionManager`로부터 현재 활성화된 확장을 가져와 `currentExtensionId`를 설정합니다.
     */
    fun initialize() {
        logger.info("동적 컨텍스트 메뉴 관리자 초기화 중")
        
        try {
            val currentProvider = extensionManager.getCurrentProvider()
            currentExtensionId = currentProvider?.getExtensionId()
            logger.info("확장 '$currentExtensionId'로 동적 컨텍스트 메뉴 관리자 초기화됨")
        } catch (e: Exception) {
            logger.warn("동적 컨텍스트 메뉴 관리자 초기화 실패", e)
        }
    }
    
    /**
     * 현재 확장을 설정하고 컨텍스트 메뉴 구성을 업데이트합니다.
     * @param extensionId 새로 설정할 확장의 ID
     */
    fun setCurrentExtension(extensionId: String) {
        logger.info("현재 확장을 '$extensionId'(으)로 설정 중")
        currentExtensionId = extensionId
        
        // 모든 컨텍스트 메뉴를 새로고침하여 변경사항을 반영합니다.
        refreshContextMenus()
    }
    
    /**
     * 현재 활성화된 확장의 ID를 가져옵니다.
     */
    fun getCurrentExtensionId(): String? {
        return extensionManager.getCurrentProvider()?.getExtensionId()
    }
    
    /**
     * 현재 확장에 대한 컨텍스트 메뉴 구성(`ContextMenuConfiguration`)을 가져옵니다.
     * @return 현재 확장의 `ContextMenuConfiguration` 객체, 없으면 기본 구성 반환
     */
    fun getContextMenuConfiguration(): ContextMenuConfiguration {
        val contextMenuProvider = getContextMenuProvider(getCurrentExtensionId())
        return contextMenuProvider?.getContextMenuConfiguration() ?: DefaultContextMenuConfiguration()
    }
    
    /**
     * 현재 확장에 대한 컨텍스트 메뉴 액션 목록을 가져옵니다.
     * @return `AnAction` 객체 리스트 형태의 컨텍스트 메뉴 액션 목록
     */
    fun getContextMenuActions(): List<com.intellij.openapi.actionSystem.AnAction> {
        val contextMenuProvider = getContextMenuProvider(getCurrentExtensionId())
        return contextMenuProvider?.getContextMenuActions(project) ?: emptyList()
    }
    
    /**
     * 지정된 확장에 대한 `ExtensionContextMenuProvider` 인스턴스를 가져옵니다.
     * @param extensionId 확장의 ID
     * @return `ExtensionContextMenuProvider` 인스턴스 또는 찾지 못하면 null
     */
    private fun getContextMenuProvider(extensionId: String?): ExtensionContextMenuProvider? {
        if (extensionId == null) return null
        
        return when (extensionId) {
            "roo-code" -> RooCodeContextMenuProvider()
            "cline" -> ClineContextMenuProvider()
            "kilo-code" -> KiloCodeContextMenuProvider()
            "costrict" -> CostrictCodeContextMenuProvider()
            // TODO: 다른 컨텍스트 메뉴 제공자 구현 시 여기에 추가
            else -> null
        }
    }
    
    /**
     * 현재 확장에 대해 특정 컨텍스트 메뉴 액션이 표시되어야 하는지 확인합니다.
     * @param actionType 확인할 컨텍스트 메뉴 액션의 타입
     * @return 액션이 표시되어야 하면 true
     */
    fun isActionVisible(actionType: ContextMenuActionType): Boolean {
        val config = getContextMenuConfiguration()
        return config.isActionVisible(actionType)
    }
    
    /**
     * 모든 컨텍스트 메뉴를 새로고침하여 현재 구성을 반영합니다.
     * IntelliJ 플랫폼의 UI 새로고침 메커니즘을 사용합니다.
     */
    private fun refreshContextMenus() {
        try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                try {
                    val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    
                    // 동적 컨텍스트 메뉴 액션 그룹을 가져옵니다.
                    val dynamicGroup = actionManager.getAction("RunVSAgent.DynamicExtensionContextMenu")
                    dynamicGroup?.let { group ->
                        // 플랫폼에 UI 새로고침을 알립니다.
                        // 플랫폼은 자동으로 적절한 업데이트 메소드를 호출합니다.
                        logger.debug("동적 컨텍스트 메뉴 그룹에 대한 UI 새로고침 트리거")
                    }
                    
                    logger.debug("확장 '$currentExtensionId'에 대한 컨텍스트 메뉴 새로고침 예약됨")
                } catch (e: Exception) {
                    logger.warn("컨텍스트 메뉴 새로고침 예약 실패", e)
                }
            }
        } catch (e: Exception) {
            logger.warn("컨텍스트 메뉴 새로고침 실패", e)
        }
    }
    
    /**
     * 동적 컨텍스트 메뉴 관리자를 해제합니다.
     */
    fun dispose() {
        logger.info("동적 컨텍스트 메뉴 관리자 해제 중")
        currentExtensionId = null
    }
}

/**
 * 기본 컨텍스트 메뉴 구성 클래스입니다.
 * 최소한의 액션만 표시되도록 설정합니다.
 */
class DefaultContextMenuConfiguration : ContextMenuConfiguration {
    override fun isActionVisible(actionType: ContextMenuActionType): Boolean {
        return when (actionType) {
            ContextMenuActionType.EXPLAIN_CODE,
            ContextMenuActionType.ADD_TO_CONTEXT -> true // 이 액션들은 표시
            ContextMenuActionType.FIX_CODE,
            ContextMenuActionType.FIX_LOGIC,
            ContextMenuActionType.IMPROVE_CODE,
            ContextMenuActionType.NEW_TASK -> false // 이 액션들은 숨김
        }
    }
    
    override fun getVisibleActions(): List<ContextMenuActionType> {
        return listOf(
            ContextMenuActionType.EXPLAIN_CODE,
            ContextMenuActionType.ADD_TO_CONTEXT
        )
    }
}
