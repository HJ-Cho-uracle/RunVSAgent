// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.editor.EditorPropertiesChangeData
import com.sina.weibo.agent.editor.TextEditorDiffInformation

/**
 * Extension Host 에디터 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 에디터 속성 변경, 위치 데이터, Diff 정보 등을 수신하기 위해 사용됩니다.
 */
interface ExtHostEditorsProxy {
    /**
     * 에디터 속성(옵션, 선택 영역, 가시 범위 등)의 변경사항을 Extension Host에 전달합니다.
     * @param id 변경된 에디터의 고유 ID
     * @param props 변경된 속성 데이터를 담은 `EditorPropertiesChangeData` 객체
     */
    fun acceptEditorPropertiesChanged(id: String, props: EditorPropertiesChangeData)

    /**
     * 에디터의 위치 데이터를 Extension Host에 전달합니다.
     * @param data 에디터 ID를 키로, 위치 정보를 값으로 하는 Map
     */
    fun acceptEditorPositionData(data: Map<String, Int>)

    /**
     * 에디터의 Diff 정보를 Extension Host에 전달합니다.
     * @param id Diff 정보가 변경된 에디터의 고유 ID
     * @param diffInformation Diff 정보를 담은 `TextEditorDiffInformation` 리스트 (null일 수 있음)
     */
    fun acceptEditorDiffInformation(id: String, diffInformation: List<TextEditorDiffInformation>?)
}
