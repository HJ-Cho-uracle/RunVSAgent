// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 터미널 인스턴스 관리자(Terminal Instance Manager) 클래스입니다.
 * 모든 터미널 인스턴스의 생명주기와 매핑을 관리합니다.
 * 다른 서비스 간의 순환 의존성을 방지하는 역할을 합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class TerminalInstanceManager : Disposable {
    private val logger = Logger.getInstance(TerminalInstanceManager::class.java)

    // --- 터미널 인스턴스 관리 ---
    // ExtHost 터미널 ID를 키로 하는 `TerminalInstance` 맵
    private val terminals = ConcurrentHashMap<String, TerminalInstance>()

    // 숫자 ID를 키로 하는 `TerminalInstance` 맵
    private val terminalsByNumericId = ConcurrentHashMap<Int, TerminalInstance>()

    // 다음 할당될 숫자 ID를 위한 카운터
    private val nextNumericId = AtomicInteger(1)

    /**
     * 새로운 숫자 ID를 할당합니다.
     * @return 할당된 숫자 ID
     */
    fun allocateNumericId(): Int {
        return nextNumericId.getAndIncrement()
    }

    /**
     * 터미널 인스턴스를 등록합니다.
     * @param extHostTerminalId ExtHost 터미널 ID
     * @param terminalInstance 등록할 `TerminalInstance` 객체
     */
    fun registerTerminal(extHostTerminalId: String, terminalInstance: TerminalInstance) {
        terminals[extHostTerminalId] = terminalInstance
        terminalsByNumericId[terminalInstance.numericId] = terminalInstance

        // 🎯 터미널 닫힘 이벤트 리스너를 추가하여 자동으로 정리합니다.
        terminalInstance.addTerminalCloseCallback {
            logger.info("🔔 터미널 닫힘 이벤트 콜백 수신: $extHostTerminalId")

            // 터미널 인스턴스를 관리자에서 자동으로 제거합니다.
            unregisterTerminal(extHostTerminalId)

            // TODO: 터미널 상태 저장, 관련 리소스 정리 등 추가 정리 로직을 여기에 추가할 수 있습니다.
        }

        logger.info("📝 터미널 인스턴스 등록됨: $extHostTerminalId (numericId: ${terminalInstance.numericId})")
    }

    /**
     * 터미널 인스턴스를 등록 해제합니다.
     * @param extHostTerminalId 등록 해제할 ExtHost 터미널 ID
     * @return 등록 해제된 `TerminalInstance` 객체, 없으면 null
     */
    fun unregisterTerminal(extHostTerminalId: String): TerminalInstance? {
        val terminalInstance = terminals.remove(extHostTerminalId)
        if (terminalInstance != null) {
            terminalsByNumericId.remove(terminalInstance.numericId)
            logger.info("🗑️ 터미널 인스턴스 등록 해제됨: $extHostTerminalId (numericId: ${terminalInstance.numericId})")
        }
        return terminalInstance
    }

    /**
     * 문자열 ID를 사용하여 터미널 인스턴스를 가져옵니다.
     * @param id ExtHost 터미널 ID
     * @return `TerminalInstance` 객체, 없으면 null
     */
    fun getTerminalInstance(id: String): TerminalInstance? {
        return terminals[id]
    }

    /**
     * 숫자 ID를 사용하여 터미널 인스턴스를 가져옵니다.
     * @param numericId 터미널의 숫자 ID
     * @return `TerminalInstance` 객체, 없으면 null
     */
    fun getTerminalInstance(numericId: Int): TerminalInstance? {
        return terminalsByNumericId[numericId]
    }

    /**
     * 모든 터미널 인스턴스를 가져옵니다.
     * @return 모든 `TerminalInstance` 객체의 컬렉션
     */
    fun getAllTerminals(): Collection<TerminalInstance> {
        return terminals.values
    }

    /**
     * 지정된 ExtHost 터미널 ID를 가진 터미널이 존재하는지 확인합니다.
     * @param extHostTerminalId 확인할 ExtHost 터미널 ID
     * @return 존재하면 true
     */
    fun containsTerminal(extHostTerminalId: String): Boolean {
        return terminals.containsKey(extHostTerminalId)
    }

    /**
     * 현재 등록된 터미널의 개수를 가져옵니다.
     */
    fun getTerminalCount(): Int {
        return terminals.size
    }

    /**
     * 등록된 모든 터미널의 ExtHost ID를 가져옵니다.
     */
    fun getAllTerminalIds(): Set<String> {
        return terminals.keys.toSet()
    }

    /**
     * 등록된 모든 터미널의 숫자 ID를 가져옵니다.
     */
    fun getAllNumericIds(): Set<Int> {
        return terminalsByNumericId.keys.toSet()
    }

    /**
     * 리소스를 해제합니다.
     * 모든 터미널 인스턴스를 해제하고 맵을 비웁니다.
     */
    override fun dispose() {
        logger.info("🧹 터미널 인스턴스 관리자 해제 중")

        try {
            // 모든 터미널 인스턴스를 해제합니다.
            val terminalList = terminals.values.toList()
            terminals.clear()
            terminalsByNumericId.clear()

            terminalList.forEach { terminal ->
                try {
                    terminal.dispose()
                } catch (e: Exception) {
                    logger.error("터미널 인스턴스 해제 실패: ${terminal.extHostTerminalId}", e)
                }
            }

            logger.info("✅ 터미널 인스턴스 관리자 해제 완료")
        } catch (e: Exception) {
            logger.error("❌ 터미널 인스턴스 관리자 해제 실패", e)
        }
    }
}
