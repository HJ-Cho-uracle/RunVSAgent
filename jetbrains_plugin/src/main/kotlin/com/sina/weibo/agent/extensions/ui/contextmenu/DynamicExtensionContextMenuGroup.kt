// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.contextmenu

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * 동적 확장 컨텍스트 메뉴 액션 그룹입니다.
 * 현재 활성화된 확장에 따라 마우스 오른쪽 클릭 메뉴에 표시되는 동적 컨텍스트 메뉴 액션을 관리합니다.
 * `DefaultActionGroup`을 상속받아 IntelliJ의 액션 시스템에 통합됩니다.
 */
class DynamicExtensionContextMenuGroup : DefaultActionGroup(), DumbAware, ActionUpdateThreadAware {

    /**
     * 현재 확장의 컨텍스트 메뉴 액션을 제공하는 관리자입니다.
     */
    private var contextMenuManager: DynamicContextMenuManager? = null

    /**
     * 액션 그룹을 현재 컨텍스트와 확장에 따라 업데이트합니다.
     * 메뉴가 표시될 때마다 호출됩니다.
     *
     * @param e 컨텍스트 정보를 포함하는 액션 이벤트
     */
    override fun update(e: AnActionEvent) {
        removeAll() // 기존 액션 모두 제거

        // 에디터와 선택된 텍스트가 있는지 확인합니다.
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true

        if (hasSelection) {
            loadDynamicContextMenuActions(e) // 선택된 텍스트가 있으면 동적 액션을 로드합니다.
        }

        // 액션 그룹의 가시성을 설정합니다. (선택된 텍스트가 있을 때만 표시)
        e.presentation.isVisible = hasSelection
    }

    /**
     * 현재 확장에 따라 동적 컨텍스트 메뉴 액션을 이 액션 그룹에 로드합니다.
     *
     * @param e 컨텍스트 정보를 포함하는 액션 이벤트
     */
    private fun loadDynamicContextMenuActions(e: AnActionEvent) {
        val project = e.project ?: return // 프로젝트가 없으면 반환

        // 컨텍스트 메뉴 관리자를 가져오거나 초기화합니다.
        if (contextMenuManager == null) {
            try {
                contextMenuManager = DynamicContextMenuManager.getInstance(project)
                contextMenuManager?.initialize()
            } catch (ex: Exception) {
                // 관리자를 사용할 수 없으면 기본 액션으로 대체합니다.
                return
            }
        }

        // 현재 확장에서 제공하는 액션들을 가져와 그룹에 추가합니다.
        val actions = contextMenuManager?.getContextMenuActions() ?: emptyList()
        actions.forEach { action ->
            add(action)
        }
    }

    /**
     * 이 액션의 업데이트에 사용될 스레드를 지정합니다.
     * UI 관련 작업이므로 EDT(Event Dispatch Thread)를 사용합니다.
     *
     * @return 액션 업데이트에 사용할 스레드
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
