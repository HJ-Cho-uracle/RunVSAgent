// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.events.WebviewViewProviderData
import com.sina.weibo.agent.webview.WebViewManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * IntelliJ 메인 스레드에서 WebView를 사용하는 뷰(View) 관련 작업을 처리하기 위한 인터페이스입니다.
 * 예를 들어, 사이드바에 웹 콘텐츠를 표시하는 뷰를 등록하고 관리하는 기능을 정의합니다.
 */
interface MainThreadWebviewViewsShape : Disposable {
    /**
     * WebView 뷰 제공자(Provider)를 등록합니다.
     * 이 제공자는 특정 `viewType`에 대한 웹 콘텐츠를 생성하고 관리하는 역할을 합니다.
     * @param extension 이 제공자를 등록하는 확장에 대한 설명
     * @param viewType 뷰의 고유 타입 (예: "my-extension.sidebar-view")
     * @param options 뷰에 대한 추가 설정 (예: 웹 콘텐츠가 영속적인지 여부)
     */
    fun registerWebviewViewProvider(
        extension: Map<String, Any?>,
        viewType: String,
        options: Map<String, Any?>,
    )

    /**
     * 등록된 WebView 뷰 제공자를 해제합니다.
     * @param viewType 해제할 뷰의 타입
     */
    fun unregisterWebviewViewProvider(viewType: String)

    /**
     * 지정된 핸들의 WebView 뷰의 제목을 설정합니다.
     * @param handle WebView 핸들
     * @param value 설정할 제목
     */
    fun setWebviewViewTitle(handle: String, value: String?)

    /**
     * 지정된 핸들의 WebView 뷰에 대한 설명을 설정합니다.
     * @param handle WebView 핸들
     * @param value 설정할 설명 내용
     */
    fun setWebviewViewDescription(handle: String, value: String?)

    /**
     * 지정된 핸들의 WebView 뷰에 배지(Badge)를 설정합니다. (예: 알림 개수 표시)
     * @param handle WebView 핸들
     * @param badge 배지 정보 (툴팁과 숫자 등)
     */
    fun setWebviewViewBadge(handle: String, badge: Map<String, Any?>?)

    /**
     * 지정된 핸들의 WebView 뷰를 사용자에게 보여줍니다.
     * @param handle WebView 핸들
     * @param preserveFocus 뷰를 보여준 후에도 현재 포커스를 유지할지 여부
     */
    fun show(handle: String, preserveFocus: Boolean)
}

/**
 * `MainThreadWebviewViewsShape` 인터페이스의 구현 클래스입니다.
 * `WebViewManager`를 통해 실제 뷰 제공자를 등록하고 관리합니다.
 */
class MainThreadWebviewViews(val project: Project) : MainThreadWebviewViewsShape {
    private val logger = Logger.getInstance(MainThreadWebviewViews::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * `WebViewManager`에 새로운 뷰 제공자를 등록하도록 요청합니다.
     */
    override fun registerWebviewViewProvider(
        extension: Map<String, Any?>,
        viewType: String,
        options: Map<String, Any?>,
    ) {
        logger.info("WebView 뷰 제공자 등록: viewType=$viewType, options=$options")

        // WebViewManager 서비스를 통해 제공자 정보를 전달합니다.
        project.getService(WebViewManager::class.java).registerProvider(WebviewViewProviderData(extension, viewType, options))
    }

    override fun unregisterWebviewViewProvider(viewType: String) {
        logger.info("WebView 뷰 제공자 등록 해제: viewType=$viewType")
    }

    override fun setWebviewViewTitle(handle: String, value: String?) {
        logger.info("WebView 뷰 제목 설정: handle=$handle, title=$value")
    }

    override fun setWebviewViewDescription(handle: String, value: String?) {
        logger.info("WebView 뷰 설명 설정: handle=$handle, description=$value")
    }

    override fun setWebviewViewBadge(handle: String, badge: Map<String, Any?>?) {
        logger.info("WebView 뷰 배지 설정: handle=$handle, badge=$badge")
    }

    override fun show(handle: String, preserveFocus: Boolean) {
        logger.info("WebView 뷰 표시: handle=$handle, preserveFocus=$preserveFocus")
    }

    override fun dispose() {
        logger.info("Disposing MainThreadWebviewViews resources")
    }
}
