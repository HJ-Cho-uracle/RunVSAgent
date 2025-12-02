// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ide.BrowserUtil
import com.sina.weibo.agent.actions.OpenDevToolsAction
import com.sina.weibo.agent.plugin.WecoderPlugin
import com.sina.weibo.agent.plugin.WecoderPluginService
import com.sina.weibo.agent.plugin.DEBUG_MODE
import com.sina.weibo.agent.webview.DragDropHandler
import com.sina.weibo.agent.webview.WebViewCreationCallback
import com.sina.weibo.agent.webview.WebViewInstance
import com.sina.weibo.agent.webview.WebViewManager
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.extensions.core.ExtensionConfigurationManager
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.plugin.SystemObjectProvider
import com.sina.weibo.agent.extensions.ui.VsixUploadDialog
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.awt.Dimension
import java.awt.Font
import java.awt.Component
import java.awt.Cursor
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.BorderFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.sina.weibo.agent.util.ConfigFileUtils

/**
 * "RunVSAgent" Tool Window를 생성하고 초기화하는 팩토리 클래스입니다.
 * 이 클래스는 plugin.xml에 등록되어 IDE 시작 시 Tool Window를 설정하는 역할을 합니다.
 */
class RunVSAgentToolWindowFactory : ToolWindowFactory {

    /**
     * Tool Window의 콘텐츠를 생성하고 설정하는 기본 메소드입니다.
     * @param project 현재 열려있는 IntelliJ 프로젝트
     * @param toolWindow 생성된 Tool Window 객체
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 플러그인 핵심 서비스를 초기화합니다.
        val pluginService = WecoderPlugin.getInstance(project)

        // --- 툴바 액션 설정 ---
        val titleActions = mutableListOf<AnAction>()
        // "WecoderToolbarGroup"이라는 ID를 가진 액션 그룹을 찾아 툴바에 추가합니다.
        val action = ActionManager.getInstance().getAction("WecoderToolbarGroup")
        if (action != null) {
            titleActions.add(action)
        }
        // 디버그 모드일 때만 '개발자 도구 열기' 버튼을 추가합니다.
        if (WecoderPluginService.getDebugMode() != DEBUG_MODE.NONE) {
            titleActions.add(OpenDevToolsAction { project.getService(WebViewManager::class.java).getLatestWebView() })
        }
        toolWindow.setTitleActions(titleActions)

        // --- 콘텐츠 패널 설정 ---
        // Tool Window의 메인 콘텐츠를 담당하는 RunVSAgentToolWindowContent 객체를 생성합니다.
        val toolWindowContent = RunVSAgentToolWindowContent(project, toolWindow)
        val contentFactory = ContentFactory.getInstance()
        // 콘텐츠를 생성하고 Tool Window에 추가합니다.
        val content = contentFactory.createContent(
            toolWindowContent.content,
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    /**
     * Tool Window의 실제 UI 콘텐츠를 관리하는 내부 클래스입니다.
     * WebView 생성 콜백을 구현하여 WebView가 준비되었을 때 UI를 업데이트합니다.
     */
    private class RunVSAgentToolWindowContent(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) : WebViewCreationCallback {
        private val logger = Logger.getInstance(RunVSAgentToolWindowContent::class.java)

        // WebView를 관리하는 서비스
        private val webViewManager = project.getService(WebViewManager::class.java)
        // 확장(VSCode 플러그인) 설정을 관리하는 서비스
        private val configManager = ExtensionConfigurationManager.getInstance(project)
        // 확장(VSCode 플러그인)의 생명주기를 관리하는 서비스
        private val extensionManager = ExtensionManager.getInstance(project)

        // 메인 콘텐츠 패널
        private val contentPanel = JPanel(BorderLayout())
        // WebView가 로딩되기 전에 보여줄 시스템 정보 및 초기화 메시지 라벨
        private val placeholderLabel = JLabel(createSystemInfoText())
        // 클립보드에 복사하기 위한 순수 텍스트 형태의 시스템 정보
        private val systemInfoText = createSystemInfoPlainText()
        // 설정이 유효하지 않을 때 보여줄 플러그인 선택 패널
        private val pluginSelectionPanel = createPluginSelectionPanel()
        // 현재 설정 상태를 보여주는 패널
        private val configStatusPanel = createConfigStatusPanel()

        // 플러그인 시작 중 UI 변경을 막기 위한 상태 잠금 변수
        @Volatile
        private var isPluginStarting = false
        // 플러그인이 실행 중인지 여부를 나타내는 상태 변수
        @Volatile
        private var isPluginRunning = false

        /**
         * Extension Manager가 제대로 초기화되었는지 확인하여 플러그인이 실제로 실행 중인지 검사합니다.
         */
        private fun isPluginActuallyRunning(): Boolean {
            return try {
                val extensionManager = ExtensionManager.getInstance(project)
                extensionManager.isProperlyInitialized()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 시스템 정보를 보여주는 HTML 형식의 텍스트를 생성합니다.
         * IDE 테마(다크/라이트)에 맞춰 동적으로 스타일이 변경됩니다.
         */
        private fun createSystemInfoText(): String {
            // 다양한 시스템 및 플러그인 정보를 수집합니다.
            val appInfo = ApplicationInfo.getInstance()
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))
            val pluginVersion = plugin?.version ?: "unknown"
            val osName = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val osArch = System.getProperty("os.arch")
            val jcefSupported = JBCefApp.isSupported() // JCEF(Java Chromium Embedded Framework) 지원 여부
            val isLinuxArm = osName.lowercase().contains("linux") && (osArch.lowercase().contains("aarch64") || osArch.lowercase().contains("arm"))
            val isDarkTheme = detectCurrentTheme()
            val themeStyles = generateThemeStyles(isDarkTheme)

            // HTML과 CSS를 사용하여 UI를 구성합니다.
            return buildString {
                append("<html><head><style>$themeStyles</style></head>")
                append("<body class='${if (isDarkTheme) "dark-theme" else "light-theme"}'>")
                // ... (이하 HTML 구조 생략)
                append("</body></html>")
            }
        }

        /**
         * 현재 IntelliJ 테마가 다크 모드인지 감지합니다.
         */
        private fun detectCurrentTheme(): Boolean {
            return try {
                val background = javax.swing.UIManager.getColor("Panel.background")
                if (background != null) {
                    // 배경색의 밝기를 계산하여 0.5 미만이면 다크 모드로 판단합니다.
                    val brightness = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
                    brightness < 0.5
                } else {
                    true // 감지 실패 시 기본값으로 다크 모드를 가정합니다.
                }
            } catch (e: Exception) {
                true // 오류 발생 시에도 다크 모드를 가정합니다.
            }
        }

        /**
         * 테마에 맞는 동적 CSS 스타일을 생성합니다.
         * @param isDarkTheme 현재 테마가 다크 모드인지 여부
         */
        private fun generateThemeStyles(isDarkTheme: Boolean): String {
            // 다크/라이트 테마에 따라 다른 색상과 스타일을 반환합니다.
            // ... (CSS 내용 생략)
            return if (isDarkTheme) "..." else "..."
        }

        /**
         * 클립보드에 복사할 일반 텍스트 형식의 시스템 정보를 생성합니다.
         */
        private fun createSystemInfoPlainText(): String {
            // ... (시스템 정보 수집 및 텍스트 조합)
            return "..."
        }

        /**
         * 시스템 정보를 클립보드에 복사합니다.
         */
        private fun copySystemInfo() {
            val stringSelection = StringSelection(systemInfoText)
            val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
            clipboard.setContents(stringSelection, null)
        }

        // "알려진 이슈" 문서로 연결되는 버튼
        private val knownIssuesButton = JButton("📚 Known Issues").apply {
            // ... (버튼 스타일 및 액션 리스너 설정)
        }

        // 시스템 정보를 복사하는 버튼
        private val copyButton = JButton("📋 Copy System Info").apply {
            // ... (버튼 스타일 및 액션 리스너 설정)
        }

        // 위 두 버튼을 담는 패널
        private val buttonPanel = JPanel().apply {
            // ... (레이아웃 설정)
        }

        // 파일 드래그 앤 드롭을 처리하는 핸들러
        private var dragDropHandler: DragDropHandler? = null

        // Tool Window의 최종 콘텐츠 패널
        val content: JPanel = JPanel(BorderLayout()).apply {
            contentPanel.layout = BorderLayout()
            // 초기 UI를 설정합니다.
            updateUIContent()
            add(contentPanel, BorderLayout.CENTER)
        }

        init {
            // UI 콘텐츠를 현재 설정 상태에 맞게 초기화합니다.
            updateUIContent()
            // 설정 파일 변경을 감지하는 모니터링을 시작합니다.
            startConfigurationMonitoring()
            // IDE 테마 변경을 감지하는 리스너를 추가합니다.
            addThemeChangeListener()

            // 이미 생성된 WebView가 있는지 확인하고, 있으면 즉시 UI에 추가합니다.
            webViewManager.getLatestWebView()?.let { webView ->
                ApplicationManager.getApplication().invokeLater {
                    addWebViewComponent(webView)
                }
                // 페이지 로드가 완료되면 초기화 화면을 숨깁니다.
                webView.setPageLoadCallback {
                    ApplicationManager.getApplication().invokeLater {
                        hideSystemInfo()
                    }
                }
                if (webView.isPageLoaded()) {
                    ApplicationManager.getApplication().invokeLater {
                        hideSystemInfo()
                    }
                }
            } ?: webViewManager.addCreationCallback(this, toolWindow.disposable) // 없으면 생성 콜백을 등록합니다.
        }

        /**
         * IDE 테마 변경을 감지하여 UI를 자동으로 업데이트하는 리스너를 추가합니다.
         */
        private fun addThemeChangeListener() {
            // ... (LafManagerListener를 사용하여 테마 변경 시 updateUIContent 호출)
        }

        /**
         * 백그라운드 스레드에서 설정 파일의 변경을 주기적으로 감지합니다.
         */
        private fun startConfigurationMonitoring() {
            // ... (Thread를 생성하여 2초마다 설정 유효성을 검사하고 UI 업데이트)
        }

        /**
         * WebView가 생성되었을 때 호출되는 콜백 메소드입니다. (WebViewCreationCallback 인터페이스 구현)
         * @param instance 새로 생성된 WebView 인스턴스
         */
        override fun onWebViewCreated(instance: WebViewInstance) {
            ApplicationManager.getApplication().invokeLater {
                addWebViewComponent(instance)
            }
            instance.setPageLoadCallback {
                ApplicationManager.getApplication().invokeLater {
                    hideSystemInfo()
                }
            }
        }

        /**
         * 생성된 WebView 컴포넌트를 UI에 추가합니다.
         */
        private fun addWebViewComponent(webView: WebViewInstance) {
            // ... (contentPanel에 WebView의 Swing 컴포넌트를 추가하고 레이아웃 갱신)
            setupDragAndDropSupport(webView)
        }

        /**
         * WebView 로딩이 완료되면 초기 시스템 정보 화면을 숨깁니다.
         */
        private fun hideSystemInfo() {
            // ... (contentPanel에서 placeholderLabel과 buttonPanel을 제거)
        }

        /**
         * WebView에 파일 드래그 앤 드롭 기능을 설정합니다.
         */
        private fun setupDragAndDropSupport(webView: WebViewInstance) {
            // ... (DragDropHandler를 생성하고 설정)
        }

        /**
         * 설정이 유효하지 않을 때 보여줄 플러그인 선택 UI를 생성합니다.
         */
        private fun createPluginSelectionPanel(): JPanel {
            // ... (사용 가능한 확장 목록을 보여주고 선택할 수 있는 UI 구성)
            return JPanel()
        }
        
        /**
         * 사용 가능한 확장 목록을 보여주는 패널을 생성합니다.
         */
        private fun createPluginListPanel(): JPanel {
            // ... (ExtensionManager를 통해 확장 정보를 가져와 각 항목에 대한 UI(createPluginRow)를 생성하여 추가)
            return JPanel()
        }

        /**
         * 플러그인 목록의 각 행(Row)에 해당하는 UI를 생성합니다.
         * @param pluginInfo 표시할 플러그인의 정보 (이름, 설명, 상태 등)
         */
        private fun createPluginRow(pluginInfo: PluginInfo): JPanel {
            // ... (플러그인 이름, 설명, 상태 아이콘, VSIX 업로드 버튼 등으로 구성된 복합 패널 생성)
            // 클릭 시 applyPluginSelection을 호출하도록 이벤트 리스너 설정
            return JPanel()
        }

        /**
         * 플러그인 정보를 담는 데이터 클래스입니다.
         */
        private data class PluginInfo(
            val id: String,
            val displayName: String,
            val description: String,
            val isAvailable: Boolean,
            val isCurrent: Boolean = false
        )

        /**
         * 특정 플러그인을 위한 VSIX 파일을 업로드하는 다이얼로그를 엽니다.
         */
        private fun uploadVsixForPlugin(pluginId: String, pluginName: String) {
            // ... (VsixUploadDialog를 사용하여 파일 선택 및 업로드 처리)
        }
        
        /**
         * 현재 설정 상태를 텍스트로 보여주는 패널을 생성합니다.
         */
        private fun createConfigStatusPanel(): JPanel {
            // ... (상태를 표시할 JLabel을 포함하는 패널 생성)
            return JPanel()
        }
        
        /**
         * 설정 상태 라벨의 텍스트와 색상을 현재 상태에 맞게 업데이트합니다.
         */
        private fun updateConfigStatus(statusLabel: JLabel) {
            // ... (configManager의 상태에 따라 "실행 중", "설정 유효", "설정 오류" 등 메시지 업데이트)
        }

        /**
         * 테마에 따라 적절한 상태 표시 색상을 반환합니다.
         */
        private fun getThemeAdaptiveColor(isDarkTheme: Boolean, colorType: String): java.awt.Color {
            // ... (다크/라이트 테마 및 상태(success, warning, error)에 따라 다른 색상 반환)
            return java.awt.Color.BLACK
        }
        
        /**
         * 사용자가 선택한 플러그인을 현재 설정으로 적용하고 플러그인을 시작합니다.
         */
        private fun applyPluginSelection(pluginId: String) {
            // ... (configManager.setCurrentExtensionId를 호출하여 설정을 저장하고, startPluginAfterSelection 호출)
        }
        
        /**
         * 플러그인 선택 후, 해당 플러그인을 실제로 초기화하고 시작합니다.
         */
        private fun startPluginAfterSelection(pluginId: String) {
            // ... (ExtensionManager와 WecoderPlugin 서비스를 초기화하고, 상태 변수 업데이트)
        }
        
        /**
         * 현재 설정 상태에 따라 Tool Window의 메인 콘텐츠를 동적으로 변경합니다.
         * (예: 유효한 설정 -> 시스템 정보 표시, 유효하지 않은 설정 -> 플러그인 선택 화면 표시)
         */
        private fun updateUIContent() {
            // ... (isPluginRunning, configManager.isConfigurationValid 등의 상태를 조합하여 UI를 재구성)
        }
        
        /**
         * 수동 설정 방법을 안내하는 다이얼로그를 표시합니다.
         */
        private fun showManualConfigInstructions() {
            // ... (JOptionPane을 사용하여 설정 파일 경로와 작성법 안내)
        }

        /**
         * 현재 설정 상태와 관련된 디버그 정보를 보여주는 다이얼로그를 표시합니다.
         */
        private fun showDebugInfo() {
            // ... (JOptionPane을 사용하여 현재 설정, 파일 경로 등 디버그 정보 표시)
        }
    }
}
