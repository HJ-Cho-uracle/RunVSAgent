// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.model.WorkspaceData
import com.sina.weibo.agent.util.URIComponents

/**
 * Extension Host 작업 공간(Workspace) 서비스 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 작업 공간 서비스와 상호작용하기 위해 사용됩니다.
 */
interface ExtHostWorkspaceProxy {
    /**
     * 작업 공간을 초기화합니다.
     * Extension Host가 시작될 때 IntelliJ 플러그인으로부터 작업 공간 정보를 받습니다.
     * @param workspace 작업 공간 설정을 담은 `WorkspaceData` 객체 (null일 수 있음)
     * @param trusted 작업 공간이 신뢰할 수 있는지 여부
     */
    fun initializeWorkspace(workspace: WorkspaceData?, trusted: Boolean)

    /**
     * 작업 공간 데이터를 Extension Host에 전달합니다.
     * @param workspace 작업 공간 데이터를 담은 `WorkspaceData` 객체 (null일 수 있음)
     */
    fun acceptWorkspaceData(workspace: WorkspaceData?)

    /**
     * 텍스트 검색 결과를 처리합니다.
     * @param result 검색 결과
     * @param requestId 요청 ID
     */
    fun handleTextSearchResult(result: Any, requestId: Long)

    /**
     * 작업 공간 신뢰가 부여되었을 때 호출됩니다.
     */
    fun onDidGrantWorkspaceTrust()

    /**
     * 편집 세션 식별자를 가져옵니다.
     * @param folder 폴더 URI 구성 요소
     * @param token 취소 토큰
     * @return 편집 세션 식별자 문자열 (null일 수 있음)
     */
    fun getEditSessionIdentifier(folder: URIComponents, token: Any): String?
}
