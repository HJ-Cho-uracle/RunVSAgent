// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.webview.WebViewInstance

/**
 * 지정된 ID를 가진 VSCode 명령을 실행하는 헬퍼 함수입니다.
 * 이 함수는 내부적으로 RPC(Remote Procedure Call) 프로토콜을 사용하여
 * IntelliJ 플러그인과 Node.js 기반의 Extension Host 간에 통신합니다.
 *
 * @param commandId 실행할 VSCode 명령의 고유 식별자입니다. (예: "workbench.action.quickOpen")
 * @param project 현재 열려있는 IntelliJ 프로젝트의 컨텍스트입니다. RPC 통신에 필요한 서비스를 얻기 위해 사용됩니다.
 * @param args 명령 실행 시 함께 전달할 인자들입니다. 가변 인자로 여러 개의 값을 전달할 수 있습니다.
 * @param hasArgs 인자가 있는지 여부를 명시적으로 지정합니다. true일 경우 인자와 함께, false일 경우 인자 없이 명령을 호출합니다.
 */
fun executeCommand(commandId: String, project: Project?, vararg args: Any?, hasArgs: Boolean? = true) {
    // 로깅을 위한 Logger 인스턴스를 가져옵니다.
    val logger = Logger.getInstance("VSCodeCommandActions")
    logger.info("🔍 VSCode 명령 실행 시도: commandId=$commandId")

    // 프로젝트 컨텍스트가 없으면 명령을 실행할 수 없으므로 경고를 기록하고 함수를 종료합니다.
    if (project == null) {
        logger.warn("❌ 프로젝트가 null이므로 명령을 실행할 수 없습니다.")
        return
    }

    try {
        // 프로젝트로부터 플러그인 전역 컨텍스트(PluginContext) 서비스를 가져옵니다.
        val pluginContext = project.getService(PluginContext::class.java)
        if (pluginContext == null) {
            logger.warn("❌ PluginContext 서비스를 찾을 수 없습니다.")
            return
        }

        // PluginContext로부터 RPC 프로토콜 인스턴스를 가져옵니다.
        val rpcProtocol = pluginContext.getRPCProtocol()
        if (rpcProtocol == null) {
            logger.warn("❌ RPC 프로토콜을 찾을 수 없습니다. Extension Host가 실행 중인지 확인하세요.")
            return
        }

        // RPC 프로토콜을 통해 Extension Host의 'ExtHostCommands' 서비스에 대한 프록시 객체를 가져옵니다.
        // 이 프록시를 통해 Extension Host에 정의된 함수를 호출할 수 있습니다.
        val proxy = rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostCommands)

        logger.info("🔍 RPC를 통해 명령 실행: commandId=$commandId, 인자 개수=${args.size}")
        // 인자 존재 여부에 따라 다른 메소드를 호출합니다.
        if (hasArgs == true) {
            // 원격 서비스의 'executeContributedCommand' 메소드를 인자와 함께 호출합니다.
            proxy.executeContributedCommand(commandId, args)
        } else {
            // 원격 서비스의 'executeContributedCommand' 메소드를 인자 없이 호출합니다.
            proxy.executeContributedCommand(commandId)
        }

        logger.info("✅ Extension Host로 명령 전송 완료: $commandId")
    } catch (e: Exception) {
        // 명령 실행 중 발생할 수 있는 모든 예외를 처리하고 에러 로그를 남깁니다.
        logger.error("❌ 명령 실행 중 오류 발생: $commandId", e)
    }
}

/**
 * WebView의 개발자 도구를 여는 IntelliJ 액션(AnAction) 클래스입니다.
 * AnAction을 상속받아 메뉴, 툴바 버튼 등 UI 요소를 통해 실행될 수 있습니다.
 *
 * @property getWebViewInstance 생성자에서 함수를 전달받아, 액션이 실행될 때 현재 활성화된 WebView 인스턴스를 가져옵니다.
 *                            람다 함수를 사용함으로써, 액션이 생성되는 시점이 아닌 실행되는 시점에 동적으로 WebView를 결정할 수 있습니다.
 */
class OpenDevToolsAction(private val getWebViewInstance: () -> WebViewInstance?) :
    AnAction("Open Developer Tools") { // 액션의 UI에 표시될 텍스트
    private val logger: Logger = Logger.getInstance(OpenDevToolsAction::class.java)

    /**
     * 사용자가 액션(예: 메뉴 클릭)을 수행했을 때 호출되는 메소드입니다.
     *
     * @param e 액션 이벤트 객체로, 현재 프로젝트, UI 위치 등 다양한 컨텍스트 정보를 담고 있습니다.
     */
    override fun actionPerformed(e: AnActionEvent) {
        // 생성자에서 전달받은 함수를 호출하여 현재 WebView 인스턴스를 가져옵니다.
        val webView = getWebViewInstance()
        if (webView != null) {
            // WebView 인스턴스가 존재하면, 해당 WebView의 개발자 도구를 엽니다.
            webView.openDevTools()
        } else {
            // WebView 인스턴스를 찾을 수 없는 경우, 경고 로그를 남깁니다.
            logger.warn("WebView 인스턴스를 찾을 수 없어 개발자 도구를 열 수 없습니다.")
        }
    }
}
