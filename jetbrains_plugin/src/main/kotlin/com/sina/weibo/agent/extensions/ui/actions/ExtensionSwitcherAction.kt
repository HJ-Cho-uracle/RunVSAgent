// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.extensions.core.ExtensionSwitcher
import com.sina.weibo.agent.extensions.ui.ExtensionSwitcherDialog

/**
 * 확장 전환 액션입니다.
 * 확장 전환 기능을 빠르게 접근할 수 있도록 제공합니다.
 * IntelliJ의 메뉴나 툴바에 등록되어 사용자가 확장을 전환할 수 있도록 합니다.
 */
class ExtensionSwitcherAction : AnAction() {
    
    /**
     * 액션이 수행될 때 호출됩니다.
     * 확장 전환 다이얼로그를 띄웁니다.
     * @param e 액션 이벤트 객체
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return // 현재 프로젝트 가져오기
        
        // 확장 전환 작업이 이미 진행 중인지 확인합니다.
        val extensionSwitcher = ExtensionSwitcher.getInstance(project)
        if (extensionSwitcher.isSwitching()) {
            Messages.showInfoMessage(
                "확장 전환 작업이 이미 진행 중입니다. 완료될 때까지 기다려주세요.",
                "전환 진행 중"
            )
            return
        }
        
        // 확장 전환 다이얼로그를 표시합니다.
        val dialog = ExtensionSwitcherDialog(project)
        dialog.show()
    }

    /**
     * 이 액션의 업데이트에 사용될 스레드를 지정합니다.
     * 백그라운드 스레드(BGT)에서 업데이트가 발생하도록 설정합니다.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * 액션의 UI 상태(가시성, 활성화 여부, 텍스트 등)를 업데이트합니다.
     * @param e 액션 이벤트 객체
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation // 액션의 UI 표현 객체
        
        if (project == null) {
            presentation.isEnabledAndVisible = false // 프로젝트가 없으면 액션을 숨깁니다.
            return
        }
        
        // 사용 가능한 확장 목록을 확인합니다.
        val extensionManager = ExtensionManager.getInstance(project)
        val availableProviders = extensionManager.getAvailableProviders()
        
        // VSIX 파일 업로드를 지원하므로, 확장이 하나만 있어도 버튼을 표시합니다.
        if (availableProviders.isEmpty()) {
            presentation.isEnabledAndVisible = false
            presentation.text = "사용 가능한 확장 없음"
            return
        }
        
        // 현재 활성화된 확장의 이름을 가져와 버튼 텍스트에 표시합니다.
        val currentProvider = extensionManager.getCurrentProvider()
        val currentExtensionName = currentProvider?.getDisplayName() ?: "알 수 없음"
        
        // 액션이 표시되는 위치에 따라 다른 텍스트를 사용합니다.
        when (e.place) {
            ActionPlaces.TOOLBAR -> {
                presentation.text = "전환 ($currentExtensionName)"
                presentation.description = "다른 확장 제공자로 전환하거나 VSIX 업로드"
            }
            ActionPlaces.MAIN_MENU -> {
                presentation.text = "확장 제공자 전환"
                presentation.description = "다른 확장 제공자로 전환하거나 VSIX 업로드"
            }
            else -> {
                presentation.text = "확장 전환 ($currentExtensionName)"
                presentation.description = "다른 확장 제공자로 전환하거나 VSIX 업로드"
            }
        }
        
        presentation.isEnabledAndVisible = true // 액션을 표시하고 활성화합니다.
        
        // 확장 전환 작업이 진행 중인 경우 UI를 업데이트합니다.
        val extensionSwitcher = ExtensionSwitcher.getInstance(project)
        if (extensionSwitcher.isSwitching()) {
            presentation.text = "전환 중..."
            presentation.description = "확장 전환 작업 진행 중..."
            presentation.isEnabled = false // 전환 중에는 버튼을 비활성화합니다.
        }
    }
}
