// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.util.URI

/**
 * 터미널 셸 통합 관리자입니다.
 * 터미널 셸 명령어 실행의 생명주기 관리 및 ExtHost와의 RPC 통신을 담당합니다.
 *
 * @param extHostTerminalId ExtHost 터미널 ID
 * @param numericId 숫자 터미널 ID
 * @param rpcProtocol RPC 프로토콜 인스턴스
 */
class TerminalShellIntegration(
    private val extHostTerminalId: String,
    private val numericId: Int,
    private val rpcProtocol: IRPCProtocol
) {
    
    companion object {
        // 로깅을 위한 접두사 상수
        private const val HIGH_CONFIDENCE = 2
        private const val DEFAULT_EXIT_CODE = 0
        private const val LOG_PREFIX_SETUP = "🔧"
        private const val LOG_PREFIX_START = "🚀"
        private const val LOG_PREFIX_END = "🏁"
        private const val LOG_PREFIX_DATA = "✨"
        private const val LOG_PREFIX_CWD = "📁"
        private const val LOG_PREFIX_SUCCESS = "✅"
        private const val LOG_PREFIX_ERROR = "❌"
        private const val LOG_PREFIX_DISPOSE = "🧹"
    }

    private val logger = Logger.getInstance(TerminalShellIntegration::class.java)
    private var shellIntegrationState: ShellIntegrationOutputState? = null // 셸 통합 출력 상태 관리자
    private var shellEventListener: ShellEventListener? = null // 셸 이벤트 리스너
    
    /**
     * ExtHost 터미널 셸 통합 프록시를 지연 초기화합니다.
     */
    private val extHostProxy by lazy {
        rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostTerminalShellIntegration)
    }

    /**
     * 셸 통합을 설정합니다.
     * 셸 이벤트 리스너와 상태 관리자를 초기화합니다.
     */
    fun setupShellIntegration() {
        runCatching {
            logger.info("$LOG_PREFIX_SETUP 셸 통합 설정 중 (터미널: $extHostTerminalId)...")
            
            initializeShellEventListener() // 셸 이벤트 리스너 초기화
            initializeShellIntegrationState() // 셸 통합 상태 관리자 초기화
            
            logger.info("$LOG_PREFIX_SUCCESS 셸 통합 설정 완료 (터미널: $extHostTerminalId)")
        }.onFailure { exception ->
            logger.error("$LOG_PREFIX_ERROR 셸 통합 설정 실패 (터미널: $extHostTerminalId)", exception)
        }
    }

    /**
     * 셸 통합을 해제하고 관련 리소스를 정리합니다.
     */
    fun dispose() {
        logger.info("$LOG_PREFIX_DISPOSE 셸 통합 해제 중: $extHostTerminalId")
        
        runCatching {
            shellIntegrationState?.apply {
                terminate() // 현재 상태 종료
                dispose() // 리소스 해제
            }
            shellEventListener = null
            shellIntegrationState = null
            
            logger.info("$LOG_PREFIX_SUCCESS 셸 통합 해제 완료: $extHostTerminalId")
        }.onFailure { exception ->
            logger.error("$LOG_PREFIX_ERROR 셸 통합 해제 실패: $extHostTerminalId", exception)
        }
    }

    /**
     * 원시 출력 데이터를 추가합니다.
     * `ShellIntegrationOutputState`에 데이터를 전달하여 파싱 및 처리를 수행합니다.
     * @param data 출력 데이터
     */
    fun appendRawOutput(data: String) {
        shellIntegrationState?.appendRawOutput(data)
    }

    /**
     * 셸 이벤트 리스너를 초기화합니다.
     */
    private fun initializeShellEventListener() {
        shellEventListener = TerminalShellEventListener()
    }

    /**
     * 셸 통합 상태 관리자를 초기화합니다.
     */
    private fun initializeShellIntegrationState() {
        shellIntegrationState = ShellIntegrationOutputState().apply {
            shellEventListener?.let { addListener(it) } // 상태 관리자에 리스너 등록
        }
    }

    /**
     * RPC 호출을 안전하게 실행하기 위한 헬퍼 함수입니다.
     * @param operation 로깅을 위한 작업 이름
     * @param action 실행할 RPC 작업
     */
    private inline fun safeRpcCall(operation: String, action: () -> Unit) {
        runCatching {
            action()
            logger.debug("$LOG_PREFIX_SUCCESS $operation 성공 (터미널: $extHostTerminalId)")
        }.onFailure { exception ->
            logger.error("$LOG_PREFIX_ERROR $operation 실패 (터미널: $extHostTerminalId)", exception)
        }
    }

    /**
     * 터미널 셸 이벤트 리스너의 내부 클래스입니다.
     * 다양한 셸 명령어 실행 이벤트를 처리하고 ExtHost에 알립니다.
     */
    private inner class TerminalShellEventListener : ShellEventListener {
        
        /**
         * 명령어 실행 시작 시 호출됩니다.
         */
        override fun onShellExecutionStart(commandLine: String, cwd: String) {
            logger.info("$LOG_PREFIX_START 명령어 실행 시작: '$commandLine' (디렉터리: '$cwd') (터미널: $extHostTerminalId)")
            
            safeRpcCall("ExtHost에 명령어 시작 알림") {
                extHostProxy.shellExecutionStart(
                    instanceId = numericId,
                    commandLineValue = commandLine,
                    commandLineConfidence = HIGH_CONFIDENCE,
                    isTrusted = true,
                    cwd = URI.file(cwd) // 현재 작업 디렉터리 URI
                )
            }
        }
        
        /**
         * 명령어 실행 종료 시 호출됩니다.
         */
        override fun onShellExecutionEnd(commandLine: String, exitCode: Int?) {
            val actualExitCode = exitCode ?: DEFAULT_EXIT_CODE
            logger.info("$LOG_PREFIX_END 명령어 실행 종료: '$commandLine' (종료 코드: $actualExitCode) (터미널: $extHostTerminalId)")
            
            safeRpcCall("ExtHost에 명령어 종료 알림") {
                extHostProxy.shellExecutionEnd(
                    instanceId = numericId,
                    commandLineValue = commandLine,
                    commandLineConfidence = HIGH_CONFIDENCE,
                    isTrusted = true,
                    exitCode = actualExitCode
                )
            }
        }
        
        /**
         * 셸 출력 데이터가 발생했을 때 호출됩니다.
         */
        override fun onShellExecutionData(data: String) {
            logger.debug("$LOG_PREFIX_DATA 깨끗한 출력 데이터: ${data.length} 문자 (터미널: $extHostTerminalId)")
            
            safeRpcCall("shellExecutionData 전송") {
                extHostProxy.shellExecutionData(
                    instanceId = numericId,
                    data = data
                )
            }
        }
        
        /**
         * 현재 작업 디렉터리(CWD)가 변경되었을 때 호출됩니다.
         */
        override fun onCwdChange(cwd: String) {
            logger.info("$LOG_PREFIX_CWD 작업 디렉터리 변경됨: '$cwd' (터미널: $extHostTerminalId)")
            
            safeRpcCall("ExtHost에 디렉터리 변경 알림") {
                extHostProxy.cwdChange(
                    instanceId = numericId,
                    cwd = URI.file(cwd)
                )
            }
        }
    }
}
