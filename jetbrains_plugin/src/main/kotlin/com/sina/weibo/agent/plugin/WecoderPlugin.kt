// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.jcef.JBCefApp
import com.sina.weibo.agent.core.ExtensionProcessManager
import com.sina.weibo.agent.core.ExtensionSocketServer
import com.sina.weibo.agent.core.ExtensionUnixDomainSocketServer
import com.sina.weibo.agent.core.ISocketServer
import com.sina.weibo.agent.extensions.core.ExtensionConfigurationManager
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.util.ExtensionUtils
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.PluginResourceUtil
import com.sina.weibo.agent.webview.WebViewManager
import com.sina.weibo.agent.workspace.WorkspaceFileChangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.CompletableFuture

/**
 * WeCode IDEA 플러그인의 진입점 클래스입니다.
 * 플러그인 초기화 및 생명주기 관리를 담당합니다.
 * `ProjectActivity`와 `StartupActivity.DumbAware`를 구현하여 프로젝트 시작 시 실행됩니다.
 */
class WecoderPlugin : ProjectActivity, StartupActivity.DumbAware {
    companion object {
        private val LOG = Logger.getInstance(WecoderPlugin::class.java)

        /**
         * 플러그인 서비스 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): WecoderPluginService {
            return project.getService(WecoderPluginService::class.java)
                ?: error("WecoderPluginService 서비스를 찾을 수 없습니다.")
        }

        /**
         * 현재 프로젝트의 기본 경로(basePath)를 가져옵니다.
         */
        @JvmStatic
        fun getProjectBasePath(project: Project): String? {
            return project.basePath
        }
    }

    /**
     * 프로젝트 시작 시 실행되는 메인 활동입니다.
     * 플러그인의 모든 핵심 컴포넌트를 초기화하고 설정합니다.
     * @param project 현재 IntelliJ 프로젝트
     */
    override fun runActivity(project: Project) {
        // IDE 및 플러그인 정보 로깅
        val appInfo = ApplicationInfo.getInstance()
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))
        val pluginVersion = plugin?.version ?: "unknown"
        val osName = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val osArch = System.getProperty("os.arch")

        LOG.info(
            "RunVSAgent 플러그인 초기화 중 (프로젝트: ${project.name}), " +
                "OS: $osName $osVersion ($osArch), " +
                "IDE: ${appInfo.fullApplicationName} (빌드 ${appInfo.build}), " +
                "플러그인 버전: $pluginVersion, " +
                "JCEF 지원: ${JBCefApp.isSupported()}",
        )

        try {
            // 1. ExtensionConfigurationManager 초기화
            val configManager = ExtensionConfigurationManager.getInstance(project)
            configManager.initialize()

            // 2. 설정 로딩 완료 대기
            var retryCount = 0
            val maxRetries = 10
            while (!configManager.isConfigurationLoaded() && retryCount < maxRetries) {
                Thread.sleep(100)
                retryCount++
            }

            // 3. 설정 유효성 검사
            if (!canProceedWithInitialization(configManager)) {
                // 기본 설정 자동 생성 허용 여부 확인
                val allowAutoCreate = System.getProperty("runvsagent.auto.create.config", "false").toBoolean()
                if (allowAutoCreate) {
                    LOG.info("기본 설정 자동 생성이 활성화되어 있습니다. 생성 시도 중...")
                    configManager.createDefaultConfiguration()

                    // 다시 설정 유효성 검사
                    if (canProceedWithInitialization(configManager)) {
                        LOG.info("기본 설정 생성 성공, 초기화 계속 진행")
                    } else {
                        LOG.warn("유효한 설정 생성 실패, 플러그인 초기화 일시 중지")
                        LOG.warn("수동으로 ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일을 생성하거나 수정해주세요.")
                        LOG.warn("그 후 IDE를 다시 시작하거나 프로젝트를 다시 로드하여 계속 진행하세요.")
                        return // 초기화 일시 중지
                    }
                } else {
                    LOG.warn("유효하지 않은 설정으로 인해 플러그인 초기화 일시 중지")
                    LOG.warn("기본 설정 자동 생성을 활성화하려면 시스템 속성: -Drunvsagent.auto.create.config=true를 설정하세요.")
                    LOG.warn("또는 수동으로 ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일을 생성/수정하세요.")
                    LOG.warn("그 후 IDE를 다시 시작하거나 프로젝트를 다시 로드하여 계속 진행하세요.")
                    return // 초기화 일시 중지
                }
            }

            // 4. 설정이 유효할 때만 ExtensionManager 초기화
            val configuredExtensionId = configManager.getCurrentExtensionId()
            if (configuredExtensionId != null) {
                val extensionManager = ExtensionManager.getInstance(project)
                extensionManager.initialize(configuredExtensionId) // 설정된 확장 ID 전달

                // 현재 확장 제공자 초기화
                extensionManager.initializeCurrentProvider()

                // 5. WecoderPluginService 초기화
                val pluginService = getInstance(project)
                pluginService.initialize(project)

                // WebViewManager 초기화 및 프로젝트 Disposer에 등록
                val webViewManager = project.getService(WebViewManager::class.java)
                Disposer.register(project, webViewManager)

                // 설정 변경 모니터링 시작
                startConfigurationMonitoring(project, configManager)

                // 프로젝트 레벨 리소스 정리 등록
                Disposer.register(
                    project,
                    Disposable {
                        LOG.info("프로젝트 '${project.name}'에 대한 RunVSAgent 플러그인 해제 중")
                        pluginService.dispose()
                        extensionManager.dispose()
                        SystemObjectProvider.dispose()
                    },
                )

                LOG.info("프로젝트 '${project.name}'에 대한 RunVSAgent 플러그인 초기화 성공")
            } else {
                LOG.error("설정은 유효하지만 확장 ID를 찾을 수 없습니다. 플러그인 초기화 일시 중지")
                return
            }
        } catch (e: Exception) {
            LOG.error("RunVSAgent 플러그인 초기화 실패", e)
        }
    }

    /**
     * 플러그인 초기화를 계속 진행할 수 있는지 확인합니다.
     * @param configManager 확장 설정 관리자
     * @return 초기화를 진행할 수 있으면 true
     */
    private fun canProceedWithInitialization(configManager: ExtensionConfigurationManager): Boolean {
        if (!configManager.isConfigurationLoaded()) {
            LOG.warn("설정이 아직 로드되지 않아 초기화를 진행할 수 없습니다.")
            return false
        }

        if (!configManager.isConfigurationValid()) {
            LOG.warn("설정이 유효하지 않아 초기화를 진행할 수 없습니다.")
            return false
        }

        val extensionId = configManager.getCurrentExtensionId()
        if (extensionId.isNullOrBlank()) {
            LOG.warn("설정에서 유효한 확장 ID를 찾을 수 없습니다.")
            return false
        }

        LOG.info("설정 유효성 검사 통과, 확장 ID: $extensionId")
        return true
    }

    /**
     * 설정 파일 변경을 감지하기 위한 모니터링을 시작합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @param configManager 확장 설정 관리자
     */
    private fun startConfigurationMonitoring(project: Project, configManager: ExtensionConfigurationManager) {
        // 백그라운드 모니터링 스레드를 시작합니다.
        Thread {
            try {
                while (!project.isDisposed) {
                    Thread.sleep(5000) // 5초마다 확인

                    if (!project.isDisposed) {
                        configManager.checkConfigurationChange() // 설정 변경 확인

                        // 설정이 유효하지 않으면 주기적으로 경고를 로깅합니다.
                        if (!configManager.isConfigurationValid()) {
                            val errorMsg = configManager.getConfigurationError() ?: "알 수 없는 설정 오류"
                            LOG.warn("설정이 여전히 유효하지 않습니다: $errorMsg")
                            LOG.warn("설정 파일: ${configManager.getConfigurationFilePath()}")
                            LOG.warn("유효하지 않은 설정으로 인해 플러그인 기능이 일시 중지됩니다.")
                            LOG.warn("설정을 수정하고 IDE를 다시 시작하여 계속 진행하세요.")
                        } else {
                            // 설정이 유효하면 가끔 성공 상태를 로깅합니다.
                            if (System.currentTimeMillis() % 60000 < 5000) { // 1분마다 로깅
                                LOG.info("설정 상태: 유효함 (${configManager.getCurrentExtensionId()})")
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                LOG.info("설정 모니터링 중단됨")
            } catch (e: Exception) {
                LOG.error("설정 모니터링 중 오류 발생", e)
            }
        }.apply {
            isDaemon = true // 데몬 스레드로 설정
            name = "RunVSAgent-ConfigMonitor"
            start()
        }
    }

    /**
     * `ProjectActivity` 인터페이스의 `execute` 메소드 구현입니다.
     * `runActivity`를 호출하여 실제 초기화 로직을 수행합니다.
     */
    override suspend fun execute(project: Project) {
        runActivity(project)
    }
}

/**
 * 디버그 모드 열거형입니다.
 * 플러그인의 디버그 동작 방식을 제어합니다.
 */
enum class DebugMode {
    ALL, // 모든 디버그 모드 (Extension Host와 직접 연결)
    IDEA, // IDEA 플러그인 디버그만 (Extension Host는 별도로 실행)
    NONE, // 디버그 비활성화
    ;

    companion object {
        /**
         * 문자열 값으로부터 해당하는 `DebugMode`를 파싱합니다.
         * @param value 문자열 값 (예: "all", "idea", "none", "true")
         * @return 해당하는 `DebugMode`
         */
        fun fromString(value: String): DebugMode {
            return when (value.lowercase()) {
                "all" -> ALL
                "idea" -> IDEA
                "true" -> ALL // 하위 호환성을 위해 "true"도 ALL로 처리
                else -> NONE
            }
        }
    }
}

/**
 * 플러그인 서비스 클래스입니다.
 * 플러그인의 전역 접근점과 핵심 기능을 제공합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class WecoderPluginService(private var currentProject: Project) : Disposable {
    private val LOG = Logger.getInstance(WecoderPluginService::class.java)

    // 초기화 완료 여부 플래그
    @Volatile
    private var isInitialized = false

    // 플러그인 초기화 완료를 나타내는 CompletableFuture
    private val initializationComplete = CompletableFuture<Boolean>()

    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 핵심 서비스 인스턴스
    private val socketServer = ExtensionSocketServer()
    private val udsSocketServer = ExtensionUnixDomainSocketServer()
    private val processManager = ExtensionProcessManager()

    companion object {
        // 디버그 모드 스위치
        @Volatile
        private var DEBUG_TYPE: DebugMode = DebugMode.NONE

        // 디버그 리소스 경로
        @Volatile
        private var DEBUG_RESOURCE: String? = null

        // 디버그 모드 연결 주소
        private const val DEBUG_HOST = "127.0.0.1"

        // 디버그 모드 연결 포트
        private const val DEBUG_PORT = 51234

        // 클래스 로드 시 설정 초기화
        private val COMPANION_LOG = Logger.getInstance(Companion::class.java)

        init {
            try {
                val properties = Properties()
                // 설정 파일(plugin.properties)을 리소스에서 읽어옵니다.
                val configStream: InputStream? = WecoderPluginService::class.java.getResourceAsStream("/com/sina/weibo/agent/plugin/config/plugin.properties")

                if (configStream != null) {
                    properties.load(configStream)
                    configStream.close()

                    // 디버그 모드 설정 읽기
                    val debugModeStr = properties.getProperty(PluginConstants.ConfigFiles.DEBUG_MODE_KEY, "none").lowercase()
                    DEBUG_TYPE = DebugMode.fromString(debugModeStr)
                    DEBUG_RESOURCE = properties.getProperty(PluginConstants.ConfigFiles.DEBUG_RESOURCE_KEY, null)

                    COMPANION_LOG.info("설정 파일에서 디버그 모드 읽음: $DEBUG_TYPE")
                } else {
                    COMPANION_LOG.warn("설정 파일을 로드할 수 없습니다. 기본 디버그 모드 사용: $DEBUG_TYPE")
                }
            } catch (e: Exception) {
                COMPANION_LOG.warn("설정 파일 읽기 오류, 기본 디버그 모드 사용: $DEBUG_TYPE", e)
            }
        }

        /**
         * 현재 디버그 모드를 가져옵니다.
         */
        @JvmStatic
        fun getDebugMode(): DebugMode {
            return DEBUG_TYPE
        }

        /**
         * 디버그 리소스 경로를 가져옵니다.
         */
        @JvmStatic
        fun getDebugResource(): String? {
            return DEBUG_RESOURCE
        }
    }

    /**
     * 플러그인 서비스 초기화
     * @param project 현재 IntelliJ 프로젝트
     */
    fun initialize(project: Project) {
        if (isInitialized) {
            LOG.info("WecoderPluginService가 이미 초기화되었습니다.")
            return
        }

        // 확장 설정의 유효성 검사
        val configManager = ExtensionConfigurationManager.getInstance(project)
        if (!configManager.isConfigurationValid()) {
            LOG.warn("플러그인 서비스 초기화 건너뜀: 유효하지 않은 설정")
            initializationComplete.complete(false)
            return
        }

        // ExtensionManager 초기화 여부 확인
        val extensionManager = ExtensionManager.getInstance(project)
        if (!extensionManager.isProperlyInitialized()) {
            LOG.warn("플러그인 서비스 초기화 건너뜀: ExtensionManager가 제대로 초기화되지 않았습니다.")
            initializationComplete.complete(false)
            return
        }

        LOG.info("WecoderPluginService 초기화 중, 디버그 모드: $DEBUG_TYPE")
        // 시스템 객체 제공자 초기화
        SystemObjectProvider.initialize(project)
        this.currentProject = project
        socketServer.project = project
        udsSocketServer.project = project

        // 시스템 객체 제공자에 플러그인 서비스 등록
        SystemObjectProvider.register("pluginService", this)

        // 백그라운드 스레드에서 초기화 시작
        coroutineScope.launch {
            try {
                initPlatformFiles() // 플랫폼 관련 파일 초기화
                val projectPath = project.basePath ?: ""

                // 서비스 프록시 레지스트리 초기화
                project.getService(com.sina.weibo.agent.core.ServiceProxyRegistry::class.java).initialize()

                if (DEBUG_TYPE == DebugMode.ALL) {
                    // ALL 디버그 모드: 디버그 포트에 직접 연결
                    LOG.info("디버그 모드 실행 중: $DEBUG_TYPE, $DEBUG_HOST:$DEBUG_PORT 에 직접 연결 시도")

                    socketServer.connectToDebugHost(DEBUG_HOST, DEBUG_PORT)

                    isInitialized = true
                    initializationComplete.complete(true)
                    LOG.info("디버그 모드 연결 성공, WecoderPluginService 초기화 완료")
                } else {
                    // 일반 모드: 소켓 서버 및 Extension Host 프로세스 시작
                    val server: ISocketServer = if (SystemInfo.isWindows) socketServer else udsSocketServer
                    val portOrPath = server.start(projectPath)
                    if (!ExtensionUtils.isValidPortOrPath(portOrPath)) {
                        LOG.error("소켓 서버 시작 실패")
                        initializationComplete.complete(false)
                        return@launch
                    }

                    LOG.info("소켓 서버 시작됨: $portOrPath")
                    if (!processManager.start(portOrPath)) {
                        LOG.error("Extension Host 프로세스 시작 실패")
                        server.stop()
                        initializationComplete.complete(false)
                        return@launch
                    }
                    isInitialized = true
                    initializationComplete.complete(true)
                    LOG.info("WecoderPluginService 초기화 완료")
                }
            } catch (e: Exception) {
                LOG.error("WecoderPluginService 초기화 중 오류 발생", e)
                cleanup()
                initializationComplete.complete(false)
            }
        }
    }

    /**
     * 플랫폼 관련 파일을 초기화합니다.
     * (예: OS 아키텍처에 맞는 Node.js 바이너리 심볼릭 링크 생성 등)
     */
    private fun initPlatformFiles() {
        val platformSuffix = when {
            SystemInfo.isWindows -> "windows-x64"
            SystemInfo.isMac -> when (System.getProperty("os.arch")) {
                "x86_64" -> "darwin-x64"
                "aarch64" -> "darwin-arm64"
                else -> ""
            }
            SystemInfo.isLinux -> "linux-x64"
            else -> ""
        }
        if (platformSuffix.isNotEmpty()) {
            val pluginDir = PluginResourceUtil.getResourcePath(PluginConstants.PLUGIN_ID, "")
                ?: throw IllegalStateException("플러그인 디렉터리를 가져올 수 없습니다.")

            val platformFile = File(pluginDir, "platform.txt")
            if (platformFile.exists()) {
                platformFile.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { originalPath ->
                        val suffixedPath = "$originalPath$platformSuffix"
                        val originalFile = File(pluginDir, "node_modules/$originalPath")
                        val suffixedFile = File(pluginDir, "node_modules/$suffixedPath")

                        if (suffixedFile.exists()) {
                            if (originalFile.exists()) {
                                originalFile.delete()
                            }
                            Files.move(
                                suffixedFile.toPath(),
                                originalFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                            originalFile.setExecutable(true)
                        }
                    }
            }
            platformFile.delete()
        }
    }

    /**
     * 초기화 완료를 기다립니다.
     * @return 초기화 성공 여부
     */
    fun waitForInitialization(): Boolean {
        return initializationComplete.get()
    }

    /**
     * 리소스를 정리합니다.
     */
    private fun cleanup() {
        try {
            // 비디버그 모드에서만 Extension Host 프로세스를 중지합니다.
            if (DEBUG_TYPE == DebugMode.NONE) {
                processManager.stop()
            }
        } catch (e: Exception) {
            LOG.error("프로세스 관리자 중지 중 오류 발생", e)
        }

        try {
            // 소켓 서버 중지
            socketServer.stop()
            udsSocketServer.stop()
        } catch (e: Exception) {
            LOG.error("소켓 서버 중지 중 오류 발생", e)
        }

        // 워크스페이스 파일 변경 리스너 등록 해제
        currentProject.getService(WorkspaceFileChangeManager::class.java).dispose()

        isInitialized = false
    }

    /**
     * 초기화 완료 여부를 가져옵니다.
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }

    /**
     * `ExtensionSocketServer` 인스턴스를 가져옵니다.
     */
    fun getSocketServer(): ExtensionSocketServer {
        return socketServer
    }

    /**
     * `ExtensionUnixDomainSocketServer` 인스턴스를 가져옵니다.
     */
    fun getUdsServer(): ExtensionUnixDomainSocketServer {
        return udsSocketServer
    }

    /**
     * `ExtensionProcessManager` 인스턴스를 가져옵니다.
     */
    fun getProcessManager(): ExtensionProcessManager {
        return processManager
    }

    /**
     * 현재 프로젝트를 가져옵니다.
     */
    fun getCurrentProject(): Project {
        return currentProject
    }

    /**
     * 서비스를 닫고 리소스를 해제합니다.
     */
    override fun dispose() {
        if (!isInitialized) {
            return
        }

        LOG.info("WecoderPluginService 해제 중")

        currentProject.getService(WebViewManager::class.java)?.dispose()

        coroutineScope.cancel() // 모든 코루틴 취소

        cleanup() // 리소스 정리

        LOG.info("WecoderPluginService 해제 완료")
    }
}
