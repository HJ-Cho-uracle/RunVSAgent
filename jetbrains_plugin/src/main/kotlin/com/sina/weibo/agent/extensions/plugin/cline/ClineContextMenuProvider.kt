// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.cline

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.ui.contextmenu.ExtensionContextMenuProvider
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuConfiguration
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuActionType

/**
 * Cline 확장 컨텍스트 메뉴 제공자입니다.
 * Cline AI 확장에 특화된 컨텍스트 메뉴 액션(동작)을 제공합니다.
 */
class ClineContextMenuProvider : ExtensionContextMenuProvider {
    
    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "cline"
    
    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Cline AI"
    
    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "Cline AI에 특화된 컨텍스트 메뉴를 제공하는 AI 기반 코딩 어시스턴트"
    
    /**
     * Cline 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: Cline 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }
    
    /**
     * Cline 확장을 위한 컨텍스트 메뉴 액션 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return `AnAction` 객체 리스트 형태의 컨텍스트 메뉴 액션 목록 (현재는 비어 있음)
     */
    override fun getContextMenuActions(project: Project): List<AnAction> {
        return listOf(
            // TODO: Cline AI에 특화된 AnAction 구현체를 여기에 추가할 수 있습니다.
        )
    }
    
    /**
     * Cline 확장을 위한 컨텍스트 메뉴 구성 정보를 반환합니다.
     */
    override fun getContextMenuConfiguration(): ContextMenuConfiguration {
        return ClineContextMenuConfiguration()
    }
    
    /**
     * Cline 컨텍스트 메뉴 구성 클래스입니다.
     * 어떤 `ContextMenuActionType`이 표시되어야 하는지 정의합니다.
     */
    private class ClineContextMenuConfiguration : ContextMenuConfiguration {
        /**
         * 특정 컨텍스트 메뉴 액션 타입이 표시되어야 하는지 여부를 반환합니다.
         */
        override fun isActionVisible(actionType: ContextMenuActionType): Boolean {
            return when (actionType) {
                ContextMenuActionType.EXPLAIN_CODE, // 코드 설명
                ContextMenuActionType.FIX_CODE,     // 코드 수정
                ContextMenuActionType.IMPROVE_CODE, // 코드 개선
                ContextMenuActionType.ADD_TO_CONTEXT, // 컨텍스트에 추가
                ContextMenuActionType.NEW_TASK      // 새 작업
                -> true // 이 액션들은 표시
                ContextMenuActionType.FIX_LOGIC -> false // Cline은 별도의 로직 수정 기능을 가지지 않음
            }
        }
        
        /**
         * 표시될 컨텍스트 메뉴 액션 타입 목록을 반환합니다.
         */
        override fun getVisibleActions(): List<ContextMenuActionType> {
            return listOf(
                ContextMenuActionType.EXPLAIN_CODE,
                ContextMenuActionType.FIX_CODE,
                ContextMenuActionType.IMPROVE_CODE,
                ContextMenuActionType.ADD_TO_CONTEXT,
                ContextMenuActionType.NEW_TASK
            )
        }
    }
}
