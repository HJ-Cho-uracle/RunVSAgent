// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.editor.EditorTabGroupDto
import com.sina.weibo.agent.editor.TabOperation

/**
 * Extension Host 에디터 탭 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 에디터 탭 및 탭 그룹 상태 변경을 수신하기 위해 사용됩니다.
 */
interface ExtHostEditorTabsProxy {
    /**
     * 에디터 탭 모델(탭 그룹 목록)의 전체 상태를 Extension Host에 전달합니다.
     * @param tabGroups 탭 그룹 정보를 담은 `EditorTabGroupDto` 리스트
     */
    fun acceptEditorTabModel(tabGroups: List<EditorTabGroupDto>)

    /**
     * 특정 탭 그룹의 업데이트(예: 탭 추가/제거)를 Extension Host에 전달합니다.
     * @param groupDto 업데이트된 탭 그룹 정보를 담은 `EditorTabGroupDto`
     */
    fun acceptTabGroupUpdate(groupDto: EditorTabGroupDto)

    /**
     * 탭 작업(예: 탭 열기, 닫기, 이동)을 Extension Host에 전달합니다.
     * @param operation 탭 작업 정보를 담은 `TabOperation` 객체
     */
    fun acceptTabOperation(operation: TabOperation)
}
