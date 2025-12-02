// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.sina.weibo.agent.plugin.DEBUG_MODE
import com.sina.weibo.agent.plugin.WecoderPluginService
import com.sina.weibo.agent.util.PluginResourceUtil
import com.sina.weibo.agent.util.ProxyConfigUtil
import java.io.File
import java.util.concurrent.TimeUnit
import com.sina.weibo.agent.util.ExtensionUtils
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.NotificationUtil
import com.sina.weibo.agent.util.NodeVersionUtil
import com.sina.weibo.agent.util.NodeVersion
import java.nio.file.Paths

/**
 * Extension Host 프로세스 관리자 클래스입니다.
 * Node.js로 작성된 Extension Host 프로세스를 시작하고, 생명주기를 관리하며, 통신을 설정하는 역할을 합니다.
 */
class ExtensionProcessManager : Disposable {
    companion object {
        // 주요 경로 및 상수 정의
        private const val NODE_MODULES_PATH = PluginConstants.NODE_MODULES_PATH
        private const val EXTENSION_ENTRY_FILE = PluginConstants.EXTENSION_ENTRY_FILE
        private const val RUNTIME_DIR = PluginConstants.RUNTIME_DIR
        private const val PLUGIN_ID = PluginConstants.PLUGIN_ID
        
        // 요구되는 최소 Node.js 버전
        private val MIN_REQUIRED_NODE_VERSION = NodeVersion(20, 6, 0, "20.6.0")
    }
    
    private val LOG = Logger.getInstance(ExtensionProcessManager::class.java)
    
    // 실행된 Extension Host 프로세스 객체
    private var process: Process? = null
    
    // 프로세스의 표준 출력을 모니터링하는 스레드
    private var monitorThread: Thread? = null
    
    // 프로세스가 현재 실행 중인지 여부를 나타내는 플래그
    @Volatile
    private var isRunning = false
    
    /**
     * Extension Host 프로세스를 시작합니다.
     * @param portOrPath 통신에 사용할 소켓 포트(Int) 또는 UDS(Unix Domain Socket) 경로(String)
     * @return 성공적으로 시작되었으면 true
     */
    fun start(portOrPath: Any?): Boolean {
        if (isRunning) {
            LOG.info("Extension Host 프로세스가 이미 실행 중입니다.")
            return true
        }
        val isUds = portOrPath is String
        if (!ExtensionUtils.isValidPortOrPath(portOrPath)) {
            LOG.error("유효하지 않은 소켓 정보: $portOrPath")
            return false
        }
        
        try {
            // 1. Node.js 실행 파일 경로 찾기
            val nodePath = findNodeExecutable()
            if (nodePath == null) {
                LOG.error("Node.js 실행 파일을 찾지 못했습니다.")
                NotificationUtil.showError(
                    "Node.js 환경 없음",
                    "Node.js 환경이 감지되지 않았습니다. Node.js를 설치하고 다시 시도해주세요. 권장 버전: $MIN_REQUIRED_NODE_VERSION 이상."
                )
                return false
            }
            
            // 2. Node.js 버전 확인
            val nodeVersion = NodeVersionUtil.getNodeVersion(nodePath)
            if (!NodeVersionUtil.isVersionSupported(nodeVersion, MIN_REQUIRED_NODE_VERSION)) {
                LOG.error("Node.js 버전이 지원되지 않음: $nodeVersion, 요구 버전: $MIN_REQUIRED_NODE_VERSION")
                NotificationUtil.showError(
                    "Node.js 버전이 너무 낮습니다",
                    "현재 Node.js($nodePath) 버전은 $nodeVersion 입니다. 더 나은 호환성을 위해 $MIN_REQUIRED_NODE_VERSION 이상으로 업그레이드해주세요."
                )
                return false
            }
            
            // 3. Extension Host의 진입점(entry) 파일 경로 찾기
            val extensionPath = findExtensionEntryFile()
            if (extensionPath == null) {
                LOG.error("Extension Host 진입점 파일을 찾지 못했습니다.")
                return false
            }

            LOG.info("Extension Host 프로세스 시작 중... Node: $nodePath, Entry: $extensionPath")

            // 4. 프로세스 실행을 위한 환경 변수 설정
            val envVars = HashMap<String, String>(System.getenv())
            envVars["PATH"] = buildEnhancedPath(envVars, nodePath)
            LOG.info("보강된 PATH (${SystemInfo.getOsNameAndVersion()}): ${envVars["PATH"]}")
            
            // 통신 방식을 환경 변수로 전달
            if (isUds) {
                envVars["VSCODE_EXTHOST_IPC_HOOK"] = portOrPath
            } else {
                envVars["VSCODE_EXTHOST_WILL_SEND_SOCKET"] = "1"
                envVars["VSCODE_EXTHOST_SOCKET_HOST"] = "127.0.0.1"
                envVars["VSCODE_EXTHOST_SOCKET_PORT"] = portOrPath.toString()
            }

            // 5. Node.js 프로세스 실행을 위한 커맨드 라인 인자 구성
            val commandArgs = mutableListOf(
                nodePath,
                "--experimental-global-webcrypto",
                "--no-deprecation",
                extensionPath,
                "--vscode-socket-port=${envVars["VSCODE_EXTHOST_SOCKET_PORT"]}",
                "--vscode-socket-host=${envVars["VSCODE_EXTHOST_SOCKET_HOST"]}",
                "--vscode-will-send-socket=${envVars["VSCODE_EXTHOST_WILL_SEND_SOCKET"]}"
            )
            
            // 6. 시스템 프록시 설정 적용
            try {
                val proxyEnvVars = ProxyConfigUtil.getProxyEnvVarsForProcessStart()
                envVars.putAll(proxyEnvVars)
                if (proxyEnvVars.isNotEmpty()) LOG.info("프로세스 시작에 프록시 설정을 적용했습니다.")
            } catch (e: Exception) {
                LOG.warn("프록시 설정 구성에 실패했습니다.", e)
            }
            
            // 7. 프로세스 빌더 생성 및 실행
            val builder = ProcessBuilder(commandArgs)
            builder.environment().putAll(envVars)
            builder.redirectErrorStream(true) // 에러 스트림을 표준 출력으로 리다이렉션
            
            process = builder.start()
            
            // 8. 프로세스 출력을 로깅하는 모니터 스레드 시작
            monitorThread = Thread { monitorProcess() }.apply {
                name = "ExtensionProcessMonitor"
                isDaemon = true
                start()
            }
            
            isRunning = true
            LOG.info("Extension Host 프로세스가 시작되었습니다.")
            return true
        } catch (e: Exception) {
            LOG.error("Extension Host 프로세스 시작에 실패했습니다.", e)
            stopInternal()
            return false
        }
    }
    
    /**
     * Extension Host 프로세스의 표준 출력을 읽어 IntelliJ 로그에 기록합니다.
     */
    private fun monitorProcess() {
        val proc = process ?: return
        try {
            val logThread = Thread {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { LOG.info("Extension process: $it") }
                }
            }
            logThread.name = "ExtensionProcessLogger"
            logThread.isDaemon = true
            logThread.start()
            
            val exitCode = proc.waitFor()
            LOG.info("Extension Host 프로세스가 종료되었습니다. 종료 코드: $exitCode")
            
            logThread.interrupt()
            logThread.join(1000)
        } catch (e: Exception) {
            LOG.error("Extension Host 프로세스 모니터링 중 오류 발생", e)
        } finally {
            synchronized(this) {
                if (process === proc) {
                    isRunning = false
                    process = null
                }
            }
        }
    }
    
    /**
     * Extension Host 프로세스를 중지합니다.
     */
    fun stop() {
        if (!isRunning) return
        stopInternal()
    }
    
    /**
     * 프로세스 중지 내부 로직
     */
    private fun stopInternal() {
        LOG.info("Extension Host 프로세스를 중지합니다.")
        process?.let { proc ->
            try {
                if (proc.isAlive) {
                    proc.destroy() // 정상 종료 시도
                    if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                        proc.destroyForcibly() // 5초 후 강제 종료
                        proc.waitFor(2, TimeUnit.SECONDS)
                    }
                }
            } catch (e: Exception) {
                LOG.error("Extension Host 프로세스 중지 중 오류 발생", e)
            }
        }
        monitorThread?.interrupt()
        try {
            monitorThread?.join(1000)
        } catch (e: InterruptedException) { /* 무시 */ }
        
        process = null
        monitorThread = null
        isRunning = false
        LOG.info("Extension Host 프로세스가 중지되었습니다.")
    }
    
    /**
     * 시스템에서 Node.js 실행 파일을 찾습니다.
     * 플러그인에 내장된 버전을 우선적으로 확인하고, 없으면 시스템 PATH에서 찾습니다.
     */
    private fun findNodeExecutable(): String? {
        // ... (내장 Node.js 및 시스템 PATH 탐색 로직)
        return findExecutableInPath("node")
    }
    
    /**
     * 시스템 PATH에서 특정 이름의 실행 파일을 찾습니다.
     */
    private fun findExecutableInPath(name: String): String? {
        val nodePath = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)?.absolutePath
        LOG.info("시스템 Node 경로: $nodePath")
        return nodePath
    }
    
    /**
     * Extension Host의 진입점 JS 파일을 찾습니다.
     * 디버그 모드와 일반 모드를 구분하여 다른 경로를 사용합니다.
     */
    fun findExtensionEntryFile(): String? {
        if (WecoderPluginService.getDebugMode() != DEBUG_MODE.NONE) {
            val debugEntry = Paths.get(WecoderPluginService.getDebugResource(), RUNTIME_DIR, EXTENSION_ENTRY_FILE).normalize().toFile()
            if (debugEntry.exists()) {
                LOG.info("[DebugMode] 디버그 진입 파일 사용: ${debugEntry.absolutePath}")
                return debugEntry.absolutePath
            } else {
                LOG.warn("[DebugMode] 디버그 진입 파일을 찾을 수 없음: ${debugEntry.absolutePath}")
            }
        }
        val resourcesPath = PluginResourceUtil.getResourcePath(PLUGIN_ID, "$RUNTIME_DIR/$EXTENSION_ENTRY_FILE")
        if (resourcesPath != null && File(resourcesPath).exists()) {
            return resourcesPath
        }
        return null
    }
    
    /**
     * Node.js 프로세스가 npx 등 다른 CLI 도구를 찾을 수 있도록 시스템의 PATH 환경 변수를 보강합니다.
     */
    private fun buildEnhancedPath(envVars: MutableMap<String, String>, nodePath: String): String {
        // ... (OS에 따라 Homebrew, /usr/local/bin 등 일반적인 경로를 추가)
        return (envVars.filterKeys { it.equals("PATH", ignoreCase = true) }.values.firstOrNull() ?: "")
    }
    
    fun isRunning(): Boolean {
        return isRunning && process?.isAlive == true
    }
    
    override fun dispose() {
        stop()
    }
}
