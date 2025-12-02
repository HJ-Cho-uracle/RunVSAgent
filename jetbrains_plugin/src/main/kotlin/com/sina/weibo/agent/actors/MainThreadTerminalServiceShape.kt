// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.terminal.TerminalInstance
import com.sina.weibo.agent.terminal.TerminalInstanceManager
import com.sina.weibo.agent.terminal.TerminalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel


/**
 * IntelliJ 메인 스레드에서 터미널 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 터미널 생성, 소멸, UI 제어, 데이터 전송 등 다양한 기능을 정의합니다.
 * VSCode Extension Host의 `MainThreadTerminalServiceShape`에 해당합니다.
 */
interface MainThreadTerminalServiceShape : Disposable {
    /**
     * 새로운 터미널을 생성합니다.
     * @param extHostTerminalId Extension Host에서 관리하는 터미널의 고유 ID
     * @param config 터미널 실행에 필요한 설정 (예: 셸 경로, 환경 변수 등)
     */
    suspend fun createTerminal(extHostTerminalId: String, config: Map<String, Any?>)

    /**
     * 지정된 터미널과 관련된 리소스를 해제합니다.
     * @param id 터미널 식별자 (문자열 또는 숫자)
     */
    fun dispose(id: Any)
    
    /**
     * 터미널을 UI에서 숨깁니다.
     * @param id 터미널 식별자
     */
    fun hide(id: Any)
    
    /**
     * 터미널에 텍스트를 보냅니다.
     * @param id 터미널 식별자
     * @param text 보낼 텍스트
     * @param shouldExecute 텍스트를 보낸 후 바로 실행할지(엔터키를 누를지) 여부
     */
    fun sendText(id: Any, text: String, shouldExecute: Boolean?)
    
    /**
     * 터미널을 UI에 표시합니다.
     * @param id 터미널 식별자
     * @param preserveFocus 터미널을 보여준 후에도 현재 포커스를 유지할지 여부
     */
    fun show(id: Any, preserveFocus: Boolean?)
    
    /**
     * 프로세스 실행을 지원하는지 여부를 등록합니다.
     */
    fun registerProcessSupport(isSupported: Boolean)
    
    /**
     * 터미널 프로필 제공자를 등록합니다. (예: Git Bash, PowerShell 등)
     */
    fun registerProfileProvider(id: String, extensionIdentifier: String)
    
    /**
     * 터미널 프로필 제공자를 등록 해제합니다.
     */
    fun unregisterProfileProvider(id: String)
    
    /**
     * 터미널 자동 완성 제공자를 등록합니다.
     */
    fun registerCompletionProvider(id: String, extensionIdentifier: String, vararg triggerCharacters: String)
    
    /**
     * 터미널 자동 완성 제공자를 등록 해제합니다.
     */
    fun unregisterCompletionProvider(id: String)
    
    /**
     * 터미널 빠른 수정(Quick Fix) 제공자를 등록합니다.
     */
    fun registerQuickFixProvider(id: String, extensionIdentifier: String)
    
    /**
     * 터미널 빠른 수정 제공자를 등록 해제합니다.
     */
    fun unregisterQuickFixProvider(id: String)
    
    /**
     * 터미널에서 사용할 환경 변수 컬렉션을 설정합니다.
     */
    fun setEnvironmentVariableCollection(
        extensionIdentifier: String,
        persistent: Boolean,
        collection: Map<String, Any?>?,
        descriptionMap: Map<String, Any?>
    )

    // --- 이벤트 전송 제어 ---
    fun startSendingDataEvents()
    fun stopSendingDataEvents()
    fun startSendingCommandEvents()
    fun stopSendingCommandEvents()
    fun startLinkProvider()
    fun stopLinkProvider()

    // --- 프로세스 관련 데이터 전송 ---
    
    /** 터미널 프로세스에 데이터를 보냅니다. */
    fun sendProcessData(terminalId: Int, data: String)
    
    /** 터미널 프로세스가 준비되었음을 알립니다. */
    fun sendProcessReady(terminalId: Int, pid: Int, cwd: String, windowsPty: Map<String, Any?>?)
    
    /** 터미널 프로세스의 속성 변경을 알립니다. */
    fun sendProcessProperty(terminalId: Int, property: Map<String, Any?>)
    
    /** 터미널 프로세스가 종료되었음을 알립니다. */
    fun sendProcessExit(terminalId: Int, exitCode: Int?)
}

/**
 * `MainThreadTerminalServiceShape` 인터페이스의 구현 클래스입니다.
 * `TerminalInstanceManager`를 통해 실제 터미널 인스턴스를 생성하고 관리합니다.
 */
class MainThreadTerminalService(private val project: Project) : MainThreadTerminalServiceShape {
    private val logger = Logger.getInstance(MainThreadTerminalService::class.java)
    
    // 터미널 인스턴스를 관리하는 프로젝트 레벨 서비스
    private val terminalManager = project.service<TerminalInstanceManager>()
    
    // 이 서비스의 생명주기에 맞춰 관리되는 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override suspend fun createTerminal(extHostTerminalId: String, config: Map<String, Any?>) {
        logger.info("🚀 터미널 생성 중: $extHostTerminalId, config: $config")
        
        try {
            if (terminalManager.containsTerminal(extHostTerminalId)) {
                logger.warn("터미널이 이미 존재함: $extHostTerminalId")
                return
            }
            
            val pluginContext = PluginContext.getInstance(project)
            val rpcProtocol = pluginContext.getRPCProtocol() ?: throw IllegalStateException("RPC 프로토콜이 초기화되지 않았습니다.")
            logger.info("✅ RPC 프로토콜 인스턴스 확보: ${rpcProtocol.javaClass.simpleName}")
            
            // 터미널을 식별할 고유 숫자 ID를 할당받습니다.
            val numericId = terminalManager.allocateNumericId()
            logger.info("🔢 터미널 숫자 ID 할당: $numericId")
            
            // Map 형태의 설정을 TerminalConfig 데이터 클래스로 변환합니다.
            val terminalConfig = TerminalConfig.fromMap(config)
            // 실제 터미널 로직을 담고 있는 TerminalInstance를 생성합니다.
            val terminalInstance = TerminalInstance(extHostTerminalId, numericId, project, terminalConfig, rpcProtocol)

            terminalInstance.initialize()

            // 생성된 터미널 인스턴스를 매니저에 등록합니다.
            terminalManager.registerTerminal(extHostTerminalId, terminalInstance)
            
            logger.info("✅ 터미널 생성 성공: $extHostTerminalId (numericId: $numericId)")
            
        } catch (e: Exception) {
            logger.error("❌ 터미널 생성 실패: $extHostTerminalId", e)
            terminalManager.unregisterTerminal(extHostTerminalId) // 실패 시 리소스 정리
            throw e
        }
    }

    override fun dispose(id: Any) {
        try {
            logger.info("🧹 터미널 파괴 중: $id")
            val terminalInstance = terminalManager.unregisterTerminal(id.toString())
            terminalInstance?.dispose()
            logger.info("✅ 터미널 파괴 완료: $id")
        } catch (e: Exception) {
            logger.error("❌ 터미널 파괴 실패: $id", e)
        }
    }

    override fun hide(id: Any) {
        try {
            logger.info("🙈 터미널 숨기는 중: $id")
            getTerminalInstance(id)?.hide()
            logger.info("✅ 터미널 숨김 완료: $id")
        } catch (e: Exception) {
            logger.error("❌ 터미널 숨기기 실패: $id", e)
        }
    }

    override fun sendText(id: Any, text: String, shouldExecute: Boolean?) {
        try {
            logger.debug("📤 터미널에 텍스트 전송 $id: $text (실행: $shouldExecute)")
            getTerminalInstance(id)?.sendText(text, shouldExecute ?: false)
            logger.debug("✅ 터미널에 텍스트 전송 완료: $id")
        } catch (e: Exception) {
            logger.error("❌ 터미널에 텍스트 전송 실패: $id", e)
        }
    }

    override fun show(id: Any, preserveFocus: Boolean?) {
        try {
            logger.info("👁️ 터미널 표시 중: $id (포커스 유지: $preserveFocus)")
            getTerminalInstance(id)?.show(preserveFocus ?: true)
            logger.info("✅ 터미널 표시 완료: $id")
        } catch (e: Exception) {
            logger.error("❌ 터미널 표시 실패: $id", e)
        }
    }

    // --- 아래는 현재 로깅만 수행하거나 TODO로 남겨진 메소드들 ---

    override fun registerProcessSupport(isSupported: Boolean) {
        logger.info("📋 프로세스 지원 등록: $isSupported")
    }

    override fun registerProfileProvider(id: String, extensionIdentifier: String) {
        logger.info("📋 프로필 제공자 등록: $id (확장: $extensionIdentifier)")
    }

    override fun unregisterProfileProvider(id: String) {
        logger.info("📋 프로필 제공자 등록 해제: $id")
    }

    override fun registerCompletionProvider(id: String, extensionIdentifier: String, vararg triggerCharacters: String) {
        logger.info("📋 자동 완성 제공자 등록: $id (확장: $extensionIdentifier, 트리거: ${triggerCharacters.joinToString()})")
    }

    override fun unregisterCompletionProvider(id: String) {
        logger.info("📋 자동 완성 제공자 등록 해제: $id")
    }

    override fun registerQuickFixProvider(id: String, extensionIdentifier: String) {
        logger.info("📋 빠른 수정 제공자 등록: $id (확장: $extensionIdentifier)")
    }

    override fun unregisterQuickFixProvider(id: String) {
        logger.info("📋 빠른 수정 제공자 등록 해제: $id")
    }

    override fun setEnvironmentVariableCollection(
        extensionIdentifier: String,
        persistent: Boolean,
        collection: Map<String, Any?>?,
        descriptionMap: Map<String, Any?>
    ) {
        logger.info("📋 환경 변수 컬렉션 설정: $extensionIdentifier (영구: $persistent)")
    }

    override fun startSendingDataEvents() { logger.info("📋 데이터 이벤트 전송 시작") }
    override fun stopSendingDataEvents() { logger.info("📋 데이터 이벤트 전송 중지") }
    override fun startSendingCommandEvents() { logger.info("📋 커맨드 이벤트 전송 시작") }
    override fun stopSendingCommandEvents() { logger.info("📋 커맨드 이벤트 전송 중지") }
    override fun startLinkProvider() { logger.info("📋 링크 제공자 시작") }
    override fun stopLinkProvider() { logger.info("📋 링크 제공자 중지") }
    override fun sendProcessData(terminalId: Int, data: String) { logger.debug("프로세스 데이터 전송: terminal=$terminalId") }
    override fun sendProcessReady(terminalId: Int, pid: Int, cwd: String, windowsPty: Map<String, Any?>?) { logger.info("프로세스 준비됨: terminal=$terminalId, pid=$pid, cwd=$cwd") }
    override fun sendProcessProperty(terminalId: Int, property: Map<String, Any?>) { logger.debug("📋 프로세스 속성 전송: terminal=$terminalId") }
    override fun sendProcessExit(terminalId: Int, exitCode: Int?) { logger.info("📋 프로세스 종료 전송: terminal=$terminalId, code=$exitCode") }

    /**
     * ID(문자열 또는 숫자)로 터미널 인스턴스를 가져옵니다.
     */
    fun getTerminalInstance(id: Any): TerminalInstance? {
        return when (id) {
            is String -> terminalManager.getTerminalInstance(id)
            is Number -> terminalManager.getTerminalInstance(id.toInt())
            else -> {
                logger.warn("지원하지 않는 ID 타입: ${id.javaClass.name}, 문자열로 변환 시도")
                terminalManager.getTerminalInstance(id.toString())
            }
        }
    }
    
    /**
     * 모든 터미널 인스턴스를 가져옵니다.
     */
    fun getAllTerminals(): Collection<TerminalInstance> {
        return terminalManager.getAllTerminals()
    }

    override fun dispose() {
        logger.info("🧹 메인 스레드 터미널 서비스 해제 중")
        try {
            scope.cancel() // 모든 코루틴 작업을 취소합니다.
            logger.info("✅ 메인 스레드 터미널 서비스 해제 완료")
        } catch (e: Exception) {
            logger.error("❌ 메인 스레드 터미널 서비스 해제 실패", e)
        }
    }
}
