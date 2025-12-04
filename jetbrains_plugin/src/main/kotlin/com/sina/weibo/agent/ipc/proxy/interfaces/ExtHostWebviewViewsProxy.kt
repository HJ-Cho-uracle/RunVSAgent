// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

/**
 * Extension Host WebView 뷰 서비스 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 WebView 뷰 서비스와 상호작용하기 위해 사용됩니다.
 */
interface ExtHostWebviewViewsProxy {
    /**
     * WebView 뷰를 해결(resolve)합니다.
     * @param webviewHandle WebView 핸들
     * @param viewType 뷰 타입
     * @param title 제목
     * @param state 상태 데이터
     * @param cancellation 취소 토큰
     * @return 완료 시 Promise
     */
    fun resolveWebviewView(
        webviewHandle: String,
        viewType: String,
        title: String?,
        state: Any?,
        cancellation: Any?,
    )

    /**
     * WebView 뷰의 가시성(visibility)이 변경되었을 때 트리거됩니다.
     * @param webviewHandle WebView 핸들
     * @param visible 가시성 여부
     */
    fun onDidChangeWebviewViewVisibility(
        webviewHandle: String,
        visible: Boolean,
    )

    /**
     * WebView 뷰를 해제합니다.
     * @param webviewHandle WebView 핸들
     */
    fun disposeWebviewView(
        webviewHandle: String,
    )
}
