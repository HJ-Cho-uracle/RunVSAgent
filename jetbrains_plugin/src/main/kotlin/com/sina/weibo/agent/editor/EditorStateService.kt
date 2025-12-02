// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.openapi.project.Project
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostDocumentsAndEditorsProxy
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostDocumentsProxy
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostEditorTabsProxy
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostEditorsProxy
import com.sina.weibo.agent.util.URI

/**
 * IntelliJ 에디터 및 문서의 상태 변경을 Extension Host에 알리는 서비스입니다.
 * RPC를 통해 Extension Host의 해당 프록시 메소드를 호출합니다.
 *
 * @param project 현재 IntelliJ 프로젝트
 */
class EditorStateService(val project: Project) {
    // Extension Host의 문서 및 에디터 관련 프록시들
    var extHostDocumentsAndEditorsProxy: ExtHostDocumentsAndEditorsProxy? = null
    var extHostEditorsProxy: ExtHostEditorsProxy? = null
    var extHostDocumentsProxy: ExtHostDocumentsProxy? = null

    /**
     * 문서 및 에디터의 추가/제거/활성화 변경사항(델타)을 Extension Host에 알립니다.
     * `ExtHostDocumentsAndEditorsProxy`의 `acceptDocumentsAndEditorsDelta` 메소드를 호출합니다.
     * @param detail 변경사항을 담은 `DocumentsAndEditorsDelta` 객체
     */
    fun acceptDocumentsAndEditorsDelta(detail: DocumentsAndEditorsDelta) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        // 프록시가 아직 초기화되지 않았으면 초기화합니다.
        if (extHostDocumentsAndEditorsProxy == null) {
            extHostDocumentsAndEditorsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostDocumentsAndEditors)
        }
        extHostDocumentsAndEditorsProxy?.acceptDocumentsAndEditorsDelta(detail)
    }

    /**
     * 에디터 속성(옵션, 선택 영역, 가시 범위 등)의 변경사항을 Extension Host에 알립니다.
     * `ExtHostEditorsProxy`의 `acceptEditorPropertiesChanged` 메소드를 호출합니다.
     * @param detail 에디터 ID를 키로 하는 `EditorPropertiesChangeData` 맵
     */
    fun acceptEditorPropertiesChanged(detail: Map<String, EditorPropertiesChangeData>) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorsProxy == null) {
            extHostEditorsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditors)
        }
        extHostEditorsProxy?.let {
            for ((id, data) in detail) {
                it.acceptEditorPropertiesChanged(id, data)
            }
        }
    }

    /**
     * 문서 내용 변경사항을 Extension Host에 알립니다.
     * `ExtHostDocumentsProxy`의 `acceptModelChanged` 메소드를 호출합니다.
     * @param detail 문서 URI를 키로 하는 `ModelChangedEvent` 맵
     */
    fun acceptModelChanged(detail: Map<URI, ModelChangedEvent>) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostDocumentsProxy == null) {
            extHostDocumentsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostDocuments)
        }
        extHostDocumentsProxy?.let {
            for ((uri, data) in detail) {
                it.acceptModelChanged(uri, data, data.isDirty)
            }
        }
    }
}

/**
 * IntelliJ 에디터 탭의 상태 변경을 Extension Host에 알리는 서비스입니다.
 * RPC를 통해 Extension Host의 `ExtHostEditorTabsProxy` 메소드를 호출합니다.
 *
 * @param project 현재 IntelliJ 프로젝트
 */
class TabStateService(val project: Project) {
    var extHostEditorTabsProxy: ExtHostEditorTabsProxy? = null

    /**
     * 에디터 탭 모델(탭 그룹 및 탭 목록)의 전체 상태를 Extension Host에 알립니다.
     * @param detail 탭 그룹 DTO 리스트
     */
    fun acceptEditorTabModel(detail: List<EditorTabGroupDto>) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null) {
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptEditorTabModel(detail)
    }

    /**
     * 탭 작업(예: 탭 닫기)을 Extension Host에 알립니다.
     * @param detail 탭 작업 정보를 담은 `TabOperation` 객체
     */
    fun acceptTabOperation(detail: TabOperation) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null) {
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptTabOperation(detail)
    }

    /**
     * 탭 그룹의 업데이트(예: 탭 추가/제거)를 Extension Host에 알립니다.
     * @param detail 업데이트된 탭 그룹 DTO
     */
    fun acceptTabGroupUpdate(detail: EditorTabGroupDto) {
        val protocol = PluginContext.getInstance(project).getRPCProtocol()
        if (extHostEditorTabsProxy == null) {
            extHostEditorTabsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostEditorTabs)
        }
        extHostEditorTabsProxy?.acceptTabGroupUpdate(detail)
    }
}
