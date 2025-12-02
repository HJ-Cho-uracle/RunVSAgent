// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.actors.*
import com.sina.weibo.agent.ipc.proxy.createProxyIdentifier
import com.sina.weibo.agent.ipc.proxy.interfaces.*

/**
 * 서비스 프록시 레지스트리 클래스입니다.
 * RPC 통신에 사용되는 모든 서비스 프록시의 등록을 중앙에서 관리합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class ServiceProxyRegistry private constructor() {
    private val logger = Logger.getInstance(this::class.java)
    
    /**
     * 모든 서비스 프록시 식별자를 초기화하고 등록합니다.
     * 이 메소드는 플러그인이 생성될 때 모든 서비스가 초기화되도록 보장합니다.
     */
    fun initialize() {
        logger.info("모든 프록시 식별자 초기화")
        
        // Main 스레드(IntelliJ 플러그인)에서 Extension Host로 호출할 서비스 프록시들
        val mainThreadProxies = listOf(
            MainContext.MainThreadAuthentication,
            MainContext.MainThreadBulkEdits,
            // ... (나머지 MainContext 프록시들)
        )
        
        // Extension Host에서 Main 스레드(IntelliJ 플러그인)로 호출할 서비스 프록시들
        val extHostProxies = listOf(
            ExtHostContext.ExtHostCodeMapper,
            ExtHostContext.ExtHostCommands,
            // ... (나머지 ExtHostContext 프록시들)
        )
        
        logger.info("${mainThreadProxies.size}개의 메인 스레드 서비스와 ${extHostProxies.size}개의 확장 호스트 서비스 초기화됨")
    }
    

    /**
     * Main 스레드(IntelliJ 플러그인)에서 Extension Host로 호출할 서비스들의 컨텍스트 ID를 정의합니다.
     * 이 ID들은 VSCode에서 정의된 컨텍스트 ID 열거형 값에 해당합니다.
     */
    object MainContext {
        // 각 서비스에 대한 프록시 식별자를 생성합니다.
        // createProxyIdentifier<T>(id: String) 함수는 T 타입의 프록시를 생성하기 위한 고유 ID를 반환합니다.
        val MainThreadAuthentication = createProxyIdentifier<Any>("MainThreadAuthentication")
        val MainThreadBulkEdits = createProxyIdentifier<MainThreadBulkEditsShape>("MainThreadBulkEdits")
        val MainThreadLanguageModels = createProxyIdentifier<Any>("MainThreadLanguageModels")
        val MainThreadEmbeddings = createProxyIdentifier<Any>("MainThreadEmbeddings")
        val MainThreadChatAgents2 = createProxyIdentifier<Any>("MainThreadChatAgents2")
        val MainThreadCodeMapper = createProxyIdentifier<Any>("MainThreadCodeMapper")
        val MainThreadLanguageModelTools = createProxyIdentifier<MainThreadLanguageModelToolsShape>("MainThreadLanguageModelTools")
        val MainThreadClipboard = createProxyIdentifier<MainThreadClipboardShape>("MainThreadClipboard")
        val MainThreadCommands = createProxyIdentifier<MainThreadCommandsShape>("MainThreadCommands")
        val MainThreadComments = createProxyIdentifier<Any>("MainThreadComments")
        val MainThreadConfiguration = createProxyIdentifier<MainThreadConfigurationShape>("MainThreadConfiguration")
        val MainThreadConsole = createProxyIdentifier<MainThreadConsoleShape>("MainThreadConsole")
        val MainThreadDebugService = createProxyIdentifier<MainThreadDebugServiceShape>("MainThreadDebugService")
        val MainThreadDecorations = createProxyIdentifier<Any>("MainThreadDecorations")
        val MainThreadDiagnostics = createProxyIdentifier<Any>("MainThreadDiagnostics")
        val MainThreadDialogs = createProxyIdentifier<MainThreadDiaglogsShape>("MainThreadDiaglogs")
        val MainThreadDocuments = createProxyIdentifier<MainThreadDocumentsShape>("MainThreadDocuments")
        val MainThreadDocumentContentProviders = createProxyIdentifier<MainThreadDocumentContentProvidersShape>("MainThreadDocumentContentProviders")
        val MainThreadTextEditors = createProxyIdentifier<MainThreadTextEditorsShape>("MainThreadTextEditors")
        val MainThreadEditorInsets = createProxyIdentifier<Any>("MainThreadEditorInsets")
        val MainThreadEditorTabs = createProxyIdentifier<MainThreadEditorTabsShape>("MainThreadEditorTabs")
        val MainThreadErrors = createProxyIdentifier<MainThreadErrorsShape>("MainThreadErrors")
        val MainThreadTreeViews = createProxyIdentifier<Any>("MainThreadTreeViews")
        val MainThreadDownloadService = createProxyIdentifier<Any>("MainThreadDownloadService")
        val MainThreadLanguageFeatures = createProxyIdentifier<MainThreadLanguageFeaturesShape>("MainThreadLanguageFeatures")
        val MainThreadLanguages = createProxyIdentifier<Any>("MainThreadLanguages")
        val MainThreadLogger = createProxyIdentifier<MainThreadLoggerShape>("MainThreadLogger")
        val MainThreadMessageService = createProxyIdentifier<MainThreadMessageServiceShape>("MainThreadMessageService")
        val MainThreadOutputService = createProxyIdentifier<MainThreadOutputServiceShape>("MainThreadOutputService")
        val MainThreadProgress = createProxyIdentifier<Any>("MainThreadProgress")
        val MainThreadQuickDiff = createProxyIdentifier<Any>("MainThreadQuickDiff")
        val MainThreadQuickOpen = createProxyIdentifier<Any>("MainThreadQuickOpen")
        val MainThreadStatusBar = createProxyIdentifier<MainThreadStatusBarShape>("MainThreadStatusBar")
        val MainThreadSecretState = createProxyIdentifier<MainThreadSecretStateShape>("MainThreadSecretState")
        val MainThreadStorage = createProxyIdentifier<MainThreadStorageShape>("MainThreadStorage")
        val MainThreadSpeech = createProxyIdentifier<Any>("MainThreadSpeechProvider")
        val MainThreadTelemetry = createProxyIdentifier<MainThreadTelemetryShape>("MainThreadTelemetry")
        val MainThreadTerminalService = createProxyIdentifier<MainThreadTerminalServiceShape>("MainThreadTerminalService")
        val MainThreadTerminalShellIntegration = createProxyIdentifier<MainThreadTerminalShellIntegrationShape>("MainThreadTerminalShellIntegration")
        val MainThreadWebviews = createProxyIdentifier<MainThreadWebviewsShape>("MainThreadWebviews")
        val MainThreadWebviewPanels = createProxyIdentifier<Any>("MainThreadWebviewPanels")
        val MainThreadWebviewViews = createProxyIdentifier<MainThreadWebviewViewsShape>("MainThreadWebviewViews")
        val MainThreadCustomEditors = createProxyIdentifier<Any>("MainThreadCustomEditors")
        val MainThreadUrls = createProxyIdentifier<MainThreadUrlsShape>("MainThreadUrls")
        val MainThreadUriOpeners = createProxyIdentifier<Any>("MainThreadUriOpeners")
        val MainThreadProfileContentHandlers = createProxyIdentifier<Any>("MainThreadProfileContentHandlers")
        val MainThreadWorkspace = createProxyIdentifier<Any>("MainThreadWorkspace")
        val MainThreadFileSystem = createProxyIdentifier<MainThreadFileSystemShape>("MainThreadFileSystem")
        val MainThreadFileSystemEventService = createProxyIdentifier<MainThreadFileSystemEventServiceShape>("MainThreadFileSystemEventService")
        val MainThreadExtensionService = createProxyIdentifier<MainThreadExtensionServiceShape>("MainThreadExtensionService")
        val MainThreadSCM = createProxyIdentifier<Any>("MainThreadSCM")
        val MainThreadSearch = createProxyIdentifier<MainThreadSearchShape>("MainThreadSearch")
        val MainThreadShare = createProxyIdentifier<Any>("MainThreadShare")
        val MainThreadTask = createProxyIdentifier<MainThreadTaskShape>("MainThreadTask")
        val MainThreadWindow = createProxyIdentifier<MainThreadWindowShape>("MainThreadWindow")
        val MainThreadLabelService = createProxyIdentifier<Any>("MainThreadLabelService")
        val MainThreadNotebook = createProxyIdentifier<Any>("MainThreadNotebook")
        val MainThreadNotebookDocuments = createProxyIdentifier<Any>("MainThreadNotebookDocumentsShape")
        val MainThreadNotebookEditors = createProxyIdentifier<Any>("MainThreadNotebookEditorsShape")
        val MainThreadNotebookKernels = createProxyIdentifier<Any>("MainThreadNotebookKernels")
        val MainThreadNotebookRenderers = createProxyIdentifier<Any>("MainThreadNotebookRenderers")
        val MainThreadInteractive = createProxyIdentifier<Any>("MainThreadInteractive")
        val MainThreadTheming = createProxyIdentifier<Any>("MainThreadTheming")
        val MainThreadTunnelService = createProxyIdentifier<Any>("MainThreadTunnelService")
        val MainThreadManagedSockets = createProxyIdentifier<Any>("MainThreadManagedSockets")
        val MainThreadTimeline = createProxyIdentifier<Any>("MainThreadTimeline")
        val MainThreadTesting = createProxyIdentifier<Any>("MainThreadTesting")
        val MainThreadLocalization = createProxyIdentifier<Any>("MainThreadLocalizationShape")
        val MainThreadMcp = createProxyIdentifier<Any>("MainThreadMcpShape")
        val MainThreadAiRelatedInformation = createProxyIdentifier<Any>("MainThreadAiRelatedInformation")
        val MainThreadAiEmbeddingVector = createProxyIdentifier<Any>("MainThreadAiEmbeddingVector")
        val MainThreadChatStatus = createProxyIdentifier<Any>("MainThreadChatStatus")
    }

    /**
     * Extension Host에서 Main 스레드(IntelliJ 플러그인)로 호출할 서비스들의 컨텍스트 ID를 정의합니다.
     * 이 ID들은 VSCode에서 정의된 컨텍스트 ID 열거형 값에 해당합니다.
     */
    object ExtHostContext {
        val ExtHostCodeMapper = createProxyIdentifier<Any>("ExtHostCodeMapper")
        val ExtHostCommands = createProxyIdentifier<ExtHostCommandsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostCommandsProxy")
        val ExtHostConfiguration = createProxyIdentifier<ExtHostConfigurationProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostConfigurationProxy")
        val ExtHostDiagnostics = createProxyIdentifier<Any>("ExtHostDiagnostics")
        val ExtHostDebugService = createProxyIdentifier<Any>("ExtHostDebugService")
        val ExtHostDecorations = createProxyIdentifier<Any>("ExtHostDecorations")
        val ExtHostDocumentsAndEditors = createProxyIdentifier<ExtHostDocumentsAndEditorsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostDocumentsAndEditorsProxy")
        val ExtHostDocuments = createProxyIdentifier<ExtHostDocumentsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostDocumentsProxy")
        val ExtHostDocumentContentProviders = createProxyIdentifier<Any>("ExtHostDocumentContentProviders")
        val ExtHostDocumentSaveParticipant = createProxyIdentifier<Any>("ExtHostDocumentSaveParticipant")
        val ExtHostEditors = createProxyIdentifier<ExtHostEditorsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostEditorsProxy")
        val ExtHostTreeViews = createProxyIdentifier<Any>("ExtHostTreeViews")
        val ExtHostFileSystem = createProxyIdentifier<Any>("ExtHostFileSystem")
        val ExtHostFileSystemInfo = createProxyIdentifier<Any>("ExtHostFileSystemInfo")
        val ExtHostFileSystemEventService = createProxyIdentifier<ExtHostFileSystemEventServiceProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostFileSystemEventServiceProxy")
        val ExtHostLanguages = createProxyIdentifier<Any>("ExtHostLanguages")
        val ExtHostLanguageFeatures = createProxyIdentifier<Any>("ExtHostLanguageFeatures")
        val ExtHostQuickOpen = createProxyIdentifier<Any>("ExtHostQuickOpen")
        val ExtHostQuickDiff = createProxyIdentifier<Any>("ExtHostQuickDiff")
        val ExtHostStatusBar = createProxyIdentifier<Any>("ExtHostStatusBar")
        val ExtHostShare = createProxyIdentifier<Any>("ExtHostShare")
        val ExtHostExtensionService = createProxyIdentifier<ExtHostExtensionServiceProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostExtensionServiceProxy")
        val ExtHostLogLevelServiceShape = createProxyIdentifier<Any>("ExtHostLogLevelServiceShape")
        val ExtHostTerminalService = createProxyIdentifier<ExtHostTerminalServiceProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostTerminalServiceProxy")
        val ExtHostTerminalShellIntegration = createProxyIdentifier<ExtHostTerminalShellIntegrationProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostTerminalShellIntegrationProxy")
        val ExtHostSCM = createProxyIdentifier<Any>("ExtHostSCM")
        val ExtHostSearch = createProxyIdentifier<Any>("ExtHostSearch")
        val ExtHostTask = createProxyIdentifier<Any>("ExtHostTask")
        val ExtHostWorkspace = createProxyIdentifier<ExtHostWorkspaceProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostWorkspaceProxy")
        val ExtHostWindow = createProxyIdentifier<Any>("ExtHostWindow")
        val ExtHostWebviews = createProxyIdentifier<ExtHostWebviewsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostWebviewsProxy")
        val ExtHostWebviewPanels = createProxyIdentifier<Any>("ExtHostWebviewPanels")
        val ExtHostCustomEditors = createProxyIdentifier<Any>("ExtHostCustomEditors")
        val ExtHostWebviewViews = createProxyIdentifier<ExtHostWebviewViewsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostWebviewViewsProxy")
        val ExtHostEditorInsets = createProxyIdentifier<Any>("ExtHostEditorInsets")
        val ExtHostEditorTabs = createProxyIdentifier<ExtHostEditorTabsProxy>("com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostEditorTabsProxy")
        val ExtHostProgress = createProxyIdentifier<Any>("ExtHostProgress")
        val ExtHostComments = createProxyIdentifier<Any>("ExtHostComments")
        val ExtHostSecretState = createProxyIdentifier<Any>("ExtHostSecretState")
        val ExtHostStorage = createProxyIdentifier<Any>("ExtHostStorage")
        val ExtHostUrls = createProxyIdentifier<Any>("ExtHostUrls")
        val ExtHostUriOpeners = createProxyIdentifier<Any>("ExtHostUriOpeners")
        val ExtHostProfileContentHandlers = createProxyIdentifier<Any>("ExtHostProfileContentHandlers")
        val ExtHostOutputService = createProxyIdentifier<Any>("ExtHostOutputService")
        val ExtHostLabelService = createProxyIdentifier<Any>("ExtHostLabelService")
        val ExtHostNotebook = createProxyIdentifier<Any>("ExtHostNotebook")
        val ExtHostNotebookDocuments = createProxyIdentifier<Any>("ExtHostNotebookDocuments")
        val ExtHostNotebookEditors = createProxyIdentifier<Any>("ExtHostNotebookEditors")
        val ExtHostNotebookKernels = createProxyIdentifier<Any>("ExtHostNotebookKernels")
        val ExtHostNotebookRenderers = createProxyIdentifier<Any>("ExtHostNotebookRenderers")
        val ExtHostNotebookDocumentSaveParticipant = createProxyIdentifier<Any>("ExtHostNotebookDocumentSaveParticipant")
        val ExtHostInteractive = createProxyIdentifier<Any>("ExtHostInteractive")
        val ExtHostChatAgents2 = createProxyIdentifier<Any>("ExtHostChatAgents")
        val ExtHostLanguageModelTools = createProxyIdentifier<Any>("ExtHostChatSkills")
        val ExtHostChatProvider = createProxyIdentifier<Any>("ExtHostChatProvider")
        val ExtHostSpeech = createProxyIdentifier<Any>("ExtHostSpeech")
        val ExtHostEmbeddings = createProxyIdentifier<Any>("ExtHostEmbeddings")
        val ExtHostAiRelatedInformation = createProxyIdentifier<Any>("ExtHostAiRelatedInformation")
        val ExtHostAiEmbeddingVector = createProxyIdentifier<Any>("ExtHostAiEmbeddingVector")
        val ExtHostTheming = createProxyIdentifier<Any>("ExtHostTheming")
        val ExtHostTunnelService = createProxyIdentifier<Any>("ExtHostTunnelService")
        val ExtHostManagedSockets = createProxyIdentifier<Any>("ExtHostManagedSockets")
        val ExtHostAuthentication = createProxyIdentifier<Any>("ExtHostAuthentication")
        val ExtHostTimeline = createProxyIdentifier<Any>("ExtHostTimeline")
        val ExtHostTesting = createProxyIdentifier<Any>("ExtHostTesting")
        val ExtHostTelemetry = createProxyIdentifier<Any>("ExtHostTelemetry")
        val ExtHostLocalization = createProxyIdentifier<Any>("ExtHostLocalization")
        val ExtHostMcp = createProxyIdentifier<Any>("ExtHostMcp")
    }
} 