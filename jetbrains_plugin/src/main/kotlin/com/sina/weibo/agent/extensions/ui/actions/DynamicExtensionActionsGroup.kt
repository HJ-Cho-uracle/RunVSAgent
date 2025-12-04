// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.common.ExtensionChangeListener
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.extensions.plugin.cline.ClineButtonProvider
import com.sina.weibo.agent.extensions.plugin.costrict.CostrictCodeButtonProvider
import com.sina.weibo.agent.extensions.plugin.kilo.KiloCodeButtonProvider
import com.sina.weibo.agent.extensions.plugin.roo.RooCodeButtonProvider
import com.sina.weibo.agent.extensions.ui.buttons.ExtensionButtonProvider

/**
 * 동적 확장 액션 그룹입니다.
 * 현재 활성화된 확장 타입에 따라 다른 버튼들을 UI에 표시합니다.
 * `DefaultActionGroup`을 상속받아 IntelliJ의 액션 시스템에 통합됩니다.
 */
class DynamicExtensionActionsGroup : DefaultActionGroup(), DumbAware, ActionUpdateThreadAware, ExtensionChangeListener {

    private val logger = Logger.getInstance(DynamicExtensionActionsGroup::class.java)

    // --- 캐시 변수 ---
    private var cachedButtonProvider: ExtensionButtonProvider? = null // 캐시된 버튼 제공자
    private var cachedExtensionId: String? = null // 캐시된 확장 ID
    private var cachedActions: List<AnAction>? = null // 캐시된 액션 목록

    /**
     * 액션 그룹을 현재 컨텍스트와 확장 타입에 따라 업데이트합니다.
     * 메뉴/툴바가 표시될 때마다 호출됩니다.
     *
     * @param e 컨텍스트 정보를 포함하는 액션 이벤트
     */
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        if (project == null) {
            e.presentation.isVisible = false // 프로젝트가 없으면 그룹을 숨깁니다.
            return
        }

        try {
            val extensionManager = ExtensionManager.getInstance(project)
            val currentProvider = extensionManager.getCurrentProvider()

            if (currentProvider != null) {
                val extensionId = currentProvider.getExtensionId()

                // 캐시된 확장 ID가 다르거나 액션이 캐시되지 않았으면 업데이트합니다.
                if (cachedExtensionId != extensionId || cachedActions == null) {
                    updateCachedActions(currentProvider, project)
                }

                // 캐시된 액션들을 사용하여 그룹을 구성합니다.
                if (cachedActions != null) {
                    removeAll() // 기존 액션 모두 제거
                    cachedActions!!.forEach { action ->
                        add(action) // 캐시된 액션 추가
                    }
                    e.presentation.isVisible = true // 그룹을 표시합니다.
                    logger.debug("확장 '$extensionId'에 대해 캐시된 액션 사용 중")
                }
            } else {
                e.presentation.isVisible = false // 현재 확장 제공자가 없으면 그룹을 숨깁니다.
                logger.debug("현재 확장 제공자가 없어 동적 액션 숨김")
            }
        } catch (exception: Exception) {
            logger.warn("동적 액션 로드 실패", exception)
            e.presentation.isVisible = false
        }
    }

    /**
     * 현재 확장 제공자에 따라 캐시된 액션들을 업데이트합니다.
     * @param provider 현재 확장 제공자
     * @param project 현재 프로젝트
     */
    private fun updateCachedActions(provider: ExtensionProvider, project: Project) {
        val extensionId = provider.getExtensionId()

        // 확장의 ID에 따라 적절한 `ExtensionButtonProvider` 인스턴스를 생성합니다.
        val buttonProvider = when (extensionId) {
            "roo-code" -> RooCodeButtonProvider()
            "cline" -> ClineButtonProvider()
            "kilo-code" -> KiloCodeButtonProvider()
            "costrict" -> CostrictCodeButtonProvider()
            else -> null
        }

        if (buttonProvider != null) {
            // 버튼 제공자로부터 액션 목록을 가져와 캐시합니다.
            val actions = buttonProvider.getButtons(project)

            // 캐시 변수들을 업데이트합니다.
            cachedButtonProvider = buttonProvider
            cachedExtensionId = extensionId
            cachedActions = actions

            logger.debug("확장 '$extensionId'에 대해 캐시된 액션 업데이트됨, 개수: ${actions.size}")
        }
    }

    /**
     * (현재 사용되지 않음) 동적 액션을 이 액션 그룹에 로드합니다.
     * `updateCachedActions` 메소드로 대체되었습니다.
     */
    private fun loadDynamicActions(provider: ExtensionProvider, project: Project) {
        // ... (이전 로직, 현재는 updateCachedActions가 처리)
    }

    /**
     * 이 액션의 업데이트에 사용될 스레드를 지정합니다.
     * 백그라운드 스레드(BGT)에서 업데이트가 발생하도록 설정합니다.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * 현재 확장이 변경되었을 때 호출됩니다.
     * `ExtensionChangeListener` 인터페이스의 일부입니다.
     *
     * @param newExtensionId 새로 활성화된 확장의 ID
     */
    override fun onExtensionChanged(newExtensionId: String) {
        logger.info("확장이 '$newExtensionId'(으)로 변경됨, 동적 액션 새로고침")

        // 참고: UI가 다음에 표시될 때 액션 그룹은 자동으로 새로고침됩니다.
        // 여기에서 수동으로 업데이트를 트리거할 필요는 없습니다.
    }
}
