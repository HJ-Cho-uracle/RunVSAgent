// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.kilo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuActionType
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuConfiguration
import com.sina.weibo.agent.extensions.ui.contextmenu.ExtensionContextMenuProvider

/**
 * Kilo Code 확장 컨텍스트 메뉴 제공자입니다.
 * Kilo Code 확장에 특화된 컨텍스트 메뉴 액션(동작)을 제공합니다.
 */
class KiloCodeContextMenuProvider : ExtensionContextMenuProvider {

    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "kilo-code"

    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Kilo Code"

    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "고급 기능과 컨텍스트 메뉴를 갖춘 AI 기반 코드 어시스턴트"

    /**
     * Kilo Code 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: Kilo Code 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }

    /**
     * Kilo Code 확장을 위한 컨텍스트 메뉴 액션 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return `AnAction` 객체 리스트 형태의 컨텍스트 메뉴 액션 목록 (현재는 비어 있음)
     */
    override fun getContextMenuActions(project: Project): List<AnAction> {
        return listOf(
            // TODO: Kilo Code에 특화된 AnAction 구현체를 여기에 추가할 수 있습니다.
        )
    }

    /**
     * Kilo Code 확장을 위한 컨텍스트 메뉴 구성 정보를 반환합니다.
     */
    override fun getContextMenuConfiguration(): ContextMenuConfiguration {
        return KiloCodeContextMenuConfiguration()
    }

    /**
     * Kilo Code 컨텍스트 메뉴 구성 클래스입니다.
     * 모든 사용 가능한 액션이 표시되도록 설정합니다.
     */
    private class KiloCodeContextMenuConfiguration : ContextMenuConfiguration {
        /**
         * 특정 컨텍스트 메뉴 액션 타입이 표시되어야 하는지 여부를 반환합니다.
         * Kilo Code의 경우 모든 액션이 표시됩니다.
         */
        override fun isActionVisible(actionType: ContextMenuActionType): Boolean {
            return when (actionType) {
                ContextMenuActionType.EXPLAIN_CODE, // 코드 설명
                ContextMenuActionType.FIX_CODE, // 코드 수정
                ContextMenuActionType.FIX_LOGIC, // 논리 수정
                ContextMenuActionType.IMPROVE_CODE, // 코드 개선
                ContextMenuActionType.ADD_TO_CONTEXT, // 컨텍스트에 추가
                ContextMenuActionType.NEW_TASK, // 새 작업
                -> true // 이 액션들은 표시
            }
        }

        /**
         * 표시될 컨텍스트 메뉴 액션 타입 목록을 반환합니다.
         * Kilo Code의 경우 모든 액션 타입을 반환합니다.
         */
        override fun getVisibleActions(): List<ContextMenuActionType> {
            return listOf(
                ContextMenuActionType.EXPLAIN_CODE,
                ContextMenuActionType.FIX_CODE,
                ContextMenuActionType.FIX_LOGIC,
                ContextMenuActionType.IMPROVE_CODE,
                ContextMenuActionType.ADD_TO_CONTEXT,
                ContextMenuActionType.NEW_TASK,
            )
        }
    }
}
