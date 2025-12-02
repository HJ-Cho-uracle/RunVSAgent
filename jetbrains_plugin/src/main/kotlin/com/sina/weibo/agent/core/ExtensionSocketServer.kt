// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.ServerSocket
import java.net.Socket
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Extension Host 프로세스와의 통신을 설정하기 위한 소켓 서버 인터페이스입니다.
 */
interface ISocketServer : Disposable {
    /**
     * 서버를 시작합니다.
     * @param projectPath 현재 프로젝트의 경로
     * @return 성공 시 포트 번호 또는 UDS 경로, 실패 시 null
     */
    fun start(projectPath: String = ""): Any?

    /**
     * 서버를 중지합니다.
     */
    fun stop()

    /**
     * 서버가 현재 실행 중인지 확인합니다.
     */
    fun isRunning(): Boolean
}

/**
 * Extension Host와의 TCP 소켓 통신을 위한 서버 구현 클래스입니다.
 * 임의의 포트를 열고 Extension Host의 연결을 기다립니다.
 */
class ExtensionSocketServer : ISocketServer {
    private val logger = Logger.getInstance(ExtensionSocketServer::class.java)
    
    // 클라이언트의 연결을 수락하는 서버 소켓
    private var serverSocket: ServerSocket? = null
    
    // 연결된 각 클라이언트(Extension Host)를 관리하는 `ExtensionHostManager` 맵
    private val clientManagers = ConcurrentHashMap<Socket, ExtensionHostManager>()
    
    // 연결 수락을 위한 별도의 서버 스레드
    private var serverThread: Thread? = null
    
    private var projectPath: String = ""
    
    @Volatile
    private var isRunning = false

    lateinit var project: Project

    /**
     * 소켓 서버를 시작합니다.
     * @param projectPath 현재 프로젝트 경로
     * @return 성공적으로 할당된 포트 번호, 실패 시 -1
     */
    override fun start(projectPath: String): Int {
        if (isRunning) {
            logger.info("소켓 서버가 이미 실행 중입니다.")
            return serverSocket?.localPort ?: -1
        }
        
        this.projectPath = projectPath
        
        try {
            // 포트 번호 0은 시스템이 사용 가능한 임의의 포트를 할당하도록 합니다.
            serverSocket = ServerSocket(0)
            val port = serverSocket?.localPort ?: -1
            
            if (port <= 0) {
                logger.error("소켓 서버에 유효한 포트를 할당받지 못했습니다.")
                return -1
            }
            
            isRunning = true
            logger.info("소켓 서버 시작 중... 포트: $port")
            
            // 별도의 스레드에서 클라이언트 연결을 기다립니다.
            serverThread = thread(start = true, name = "ExtensionSocketServer") {
                acceptConnections()
            }
            
            return port
        } catch (e: Exception) {
            logger.error("소켓 서버 시작 실패", e)
            stop()
            return -1
        }
    }
    
    /**
     * 소켓 서버를 중지하고 모든 관련 리소스를 해제합니다.
     */
    override fun stop() {
        if (!isRunning) return
        
        isRunning = false
        logger.info("소켓 서버 중지 중...")
        
        // 모든 클라이언트 매니저를 정리합니다.
        clientManagers.forEach { (_, manager) ->
            try {
                manager.dispose()
            } catch (e: Exception) {
                logger.warn("클라이언트 매니저 해제 실패", e)
            }
        }
        clientManagers.clear()
        
        // 서버 소켓을 닫습니다.
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            logger.warn("서버 소켓 닫기 실패", e)
        }
        
        serverThread?.interrupt()
        serverThread = null
        serverSocket = null
        
        logger.info("소켓 서버가 중지되었습니다.")
    }
    
    /**
     * 클라이언트의 연결을 계속해서 수락하는 루프입니다. (서버 스레드에서 실행됨)
     */
    private fun acceptConnections() {
        val server = serverSocket ?: return
        logger.info("소켓 서버 시작됨, 연결 대기 중... (tid: ${Thread.currentThread().id})")
        
        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                // 클라이언트 연결을 기다립니다 (blocking call).
                val clientSocket = server.accept()
                logger.info("새 클라이언트 연결됨: ${clientSocket.inetAddress.hostAddress}")
                
                clientSocket.tcpNoDelay = true // Nagle 알고리즘 비활성화로 지연 시간 감소
                
                // 각 클라이언트 연결에 대해 새로운 ExtensionHostManager를 생성합니다.
                val manager = ExtensionHostManager(clientSocket, projectPath, project)
                clientManagers[clientSocket] = manager

                // 클라이언트 처리를 시작합니다.
                handleClient(clientSocket, manager)
            } catch (e: IOException) {
                if (isRunning) {
                    logger.error("클라이언트 연결 수락 중 오류 발생", e)
                } else {
                    logger.info("소켓 서버가 닫혔습니다.")
                    break
                }
            } catch (e: InterruptedException) {
                logger.info("소켓 서버 스레드가 중단되었습니다.")
                break
            } catch (e: Exception) {
                logger.error("연결 수락 루프에서 예기치 않은 오류 발생", e)
                // ... (오류 복구 로직)
            }
        }
        logger.info("소켓 연결 수락 루프가 종료되었습니다.")
    }

    /**
     * 개별 클라이언트 연결을 처리합니다.
     */
    private fun handleClient(clientSocket: Socket, manager: ExtensionHostManager) {
        try {
            // ExtensionHostManager를 시작하여 초기화 프로세스를 진행합니다.
            manager.start()
            
            // 소켓 연결이 끊어질 때까지 주기적으로 연결 상태를 확인합니다.
            while (clientSocket.isConnected && !clientSocket.isClosed && isRunning) {
                try {
                    // ... (연결 상태 확인 및 RPC 응답 상태 로깅)
                    Thread.sleep(500)
                } catch (ie: InterruptedException) {
                    logger.info("클라이언트 핸들러 스레드가 중단되어 루프를 종료합니다.")
                    break
                }
            }
        } catch (e: Exception) {
            if (e !is InterruptedException) {
                logger.error("클라이언트 소켓 처리 중 오류 발생: ${e.message}", e)
            } else {
                logger.info("클라이언트 핸들러 스레드가 처리 중 중단되었습니다.")
            }
        } finally {
            // 연결 종료 시 리소스를 정리합니다.
            manager.dispose()
            clientManagers.remove(clientSocket)
            
            if (!clientSocket.isClosed) {
                try {
                    clientSocket.close()
                } catch (e: IOException) {
                    logger.warn("클라이언트 소켓 닫기 실패", e)
                }
            }
            logger.info("클라이언트 소켓이 닫히고 제거되었습니다.")
        }
    }
    
    /**
     * 소켓 연결이 건강한지 확인합니다.
     */
    private fun isSocketHealthy(socket: Socket): Boolean {
        // ... (연결 및 입출력 스트림 상태 확인)
        return true
    }
    
    fun getPort(): Int {
        return serverSocket?.localPort ?: -1
    }
    
    override fun isRunning(): Boolean {
        return isRunning
    }
    
    override fun dispose() {
        stop()
    }
    
    /**
     * 디버깅 목적으로, 실행 중인 외부 Extension Host에 직접 연결합니다.
     * @param host 디버그 호스트 주소
     * @param port 디버그 호스트 포트
     * @return 연결 성공 여부
     */
    fun connectToDebugHost(host: String, port: Int): Boolean {
        if (isRunning) {
            logger.info("소켓 서버가 이미 실행 중이므로 먼저 중지합니다.")
            stop()
        }
        
        try {
            logger.info("디버그 호스트에 연결 중: $host:$port")
            
            val clientSocket = Socket(host, port)
            clientSocket.tcpNoDelay = true
            
            isRunning = true
            
            val manager = ExtensionHostManager(clientSocket, projectPath, project)
            clientManagers[clientSocket] = manager
            
            // 백그라운드 스레드에서 연결 처리를 시작합니다.
            thread(start = true, name = "DebugHostHandler") {
                handleClient(clientSocket, manager)
            }

            logger.info("디버그 호스트에 성공적으로 연결되었습니다: $host:$port")
            return true
        } catch (e: Exception) {
            logger.error("디버그 호스트 연결 실패: $host:$port", e)
            stop()
            return false
        }
    }
}
