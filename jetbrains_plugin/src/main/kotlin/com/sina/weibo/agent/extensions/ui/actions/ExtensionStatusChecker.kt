// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui.actions

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.ProxyConfigUtil
import com.sina.weibo.agent.webview.WebViewManager
import java.awt.datatransfer.StringSelection
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * 확장 상태를 확인하고 문제를 진단하는 액션입니다.
 * 이 액션은 IntelliJ의 메뉴나 툴바를 통해 실행될 수 있습니다.
 */
class ExtensionStatusChecker : AnAction("확장 상태 확인") {

    private val logger = Logger.getInstance(ExtensionStatusChecker::class.java)

    /**
     * 액션이 수행될 때 호출됩니다.
     * 확장 상태를 확인하고 결과를 다이얼로그로 표시합니다.
     * @param e 액션 이벤트 객체
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return // 현재 프로젝트 가져오기

        val status = checkExtensionStatus(project) // 확장 상태 확인 로직 실행
        showStatusDialog(status) // 결과를 다이얼로그로 표시
    }

    /**
     * 플러그인 및 확장과 관련된 다양한 상태 정보를 수집하여 문자열로 반환합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 수집된 상태 정보를 담은 문자열
     */
    private fun checkExtensionStatus(project: Project): String {
        val sb = StringBuilder()
        sb.appendLine("🔍 확장 상태 확인")
        sb.appendLine("=".repeat(50))

        // 1. 시스템 정보 추가
        addSystemInformation(sb)

        // 2. Extension Manager 상태 확인
        try {
            val extensionManager = ExtensionManager.getInstance(project)
            val currentProvider = extensionManager.getCurrentProvider()
            sb.appendLine("📋 현재 확장 제공자: ${currentProvider?.getExtensionId() ?: "없음"}")
            sb.appendLine("📋 현재 확장 이름: ${currentProvider?.getDisplayName() ?: "없음"}")
        } catch (e: Exception) {
            sb.appendLine("❌ 확장 관리자 오류: ${e.message}")
        }

        // 3. Plugin Context 및 RPC 프로토콜 상태 확인
        try {
            val pluginContext = project.getService(PluginContext::class.java)
            if (pluginContext != null) {
                sb.appendLine("✅ PluginContext: 사용 가능")

                val rpcProtocol = pluginContext.getRPCProtocol()
                if (rpcProtocol != null) {
                    sb.appendLine("✅ RPC 프로토콜: 사용 가능")

                    val commandsProxy = rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostCommands)
                    if (commandsProxy != null) {
                        sb.appendLine("✅ ExtHostCommands 프록시: 사용 가능")
                    } else {
                        sb.appendLine("❌ ExtHostCommands 프록시: 사용 불가")
                    }
                } else {
                    sb.appendLine("❌ RPC 프로토콜: 사용 불가")
                }
            } else {
                sb.appendLine("❌ PluginContext: 사용 불가")
            }
        } catch (e: Exception) {
            sb.appendLine("❌ 플러그인 컨텍스트 오류: ${e.message}")
        }

        // 4. 사용 가능한 확장 목록 확인
        try {
            val extensionManager = ExtensionManager.getInstance(project)
            val availableProviders = extensionManager.getAvailableProviders()
            sb.appendLine("\n📋 사용 가능한 확장:")
            availableProviders.forEach { provider ->
                sb.appendLine("  - ${provider.getExtensionId()}: ${provider.getDisplayName()}")
            }
        } catch (e: Exception) {
            sb.appendLine("❌ 사용 가능한 확장 가져오기 오류: ${e.message}")
        }

        // 5. WebView 상태 확인
        try {
            val webViewManager = project.getService(WebViewManager::class.java)
            if (webViewManager != null) {
                sb.appendLine("\n🌐 WebView 상태:")
                val latestWebView = webViewManager.getLatestWebView()
                if (latestWebView != null) {
                    sb.appendLine("✅ 최신 WebView: 사용 가능")
                } else {
                    sb.appendLine("❌ 최신 WebView: 사용 불가")
                }
            } else {
                sb.appendLine("\n❌ WebView 관리자: 사용 불가")
            }
        } catch (e: Exception) {
            sb.appendLine("\n❌ WebView 상태 오류: ${e.message}")
        }

        // 6. 프록시 상태 확인
        try {
            val proxyConfig = ProxyConfigUtil.getProxyConfig()
            sb.appendLine("\n🌐 프록시 상태:")

            val sourceDescription = when (proxyConfig.source) {
                "ide-pac" -> "IDE 설정 (PAC)"
                "ide-http" -> "IDE 설정 (HTTP 프록시)"
                "ide-none" -> "IDE 설정 (프록시 없음)"
                "env" -> "환경 변수"
                "none" -> "프록시 설정 없음"
                "ide-error" -> "IDE 설정 (오류)"
                "env-error" -> "환경 변수 (오류)"
                else -> proxyConfig.source
            }
            sb.appendLine("  출처: $sourceDescription")

            if (proxyConfig.hasProxy) {
                if (!proxyConfig.pacUrl.isNullOrEmpty()) {
                    sb.appendLine("  PAC URL: ${proxyConfig.pacUrl}")
                } else if (!proxyConfig.proxyUrl.isNullOrEmpty()) {
                    sb.appendLine("  프록시 URL: ${proxyConfig.proxyUrl}")
                }

                if (!proxyConfig.proxyExceptions.isNullOrEmpty()) {
                    sb.appendLine("  프록시 예외: ${proxyConfig.proxyExceptions}")
                }
            } else {
                sb.appendLine("  프록시 설정 없음")
            }
        } catch (e: Exception) {
            sb.appendLine("\n❌ 프록시 상태 오류: ${e.message}")
        }

        return sb.toString()
    }

    /**
     * 상태 보고서에 시스템 정보를 추가합니다.
     */
    private fun addSystemInformation(sb: StringBuilder) {
        try {
            val appInfo = ApplicationInfo.getInstance()
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))
            val pluginVersion = plugin?.version ?: "알 수 없음"
            val osName = System.getProperty("os.name")
            val osVersion = System.getProperty("os.version")
            val osArch = System.getProperty("os.arch")
            val jcefSupported = JBCefApp.isSupported() // JCEF 지원 여부

            // Linux ARM 시스템 여부 확인
            val isLinuxArm = osName.lowercase().contains("linux") && (osArch.lowercase().contains("aarch64") || osArch.lowercase().contains("arm"))

            sb.appendLine("\n📊 시스템 정보:")
            sb.appendLine("  💻 CPU 아키텍처: $osArch")
            sb.appendLine("  🖥️ 운영체제: $osName $osVersion")
            sb.appendLine("  🔧 IDE 버전: ${appInfo.fullApplicationName} (빌드 ${appInfo.build})")
            sb.appendLine("  📦 플러그인 버전: $pluginVersion")
            sb.appendLine("  🌐 JCEF 지원: ${if (jcefSupported) "✅ 예" else "❌ 아니오"}")

            // 지원되지 않는 구성에 대한 경고 추가
            if (isLinuxArm) {
                sb.appendLine("  ⚠️ 경고: Linux ARM 시스템은 현재 지원되지 않습니다.")
            }

            if (!jcefSupported) {
                sb.appendLine("  ❌ 경고: JCEF 미지원 - WebView 기능이 작동하지 않을 수 있습니다.")
            }
        } catch (e: Exception) {
            sb.appendLine("\n❌ 시스템 정보 오류: ${e.message}")
        }
    }

    /**
     * 수집된 상태 정보를 다이얼로그로 표시합니다.
     * @param status 표시할 상태 정보 문자열
     */
    private fun showStatusDialog(status: String) {
        val dialog = ExtensionStatusDialog(status)
        dialog.show()
    }

    /**
     * 확장 상태를 표시하는 내부 다이얼로그 클래스입니다.
     */
    private class ExtensionStatusDialog(private val statusText: String) : DialogWrapper(true) {

        init {
            title = "확장 상태" // 다이얼로그 제목
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

            val textArea = JTextArea(statusText) // 상태 텍스트를 표시할 텍스트 영역
            textArea.isEditable = false // 편집 불가능
            textArea.font = JLabel().font // 기본 폰트 사용
            textArea.background = JLabel().background // 기본 배경색 사용

            val scrollPane = JScrollPane(textArea) // 스크롤 가능한 텍스트 영역
            scrollPane.preferredSize = java.awt.Dimension(600, 400)
            scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED

            panel.add(scrollPane)
            return panel
        }

        /**
         * 다이얼로그 하단에 표시될 액션 버튼들을 생성합니다.
         * "클립보드에 복사" 버튼과 "OK" 버튼을 제공합니다.
         */
        override fun createActions(): Array<Action> {
            val copyAction = object : AbstractAction("클립보드에 복사") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val selection = StringSelection(statusText)
                    CopyPasteManager.getInstance().setContents(selection)
                    Messages.showInfoMessage("상태 정보가 클립보드에 복사되었습니다!", "복사 완료")
                }
            }

            return arrayOf(copyAction, okAction)
        }
    }
}
