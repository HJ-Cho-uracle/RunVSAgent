// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.pty4j.PtyProcess
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostTerminalShellIntegrationProxy
import com.sina.weibo.agent.ipc.proxy.interfaces.ShellLaunchConfigDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget

/**
 * 터미널 인스턴스 클래스입니다.
 * 단일 터미널의 생명주기 및 작업을 관리합니다. 다음을 포함합니다:
 * - 터미널 생성 및 초기화
 * - ExtHost 프로세스와의 RPC 통신
 * - 셸 통합 관리
 * - 터미널 표시 및 숨기기
 * - 텍스트 전송 및 명령어 실행
 * - 리소스 정리 및 해제
 *
 * @property extHostTerminalId ExtHost 프로세스 내 터미널 식별자
 * @property numericId RPC 통신을 위한 숫자 ID
 * @property project IDEA 프로젝트 인스턴스
 * @property config 터미널 설정 파라미터
 * @property rpcProtocol RPC 프로토콜 인스턴스
 */
class TerminalInstance(
    val extHostTerminalId: String,
    val numericId: Int,
    val project: Project,
    private val config: TerminalConfig,
    private val rpcProtocol: IRPCProtocol
) : Disposable {

    companion object {
        private const val DEFAULT_TERMINAL_NAME = "roo-cline" // 기본 터미널 이름
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal" // 터미널 툴 윈도우 ID
    }

    private val logger = Logger.getInstance(TerminalInstance::class.java)

    // 터미널 컴포넌트
    private var terminalWidget: TerminalWidget? = null
    private var shellWidget: ShellTerminalWidget? = null

    // 상태 관리
    private val state = TerminalState()

    // 코루틴 스코프 (IO 디스패처 사용)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 셸 통합 관리자
    private val terminalShellIntegration = TerminalShellIntegration(extHostTerminalId, numericId, rpcProtocol)

    // 이벤트 콜백 관리자
    private val callbackManager = TerminalCallbackManager()

    /**
     * 터미널 닫힘 콜백을 추가합니다.
     */
    fun addTerminalCloseCallback(callback: () -> Unit) {
        callbackManager.addCloseCallback(callback)
    }

    /**
     * 터미널 인스턴스를 초기화합니다.
     *
     * @throws IllegalStateException 터미널이 이미 초기화되었거나 해제된 경우
     * @throws Exception 초기화 중 오류가 발생한 경우
     */
    fun initialize() {
        state.checkCanInitialize(extHostTerminalId) // 초기화 가능 여부 확인

        try {
            logger.info("🚀 터미널 인스턴스 초기화 중: $extHostTerminalId (numericId: $numericId)")

            // 🎯 메모리 누수 방지를 위해 프로젝트의 Disposer에 등록합니다.
            registerToProjectDisposer()

            // UI 작업은 EDT 스레드에서 수행해야 합니다.
            ApplicationManager.getApplication().invokeAndWait {
                performInitialization()
            }
        } catch (e: Exception) {
            logger.error("❌ 터미널 인스턴스 초기화 실패: $extHostTerminalId", e)
            throw e
        }
    }

    /**
     * 프로젝트의 Disposer에 터미널 인스턴스를 등록합니다.
     */
    private fun registerToProjectDisposer() {
        try {
            Disposer.register(project, this) // `this` (TerminalInstance)를 자식 Disposable로 등록
            logger.info("✅ 터미널 인스턴스가 프로젝트 Disposer에 등록됨: $extHostTerminalId")
        } catch (e: Exception) {
            logger.error("❌ 터미널 인스턴스를 프로젝트 Disposer에 등록 실패: $extHostTerminalId", e)
            throw e
        }
    }

    /**
     * 초기화 단계를 수행합니다.
     */
    private fun performInitialization() {
        try {
            createTerminalWidget() // 터미널 위젯 생성
            setupShellIntegration() // 셸 통합 설정
            finalizeInitialization() // 초기화 마무리
        } catch (e: Exception) {
            logger.error("❌ EDT 스레드에서 터미널 초기화 실패: $extHostTerminalId", e)
            throw e
        }
    }

    /**
     * 셸 통합을 설정합니다.
     */
    private fun setupShellIntegration() {
        terminalShellIntegration.setupShellIntegration()
    }

    /**
     * 초기화 마무리 단계입니다.
     */
    private fun finalizeInitialization() {
        state.markInitialized() // 초기화 완료 상태로 표시
        logger.info("✅ 터미널 인스턴스 초기화 완료: $extHostTerminalId")

        // 🎯 터미널 위젯을 터미널 툴 윈도우에 추가합니다.
        addToTerminalToolWindow()
        
        notifyTerminalOpened() // ExtHost에 터미널이 열렸음을 알립니다.
        notifyShellIntegrationChange() // ExtHost에 셸 통합 변경을 알립니다.
        handleInitialText() // 초기 텍스트 처리
    }

    /**
     * 초기 텍스트가 설정되어 있으면 터미널에 보냅니다.
     */
    private fun handleInitialText() {
        config.initialText?.let { initialText ->
            sendText(initialText, shouldExecute = false)
        }
    }

    /**
     * 터미널 위젯을 생성합니다.
     */
    private fun createTerminalWidget() {
        try {
            val customRunner = createCustomRunner() // 커스텀 러너 생성
            val startupOptions = createStartupOptions() // 시작 옵션 생성

            logger.info("🚀 startShellTerminalWidget 호출 중...")

            terminalWidget = customRunner.startShellTerminalWidget(
                this, // 부모 Disposable
                startupOptions,
                false  // deferSessionStartUntilUiShown - 세션을 즉시 시작 (false로 설정해야 함)
            )

            logger.info("✅ startShellTerminalWidget 호출 완료, 반환된 위젯: ${terminalWidget?.javaClass?.name}")

            initializeWidgets() // 위젯 초기화
            setupTerminalCloseListener() // 터미널 닫힘 리스너 설정

            logger.info("✅ 터미널 위젯 생성 성공")

        } catch (e: Exception) {
            logger.error("❌ 터미널 위젯 생성 실패", e)
            throw e
        }
    }

    /**
     * `PtyProcess`를 프록시하여 입출력 스트림을 가로챌 수 있는 커스텀 러너를 생성합니다.
     */
    private fun createCustomRunner(): LocalTerminalDirectRunner {
        return object : LocalTerminalDirectRunner(project) {
            override fun createProcess(options: ShellStartupOptions): PtyProcess {
                logger.info("🔧 커스텀 createProcess 메소드 호출됨...")
                logger.info("시작 옵션: $options")

                val originalProcess = super.createProcess(options) // 원본 프로세스 생성
                logger.info("✅ 원본 프로세스 생성됨: ${originalProcess.javaClass.name}")

                return createProxyPtyProcess(originalProcess) // 프록시 `PtyProcess` 생성
            }

            override fun createShellTerminalWidget(
                parent: Disposable,
                startupOptions: ShellStartupOptions
            ): TerminalWidget {
                logger.info("🔧 커스텀 createShellTerminalWidget 메소드 호출됨...")
                return super.createShellTerminalWidget(parent, startupOptions)
            }

            override fun configureStartupOptions(baseOptions: ShellStartupOptions): ShellStartupOptions {
                logger.info("🔧 커스텀 configureStartupOptions 메소드 호출됨...")
                return super.configureStartupOptions(baseOptions)
            }
        }
    }

    /**
     * 셸 시작 옵션을 생성합니다.
     */
    private fun createStartupOptions(): ShellStartupOptions {
        val fullShellCommand = buildShellCommand() // 전체 셸 명령어 구성

        logger.info("🔧 셸 설정: shellPath=${config.shellPath}, shellArgs=${config.shellArgs}")
        logger.info("🔧 전체 셸 명령어: $fullShellCommand")

        return ShellStartupOptions.Builder()
            .workingDirectory(config.cwd ?: project.basePath) // 작업 디렉터리 설정
            .shellCommand(fullShellCommand) // 셸 명령어 설정
            .build()
    }

    /**
     * 셸 명령어를 구성합니다.
     */
    private fun buildShellCommand(): List<String>? {
        return buildList {
            config.shellPath?.let { add(it) }
            config.shellArgs?.let { addAll(it) }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * 위젯 컴포넌트들을 초기화합니다.
     */
    private fun initializeWidgets() {
        shellWidget = JBTerminalWidget.asJediTermWidget(terminalWidget!!) as? ShellTerminalWidget
            ?: throw IllegalStateException("ShellTerminalWidget을 가져올 수 없습니다.")

        // 터미널 제목 설정
        terminalWidget!!.terminalTitle.change {
            userDefinedTitle = config.name ?: DEFAULT_TERMINAL_NAME
        }
    }

    /**
     * 터미널 닫힘 이벤트 리스너를 설정합니다.
     */
    private fun setupTerminalCloseListener() {
        try {
            Disposer.register(terminalWidget!!) { // `terminalWidget`이 해제될 때 콜백 호출
                logger.info("🔔 TerminalWidget dispose 이벤트: $extHostTerminalId")
                if (!state.isDisposed) { // 이미 해제된 상태가 아니면
                    onTerminalClosed() // 터미널 닫힘 처리
                }
            }
        } catch (e: Exception) {
            logger.error("❌ 터미널 닫힘 이벤트 리스너 설정 실패: $extHostTerminalId", e)
        }
    }

    /**
     * 입출력 스트림을 가로채기 위한 프록시 `PtyProcess`를 생성합니다.
     */
    private fun createProxyPtyProcess(originalProcess: PtyProcess): PtyProcess {
        logger.info("🔧 입출력 스트림을 가로채기 위한 프록시 PtyProcess 생성 중...")

        val rawDataCallback = createRawDataCallback() // 원시 데이터 콜백 생성
        return ProxyPtyProcess(originalProcess, rawDataCallback)
    }

    /**
     * 원시 데이터 콜백 핸들러를 생성합니다.
     */
    private fun createRawDataCallback(): ProxyPtyProcessCallback {
        return object : ProxyPtyProcessCallback {
            override fun onRawData(data: String, streamType: String) {
                logger.debug("📥 원시 데이터 [$streamType]: ${data.length} 문자")

                try {
                    sendRawDataToExtHost(data) // ExtHost로 원시 데이터 전송
                    terminalShellIntegration.appendRawOutput(data) // 셸 통합 로직에 데이터 추가
                } catch (e: Exception) {
                    logger.error("❌ 원시 데이터 처리 실패 (터미널: $extHostTerminalId)", e)
                }
            }
        }
    }

    /**
     * 원시 데이터를 ExtHost로 전송합니다.
     */
    private fun sendRawDataToExtHost(data: String) {
        val extHostTerminalServiceProxy =
            rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostTerminalService)
        extHostTerminalServiceProxy.acceptTerminalProcessData(
            id = numericId,
            data = data
        )
        logger.debug("✅ ExtHost로 원시 데이터 전송 완료: ${data.length} 문자 (터미널: $extHostTerminalId)")
    }

    /**
     * 터미널을 표시합니다.
     */
    fun show(preserveFocus: Boolean = false) {
        if (!state.canOperate()) {
            logger.warn("터미널이 초기화되지 않았거나 해제되어 표시할 수 없음: $extHostTerminalId")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                showTerminalToolWindow() // 터미널 툴 윈도우 표시
                shellWidget?.show(preserveFocus) // 셸 위젯 표시
                logger.info("✅ 터미널 표시됨: $extHostTerminalId")
            } catch (e: Exception) {
                logger.error("❌ 터미널 표시 실패: $extHostTerminalId", e)
            }
        }
    }

    /**
     * 터미널을 숨깁니다.
     */
    fun hide() {
        if (!state.canOperate()) {
            logger.warn("터미널이 초기화되지 않았거나 해제되어 숨길 수 없음: $extHostTerminalId")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                hideTerminalToolWindow() // 터미널 툴 윈도우 숨기기
                shellWidget?.hide() // 셸 위젯 숨기기
                logger.info("✅ 터미널 숨김: $extHostTerminalId")
            } catch (e: Exception) {
                logger.error("❌ 터미널 숨기기 실패: $extHostTerminalId", e)
            }
        }
    }

    /**
     * 터미널 툴 윈도우를 표시하고 현재 터미널 탭을 활성화합니다.
     */
    private fun showTerminalToolWindow() {
        try {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            toolWindow?.show(null) // 툴 윈도우 표시
        } catch (e: Exception) {
            logger.error("❌ 터미널 툴 윈도우 표시 실패", e)
        }
    }

    /**
     * `terminalWidget`을 터미널 툴 윈도우에 추가합니다.
     */
    private fun addToTerminalToolWindow() {
        if (terminalWidget == null) {
            logger.warn("TerminalWidget이 null이므로 툴 윈도우에 추가할 수 없음")
            return
        }

        try {
            val terminalToolWindowManager = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            
            if (toolWindow == null) {
                logger.warn("터미널 툴 윈도우가 존재하지 않음")
                return
            }
            
            // `TerminalToolWindowManager`의 `newTab` 메소드를 사용하여 새 Content를 생성합니다.
            val content = terminalToolWindowManager.newTab(toolWindow, terminalWidget!!)
            content.displayName = config.name ?: DEFAULT_TERMINAL_NAME // 탭 이름 설정
            
            logger.info("✅ terminalWidget이 터미널 툴 윈도우에 추가됨: ${content.displayName}")
        } catch (e: Exception) {
            logger.error("❌ terminalWidget을 툴 윈도우에 추가 실패", e)
        }
    }

    /**
     * 터미널 툴 윈도우를 숨깁니다.
     */
    private fun hideTerminalToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
        toolWindow?.hide(null)
    }

    /**
     * 터미널에 텍스트를 보냅니다.
     */
    fun sendText(text: String, shouldExecute: Boolean = false) {
        if (!state.canOperate()) {
            logger.warn("터미널이 초기화되지 않았거나 해제되어 텍스트를 보낼 수 없음: $extHostTerminalId")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                val shell = shellWidget ?: return@invokeLater

                if (shouldExecute) {
                    shell.executeCommand(text) // 명령 실행
                    logger.info("✅ 명령어 실행됨: $text (터미널: $extHostTerminalId)")
                } else {
                    shell.writePlainMessage(text) // 일반 텍스트 쓰기
                    logger.info("✅ 텍스트 전송됨: $text (터미널: $extHostTerminalId)")
                }
            } catch (e: Exception) {
                logger.error("❌ 텍스트 전송 실패: $extHostTerminalId", e)
            }
        }
    }

    /**
     * ExtHost 프로세스에 터미널이 열렸음을 알립니다.
     */
    private fun notifyTerminalOpened() {
        try {
            logger.info("📤 ExtHost 프로세스에 터미널 열림 알림: $extHostTerminalId (numericId: $numericId)")

            val shellLaunchConfigDto = config.toShellLaunchConfigDto(project.basePath)
            val extHostTerminalServiceProxy =
                rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostTerminalService)

            extHostTerminalServiceProxy.acceptTerminalOpened(
                id = numericId,
                extHostTerminalId = extHostTerminalId,
                name = config.name ?: DEFAULT_TERMINAL_NAME,
                shellLaunchConfig = shellLaunchConfigDto
            )

            logger.info("✅ ExtHost 프로세스에 터미널 열림 알림 성공: $extHostTerminalId")
        } catch (e: Exception) {
            logger.error("❌ ExtHost 프로세스에 터미널 열림 알림 실패: $extHostTerminalId", e)
        }
    }

    /**
     * 셸 통합 변경을 알립니다.
     */
    private fun notifyShellIntegrationChange() {
        try {
            val extHostTerminalShellIntegrationProxy =
                rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostTerminalShellIntegration)

            extHostTerminalShellIntegrationProxy.shellIntegrationChange(instanceId = numericId)
            logger.info("✅ ExtHost에 셸 통합 초기화 알림: (터미널: $extHostTerminalId)")

            notifyEnvironmentVariableChange(extHostTerminalShellIntegrationProxy)
        } catch (e: Exception) {
            logger.error("❌ ExtHost에 셸 통합 초기화 알림 실패: (터미널: $extHostTerminalId)", e)
        }
    }

    /**
     * 환경 변수 변경을 알립니다.
     */
    private fun notifyEnvironmentVariableChange(extHostTerminalShellIntegrationProxy: ExtHostTerminalShellIntegrationProxy) {
        config.env?.takeIf { it.isNotEmpty() }?.let { env ->
            try {
                val envKeys = env.keys.toTypedArray()
                val envValues = env.values.toTypedArray()

                extHostTerminalShellIntegrationProxy.shellEnvChange(
                    instanceId = numericId,
                    shellEnvKeys = envKeys,
                    shellEnvValues = envValues,
                    isTrusted = true
                )

                logger.info("✅ ExtHost에 환경 변수 변경 알림: ${env.size} 변수 (터미널: $extHostTerminalId)")
            } catch (e: Exception) {
                logger.error("❌ 환경 변수 변경 알림 실패: (터미널: $extHostTerminalId)", e)
            }
        }
    }

    /**
     * 터미널 닫힘 이벤트를 트리거합니다.
     */
    private fun onTerminalClosed() {
        logger.info("🔔 터미널 닫힘 이벤트 트리거됨: $extHostTerminalId (numericId: $numericId)")

        try {
            notifyTerminalClosed() // ExtHost에 터미널 닫힘 알림
            callbackManager.executeCloseCallbacks() // 등록된 닫힘 콜백 실행

            if (!state.isDisposed) {
                dispose() // 아직 해제되지 않았으면 해제
            }
        } catch (e: Exception) {
            logger.error("터미널 닫힘 이벤트 처리 실패: $extHostTerminalId", e)
        }
    }

    /**
     * ExtHost 프로세스에 터미널이 닫혔음을 알립니다.
     */
    private fun notifyTerminalClosed() {
        try {
            logger.info("📤 ExtHost 프로세스에 터미널 닫힘 알림: $extHostTerminalId (numericId: $numericId)")

            val extHostTerminalServiceProxy =
                rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostTerminalService)
            extHostTerminalServiceProxy.acceptTerminalClosed(
                id = numericId,
                exitCode = null,
                exitReason = numericId
            )

            logger.info("✅ ExtHost 프로세스에 터미널 닫힘 알림 성공: $extHostTerminalId")
        } catch (e: Exception) {
            logger.error("❌ ExtHost 프로세스에 터미널 닫힘 알림 실패: $extHostTerminalId", e)
        }
    }

    /**
     * 리소스를 해제합니다.
     */
    override fun dispose() {
        if (state.isDisposed) return // 이미 해제되었으면 중복 호출 방지

        logger.info("🧹 터미널 인스턴스 해제 중: $extHostTerminalId")

        try {
            state.markDisposed() // 해제 상태로 표시
            
            callbackManager.clear() // 콜백 정리
            scope.cancel() // 코루틴 스코프 취소

            // terminalWidget 해제 (onTerminalClosed 콜백은 state.isDisposed=true이므로 건너뜀)
            terminalWidget?.let { widget ->
                try {
                    Disposer.dispose(widget)
                } catch (e: Exception) {
                    logger.error("❌ terminalWidget 해제 실패: $extHostTerminalId", e)
                }
            }

            terminalShellIntegration.dispose() // 셸 통합 리소스 해제
            cleanupResources() // 기타 리소스 정리

            logger.info("✅ 터미널 인스턴스 해제 완료: $extHostTerminalId")
        } catch (e: Exception) {
            logger.error("❌ 터미널 인스턴스 해제 실패: $extHostTerminalId", e)
        }
    }

    /**
     * 리소스를 정리합니다.
     */
    private fun cleanupResources() {
        terminalWidget = null
        shellWidget = null
    }
}

/**
 * 터미널 설정 데이터를 담는 데이터 클래스입니다.
 */
data class TerminalConfig(
    val name: String? = null,             // 터미널 이름
    val shellPath: String? = null,        // 셸 실행 파일 경로
    val shellArgs: List<String>? = null,  // 셸 실행 인자
    val cwd: String? = null,              // 현재 작업 디렉터리
    val env: Map<String, String>? = null, // 환경 변수
    val useShellEnvironment: Boolean? = null, // 셸 환경 사용 여부
    val hideFromUser: Boolean? = null,    // 사용자에게 숨길지 여부
    val isFeatureTerminal: Boolean? = null, // 기능 터미널 여부
    val forceShellIntegration: Boolean? = null, // 셸 통합 강제 여부
    val initialText: String? = null       // 초기 텍스트
) {
    companion object {
        /**
         * Map으로부터 `TerminalConfig` 객체를 생성합니다.
         */
        fun fromMap(config: Map<String, Any?>): TerminalConfig {
            return TerminalConfig(
                name = config["name"] as? String,
                shellPath = config["shellPath"] as? String,
                shellArgs = config["shellArgs"] as? List<String>,
                cwd = config["cwd"] as? String,
                env = config["env"] as? Map<String, String>,
                useShellEnvironment = config["useShellEnvironment"] as? Boolean,
                hideFromUser = config["hideFromUser"] as? Boolean,
                isFeatureTerminal = config["isFeatureTerminal"] as? Boolean,
                forceShellIntegration = config["forceShellIntegration"] as? Boolean,
                initialText = config["initialText"] as? String
            )
        }
    }

    /**
     * `ShellLaunchConfigDto`로 변환합니다.
     */
    fun toShellLaunchConfigDto(defaultCwd: String?): ShellLaunchConfigDto {
        return ShellLaunchConfigDto(
            name = name,
            executable = shellPath,
            args = shellArgs,
            cwd = cwd ?: defaultCwd,
            env = env,
            useShellEnvironment = useShellEnvironment,
            hideFromUser = hideFromUser,
            reconnectionProperties = null,
            type = null,
            isFeatureTerminal = isFeatureTerminal,
            tabActions = null,
            shellIntegrationEnvironmentReporting = forceShellIntegration
        )
    }
}

/**
 * 터미널 상태 관리자입니다.
 * 터미널의 초기화 및 해제 상태를 추적합니다.
 */
private class TerminalState {
    @Volatile
    private var isInitialized = false

    @Volatile
    private var _isDisposed = false

    val isDisposed: Boolean get() = _isDisposed

    /**
     * 터미널 인스턴스가 초기화될 수 있는지 확인합니다.
     * 이미 초기화되었거나 해제된 경우 예외를 발생시킵니다.
     */
    fun checkCanInitialize(terminalId: String) {
        if (isInitialized || _isDisposed) {
            throw IllegalStateException("터미널 인스턴스가 이미 초기화되었거나 해제되었습니다: $terminalId")
        }
    }

    /**
     * 터미널을 초기화된 상태로 표시합니다.
     */
    fun markInitialized() {
        isInitialized = true
    }

    /**
     * 터미널을 해제된 상태로 표시합니다.
     */
    fun markDisposed() {
        _isDisposed = true
    }

    /**
     * 터미널이 현재 작동 가능한 상태인지 확인합니다.
     */
    fun canOperate(): Boolean {
        return isInitialized && !_isDisposed
    }
}

/**
 * 터미널 콜백 관리자입니다.
 * 터미널 닫힘 콜백을 등록하고 실행합니다.
 */
private class TerminalCallbackManager {
    private val logger = Logger.getInstance(TerminalCallbackManager::class.java)
    private val terminalCloseCallbacks = mutableListOf<() -> Unit>()

    /**
     * 터미널 닫힘 콜백을 추가합니다.
     */
    fun addCloseCallback(callback: () -> Unit) {
        terminalCloseCallbacks.add(callback)
    }

    /**
     * 등록된 모든 터미널 닫힘 콜백을 실행합니다.
     */
    fun executeCloseCallbacks() {
        terminalCloseCallbacks.forEach { callback ->
            try {
                callback()
            } catch (e: Exception) {
                logger.error("터미널 닫힘 콜백 실행 실패", e)
            }
        }
    }

    /**
     * 모든 콜백을 지웁니다.
     */
    fun clear() {
        terminalCloseCallbacks.clear()
    }
}
