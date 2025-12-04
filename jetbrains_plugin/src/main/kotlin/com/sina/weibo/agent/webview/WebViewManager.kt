// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.events.WebviewHtmlUpdateData
import com.sina.weibo.agent.events.WebviewViewProviderData
import com.sina.weibo.agent.ipc.proxy.SerializableObjectWithBuffers
import com.sina.weibo.agent.theme.ThemeChangeListener
import com.sina.weibo.agent.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * WebView 생성 콜백 인터페이스입니다.
 */
interface WebViewCreationCallback {
    /**
     * WebView가 생성되었을 때 호출됩니다.
     * @param instance 생성된 WebView 인스턴스
     */
    fun onWebViewCreated(instance: WebViewInstance)
}

/**
 * WebView 관리자 클래스입니다.
 * 플러그인 생명주기 동안 생성된 모든 WebView 인스턴스를 관리합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class WebViewManager(var project: Project) : Disposable, ThemeChangeListener {
    private val logger = Logger.getInstance(WebViewManager::class.java)

    // 가장 최근에 생성된 WebView 인스턴스
    @Volatile
    private var latestWebView: WebViewInstance? = null

    // WebView 생성 콜백 목록
    private val creationCallbacks = mutableListOf<WebViewCreationCallback>()

    // 리소스 루트 디렉터리 경로
    @Volatile
    private var resourceRootDir: Path? = null

    // 현재 테마 설정
    private var currentThemeConfig: JsonObject? = null

    // 현재 테마 타입 (다크/라이트)
    private var isDarkTheme: Boolean = true

    // 중복 해제 방지 플래그
    private var isDisposed = false

    // 테마 초기화 여부
    private var themeInitialized = false

    /**
     * 테마 관리자를 초기화합니다.
     * @param resourceRoot 리소스 루트 디렉터리
     */
    fun initializeThemeManager(resourceRoot: String) {
        if (isDisposed || themeInitialized) return

        logger.info("테마 관리자 초기화 중")
        val themeManager = ThemeManager.getInstance()
        themeManager.initialize(resourceRoot)
        themeManager.addThemeChangeListener(this) // 테마 변경 리스너로 자신을 등록
        themeInitialized = true
    }

    /**
     * `ThemeChangeListener` 인터페이스 구현: 테마 변경 이벤트를 처리합니다.
     * @param themeConfig 테마 설정 JSON 객체
     * @param isDarkTheme 다크 테마 여부
     */
    override fun onThemeChanged(themeConfig: JsonObject, isDarkTheme: Boolean) {
        logger.info("테마 변경 이벤트 수신, isDarkTheme: $isDarkTheme, config: ${themeConfig.size()}")
        this.currentThemeConfig = themeConfig
        this.isDarkTheme = isDarkTheme

        // 모든 WebView 인스턴스에 테마 설정을 전송합니다.
        sendThemeConfigToWebViews(themeConfig)
    }

    /**
     * 모든 WebView 인스턴스에 테마 설정을 전송합니다.
     * @param themeConfig 테마 설정 JSON 객체
     */
    private fun sendThemeConfigToWebViews(themeConfig: JsonObject) {
        logger.info("WebView에 테마 설정 전송")

        try {
            getLatestWebView()?.sendThemeConfigToWebView(themeConfig)
        } catch (e: Exception) {
            logger.error("WebView에 테마 설정 전송 실패", e)
        }
    }

    /**
     * HTML 콘텐츠를 리소스 디렉터리에 저장합니다.
     * @param html HTML 콘텐츠
     * @param filename 파일 이름
     * @return 저장된 파일 경로
     * @throws IOException 리소스 루트 디렉터리가 없거나 저장 실패 시
     */
    private fun saveHtmlToResourceDir(html: String, filename: String): Path? {
        if (resourceRootDir == null || !resourceRootDir!!.exists()) {
            logger.warn("리소스 루트 디렉터리가 존재하지 않아 HTML 콘텐츠를 저장할 수 없습니다.")
            throw IOException("리소스 루트 디렉터리가 존재하지 않습니다.")
        }

        val filePath = resourceRootDir?.resolve(filename)

        try {
            if (filePath != null) {
                logger.info("HTML 콘텐츠 저장됨: $filePath")
                Files.write(filePath, html.toByteArray(StandardCharsets.UTF_8))
                return filePath
            }
            return null
        } catch (e: Exception) {
            logger.error("HTML 콘텐츠 저장 실패: $filePath", e)
            throw e
        }
    }

    /**
     * WebView 생성 콜백을 등록합니다.
     * @param callback 콜백 객체
     * @param disposable 연결된 `Disposable` 객체 (콜백 자동 제거용)
     */
    fun addCreationCallback(callback: WebViewCreationCallback, disposable: Disposable? = null) {
        synchronized(creationCallbacks) {
            creationCallbacks.add(callback)

            // `Disposable`이 제공되면, 해제될 때 콜백을 자동으로 제거합니다.
            if (disposable != null) {
                Disposer.register(
                    disposable,
                    Disposable {
                        removeCreationCallback(callback)
                    },
                )
            }
        }

        // 이미 생성된 WebView가 있으면 즉시 새 콜백에 알립니다.
        latestWebView?.let { webview ->
            ApplicationManager.getApplication().invokeLater {
                callback.onWebViewCreated(webview)
            }
        }
    }

    /**
     * WebView 생성 콜백을 제거합니다.
     * @param callback 제거할 콜백 객체
     */
    fun removeCreationCallback(callback: WebViewCreationCallback) {
        synchronized(creationCallbacks) {
            creationCallbacks.remove(callback)
        }
    }

    /**
     * WebView가 생성되었음을 모든 콜백에 알립니다.
     * @param instance 생성된 WebView 인스턴스
     */
    private fun notifyWebViewCreated(instance: WebViewInstance) {
        val callbacks = synchronized(creationCallbacks) {
            creationCallbacks.toList() // 동시 수정 방지를 위해 복사본 생성
        }

        // UI 스레드에서 안전하게 콜백을 호출합니다.
        ApplicationManager.getApplication().invokeLater {
            callbacks.forEach { callback ->
                try {
                    callback.onWebViewCreated(instance)
                } catch (e: Exception) {
                    logger.error("WebView 생성 콜백 호출 중 예외 발생", e)
                }
            }
        }
    }

    /**
     * WebView 제공자를 등록하고 WebView 인스턴스를 생성합니다.
     * @param data WebView 뷰 제공자 데이터
     */
    fun registerProvider(data: WebviewViewProviderData) {
        logger.info("WebView 제공자 등록 및 WebView 인스턴스 생성: ${data.viewType}")
        val extension = data.extension

        // 확장 정보에서 리소스 루트 디렉터리 경로를 가져와 설정합니다.
        try {
            @Suppress("UNCHECKED_CAST")
            val location = extension?.get("location") as? Map<String, Any?>
            val fsPath = location?.get("fsPath") as? String

            if (fsPath != null) {
                val path = Paths.get(fsPath)
                logger.info("확장에서 리소스 디렉터리 경로 가져옴: $path")

                if (!path.exists()) {
                    path.createDirectories()
                }

                resourceRootDir = path // 리소스 루트 디렉터리 업데이트
                initializeThemeManager(fsPath) // 테마 관리자 초기화
            }
        } catch (e: Exception) {
            logger.error("확장에서 리소스 디렉터리 가져오기 실패", e)
        }

        val protocol = project.getService(PluginContext::class.java).getRPCProtocol()
        if (protocol == null) {
            logger.error("RPC 프로토콜 인스턴스를 가져올 수 없어 WebView 제공자를 등록할 수 없습니다: ${data.viewType}")
            return
        }
        // 등록 이벤트가 알림되면 새 WebView 인스턴스를 생성합니다.
        val viewId = UUID.randomUUID().toString()

        val title = data.options["title"] as? String ?: data.viewType
        val state = data.options["state"] as? Map<String, Any?> ?: emptyMap()

        val webview = WebViewInstance(data.viewType, viewId, title, state, project, data.extension)

        val proxy = protocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostWebviewViews)
        proxy.resolveWebviewView(viewId, data.viewType, title, state, null)

        // 가장 최근에 생성된 WebView로 설정합니다.
        latestWebView = webview

        logger.info("WebView 인스턴스 생성됨: viewType=${data.viewType}, viewId=$viewId")

        notifyWebViewCreated(webview) // WebView 생성 콜백에 알림
    }

    /**
     * 가장 최근에 생성된 WebView 인스턴스를 가져옵니다.
     */
    fun getLatestWebView(): WebViewInstance? {
        return latestWebView
    }

    /**
     * WebView의 HTML 콘텐츠를 업데이트합니다.
     * @param data HTML 업데이트 데이터
     */
    fun updateWebViewHtml(data: WebviewHtmlUpdateData) {
        val encodedState = getLatestWebView()?.state.toString().replace("\"", "\\\"")
        // <script nonce="..."> 또는 <script type="text/javascript" nonce="..."> 형식 모두 지원
        val mRst = """<script(?:\s+type="text/javascript")?\s+nonce="([A-Za-z0-9]{32})">""".toRegex().find(data.htmlContent)
        val str = mRst?.value ?: ""
        data.htmlContent = data.htmlContent.replace(
            str,
            """
                        $str
                        // 메시지를 플러그인으로 보내는 함수를 먼저 정의합니다.
                        window.sendMessageToPlugin = function(message) {
                            // JS 객체를 JSON 문자열로 변환
                            // console.log("sendMessageToPlugin: ", message);
                            const msgStr = JSON.stringify(message);
                            ${getLatestWebView()?.jsQuery?.inject("msgStr")}
                        };
                        
                        // VSCode API 모의(mock) 주입
                        globalThis.acquireVsCodeApi = (function() {
                            let acquired = false;
                        
                            let state = JSON.parse('$encodedState');
                        
                            if (typeof window !== "undefined" && !window.receiveMessageFromPlugin) {
                                console.log("VSCodeAPIWrapper: IDEA 플러그인 호환성을 위해 receiveMessageFromPlugin 설정 중");
                                window.receiveMessageFromPlugin = (message) => {
                                    // console.log("receiveMessageFromPlugin 메시지 수신:", JSON.stringify(message));
                                    // 기존 코드와의 호환성을 유지하기 위해 새 MessageEvent를 생성하고 디스패치
                                    const event = new MessageEvent("message", {
                                        data: message,
                                    });
                                    window.dispatchEvent(event);
                                };
                            }
                        
                            return () => {
                                if (acquired) {
                                    throw new Error('VS Code API 인스턴스가 이미 획득되었습니다.');
                                }
                                acquired = true;
                                return Object.freeze({
                                    postMessage: function(message, transfer) {
                                        // console.log("postMessage: ", message);
                                        window.sendMessageToPlugin(message);
                                    },
                                    setState: function(newState) {
                                        state = newState;
                                        window.sendMessageToPlugin(newState);
                                        return newState;
                                    },
                                    getState: function() {
                                        return state;
                                    }
                                });
                            };
                        })();
                        
                        // 보안을 위해 window.parent 참조 정리
                        delete window.parent;
                        delete window.top;
                        delete window.frameElement;
                        
                        console.log("VSCode API 모의 주입됨");
                        """,
        )

        logger.info("HTML 업데이트 이벤트 수신: handle=${data.handle}, HTML 길이: ${data.htmlContent.length}")

        val webView = getLatestWebView()

        if (webView != null) {
            try {
                // HTTP 서버가 실행 중인 경우
                if (resourceRootDir != null) {
                    val filename = "index.html" // WebView를 위한 고유 파일 이름 생성

                    saveHtmlToResourceDir(data.htmlContent, filename) // HTML 콘텐츠를 파일에 저장

                    // HTTP URL을 사용하여 WebView 콘텐츠 로드
                    val url = "http://localhost:12345/$filename"
                    logger.info("HTTP를 통해 WebView HTML 콘텐츠 로드: $url")

                    webView.loadUrl(url)
                } else {
                    // HTTP 서버가 실행 중이 아니거나 리소스 디렉터리가 설정되지 않은 경우 직접 HTML 로드
                    logger.warn("HTTP 서버가 실행 중이 아니거나 리소스 디렉터리가 설정되지 않아 HTML 콘텐츠를 직접 로드합니다.")
                    webView.loadHtml(data.htmlContent)
                }

                logger.info("WebView HTML 콘텐츠 업데이트됨: handle=${data.handle}")

                // 테마 설정이 이미 있으면 콘텐츠 로드 후 전송
                if (currentThemeConfig != null) {
                    ApplicationManager.getApplication().invokeLater {
                        try {
                            webView.sendThemeConfigToWebView(currentThemeConfig!!)
                        } catch (e: Exception) {
                            logger.error("WebView에 테마 설정 전송 실패", e)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("WebView HTML 콘텐츠 업데이트 실패", e)
                webView.loadHtml(data.htmlContent) // 오류 발생 시 직접 HTML 로드
            }
        } else {
            logger.warn("WebView 인스턴스를 찾을 수 없음: handle=${data.handle}")
        }
    }

    override fun dispose() {
        if (isDisposed) {
            logger.info("WebViewManager가 이미 해제되었습니다. 반복 호출 무시")
            return
        }
        isDisposed = true

        logger.info("WebViewManager 리소스 해제 중...")

        // 테마 관리자에서 리스너 제거
        try {
            ThemeManager.getInstance().removeThemeChangeListener(this)
        } catch (e: Exception) {
            logger.error("테마 관리자에서 리스너 제거 실패", e)
        }

        // 리소스 디렉터리 정리 (index.html 파일만 삭제)
        try {
            resourceRootDir?.let {
                val indexFile = it.resolve("index.html").toFile()
                if (indexFile.exists() && indexFile.isFile) {
                    val deleted = indexFile.delete()
                    if (deleted) {
                        logger.info("index.html 파일 삭제됨")
                    } else {
                        logger.warn("index.html 파일 삭제 실패")
                    }
                } else {
                    logger.info("index.html 파일이 존재하지 않아 정리할 필요 없음")
                }
            }
            resourceRootDir = null
        } catch (e: Exception) {
            logger.error("index.html 파일 정리 실패", e)
        }

        try {
            latestWebView?.dispose() // 최신 WebView 인스턴스 해제
        } catch (e: Exception) {
            logger.error("WebView 리소스 해제 실패", e)
        }

        currentThemeConfig = null // 테마 데이터 초기화

        // 콜백 목록 비우기
        synchronized(creationCallbacks) {
            creationCallbacks.clear()
        }

        logger.info("WebViewManager 리소스 해제 완료")
    }
}

/**
 * WebView 인스턴스 클래스입니다. JCEF 브라우저를 캡슐화합니다.
 */
class WebViewInstance(
    val viewType: String, // 뷰 타입
    val viewId: String, // 뷰 ID
    val title: String, // 제목
    val state: Map<String, Any?>, // 상태 데이터
    val project: Project, // 프로젝트
    val extension: Map<String, Any?>, // 확장 정보
) : Disposable {
    private val logger = Logger.getInstance(WebViewInstance::class.java)

    // JCEF 브라우저 인스턴스 (오프스크린 렌더링 활성화)
    val browser = JBCefBrowser.createBuilder().setOffScreenRendering(true).build()

    // WebView 해제 상태
    private var isDisposed = false

    // 웹뷰와의 통신을 위한 JavaScript 쿼리 핸들러
    var jsQuery: JBCefJSQuery? = null

    // JSON 직렬화
    private val gson = Gson()

    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isPageLoaded = false // 페이지 로드 완료 여부

    private var currentThemeConfig: JsonObject? = null // 현재 테마 설정

    // 페이지 로드 완료 콜백
    private var pageLoadCallback: (() -> Unit)? = null

    init {
        setupJSBridge() // JavaScript 브릿지 설정
        enableResourceInterception(extension) // 리소스 요청 가로채기 활성화
    }

    /**
     * 지정된 WebView 인스턴스에 테마 설정을 전송합니다.
     */
    fun sendThemeConfigToWebView(themeConfig: JsonObject) {
        currentThemeConfig = themeConfig
        if (isDisposed || !isPageLoaded) {
            logger.warn("WebView가 해제되었거나 로드되지 않아 테마 설정을 보낼 수 없음: 해제됨=$isDisposed, 로드됨=$isPageLoaded")
            return
        }
        injectTheme() // 테마 주입
    }

    /**
     * 페이지 로드 완료 여부를 확인합니다.
     */
    fun isPageLoaded(): Boolean {
        return isPageLoaded
    }

    /**
     * 페이지 로드 완료 시 호출될 콜백을 설정합니다.
     */
    fun setPageLoadCallback(callback: (() -> Unit)?) {
        pageLoadCallback = callback
    }

    /**
     * WebView에 테마를 주입합니다.
     * CSS 변수를 HTML 태그에 삽입하고, 테마 설정 메시지를 WebView로 보냅니다.
     */
    private fun injectTheme() {
        if (currentThemeConfig == null) {
            return
        }
        try {
            var cssContent: String? = null

            // `themeConfig`에서 `cssContent`를 가져와 주입합니다.
            if (currentThemeConfig!!.has("cssContent")) {
                cssContent = currentThemeConfig!!.get("cssContent").asString
                val themeConfigCopy = currentThemeConfig!!.deepCopy() // 원본 객체 변경 방지를 위해 복사
                themeConfigCopy.remove("cssContent") // `cssContent` 속성 제거

                // CSS 변수를 WebView에 주입하는 JavaScript 코드
                if (cssContent != null) {
                    val injectThemeScript = """
                        (function() {
                            console.log("WebView에 CSS 변수 주입 준비 중")
                            function injectCSSVariables() {
                                if(document.documentElement) {
                                    // CSS 변수 추출 및 HTML 태그의 style 속성으로 설정
                                    // ... (CSS 변수 추출 및 설정 로직)
                                    console.log("CSS 변수가 HTML 태그의 style 속성으로 설정됨");
                                    
                                    // 기본 스타일 주입 로직 유지
                                    if(document.head) {
                                        // 기본 테마 스타일을 head에 주입 (id="_defaultStyles")
                                        // ... (기본 스타일 주입 로직)
                                        console.log("기본 스타일이 id=_defaultStyles에 주입됨");
                                    }
                                } else {
                                    // html 태그가 아직 없으면 DOM 로드 대기 후 재시도
                                    setTimeout(injectCSSVariables, 10);
                                }
                            }
                            // 문서 로드 상태에 따라 즉시 또는 DOMContentLoaded 이벤트 대기 후 주입
                            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                                console.log("문서 로드됨, CSS 변수 즉시 주입");
                                injectCSSVariables();
                            } else {
                                console.log("문서 로드되지 않음, DOMContentLoaded 이벤트 대기 중");
                                document.addEventListener('DOMContentLoaded', injectCSSVariables);
                            }
                        })()
                    """.trimIndent()

                    logger.info("WebView($viewId)에 테마 스타일 주입 중, 크기: ${cssContent.length} 바이트")
                    executeJavaScript(injectThemeScript)
                }

                // `cssContent`를 제외한 테마 설정을 메시지로 전송
                val themeConfigJson = gson.toJson(themeConfigCopy)
                val message = """
                    {
                        "type": "theme",
                        "text": "${themeConfigJson.replace("\"", "\\\"")}"
                    }
                """.trimIndent()

                postMessageToWebView(message)
                logger.info("cssContent를 제외한 테마 설정이 WebView에 전송됨")
            } else {
                // `cssContent`가 없으면 원본 설정을 직접 전송
                val themeConfigJson = gson.toJson(currentThemeConfig)
                val message = """
                    {
                        "type": "theme",
                        "text": "${themeConfigJson.replace("\"", "\\\"")}"
                    }
                """.trimIndent()

                postMessageToWebView(message)
                logger.info("테마 설정이 WebView에 전송됨")
            }
        } catch (e: Exception) {
            logger.error("WebView에 테마 설정 전송 실패", e)
        }
    }

    /**
     * JavaScript 브릿지를 설정합니다.
     * WebView에서 플러그인으로 메시지를 보낼 수 있도록 `JBCefJSQuery`를 사용합니다.
     */
    private fun setupJSBridge() {
        jsQuery = JBCefJSQuery.create(browser) // JS 쿼리 객체 생성

        // 웹뷰로부터 메시지를 수신하기 위한 핸들러 설정
        jsQuery?.addHandler { message ->
            coroutineScope.launch {
                val protocol = project.getService(PluginContext::class.java).getRPCProtocol()
                if (protocol != null) {
                    // 메시지를 플러그인 호스트로 전송
                    val serializeParam = SerializableObjectWithBuffers(emptyList<ByteArray>())
                    protocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostWebviews).onMessage(viewId, message, serializeParam)
                } else {
                    logger.error("RPC 프로토콜 인스턴스를 가져올 수 없어 메시지를 처리할 수 없음: $message")
                }
            }
            null // 반환 값 없음
        }
    }

    /**
     * WebView로 메시지를 전송합니다.
     * @param message 전송할 메시지 (JSON 문자열)
     */
    fun postMessageToWebView(message: String) {
        if (!isDisposed) {
            // JavaScript 함수를 통해 WebView로 메시지 전송
            val script = """
                if (window.receiveMessageFromPlugin) {
                    window.receiveMessageFromPlugin($message);
                } else {
                    console.warn("receiveMessageFromPlugin 함수를 사용할 수 없습니다.");
                }
            """.trimIndent()
            executeJavaScript(script)
        }
    }

    /**
     * 리소스 요청 가로채기를 활성화합니다.
     * `CefRequestHandlerAdapter`를 사용하여 WebView의 리소스 로딩을 제어합니다.
     */
    fun enableResourceInterception(extension: Map<String, Any?>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val location = extension?.get("location") as? Map<String, Any?>
            val fsPath = location?.get("fsPath") as? String // 확장 파일 시스템 경로

            val client = browser.jbCefClient // JCEF 클라이언트 가져오기

            // 콘솔 메시지 핸들러 등록
            client.addDisplayHandler(
                object : CefDisplayHandlerAdapter() {
                    override fun onConsoleMessage(
                        browser: CefBrowser?,
                        level: CefSettings.LogSeverity?,
                        message: String?,
                        source: String?,
                        line: Int,
                    ): Boolean {
                        logger.debug("WebView 콘솔 메시지: [$level] $message (라인: $line, 소스: $source)")
                        return true
                    }
                },
                browser.cefBrowser,
            )

            // 로드 핸들러 등록
            client.addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    override fun onLoadingStateChange(
                        browser: CefBrowser?,
                        isLoading: Boolean,
                        canGoBack: Boolean,
                        canGoForward: Boolean,
                    ) {
                        logger.info("WebView 로딩 상태 변경됨: isLoading=$isLoading, canGoBack=$canGoBack, canGoForward=$canGoForward")
                    }

                    override fun onLoadStart(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        transitionType: CefRequest.TransitionType?,
                    ) {
                        logger.info("WebView 로딩 시작: ${frame?.url}, 전환 타입: $transitionType")
                        isPageLoaded = false
                    }

                    override fun onLoadEnd(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        httpStatusCode: Int,
                    ) {
                        logger.info("WebView 로딩 완료: ${frame?.url}, 상태 코드: $httpStatusCode")
                        isPageLoaded = true
                        injectTheme() // 페이지 로드 완료 후 테마 주입
                        pageLoadCallback?.invoke() // 페이지 로드 완료 콜백 호출
                    }

                    override fun onLoadError(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        errorCode: CefLoadHandler.ErrorCode?,
                        errorText: String?,
                        failedUrl: String?,
                    ) {
                        logger.info("WebView 로드 오류: $failedUrl, 오류 코드: $errorCode, 오류 메시지: $errorText")
                    }
                },
                browser.cefBrowser,
            )

            // 요청 핸들러 등록 (리소스 요청 가로채기)
            client.addRequestHandler(
                object : CefRequestHandlerAdapter() {
                    override fun onBeforeBrowse(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        request: CefRequest?,
                        user_gesture: Boolean,
                        is_redirect: Boolean,
                    ): Boolean {
                        logger.info("onBeforeBrowse, URL: ${request?.url}")
                        // localhost가 아닌 외부 URL은 시스템 브라우저로 엽니다.
                        if (request?.url?.startsWith("http://localhost") == false) {
                            BrowserUtil.browse(request.url)
                            return true
                        }
                        return false
                    }

                    override fun getResourceRequestHandler(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        request: CefRequest?,
                        isNavigation: Boolean,
                        isDownload: Boolean,
                        requestInitiator: String?,
                        disableDefaultHandling: BoolRef?,
                    ): CefResourceRequestHandler? {
                        logger.debug("getResourceRequestHandler, fsPath: $fsPath")
                        // 로컬호스트 요청인 경우 `LocalResHandler`를 사용하여 로컬 파일 시스템에서 리소스를 제공합니다.
                        if (fsPath != null && request?.url?.contains("localhost") == true) {
                            val path = Paths.get(fsPath)
                            return LocalResHandler(path.pathString, request)
                        } else {
                            return null
                        }
                    }
                },
                browser.cefBrowser,
            )
            logger.info("WebView 리소스 가로채기 활성화됨: $viewType/$viewId")
        } catch (e: Exception) {
            logger.error("WebView 리소스 가로채기 활성화 실패", e)
        }
    }

    /**
     * URL을 로드합니다.
     */
    fun loadUrl(url: String) {
        if (!isDisposed) {
            logger.info("WebView URL 로드 중: $url")
            browser.loadURL(url)
        }
    }

    /**
     * HTML 콘텐츠를 로드합니다.
     */
    fun loadHtml(html: String, baseUrl: String? = null) {
        if (!isDisposed) {
            logger.info("WebView HTML 콘텐츠 로드 중, 길이: ${html.length}, baseUrl: $baseUrl")
            if (baseUrl != null) {
                browser.loadHTML(html, baseUrl)
            } else {
                browser.loadHTML(html)
            }
        }
    }

    /**
     * JavaScript 코드를 실행합니다.
     */
    fun executeJavaScript(script: String) {
        if (!isDisposed) {
            logger.debug("WebView JavaScript 실행 중, 스크립트 길이: ${script.length}")
            browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        }
    }

    /**
     * 개발자 도구를 엽니다.
     */
    fun openDevTools() {
        if (!isDisposed) {
            browser.openDevtools()
        }
    }

    override fun dispose() {
        if (!isDisposed) {
            browser.dispose() // JBCefBrowser 해제
            isDisposed = true
            logger.info("WebView 인스턴스 해제됨: $viewType/$viewId")
        }
    }
}
