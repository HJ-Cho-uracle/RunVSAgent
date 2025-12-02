// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Extension Host 프로세스와 IntelliJ 플러그인 프로세스 간의 통신을 위해
 * Unix Domain Socket (UDS) 서버를 구현하는 클래스입니다.
 * UDS는 로컬 프로세스 간 통신에 TCP/IP 소켓보다 효율적입니다.
 */
class ExtensionUnixDomainSocketServer : ISocketServer {
    private val logger = Logger.getInstance(ExtensionUnixDomainSocketServer::class.java)
    
    // UDS 서버 채널
    private var udsServerChannel: ServerSocketChannel? = null
    // UDS 소켓 파일의 경로
    private var udsSocketPath: Path? = null
    // 연결된 클라이언트 채널과 해당 클라이언트를 관리하는 ExtensionHostManager 맵
    private val clientManagers = ConcurrentHashMap<SocketChannel, ExtensionHostManager>()
    // 서버의 연결 수락 스레드
    private var serverThread: Thread? = null
    // 현재 프로젝트 경로
    private var projectPath: String = ""

    lateinit var project: Project

    @Volatile private var isRunning = false // 서버 실행 상태

    /**
     * UDS 서버를 시작하고, 생성된 소켓 파일의 경로를 반환합니다.
     * @param projectPath 현재 프로젝트 경로
     * @return UDS 소켓 파일 경로 문자열, 실패 시 null
     */
    override fun start(projectPath: String): String? {
        if (isRunning) {
            logger.info("UDS 서버가 이미 실행 중입니다.")
            return udsSocketPath?.toString()
        }
        this.projectPath = projectPath
        return startUds()
    }

    /**
     * UDS 서버를 실제로 시작하는 내부 로직입니다.
     */
    private fun startUds(): String? {
        try {
            val sockPath = createSocketFile() // 임시 소켓 파일 생성
            val udsAddr = UnixDomainSocketAddress.of(sockPath) // UDS 주소 생성
            
            // UDS 서버 채널을 열고 바인딩합니다.
            udsServerChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            udsServerChannel!!.bind(udsAddr)
            udsSocketPath = sockPath
            isRunning = true
            logger.info("[UDS] 리스닝 시작: $sockPath")
            
            // 별도의 스레드에서 클라이언트 연결을 비동기적으로 수락합니다.
            serverThread = thread(start = true, name = "ExtensionUDSSocketServer") {
                acceptUdsConnections()
            }
            return sockPath.toString()
        } catch (e: Exception) {
            logger.error("[UDS] 서버 시작 실패", e)
            stop()
            return null
        }
    }

    /**
     * UDS 서버를 중지하고 모든 관련 리소스를 해제합니다.
     */
    override fun stop() {
        if (!isRunning) return
        isRunning = false
        logger.info("UDS 소켓 서버 중지 중...")
        
        // 모든 클라이언트 연결을 닫고 매니저를 해제합니다.
        clientManagers.forEach { (_, manager) ->
            try { manager.dispose() } catch (e: Exception) { logger.warn("클라이언트 매니저 해제 실패", e) }
        }
        clientManagers.clear()
        
        // 서버 채널을 닫습니다.
        try { udsServerChannel?.close() } catch (e: Exception) { logger.warn("UDS 서버 채널 닫기 실패", e) }
        
        // 생성했던 소켓 파일을 삭제합니다.
        try { udsSocketPath?.let { Files.deleteIfExists(it) } } catch (e: Exception) { logger.warn("UDS 소켓 파일 삭제 실패", e) }
        
        // 스레드 및 채널 정리
        serverThread?.interrupt()
        serverThread = null
        udsServerChannel = null
        udsSocketPath = null
        logger.info("UDS 소켓 서버가 중지되었습니다.")
    }

    override fun isRunning(): Boolean = isRunning
    override fun dispose() { stop() }

    /**
     * UDS 클라이언트 연결을 수락하는 루프입니다. (서버 스레드에서 실행됨)
     */
    private fun acceptUdsConnections() {
        val server = udsServerChannel ?: return
        logger.info("[UDS] 연결 대기 중... (tid: ${Thread.currentThread().id})")
        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                val clientChannel = server.accept() // 새로운 연결을 기다립니다.
                logger.info("[UDS] 새 클라이언트 연결됨")
                val manager = ExtensionHostManager(clientChannel, projectPath, project)
                clientManagers[clientChannel] = manager
                handleClient(clientChannel, manager) // 클라이언트 처리를 시작합니다.
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error("[UDS] 연결 수락 실패, 1초 후 재시도", e)
                    Thread.sleep(1000)
                } else {
                    logger.info("[UDS] 연결 수락 루프 종료 (서버 중지됨)")
                    break
                }
            }
        }
        logger.info("[UDS] 연결 수락 루프 종료.")
    }

    /**
     * 개별 클라이언트 연결을 처리합니다.
     * 연결 상태 확인 및 리소스 해제를 담당합니다.
     */
    private fun handleClient(clientChannel: SocketChannel, manager: ExtensionHostManager) {
        try {
            manager.start() // ExtensionHostManager를 시작하여 초기화 프로세스를 진행합니다.

            var lastCheckTime = System.currentTimeMillis()
            val CHECK_INTERVAL = 15000 // 연결 상태 확인 주기 (15초)

            while (clientChannel.isConnected && clientChannel.isOpen && isRunning) {
                try {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastCheckTime > CHECK_INTERVAL) {
                        lastCheckTime = currentTime

                        // UDS는 TCP 소켓과 달리 isInputShutdown/isOutputShutdown 플래그가 없으므로 isOpen만 확인합니다.
                        if (!clientChannel.isOpen) {
                            logger.error("[UDS] 클라이언트 채널 상태 불량, 연결 종료.")
                            break
                        }

                        val responsiveState = manager.getResponsiveState()
                        if (responsiveState != null) {
                            logger.debug("[UDS] 클라이언트 RPC 상태: $responsiveState")
                        }
                    }

                    Thread.sleep(500)
                } catch (ie: InterruptedException) {
                    logger.info("[UDS] 클라이언트 핸들러 중단, 루프 종료")
                    break
                }
            }
        } catch (e: Exception) {
            if (e !is InterruptedException) {
                logger.error("[UDS] 클라이언트 핸들러에서 오류 발생: ${e.message}", e)
            } else {
                logger.info("[UDS] 클라이언트 핸들러가 처리 중 중단되었습니다.")
            }
        } finally {
            // 연결 종료 시 리소스 정리
            manager.dispose()
            clientManagers.remove(clientChannel)
            try { clientChannel.close() } catch (e: IOException) { logger.warn("[UDS] 클라이언트 채널 닫기 오류", e) }
            logger.info("[UDS] 클라이언트 채널이 닫히고 제거되었습니다.")
        }
    }

    /**
     * 임시 UDS 소켓 파일을 생성합니다.
     * 파일 이름의 고유성을 보장합니다.
     */
    private fun createSocketFile(): Path {
        val tmpDir = java.nio.file.Paths.get("/tmp")
        val sockPath = Files.createTempFile(tmpDir, "roo-cline-idea-extension-ipc-", ".sock")
        Files.deleteIfExists(sockPath) // 이미 존재할 경우 삭제하여 깨끗한 상태로 시작
        return sockPath
    }
}
