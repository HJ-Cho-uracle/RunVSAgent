// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationInfo
import com.sina.weibo.agent.editor.EditorAndDocManager
import com.sina.weibo.agent.ipc.NodeSocket
import com.sina.weibo.agent.ipc.PersistentProtocol
import com.sina.weibo.agent.ipc.proxy.ResponsiveState
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.PluginResourceUtil
import com.sina.weibo.agent.util.URI
import com.sina.weibo.agent.workspace.WorkspaceFileChangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.Socket
import java.nio.channels.SocketChannel
import java.nio.file.Paths
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.sina.weibo.agent.extensions.core.ExtensionManager as GlobalExtensionManager
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.extensions.config.ExtensionMetadata
import com.sina.weibo.agent.extensions.core.VsixManager.Companion.getBaseDirectory
import com.sina.weibo.agent.util.PluginConstants.ConfigFiles.getUserConfigDir
import java.io.File

/**
 * Extension Host 프로세스를 관리하고 통신하는 핵심 클래스입니다.
 * Extension Host로부터 'Ready', 'Initialized' 와 같은 생명주기 메시지를 수신하고 처리하여
 * 플러그인과 Extension Host 간의 RPC 통신을 설정합니다.
 */
class ExtensionHostManager : Disposable {
    companion object {
        val LOG = Logger.getInstance(ExtensionHostManager::class.java)
    }

    private val project: Project
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Extension Host와의 통신을 위한 소켓 및 프로토콜
    private var nodeSocket: NodeSocket
    private var protocol: PersistentProtocol? = null
    
    // RPC 통신을 관리하는 매니저
    private var rpcManager: RPCManager? = null
    
    // VSCode 확장(Extension)을 관리하는 매니저
    private var extensionManager: ExtensionManager? = null
    
    // 현재 활성화된 확장 제공자
    private var currentExtensionProvider: ExtensionProvider? = null
    
    // 현재 확장의 식별자
    private var extensionIdentifier: String? = null
    
    // JSON 직렬화/역직렬화를 위한 Gson 인스턴스
    private val gson = Gson()
    
    // 진단 로그 출력 빈도를 제어하기 위한 타임스탬프
    private var lastDiagnosticLogTime = 0L

    private var projectPath: String? = null
    
    // Socket을 사용하는 생성자
    constructor(clientSocket: Socket, projectPath: String, project: Project) {
        clientSocket.tcpNoDelay = true
        this.nodeSocket = NodeSocket(clientSocket, "extension-host")
        this.projectPath = projectPath
        this.project = project
    }
    // SocketChannel을 사용하는 생성자
    constructor(clientChannel: SocketChannel, projectPath: String, project: Project) {
        this.nodeSocket = NodeSocket(clientChannel, "extension-host")
        this.projectPath = projectPath
        this.project = project
    }
    
    /**
     * Extension Host와의 통신을 시작하고 초기화 과정을 수행합니다.
     */
    fun start() {
        try {
            // 전역 확장 매니저로부터 현재 설정된 확장 제공자를 가져옵니다.
            val globalExtensionManager = GlobalExtensionManager.getInstance(project)
            currentExtensionProvider = globalExtensionManager.getCurrentProvider()
            if (currentExtensionProvider == null) {
                LOG.error("사용 가능한 확장 제공자가 없습니다.")
                dispose()
                return
            }
            
            extensionManager = ExtensionManager()
            
            val extensionConfig = currentExtensionProvider!!.getConfiguration(project)
            val extensionPath = getExtensionPath(extensionConfig)
            
            if (extensionPath != null && File(extensionPath).exists()) {
                // 확장 경로를 기반으로 확장을 등록합니다.
                val extensionDesc = extensionManager!!.registerExtension(extensionPath, extensionConfig)
                extensionIdentifier = extensionDesc.identifier.value
                LOG.info("확장 등록됨: ${currentExtensionProvider!!.getExtensionId()}")
            } else {
                LOG.error("확장 경로를 찾을 수 없음: $extensionPath")
                dispose()
                return
            }
            
            // 통신 프로토콜을 생성하고 메시지 핸들러를 등록합니다.
            protocol = PersistentProtocol(
                PersistentProtocol.PersistentProtocolOptions(socket = nodeSocket),
                this::handleMessage
            )

            LOG.info("ExtensionHostManager가 성공적으로 시작되었습니다. 확장: ${currentExtensionProvider!!.getExtensionId()}")
        } catch (e: Exception) {
            LOG.error("ExtensionHostManager 시작 실패", e)
            dispose()
        }
    }
    
    /**
     * RPC 응답 상태를 가져옵니다. (연결 상태 진단용)
     */
    fun getResponsiveState(): ResponsiveState? {
        // ... (진단 로그 출력 로직)
        return rpcManager?.getRPCProtocol()?.responsiveState
    }
    
    /**
     * Extension Host로부터 받은 메시지를 처리합니다.
     */
    private fun handleMessage(data: ByteArray) {
        // 1바이트 메시지는 Extension Host의 프로토콜 메시지로 간주합니다.
        if (data.size == 1) {
            when (ExtensionHostMessageType.fromData(data)) {
                ExtensionHostMessageType.Ready -> handleReadyMessage()
                ExtensionHostMessageType.Initialized -> handleInitializedMessage()
                ExtensionHostMessageType.Terminate -> LOG.info("종료(Terminate) 메시지 수신")
                null -> LOG.debug("알 수 없는 메시지 타입 수신: ${data.contentToString()}")
            }
        } else {
            LOG.debug("길이가 ${data.size}인 메시지 수신, Extension Host 메시지로 처리하지 않음")
        }
    }
    
    /**
     * 'Ready' 메시지를 처리합니다. Extension Host가 준비되었음을 의미하며,
     * 이에 대한 응답으로 플러그인 초기화 데이터를 전송합니다.
     */
    private fun handleReadyMessage() {
        LOG.info("Extension Host로부터 'Ready' 메시지 수신")
        try {
            val initData = createInitData()
            LOG.info("handleReadyMessage createInitData: $initData")
            
            val jsonData = gson.toJson(initData).toByteArray()
            protocol?.send(jsonData)
            LOG.info("Extension Host로 초기화 데이터 전송 완료")
        } catch (e: Exception) {
            LOG.error("'Ready' 메시지 처리 실패", e)
        }
    }
    
    /**
     * 'Initialized' 메시지를 처리합니다. Extension Host가 초기화 데이터를 받고
     * 성공적으로 초기화되었음을 의미합니다. 이제 RPC 통신을 시작할 수 있습니다.
     */
    private fun handleInitializedMessage() {
        LOG.info("Extension Host로부터 'Initialized' 메시지 수신")
        try {
            val protocol = this.protocol ?: throw IllegalStateException("프로토콜이 초기화되지 않았습니다.")
            val extensionManager = this.extensionManager ?: throw IllegalStateException("ExtensionManager가 초기화되지 않았습니다.")
            val currentProvider = this.currentExtensionProvider ?: throw IllegalStateException("확장 제공자가 초기화되지 않았습니다.")

            // RPC 매니저를 생성하고 초기화를 시작합니다.
            rpcManager = RPCManager(protocol, extensionManager, null, project)
            rpcManager?.startInitialize()

            // 파일 변경 감시 및 에디터 동기화 서비스를 시작합니다.
            project.getService(WorkspaceFileChangeManager::class.java)
            project.getService(EditorAndDocManager::class.java).initCurrentIdeaEditor()
            
            // 등록된 확장을 활성화합니다.
            val extensionId = extensionIdentifier ?: throw IllegalStateException("확장 식별자가 초기화되지 않았습니다.")
            extensionManager.activateExtension(extensionId, rpcManager!!.getRPCProtocol())
                .whenComplete { _, error ->
                    if (error != null) {
                        LOG.error("확장 활성화 실패: ${currentProvider.getExtensionId()}", error)
                    } else {
                        LOG.info("확장 활성화 성공: ${currentProvider.getExtensionId()}")
                    }
                }

            LOG.info("Extension Host 초기화 완료")
        } catch (e: Exception) {
            LOG.error("'Initialized' 메시지 처리 실패", e)
        }
    }
    
    /**
     * Extension Host로 보낼 초기화 데이터를 생성합니다.
     * VSCode의 `main.js`에 있는 `initData` 객체와 동일한 구조를 가집니다.
     */
    private fun createInitData(): Map<String, Any?> {
        val pluginDir = getPluginDir()
        val basePath = projectPath
        
        return mapOf(
            "commit" to "development",
            "version" to getIDEVersion(),
            // ... (환경 정보, 워크스페이스 정보, 확장 정보 등 다양한 초기 데이터 구성)
            "environment" to mapOf(
                "isExtensionDevelopmentDebug" to false,
                "appName" to getCurrentIDEName(),
                // ...
            ),
            "workspace" to mapOf(
                "id" to "intellij-workspace",
                "name" to "IntelliJ Workspace",
                // ...
            ),
            "extensions" to mapOf(
                "allExtensions" to (extensionManager?.getAllExtensionDescriptions() ?: emptyList<Any>()),
                // ...
            ),
            // ...
        )
    }
    
    /**
     * 현재 실행 중인 IDE의 이름을 가져옵니다. (예: "IntelliJ IDEA", "Android Studio")
     */
    private fun getCurrentIDEName(): String {
        // ... (ApplicationInfo를 사용하여 IDE 제품 코드에 따라 이름 반환)
        return "JetBrains"
    }
    
    /**
     * 현재 IDE의 버전을 가져옵니다.
     */
    private fun getIDEVersion(): String {
        // ... (ApplicationInfo와 PluginManagerCore를 사용하여 버전 정보 조합)
        return "1.0.0"
    }
    
    /**
     * 현재 플러그인의 루트 디렉터리 경로를 가져옵니다.
     */
    private fun getPluginDir(): String {
        return PluginResourceUtil.getResourcePath(PluginConstants.PLUGIN_ID, "")
            ?: throw IllegalStateException("플러그인 디렉터리를 가져올 수 없습니다.")
    }
    
    /**
     * 설정에 따라 실제 확장 파일이 위치한 경로를 찾습니다.
     */
    private fun getExtensionPath(extensionConfig: ExtensionMetadata): String? {
        // ... (프로젝트 경로, 플러그인 리소스 경로 등 여러 위치를 순차적으로 탐색)
        return null
    }
    
    /**
     * 파일 경로 문자열로부터 URI 객체를 생성합니다.
     */
    private fun uriFromPath(path: String): URI {
        return URI.file(path)
    }
    
    /**
     * 리소스를 해제합니다.
     */
    override fun dispose() {
        LOG.info("ExtensionHostManager 해제 중")
        coroutineScope.cancel()
        rpcManager = null
        protocol?.dispose()
        protocol = null
        nodeSocket.dispose()
        LOG.info("ExtensionHostManager 해제 완료")
    }
}
