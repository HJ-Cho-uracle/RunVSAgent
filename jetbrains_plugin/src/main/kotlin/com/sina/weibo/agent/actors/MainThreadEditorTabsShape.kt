// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.editor.EditorAndDocManager

/**
 * IntelliJ 메인 스레드에서 에디터 탭 관련 작업을 처리하기 위한 인터페이스입니다.
 * 탭을 이동하거나 닫는 등의 UI 조작 기능을 정의합니다.
 */
interface MainThreadEditorTabsShape {
    /**
     * 지정된 에디터 탭을 다른 위치로 이동시킵니다.
     * @param tabId 이동할 탭의 고유 ID
     * @param index 이동할 위치의 인덱스
     * @param viewColumn 탭이 속한 뷰 컬럼(에디터 그룹)
     * @param preserveFocus 이동 후에도 포커스를 유지할지 여부
     */
    fun moveTab(tabId: String, index: Int, viewColumn: Int, preserveFocus: Boolean?)

    /**
     * 지정된 ID 목록에 해당하는 에디터 탭들을 닫습니다.
     * @param tabIds 닫을 탭들의 고유 ID 리스트
     * @param preserveFocus 탭을 닫은 후 포커스를 유지할지 여부
     * @return 하나 이상의 탭이 성공적으로 닫혔으면 true
     */
    suspend fun closeTab(tabIds: List<String>, preserveFocus: Boolean?): Boolean

    /**
     * 지정된 ID 목록에 해당하는 에디터 탭 그룹 전체를 닫습니다.
     * @param groupIds 닫을 탭 그룹들의 ID 리스트
     * @param preservceFocus 그룹을 닫은 후 포커스를 유지할지 여부
     * @return 하나 이상의 그룹이 성공적으로 닫혔으면 true
     */
    suspend fun closeGroup(groupIds: List<Int>, preservceFocus: Boolean?): Boolean
}

/**
 * `MainThreadEditorTabsShape` 인터페이스의 구현 클래스입니다.
 * `EditorAndDocManager` 서비스를 사용하여 실제 탭 조작을 수행합니다.
 *
 * @property project 현재 IntelliJ 프로젝트 컨텍스트
 */
class MainThreadEditorTabs(val project: Project) : MainThreadEditorTabsShape {
    private val logger = Logger.getInstance(MainThreadEditorTabs::class.java)

    /**
     * 탭 이동 기능을 구현합니다. (현재는 로깅만 수행)
     */
    override fun moveTab(tabId: String, index: Int, viewColumn: Int, preserveFocus: Boolean?) {
        logger.info("moveTab 호출됨: $tabId")
        // TODO: 실제 탭 이동 로직 구현 필요
    }

    /**
     * 여러 탭을 닫는 기능을 구현합니다.
     */
    override suspend fun closeTab(tabIds: List<String>, preserveFocus: Boolean?): Boolean {
        logger.info("closeTab 호출됨: $tabIds")

        var closedAny = true
        // 모든 탭 ID에 대해 반복하여 닫기 이벤트를 트리거합니다.
        for (tabId in tabIds) {
            // EditorAndDocManager 서비스를 통해 해당 탭을 닫습니다.
            project.getService(EditorAndDocManager::class.java).closeTab(tabId)
            // 주석 처리된 코드는 과거에 사용되었던 로직으로 보이며, 현재는 직접 닫는 방식으로 변경되었습니다.
//            closedAny = tab?.triggerClose()?:false
//            if (closedAny){
//                project.getService(TabStateManager::class.java).removeTab(tabId)
//            }
        }

        return closedAny
    }

    /**
     * 여러 탭 그룹을 닫는 기능을 구현합니다.
     */
    override suspend fun closeGroup(groupIds: List<Int>, preservceFocus: Boolean?): Boolean {
        logger.info("closeGroup 호출됨: $groupIds")

        var closedAny = false
        // 모든 그룹 ID에 대해 반복하여 닫기 이벤트를 트리거합니다.
        for (groupId in groupIds) {
            // EditorAndDocManager 서비스를 통해 해당 그룹을 닫습니다.
            project.getService(EditorAndDocManager::class.java).closeGroup(groupId)
//            closedAny = group?.triggerClose()?:false
        }
        return closedAny
    }
}
