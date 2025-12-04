// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.logger

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.ipc.proxy.IRPCProtocolLogger
import com.sina.weibo.agent.ipc.proxy.RequestInitiator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 파일 기반 RPC 프로토콜 로거입니다.
 * RPC 통신에서 주고받는 메시지들을 파일에 기록합니다.
 */
class FileRPCProtocolLogger : IRPCProtocolLogger, Disposable {
    private val logger = Logger.getInstance(FileRPCProtocolLogger::class.java)

    // 총 수신 바이트 수
    private var totalIncoming = 0

    // 총 발신 바이트 수
    private var totalOutgoing = 0

    // 로그 디렉터리 경로
    private var logDir: Path? = null

    // 로그 파일 객체
    private var logFile: File? = null

    // 로그 파일에 쓰기 위한 BufferedWriter
    private var writer: BufferedWriter? = null

    // 로그 메시지를 비동기적으로 처리하기 위한 큐
    private val logQueue = LinkedBlockingQueue<String>()

    // 로거 초기화 여부
    private val isInitialized = AtomicBoolean(false)

    // 로거 해제 여부
    private val isDisposed = AtomicBoolean(false)

    // 로그 메시지를 파일에 쓰는 전용 스레드
    private var loggerThread: Thread? = null

    // 코루틴 스코프 (로그 큐에 메시지를 추가하는 데 사용)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 로깅 활성화 여부 (현재는 비활성화되어 있음)
    private val isEnabled = false

    init {
        if (!isEnabled) {
            logger.warn("FileRPCProtocolLogger가 활성화되지 않았습니다.")
        } else {
            // 1. 로그 디렉터리 생성
            val userHome = System.getProperty("user.home")
            logDir = Paths.get(userHome, ".ext_host", "log")

            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir)
            }

            // 2. 로그 파일 이름 생성 (타임스탬프를 사용하여 고유하게 만듦)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            logFile = logDir?.resolve("rpc_$timestamp-idea.log")?.toFile()

            // 3. 파일 쓰기 객체 생성
            writer = BufferedWriter(FileWriter(logFile))

            // 4. 로그 쓰기 전용 스레드 시작
            startLoggerThread()

            // 5. 로그 파일 헤더 작성
            val startTime = formatTimestampWithMilliseconds(Date())
            val header = """
               |-------------------------------------------------------------
               | IDEA RPC 프로토콜 로거
               | 시작 시간: $startTime
               | 로그 파일: ${logFile?.absolutePath}
               |-------------------------------------------------------------
           
            """.trimMargin()

            logQueue.add(header) // 큐에 헤더 추가

            isInitialized.set(true)
            logger.info("FileRPCProtocolLogger가 성공적으로 초기화되었습니다. 로그 파일: ${logFile?.absolutePath}")
        }
    }

    /**
     * 로그 쓰기 전용 스레드를 시작합니다.
     * `logQueue`에서 메시지를 가져와 파일에 기록합니다.
     */
    private fun startLoggerThread() {
        loggerThread = thread(start = true, isDaemon = true, name = "RPC-Logger") {
            try {
                while (!isDisposed.get()) {
                    val logEntry = logQueue.take() // 큐에서 메시지를 가져올 때까지 블록
                    try {
                        writer?.write(logEntry)
                        writer?.newLine()
                        writer?.flush() // 즉시 파일에 쓰기
                    } catch (e: Exception) {
                        logger.error("로그 파일 쓰기 실패", e)
                    }
                }
            } catch (e: InterruptedException) {
                // 스레드가 중단되면 정상 종료
            } catch (e: Exception) {
                logger.error("로거 스레드 예외 발생", e)
            }
        }
    }

    /**
     * 수신 메시지를 로깅합니다.
     */
    override fun logIncoming(msgLength: Int, req: Int, initiator: RequestInitiator, str: String, data: Any?) {
        if (!isInitialized.get()) return

        totalIncoming += msgLength
        logMessage("Ext → IDEA", totalIncoming, msgLength, req, initiator, str, data)
    }

    /**
     * 발신 메시지를 로깅합니다.
     */
    override fun logOutgoing(msgLength: Int, req: Int, initiator: RequestInitiator, str: String, data: Any?) {
        if (!isInitialized.get()) return

        totalOutgoing += msgLength
        logMessage("IDEA → Ext", totalOutgoing, msgLength, req, initiator, str, data)
    }

    /**
     * 실제 로그 메시지를 포맷하고 큐에 추가합니다.
     */
    private fun logMessage(
        direction: String,
        totalLength: Int,
        msgLength: Int,
        req: Int,
        initiator: RequestInitiator,
        str: String,
        data: Any?,
    ) {
        try {
            val timestamp = formatTimestampWithMilliseconds(Date())
            val initiatorStr = when (initiator) {
                RequestInitiator.LocalSide -> "Local"
                RequestInitiator.OtherSide -> "Other"
            }

            val logEntry = StringBuilder()
            logEntry.append("[$timestamp] ")
            logEntry.append("[$direction] ")
            logEntry.append("[총: ${totalLength.toString().padStart(7)}] ")
            logEntry.append("[길이: ${msgLength.toString().padStart(5)}] ")
            logEntry.append("[${req.toString().padStart(5)}] ")
            logEntry.append("[$initiatorStr] ")
            logEntry.append(str)

            if (data != null) {
                val dataStr = if (str.endsWith("(")) {
                    "$data)"
                } else {
                    data.toString()
                }
                logEntry.append(" ").append(dataStr)
            }

            // 코루틴을 사용하여 로그 큐에 비동기적으로 추가합니다.
            coroutineScope.launch(Dispatchers.IO) {
                logQueue.add(logEntry.toString())
            }
        } catch (e: Exception) {
            logger.error("로그 메시지 포맷 실패", e)
        }
    }

    /**
     * 데이터를 안전하게 문자열로 변환합니다.
     */
    private fun stringify(data: Any?): String {
        return try {
            when (data) {
                is Map<*, *> -> data.toString()
                is Collection<*> -> data.toString()
                is Array<*> -> data.contentToString()
                else -> data.toString()
            }
        } catch (e: Exception) {
            "직렬화 불가능한 데이터: ${e.message}"
        }
    }

    /**
     * 밀리초를 포함한 타임스탬프를 포맷합니다.
     */
    private fun formatTimestampWithMilliseconds(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val hours = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minutes = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        val seconds = calendar.get(Calendar.SECOND).toString().padStart(2, '0')
        val milliseconds = calendar.get(Calendar.MILLISECOND).toString().padStart(3, '0')

        return "$year-$month-$day $hours:$minutes:$seconds.$milliseconds"
    }

    /**
     * 리소스를 해제합니다.
     * 로그 파일 푸터(footer)를 작성하고, 쓰기 객체를 닫으며, 로거 스레드를 중단합니다.
     */
    override fun dispose() {
        if (isDisposed.getAndSet(true)) {
            return
        }

        try {
            // 로그 파일 푸터 작성
            val endTime = formatTimestampWithMilliseconds(Date())
            val footer = """
               |-------------------------------------------------------------
               | IDEA RPC 프로토콜 로거
               | 종료 시간: $endTime
               | 총 수신: $totalIncoming 바이트
               | 총 발신: $totalOutgoing 바이트
               |-------------------------------------------------------------
            """.trimMargin()

            logQueue.add(footer) // 큐에 푸터 추가

            // 로그 큐가 비워질 때까지 잠시 기다립니다.
            var retries = 0
            while (logQueue.isNotEmpty() && retries < 10) {
                Thread.sleep(100)
                retries++
            }

            // 쓰기 객체를 닫습니다.
            writer?.close()
            writer = null

            // 로거 스레드를 중단합니다.
            loggerThread?.interrupt()
            loggerThread = null

            logger.info("FileRPCProtocolLogger 해제됨")
        } catch (e: Exception) {
            logger.error("FileRPCProtocolLogger 해제 실패", e)
        }
    }
}
