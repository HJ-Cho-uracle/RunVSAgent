// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.events.WebviewHtmlUpdateData
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.webview.WebViewManager
import java.util.concurrent.ConcurrentHashMap

/**
 * WebView를 식별하는 고유 핸들(ID)을 위한 타입 별칭입니다.
 * TypeScript의 `WebviewHandle` 타입에 해당합니다.
 */
typealias WebviewHandle = String

/**
 * IntelliJ 메인 스레드에서 WebView 관련 서비스를 처리하기 위한 인터페이스입니다.
 * WebView의 HTML 내용을 설정하고, 옵션을 변경하며, 메시지를 보내는 기능을 정의합니다.
 * VSCode Extension Host의 `MainThreadWebviewsShape`에 해당합니다.
 */
interface MainThreadWebviewsShape : Disposable {
    /**
     * 지정된 핸들의 WebView에 HTML 내용을 설정합니다.
     * @param handle WebView 핸들
     * @param value 설정할 HTML 내용
     */
    fun setHtml(handle: WebviewHandle, value: String)
    
    /**
     * 지정된 핸들의 WebView에 대한 옵션을 설정합니다.
     * @param handle WebView 핸들
     * @param options WebView 콘텐츠에 대한 옵션 (예: 스크립트 활성화 여부)
     */
    fun setOptions(handle: WebviewHandle, options: Map<String, Any?>)
    
    /**
     * 지정된 핸들의 WebView로 메시지를 보냅니다. (IntelliJ -> WebView)
     * @param handle WebView 핸들
     * @param value 보낼 메시지 내용 (일반적으로 JSON 형식의 문자열)
     * @return 작업 성공 여부
     */
    fun postMessage(handle: WebviewHandle, value: String): Boolean
}

/**
 * `MainThreadWebviewsShape` 인터페이스의 구현 클래스입니다.
 * `WebViewManager` 서비스를 통해 실제 WebView 인스턴스를 제어합니다.
 */
class MainThreadWebviews(val project: Project) : MainThreadWebviewsShape {
    private val logger = Logger.getInstance(MainThreadWebviews::class.java)
    
    // 등록된 WebView들을 저장하는 맵 (현재는 사용되지 않음)
    private val webviews = ConcurrentHashMap<WebviewHandle, Any?>()
    private var webviewHandle : WebviewHandle = ""
    
    /**
     * WebView의 HTML 내용을 설정합니다.
     * Extension Host로부터 받은 HTML 내용에서 `vscode-file:` 프로토콜 경로를
     * 웹 서버에서 접근 가능한 상대 경로로 변환한 후, `WebViewManager`를 통해 WebView에 전달합니다.
     */
    override fun setHtml(handle: WebviewHandle, value: String) {
        logger.info("WebView HTML 설정: handle=$handle, 길이=${value.length}")
        webviewHandle = handle
        try {
            // `vscode-file:` 프로토콜 경로를 웹 서버의 루트 경로('/')로 변경합니다.
            // 예: "vscode-file:/path/to/extension/media/main.js" -> "/media/main.js"
            val extensionManager = ExtensionManager.getInstance(project)
            val currentProvider = extensionManager.getCurrentProvider()
            val extensionDir = currentProvider?.getConfiguration(project)?.getCodeDir() ?: "unknown-extension"
            val modifiedHtml = value.replace(Regex("vscode-file:/.*?/$extensionDir/"), "/")
            logger.info("vscode-file 프로토콜 경로를 변환했습니다.")
            
            // `WebViewManager`를 통해 HTML 업데이트 이벤트를 전달합니다.
            val data = WebviewHtmlUpdateData(handle, modifiedHtml)
            project.getService(WebViewManager::class.java).updateWebViewHtml(data)
            logger.info("HTML 콘텐츠 업데이트 이벤트 전송 완료: handle=$handle")
        } catch (e: Exception) {
            logger.error("WebView HTML 설정 실패", e)
        }
    }
    
    /**
     * WebView 옵션을 설정합니다. (현재는 로깅만 수행)
     */
    override fun setOptions(handle: WebviewHandle, options: Map<String, Any?>) {
        logger.info("WebView 옵션 설정: handle=$handle, options=$options")
        webviewHandle = handle
        // TODO: 실제 WebView 컴포넌트에 옵션을 설정하는 로직 구현 필요
    }
    
    /**
     * WebView로 메시지를 전송합니다.
     * `WebViewManager`를 통해 가장 최근에 생성된 WebView에 메시지를 전달합니다.
     */
    override fun postMessage(handle: WebviewHandle, value: String): Boolean {
        // 테마 관련 메시지는 너무 빈번하므로 디버그 레벨로 로깅합니다.
        if(value.contains("theme")) {
            logger.debug("WebView로 테마 메시지 전송")
        }

        return try {
            val manager = project.getService(WebViewManager::class.java)
            // 가장 최근의 WebView에 메시지를 보냅니다.
            manager.getLatestWebView()?.postMessageToWebView(value)
            true
        } catch (e: Exception) {
            logger.error("WebView로 메시지 전송 실패", e)
            false
        }
    }
    
    override fun dispose() {
        logger.info("Disposing MainThreadWebviews resources")
        webviews.clear()
    }
}
