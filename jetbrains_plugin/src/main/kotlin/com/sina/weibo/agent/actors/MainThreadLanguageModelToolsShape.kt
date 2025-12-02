// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.plugin.SystemObjectProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * IntelliJ 메인 스레드에서 언어 모델(LM) 도구 관련 서비스를 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadLanguageModelTools`에 해당하며,
 * 언어 모델이 사용할 수 있는 도구를 등록, 해제, 실행하는 기능을 정의합니다.
 */
interface MainThreadLanguageModelToolsShape : Disposable {
    /**
     * 사용 가능한 모든 도구의 목록을 가져옵니다.
     * @return 각 도구의 정보를 담은 Map의 리스트
     */
    fun getTools(): List<Map<String, Any?>>

    /**
     * 지정된 도구를 실행합니다.
     * @param dto 도구 실행에 필요한 파라미터 (예: 도구 ID, 인자 등)
     * @param token 작업 취소를 위한 토큰 (선택 사항)
     * @return 도구 실행 결과를 담은 Map
     */
    fun invokeTool(dto: Map<String, Any?>, token: Any? = null): Map<String, Any?>

    /**
     * 주어진 입력에 대한 토큰 수를 계산합니다.
     * 이는 언어 모델의 컨텍스트 크기 제한을 관리하는 데 사용됩니다.
     * @param callId 호출을 식별하는 고유 ID
     * @param input 토큰 수를 계산할 입력 문자열 또는 내용
     * @param token 작업 취소를 위한 토큰
     * @return 계산된 토큰 수
     */
    fun countTokensForInvocation(callId: String, input: String, token: Any?): Int

    /**
     * 새로운 도구를 등록합니다.
     * @param id 등록할 도구의 고유 ID
     */
    fun registerTool(id: String)

    /**
     * 등록된 도구를 해제합니다.
     * @param name 해제할 도구의 이름(ID)
     */
    fun unregisterTool(name: String)
}

/**
 * `MainThreadLanguageModelToolsShape` 인터페이스의 구현 클래스입니다.
 * 등록된 도구들을 관리하고, 실행 요청 시 해당 도구를 호출하는 로직을 담당합니다.
 */
class MainThreadLanguageModelTools : MainThreadLanguageModelToolsShape {
    
    private val logger = logger<MainThreadLanguageModelTools>()
    // 등록된 도구들을 저장하는 동시성 지원 맵
    private val tools = ConcurrentHashMap<String, ToolInfo>()

    /**
     * 도구의 정보를 담는 내부 데이터 클래스입니다.
     */
    private data class ToolInfo(
        val id: String,
        val registered: Boolean = true // 도구가 현재 등록된 상태인지 여부
    )

    override fun getTools(): List<Map<String, Any?>> {
        logger.info("사용 가능한 언어 모델 도구 목록 조회")
        // 등록된 도구들만 필터링하여 리스트로 반환합니다.
        return tools.values.filter { it.registered }.map {
            mapOf("id" to it.id) 
        }
    }

    override fun invokeTool(dto: Map<String, Any?>, token: Any?): Map<String, Any?> {
        val toolId = dto["id"] as? String ?: throw IllegalArgumentException("도구 ID는 비어 있을 수 없습니다.")
        val params = dto["params"] ?: emptyMap<String, Any?>()
        
        logger.info("언어 모델 도구 실행: $toolId")
        val toolInfo = tools[toolId] ?: throw IllegalArgumentException("ID가 $toolId 인 도구를 찾을 수 없습니다.")
        
        if (!toolInfo.registered) {
            throw IllegalStateException("도구 $toolId 는 등록되지 않았습니다.")
        }
        
        // 실제 도구는 여기서 호출되어야 합니다. 현재는 모의(mock) 결과를 반환합니다.
        // 실제 구현에서는 RPC를 통해 확장 프로세스에 있는 실제 도구를 호출해야 할 수 있습니다.
        return mapOf(
            "result" to "도구 $toolId 가 성공적으로 실행되었습니다.",
            "id" to toolId
        )
    }

    override fun countTokensForInvocation(callId: String, input: String, token: Any?): Int {
        logger.info("도구 실행을 위한 토큰 수 계산: $callId")
        
        // 실제 토큰 수는 여기서 계산되어야 합니다. 현재는 모의 결과를 반환합니다.
        // 실제 구현에서는 특정 알고리즘이나 서비스를 사용하여 토큰 수를 계산해야 할 수 있습니다.
        return input.length / 4 + 1 // 간단한 모의 토큰 계산
    }

    override fun registerTool(id: String) {
        logger.info("언어 모델 도구 등록: $id")
        tools[id] = ToolInfo(id, true)
    }

    override fun unregisterTool(name: String) {
        logger.info("언어 모델 도구 해제: $name")
        
        if (tools.containsKey(name)) {
            // 실제로 제거하는 대신, 'registered' 플래그를 false로 설정합니다.
            tools[name] = tools[name]!!.copy(registered = false)
        } else {
            logger.warn("존재하지 않는 도구를 해제하려고 시도 중: $name")
        }
    }
    
    override fun dispose() {
        logger.info("MainThreadLanguageModelTools 리소스 해제")
        tools.clear()
    }
}
