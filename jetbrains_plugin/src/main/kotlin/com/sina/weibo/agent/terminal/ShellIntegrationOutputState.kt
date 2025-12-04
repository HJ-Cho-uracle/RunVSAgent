// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 셸 통합 이벤트 타입을 나타내는 봉인된(sealed) 클래스입니다.
 */
sealed class ShellEvent {
    data class ShellExecutionStart(val commandLine: String, val cwd: String) : ShellEvent()
    data class ShellExecutionEnd(val commandLine: String, val exitCode: Int?) : ShellEvent()
    data class ShellExecutionData(val data: String) : ShellEvent()
    data class CwdChange(val cwd: String) : ShellEvent()
}

/**
 * 셸 이벤트 리스너 인터페이스입니다.
 */
interface ShellEventListener {
    fun onShellExecutionStart(commandLine: String, cwd: String)
    fun onShellExecutionEnd(commandLine: String, exitCode: Int?)
    fun onShellExecutionData(data: String)
    fun onCwdChange(cwd: String)
}

/**
 * 셸 통합 출력 상태 관리자입니다.
 * VSCode 셸 통합 구현을 참조하여 터미널 출력에서 셸 통합 마커를 파싱하고 상태를 관리합니다.
 * 참조: https://github.com/microsoft/vscode/blob/main/src/vs/workbench/contrib/terminal/common/terminalShellIntegration.ts
 */
class ShellIntegrationOutputState {
    private val logger = Logger.getInstance(ShellIntegrationOutputState::class.java)

    // 이벤트 리스너 목록
    private val listeners = mutableListOf<ShellEventListener>()

    // --- 상태 속성 ---
    @Volatile
    var isCommandRunning: Boolean = false // 명령어가 실행 중인지 여부
        private set

    @Volatile
    var currentCommand: String = "" // 현재 실행 중인 명령어
        private set

    @Volatile
    var currentNonce: String = "" // 현재 명령어의 Nonce (고유 식별자)
        private set

    @Volatile
    var commandStatus: Int? = null // 명령어의 종료 코드
        private set

    @Volatile
    var currentDirectory: String = "" // 현재 작업 디렉터리
        private set

    @Volatile
    var output: String = "" // 터미널 출력 내용
        private set

    // --- 출력 버퍼링 관련 ---
    private val pendingOutput = StringBuilder() // 보류 중인 출력 버퍼
    private val pendingOutputLock = Any() // 버퍼 동기화를 위한 락 객체
    private val lastAppendTime = AtomicLong(0) // 마지막으로 출력 데이터가 추가된 시간
    private val isFlushScheduled = AtomicBoolean(false) // 플러시 작업이 예약되었는지 여부

    // 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 이벤트 리스너를 추가합니다.
     */
    fun addListener(listener: ShellEventListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * 이벤트 리스너를 제거합니다.
     */
    fun removeListener(listener: ShellEventListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * 모든 리스너에게 이벤트를 알립니다.
     */
    private fun notifyListeners(event: ShellEvent) {
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    when (event) {
                        is ShellEvent.ShellExecutionStart ->
                            listener.onShellExecutionStart(event.commandLine, event.cwd)
                        is ShellEvent.ShellExecutionEnd ->
                            listener.onShellExecutionEnd(event.commandLine, event.exitCode)
                        is ShellEvent.ShellExecutionData ->
                            listener.onShellExecutionData(event.data)
                        is ShellEvent.CwdChange ->
                            listener.onCwdChange(event.cwd)
                    }
                } catch (e: Exception) {
                    logger.warn("셸 이벤트 리스너 알림 실패", e)
                }
            }
        }
    }

    /**
     * 출력 데이터를 버퍼에 추가하고, 지연된 플러시를 스케줄링합니다.
     */
    private fun appendOutput(text: String) {
        logger.debug("📝 appendOutput 호출됨: '$text', 길이=${text.length}")
        synchronized(pendingOutputLock) {
            pendingOutput.append(text)
            logger.debug("📝 pendingOutput 업데이트됨, 길이: ${pendingOutput.length}")
        }

        lastAppendTime.set(System.currentTimeMillis())

        // 플러시 작업이 예약되어 있지 않으면 새로 스케줄링합니다.
        if (isFlushScheduled.compareAndSet(false, true)) {
            logger.debug("📝 플러시 작업 스케줄링 중, 50ms 후 실행 예정")
            scope.launch {
                delay(50) // 50ms 지연
                flushPendingOutput()
            }
        } else {
            logger.debug("📝 플러시 작업이 이미 예약되어 있어 건너뜀")
        }
    }

    /**
     * 보류 중인 출력을 플러시하고 리스너들에게 알립니다.
     */
    private fun flushPendingOutput() {
        logger.debug("🚀 flushPendingOutput 호출됨")
        val textToFlush = synchronized(pendingOutputLock) {
            if (pendingOutput.isNotEmpty()) {
                val text = pendingOutput.toString()
                pendingOutput.clear()
                logger.debug("🚀 플러시할 텍스트 준비: '$text', 길이=${text.length}")
                text
            } else {
                logger.debug("🚀 pendingOutput이 비어 있어 플러시할 내용 없음")
                null
            }
        }

        isFlushScheduled.set(false) // 플러시 스케줄링 상태 초기화

        textToFlush?.let { text ->
            output += text // 전체 출력에 추가
            logger.info("🚀 ShellExecutionData 이벤트 전송: '$text', 길이=${text.length}")
            notifyListeners(ShellEvent.ShellExecutionData(text))
        }
    }

    /**
     * 출력을 지우고 상태를 초기화합니다.
     */
    fun clearOutput() {
        synchronized(pendingOutputLock) {
            output = ""
            pendingOutput.clear()
            currentNonce = ""
        }
        isFlushScheduled.set(false)
    }

    /**
     * 현재 상태를 종료합니다.
     */
    fun terminate() {
        isCommandRunning = false
        flushPendingOutput() // 종료 전에 보류 중인 출력 플러시
    }

    /**
     * 터미널의 원시 출력 데이터를 처리합니다.
     * 셸 통합 마커를 파싱하고 깨끗한 내용을 추출합니다.
     */
    fun appendRawOutput(output: String) {
        logger.debug("📥 원시 출력 처리 중: ${output.length} 문자, isCommandRunning=$isCommandRunning")
        logger.debug("📥 원시 출력 내용: '${output.replace("\u001b", "\\u001b").replace("\u0007", "\\u0007")}'")

        var currentIndex = 0
        var hasShellIntegrationMarkers = false

        while (currentIndex < output.length) {
            // 셸 통합 마커 찾기: \u001b]633;
            val markerIndex = output.indexOf("\u001b]633;", currentIndex)

            if (markerIndex == -1) {
                // 마커를 찾지 못함
                val remainingContent = output.substring(currentIndex)
                logger.debug("📤 셸 통합 마커를 찾지 못함, 남은 내용: '$remainingContent', isCommandRunning=$isCommandRunning")

                if (!hasShellIntegrationMarkers && remainingContent.isNotEmpty()) {
                    // 전체 출력에 셸 통합 마커가 없으면 모든 내용을 명령어 출력으로 간주
                    logger.debug("📤 셸 통합 마커 없음, 모든 내용을 명령어 출력으로 처리")
                    appendOutput(remainingContent)
                } else if (isCommandRunning && currentIndex < output.length) {
                    logger.debug("📤 남은 내용을 출력에 추가: '$remainingContent'")
                    appendOutput(remainingContent)
                } else if (!isCommandRunning) {
                    logger.debug("⚠️ 명령어가 실행 중이 아님, 출력 무시: '$remainingContent'")
                }
                break
            }

            hasShellIntegrationMarkers = true

            // 마커 이전의 내용을 출력에 추가
            if (isCommandRunning && currentIndex < markerIndex) {
                val beforeMarker = output.substring(currentIndex, markerIndex)
                logger.debug("📤 마커 이전 내용 추가: '$beforeMarker'")
                appendOutput(beforeMarker)
            } else if (!isCommandRunning && currentIndex < markerIndex) {
                val beforeMarker = output.substring(currentIndex, markerIndex)
                logger.debug("⚠️ 명령어가 실행 중이 아님, 마커 이전 내용 무시: '$beforeMarker'")
            }

            // 마커 파싱
            val typeStart = markerIndex + 6 // "\u001b]633;".length
            if (typeStart >= output.length) {
                if (isCommandRunning && currentIndex < output.length) {
                    appendOutput(output.substring(currentIndex))
                }
                break
            }

            val type = MarkerType.fromChar(output[typeStart]) // 마커 타입 추출
            val paramStart = typeStart + 1

            // 마커 끝 찾기: \u0007 (BEL 문자)
            val paramEnd = output.indexOf('\u0007', paramStart)
            if (paramEnd == -1) {
                logger.debug("⚠️ 마커 끝을 찾지 못함, 건너뜀")
                currentIndex = typeStart
                continue
            }

            // 파라미터 추출
            val params = if (paramStart < paramEnd) {
                output.substring(paramStart, paramEnd)
            } else {
                ""
            }

            val components = if (params.startsWith(";")) {
                params.substring(1).split(";")
            } else {
                listOf(params)
            }

            logger.debug("🔍 셸 통합 마커 파싱: 타입=$type, 파라미터='$params', 구성요소=$components")

            // 마커 타입에 따른 처리
            when (type) {
                MarkerType.COMMAND_LINE -> {
                    logger.info("🎯 셸 통합 - 명령어 라인 마커 감지")
                    if (components.isNotEmpty() && components[0].isNotEmpty()) {
                        currentCommand = components[0]
                        currentNonce = if (components.size >= 2) components[1] else ""
                        logger.info("🎯 셸 통합 - 명령어 라인: '$currentCommand'")
                    }
                }

                MarkerType.COMMAND_EXECUTED -> {
                    logger.info("🚀 셸 통합 - 명령어 실행 마커 감지")
                    isCommandRunning = true
                    if (currentCommand.isNotEmpty()) {
                        logger.info("🚀 셸 통합 - 명령어 시작: '$currentCommand', isCommandRunning=$isCommandRunning")
                        notifyListeners(ShellEvent.ShellExecutionStart(currentCommand, currentDirectory))
                        appendOutput(output.substring(markerIndex, paramEnd + 1)) // 마커 자체도 출력에 포함
                    }
                }

                MarkerType.COMMAND_FINISHED -> {
                    logger.info("🏁 셸 통합 - 명령어 종료 마커 감지")
                    if (currentCommand.isNotEmpty()) {
                        appendOutput(output.substring(markerIndex, paramEnd + 1)) // 마커 자체도 출력에 포함
                        flushPendingOutput() // 명령어 종료 전에 보류 중인 출력 플러시

                        commandStatus = components.firstOrNull()?.toIntOrNull()
                        logger.info("🏁 셸 통합 - 명령어 종료: '$currentCommand' (종료 코드: $commandStatus)")
                        notifyListeners(ShellEvent.ShellExecutionEnd(currentCommand, commandStatus))
                        currentCommand = ""
                    }
                    isCommandRunning = false
                }

                MarkerType.PROPERTY -> {
                    logger.debug("📋 셸 통합 - 속성 마커 감지")
                    if (components.isNotEmpty()) {
                        val property = components[0]
                        if (property.startsWith("Cwd=")) {
                            val cwdValue = property.substring(4) // "Cwd=".length
                            if (cwdValue != currentDirectory) {
                                currentDirectory = cwdValue
                                logger.info("📁 셸 통합 - 디렉터리 변경됨: '$cwdValue'")
                                notifyListeners(ShellEvent.CwdChange(cwdValue))
                            }
                        }
                    }
                }

                MarkerType.PROMPT_START -> { logger.debug("🎯 셸 통합 - 프롬프트 시작") }
                MarkerType.COMMAND_START -> { logger.debug("🎯 셸 통합 - 명령어 입력 시작") }

                else -> { logger.debug("🔍 셸 통합 - 처리되지 않은 마커 타입: $type") }
            }

            currentIndex = paramEnd + 1 // 다음 마커 검색을 위해 인덱스 업데이트
        }
    }

    /**
     * 셸 통합 마커가 제거된 깨끗한 출력 문자열을 가져옵니다.
     */
    fun getCleanOutput(rawOutput: String): String {
        var result = rawOutput

        // 모든 셸 통합 마커를 제거합니다.
        val markerPattern = Regex("\u001b\\]633;[^\\u0007]*\\u0007")
        result = markerPattern.replace(result, "")

        return result
    }

    /**
     * 리소스를 해제합니다.
     */
    fun dispose() {
        scope.cancel() // 코루틴 스코프 취소
        synchronized(listeners) {
            listeners.clear() // 리스너 목록 비우기
        }
    }

    /**
     * VSCode 셸 통합 마커 타입을 정의하는 열거형입니다.
     * 참조: https://github.com/microsoft/vscode/blob/main/src/vs/workbench/contrib/terminal/common/terminalShellIntegration.ts
     */
    private enum class MarkerType(val char: Char) {
        // 구현된 타입
        COMMAND_LINE('E'), // 명령어 라인 내용
        COMMAND_FINISHED('D'), // 명령어 종료
        COMMAND_EXECUTED('C'), // 명령어 출력 시작
        PROPERTY('P'), // 속성 설정 (예: Cwd)

        // 프롬프트 관련
        PROMPT_START('A'), // 프롬프트 시작
        COMMAND_START('B'), // 명령어 입력 시작

        // 라인 연속 관련 (아직 구현되지 않음)
        CONTINUATION_START('F'),
        CONTINUATION_END('G'),

        // 오른쪽 프롬프트 관련 (아직 구현되지 않음)
        RIGHT_PROMPT_START('H'),
        RIGHT_PROMPT_END('I'),

        UNKNOWN('?'), // 알 수 없는 마커 타입
        ;

        companion object {
            fun fromChar(char: Char): MarkerType {
                return values().find { it.char == char } ?: UNKNOWN
            }
        }
    }
}
