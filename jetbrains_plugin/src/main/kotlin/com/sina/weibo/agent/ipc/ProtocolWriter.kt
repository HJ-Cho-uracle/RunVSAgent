// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 프로토콜 작성기(Protocol Writer) 클래스입니다.
 * 프로토콜 메시지를 소켓에 쓰고 전송하는 역할을 합니다.
 * VSCode의 `ProtocolWriter`에 해당하며, 메시지 순서 보장 기능이 강화되었습니다.
 *
 * @param socket 메시지를 쓸 `ISocket` 인스턴스
 * @param enableLogging 로깅 활성화 여부
 */
class ProtocolWriter(
    private val socket: ISocket,
    private val enableLogging: Boolean = false, // 로깅 제어 변수
) {
    private val logger = Logger.getInstance(ProtocolWriter::class.java)

    // --- 핵심 상태 변수 ---
    private val isDisposed = AtomicBoolean(false) // 객체 해제 여부
    private val isPaused = AtomicBoolean(false) // 쓰기 일시 중지 여부
    private val lastWriteTime = AtomicLong(0) // 마지막 쓰기 시간

    // 메시지 큐 보호를 위한 락
    private val queueLock = ReentrantLock()

    // 메시지 ID로 정렬되는 일반 메시지 큐 (TreeMap 사용)
    private val messageQueue = TreeMap<Int, MessagePackage>()

    // 특별 메시지 큐 (ACK, 우선순위 메시지 등 순서가 중요하지 않은 메시지)
    private val specialMessageQueue = mutableListOf<MessagePackage>()

    // 다음에 예상되는 메시지 ID (순서 보장용)
    private var nextExpectedId = 1

    // --- 쓰기 스케줄링 상태 ---
    private var isWriteScheduled = AtomicBoolean(false) // 쓰기 작업이 예약되었는지 여부
    private var writeJob: Job? = null // 쓰기 작업을 위한 코루틴 Job

    // 메시지 차단 감지 작업
    private var blockingDetectionJob: Job? = null

    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        // 메시지 차단 감지 작업을 시작합니다.
        startBlockingDetection()
    }

    /**
     * 메시지 차단 감지 작업을 시작합니다.
     * 주기적으로 메시지 큐를 확인하여 메시지 손실 또는 순서 불일치를 감지합니다.
     */
    private fun startBlockingDetection() {
        blockingDetectionJob = coroutineScope.launch {
            while (!isDisposed.get()) {
                try {
                    delay(5000) // 5초마다 확인
                    checkMessageBlocking()
                } catch (e: Exception) {
                    if (!isDisposed.get()) {
                        logWarn("차단 감지 중 오류 발생: ${e.message}", e)
                    }
                    break
                }
            }
        }
    }

    /**
     * 메시지 차단 상황을 확인합니다.
     * 메시지 큐가 비어 있지 않지만 예상되는 다음 ID의 메시지가 없으면 차단된 것으로 간주합니다.
     */
    private fun checkMessageBlocking() {
        if (isDisposed.get()) {
            return
        }

        queueLock.withLock {
            // 메시지 큐가 비어 있지 않고, 예상되는 다음 ID의 메시지가 없으면 메시지가 차단된 것입니다.
            if (messageQueue.isNotEmpty() && !messageQueue.containsKey(nextExpectedId)) {
                val minId = messageQueue.firstKey()
                val queueSize = messageQueue.size
                val queueIds = messageQueue.keys.take(10).joinToString(", ") // 처음 10개 ID 표시

                logWarn(
                    "메시지 차단 감지! " +
                        "예상되는 다음 ID: $nextExpectedId, " +
                        "큐의 최소 ID: $minId, " +
                        "큐 크기: $queueSize, " +
                        "큐 ID: [$queueIds${if (queueSize > 10) "..." else ""}]",
                )

                // 연속적인 ID 세그먼트가 있는지 확인합니다.
                val consecutiveIds = mutableListOf<Int>()
                var currentId = minId
                while (messageQueue.containsKey(currentId)) {
                    consecutiveIds.add(currentId)
                    currentId++
                }

                if (consecutiveIds.isNotEmpty()) {
                    logWarn("ID $minId 부터 사용 가능한 연속 ID: ${consecutiveIds.joinToString(", ")}")
                }

                // 누락된 메시지 ID 범위를 확인합니다.
                if (minId > nextExpectedId) {
                    logWarn("누락된 메시지 ID: ${nextExpectedId}부터 ${minId - 1}까지 (${minId - nextExpectedId}개 메시지)")
                }
            }
        }
    }

    // --- 로깅 헬퍼 메소드 (enableLogging 설정에 따라 출력) ---
    private fun logInfo(message: String) { if (enableLogging) logger.info(message) }
    private fun logDebug(message: String) { if (enableLogging) logger.debug(message) }
    private fun logWarn(message: String, throwable: Throwable? = null) { if (enableLogging) logger.warn(message, throwable) }
    private fun logError(message: String, throwable: Throwable? = null) { if (enableLogging) logger.error(message, throwable) }

    /**
     * 메시지 패키지 구조를 나타내는 데이터 클래스입니다.
     */
    private data class MessagePackage(
        val id: Int,
        val data: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessagePackage) return false
            if (id != other.id) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    companion object {
        // 특별 메시지 ID 상수
        private const val ACK_MESSAGE_ID = 0
        private const val PRIORITY_MESSAGE_ID = -1
    }

    /**
     * 리소스를 해제합니다.
     */
    fun dispose() {
        if (isDisposed.getAndSet(true)) {
            return
        }

        blockingDetectionJob?.cancel() // 차단 감지 작업 중지
        blockingDetectionJob = null

        try {
            flush() // 남아있는 메시지 플러시
        } catch (e: Exception) {
            logWarn("프로토콜 작성기 플러시 중 오류 발생: ${e.message}", e)
        }

        writeJob?.cancel() // 쓰기 작업 중지
        logInfo("ProtocolWriter 해제됨")
    }

    /**
     * 모든 데이터가 전송될 때까지 기다립니다.
     */
    suspend fun drain() {
        flush()
        return socket.drain()
    }

    /**
     * 모든 전송 가능한 메시지를 플러시하고 즉시 전송합니다.
     */
    fun flush() {
        writeNow()
    }

    /**
     * 쓰기 작업을 일시 중지합니다.
     */
    fun pause() {
        isPaused.set(true)
    }

    /**
     * 쓰기 작업을 재개합니다.
     */
    fun resume() {
        if (!isPaused.getAndSet(false)) {
            return
        }

        scheduleWriting() // 쓰기 재개 시 스케줄링
    }

    /**
     * 메시지를 소켓에 씁니다.
     * @param msg 쓸 프로토콜 메시지
     */
    fun write(msg: ProtocolMessage) {
        if (isDisposed.get()) {
            logDebug("쓰기 요청 무시됨, 작성기가 해제됨")
            return
        }

        if (msg.type != ProtocolMessageType.KEEP_ALIVE) {
            logInfo("메시지 쓰기: id=${msg.id}, ack=${msg.ack}, type=${msg.type}, 데이터 크기=${msg.data.size}")
        }
        msg.writtenTime = System.currentTimeMillis() // 쓰기 시간 기록
        lastWriteTime.set(System.currentTimeMillis())

        // 메시지 헤더 생성
        val headerBuffer = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH)
        headerBuffer.put(0, msg.type.value.toByte())
        headerBuffer.putInt(1, msg.id)
        headerBuffer.putInt(5, msg.ack)
        headerBuffer.putInt(9, msg.data.size)

        val header = headerBuffer.array()

        // 소켓 이벤트 추적
        socket.traceSocketEvent(
            SocketDiagnosticsEventType.PROTOCOL_HEADER_WRITE,
            mapOf(
                "messageType" to msg.type.toTypeString(),
                "id" to msg.id,
                "ack" to msg.ack,
                "messageSize" to msg.data.size,
            ),
        )
        socket.traceSocketEvent(SocketDiagnosticsEventType.PROTOCOL_MESSAGE_WRITE, msg.data)

        // 헤더와 데이터를 병합하여 하나의 바이트 배열로 만듭니다.
        val combined = ByteArray(header.size + msg.data.size)
        System.arraycopy(header, 0, combined, 0, header.size)
        System.arraycopy(msg.data, 0, combined, header.size, msg.data.size)

        // 메시지를 큐에 추가하고 쓰기 작업을 스케줄링합니다.
        addMessageToQueue(msg.id, combined)
    }

    /**
     * 메시지를 큐에 추가합니다.
     * @param id 메시지 ID
     * @param data 헤더와 내용을 포함한 완전한 메시지 데이터
     */
    private fun addMessageToQueue(id: Int, data: ByteArray) {
        val pkg = MessagePackage(id, data)

        queueLock.withLock {
            // 특별 메시지(ACK 또는 우선순위 메시지)는 특별 큐에 직접 추가합니다.
            if (id == ACK_MESSAGE_ID || id == PRIORITY_MESSAGE_ID) {
                specialMessageQueue.add(pkg)
                logDebug("특별 메시지를 큐에 추가: id=$id")
            } else {
                // 일반 메시지는 ID로 정렬되는 큐에 추가합니다.
                messageQueue[id] = pkg
                logDebug("정렬된 큐에 메시지 추가: id=$id, 큐 크기=${messageQueue.size}")
            }
        }

        scheduleWriting() // 쓰기 작업을 스케줄링합니다.
    }

    /**
     * 쓰기 작업을 스케줄링합니다.
     */
    private fun scheduleWriting() {
        if (isPaused.get() || isDisposed.get()) {
            return
        }

        // 이미 예약된 작업이 없으면 새로 예약합니다.
        if (!isWriteScheduled.compareAndSet(false, true)) {
            return
        }

        writeJob = coroutineScope.launch {
            try {
                writeNow() // 즉시 쓰기 작업을 수행합니다.

                isWriteScheduled.set(false) // 스케줄링 상태 초기화

                // 아직 쓸 데이터가 남아 있으면 다시 스케줄링합니다.
                if (hasDataToWrite()) {
                    scheduleWriting()
                }
            } catch (e: Exception) {
                logError("쓰기 작업 중 오류 발생: ${e.message}", e)
                isWriteScheduled.set(false)
                if (!isDisposed.get() && hasDataToWrite()) {
                    delay(100) // 오류 발생 시 잠시 후 재시도
                    scheduleWriting()
                }
            }
        }
    }

    /**
     * 쓸 데이터가 큐에 있는지 확인합니다.
     */
    private fun hasDataToWrite(): Boolean {
        return queueLock.withLock {
            specialMessageQueue.isNotEmpty() || messageQueue.isNotEmpty()
        }
    }

    /**
     * 현재 전송 가능한 모든 메시지를 즉시 소켓에 씁니다.
     */
    private fun writeNow() {
        if (isPaused.get() || isDisposed.get()) {
            return
        }

        val dataToWrite = queueLock.withLock {
            if (specialMessageQueue.isEmpty() && messageQueue.isEmpty()) {
                return@withLock null
            }

            // 1. 특별 메시지 먼저 처리
            var specialData: ByteArray? = null
            if (specialMessageQueue.isNotEmpty()) {
                specialData = specialMessageQueue.flatMap { it.data.toList() }.toByteArray()
                specialMessageQueue.clear()
            }

            // 2. 일반 메시지 큐 확인
            if (messageQueue.isEmpty()) {
                return@withLock specialData
            }

            // `nextExpectedId`부터 시작하는 연속적인 메시지들을 찾습니다.
            val messagesToSend = mutableListOf<MessagePackage>()
            var currentId = nextExpectedId

            // 예상되는 다음 ID의 메시지가 큐에 없으면, 더 이상 메시지를 보내지 않고 기다립니다.
            if (!messageQueue.containsKey(nextExpectedId)) {
                logInfo("ID=$nextExpectedId 메시지를 기다리는 중, 큐: ${messageQueue.size}")
                return@withLock specialData
            }

            // 연속적인 메시지들을 수집합니다.
            while (messageQueue.containsKey(currentId)) {
                val message = messageQueue[currentId]!!
                messagesToSend.add(message)
                messageQueue.remove(currentId)
                currentId++
            }

            // `nextExpectedId`를 업데이트합니다.
            if (messagesToSend.isNotEmpty()) {
                nextExpectedId = currentId
                logDebug("다음 예상 ID 업데이트됨: $nextExpectedId")

                // 특별 메시지와 일반 메시지를 병합하여 반환합니다.
                if (specialData != null) {
                    return@withLock specialData + messagesToSend.flatMap { it.data.toList() }.toByteArray()
                } else {
                    return@withLock messagesToSend.flatMap { it.data.toList() }.toByteArray()
                }
            }

            specialData // 연속적인 메시지가 없으면 특별 메시지만 반환
        }

        // (락 외부에서) 데이터를 소켓에 씁니다.
        if (dataToWrite != null && dataToWrite.isNotEmpty()) {
            try {
                logInfo("소켓에 ${dataToWrite.size} 바이트 쓰기")
                socket.traceSocketEvent(
                    SocketDiagnosticsEventType.PROTOCOL_WRITE,
                    mapOf("byteLength" to dataToWrite.size),
                )
                socket.write(dataToWrite)
            } catch (e: Exception) {
                logError("소켓 쓰기 중 오류 발생: ${e.message}", e)
                if (!isDisposed.get()) {
                    isDisposed.set(true)
                }
                throw e
            }
        }
    }

    /**
     * 마지막 쓰기 시간을 가져옵니다.
     */
    fun getLastWriteTime(): Long {
        return lastWriteTime.get()
    }
}
