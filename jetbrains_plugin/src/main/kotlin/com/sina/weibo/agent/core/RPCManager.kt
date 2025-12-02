// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.actors.*
import com.sina.weibo.agent.ipc.IMessagePassingProtocol
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol
import com.sina.weibo.agent.ipc.proxy.RPCProtocol
import com.sina.weibo.agent.ipc.proxy.logger.FileRPCProtocolLogger
import com.sina.weibo.agent.ipc.proxy.uri.IURITransformer
import com.sina.weibo.agent.theme.ThemeManager
import com.sina.weibo.agent.util.ProxyConfigUtil
import kotlinx.coroutines.runBlocking

/**
 * RPC(Remote Procedure Call) 통신을 관리하는 핵심 클래스입니다.
 * Extension Host와 IntelliJ 플러그인 간의 서비스 등록, 구현, 플러그인 생명주기 관리를 담당합니다.
 * VSCode의 `rpcManager.js` 구현을 기반으로 합니다.
 *
 * @param protocol Extension Host와의 메시지 전달 프로토콜 (IMessagePassingProtocol)
 * @param extensionManager 확장을 관리하는 서비스
 * @param uriTransformer URI 변환기 (선택 사항)
 * @param project 현재 IntelliJ 프로젝트
 */
class RPCManager(
    private val protocol: IMessagePassingProtocol,
    private val extensionManager: ExtensionManager,
    private val uriTransformer: IURITransformer? = null,
    private val project: Project
) {
    private val logger = Logger.getInstance(RPCManager::class.java)
    // 실제 RPC 통신을 처리하는 프로토콜 인스턴스
    private val rpcProtocol: IRPCProtocol = RPCProtocol(protocol, FileRPCProtocolLogger(), uriTransformer)

    init {
        // 클래스 초기화 시 모든 필요한 프로토콜 핸들러를 설정합니다.
        setupDefaultProtocols()
        setupExtensionRequiredProtocols()
        setupWeCodeRequiredProtocols()
        setupRooCodeFuncitonProtocols()
        setupKiloCodeFunctionProtocols()
        setupWebviewProtocols()
    }


    /**
     * 플러그인 환경 초기화를 시작합니다.
     * Extension Host로 설정 및 워크스페이스 정보를 전송합니다.
     */
    fun startInitialize() {
        try {
            logger.info("플러그인 환경 초기화 시작")
            runBlocking { // 코루틴을 사용하여 비동기 작업을 동기적으로 실행합니다.
                // --- 1. 설정 정보 전송 ---
                // ExtHostConfiguration 프록시를 가져와 설정 정보를 전송합니다.
                val extHostConfiguration = rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostConfiguration)

                logger.info("확장 프로세스로 설정 정보 전송 중")
                // 현재 IDE 테마에 따라 VSCode 테마 이름을 설정합니다.
                val themeName = if (ThemeManager.getInstance().isDarkThemeForce()) "Visual Studio 2017 Dark - C++" else "Visual Studio 2017 Light - C++"

                // 프록시 설정 정보를 가져옵니다.
                val httpProxyConfig = ProxyConfigUtil.getHttpProxyConfigForInitialization()

                // 기본 설정 내용을 구성합니다.
                val contentsBuilder = mutableMapOf<String, Any>(
                    "workbench.colorTheme" to themeName
                )

                // 프록시 설정이 있으면 추가합니다.
                httpProxyConfig?.let {
                    contentsBuilder["http"] = it
                    logger.info("초기화에 프록시 설정 사용: $it")
                }

                // 빈 설정 모델을 생성합니다.
                val emptyMap = mapOf(
                    "contents" to emptyMap<String, Any>(),
                    "keys" to emptyList<String>(),
                    "overrides" to emptyList<String>()
                )
                
                // Extension Host에 전달할 전체 설정 모델을 구성합니다.
                val emptyConfigModel = mapOf(
                    "defaults" to mapOf(
                        "contents" to contentsBuilder,
                        "keys" to emptyList<String>(),
                        "overrides" to emptyList<String>()
                    ),
                    "policy" to emptyMap,
                    "application" to emptyMap,
                    "userLocal" to emptyMap,
                    "userRemote" to emptyMap,
                    "workspace" to emptyMap,
                    "folders" to emptyList<Any>(),
                    "configurationScopes" to emptyList<Any>()
                )

                // ExtHostConfiguration 서비스의 `initializeConfiguration` 메소드를 호출하여 설정 정보를 전달합니다.
                extHostConfiguration.initializeConfiguration(emptyConfigModel)

                // --- 2. 워크스페이스 정보 전송 ---
                // ExtHostWorkspace 프록시를 가져와 워크스페이스 정보를 전송합니다.
                val extHostWorkspace = rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostWorkspace)

                logger.info("현재 워크스페이스 데이터 가져오는 중")
                val workspaceData = project.getService(WorkspaceManager::class.java).getCurrentWorkspaceData()

                // 워크스페이스 데이터가 있으면 전송하고, 없으면 null을 전송합니다.
                if (workspaceData != null) {
                    logger.info("확장 프로세스로 워크스페이스 데이터 전송: ${workspaceData.name}, 폴더: ${workspaceData.folders.size}")
                    extHostWorkspace.initializeWorkspace(workspaceData, true)
                } else {
                    logger.info("사용 가능한 워크스페이스 데이터가 없습니다. 확장 프로세스로 null 전송")
                    extHostWorkspace.initializeWorkspace(null, true)
                }

                logger.info("워크스페이스 초기화 완료")
            }
        } catch (e: Exception) {
            logger.error("플러그인 환경 초기화 실패: ${e.message}", e)
        }
    }

    /**
     * 기본 프로토콜 핸들러를 설정합니다.
     * 이 프로토콜들은 Extension Host 프로세스 시작 및 초기화에 필수적입니다.
     */
    private fun setupDefaultProtocols() {
        logger.info("기본 프로토콜 핸들러 설정 중")
        // PluginContext에 RPC 프로토콜 인스턴스를 설정합니다.
        PluginContext.getInstance(project).setRPCProtocol(rpcProtocol)

        // 각 MainThreadShape 구현체를 RPC 프로토콜에 등록합니다.
        // Extension Host는 이들을 호출하여 IntelliJ 플러그인의 기능을 사용합니다.
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadErrors, MainThreadErrors())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadConsole, MainThreadConsole())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadLogger, MainThreadLogger())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadCommands, MainThreadCommands(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadDebugService, MainThreadDebugService())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadConfiguration, MainThreadConfiguration())
    }

    /**
     * 플러그인 패키지 일반 로딩 프로세스에 필요한 프로토콜 핸들러를 설정합니다.
     */
    private fun setupExtensionRequiredProtocols() {
        logger.info("플러그인에 필요한 프로토콜 핸들러 설정 중")

        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadExtensionService, MainThreadExtensionService(extensionManager, rpcProtocol))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadTelemetry, MainThreadTelemetry())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadTerminalShellIntegration, MainThreadTerminalShellIntegration(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadTerminalService, MainThreadTerminalService(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadTask, MainThreadTask())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadSearch, MainThreadSearch())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadWindow, MainThreadWindow(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadDialogs, MainThreadDiaglogs())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadLanguageModelTools, MainThreadLanguageModelTools())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadClipboard, MainThreadClipboard())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadBulkEdits, MainThreadBulkEdits(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadEditorTabs, MainThreadEditorTabs(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadDocuments, MainThreadDocuments(project))
    }

    /**
     * WeCode 플러그인에 필요한 프로토콜 핸들러를 설정합니다.
     */
    private fun setupWeCodeRequiredProtocols() {
        logger.info("WeCode에 필요한 프로토콜 핸들러 설정 중")

        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadTextEditors, MainThreadTextEditors(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadStorage, MainThreadStorage())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadOutputService, MainThreadOutputService())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadWebviewViews, MainThreadWebviewViews(project))
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadDocumentContentProviders, MainThreadDocumentContentProviders())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadUrls, MainThreadUrls())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadLanguageFeatures, MainThreadLanguageFeatures())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadFileSystem, MainThreadFileSystem())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadMessageService, MainThreadMessageService())
    }

    /**
     * RooCode 특정 기능에 필요한 프로토콜 핸들러를 설정합니다.
     */
    private fun setupRooCodeFuncitonProtocols() {
        logger.info("RooCode 특정 기능에 필요한 프로토콜 핸들러 설정 중")

        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadFileSystemEventService, MainThreadFileSystemEventService())
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadSecretState, MainThreadSecretState())
    }

    /**
     * KiloCode 특정 기능에 필요한 프로토콜 핸들러를 설정합니다.
     */
    private fun setupKiloCodeFunctionProtocols() {
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadStatusBar, MainThreadStatusBar())
    }

    /**
     * Webview 관련 프로토콜 핸들러를 설정합니다.
     */
    private fun setupWebviewProtocols() {
        logger.info("Webview에 필요한 프로토콜 핸들러 설정 중")
        rpcProtocol.set(ServiceProxyRegistry.MainContext.MainThreadWebviews, MainThreadWebviews(project))
    }

    /**
     * RPC 프로토콜 인스턴스를 가져옵니다.
     */
    fun getRPCProtocol(): IRPCProtocol {
        return rpcProtocol
    }
}
