// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.util.URI

/**
 * IntelliJ 메인 스레드에서 출력(Output) 채널 관련 서비스를 처리하기 위한 인터페이스입니다.
 * VSCode의 'Output' 뷰와 유사한 기능을 제공하며, 특정 채널을 생성하고 내용을 업데이트하는 역할을 합니다.
 * VSCode Extension Host의 `MainThreadOutputServiceShape`에 해당합니다.
 */
interface MainThreadOutputServiceShape : Disposable {
    /**
     * 새로운 출력 채널을 등록합니다.
     * @param label 채널의 이름 (예: "Git", "Tasks")
     * @param file 채널 내용이 저장될 파일의 URI 정보
     * @param languageId 채널에 표시될 내용의 언어 ID (예: "log", "json")
     * @param extensionId 이 채널을 등록하는 확장의 ID
     * @return 생성된 채널의 고유 ID
     */
    suspend fun register(label: String, file: Map<String, Any>, languageId: String?, extensionId: String): String
    
    /**
     * 출력 채널의 내용을 업데이트합니다.
     * @param channelId 업데이트할 채널의 ID
     * @param mode 업데이트 모드 (예: 덮어쓰기, 추가하기 등)
     * @param till 특정 위치까지 업데이트할 때 사용 (선택 사항)
     */
    suspend fun update(channelId: String, mode: Int, till: Int? = null)
    
    /**
     * 지정된 출력 채널을 사용자에게 보여줍니다. (예: 해당 탭을 활성화)
     * @param channelId 보여줄 채널의 ID
     * @param preserveFocus 채널을 보여준 후에도 현재 포커스를 유지할지 여부
     */
    suspend fun reveal(channelId: String, preserveFocus: Boolean)
    
    /**
     * 지정된 출력 채널을 닫습니다.
     * @param channelId 닫을 채널의 ID
     */
    suspend fun close(channelId: String)
    
    /**
     * 지정된 출력 채널과 관련된 리소스를 해제합니다.
     * @param channelId 해제할 채널의 ID
     */
    suspend fun dispose(channelId: String)
}

/**
 * `MainThreadOutputServiceShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며,
 * 향후 IntelliJ의 `ToolWindowManager`와 연동하여 실제 출력 뷰를 생성하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadOutputService : MainThreadOutputServiceShape {
    private val logger = Logger.getInstance(MainThreadOutputService::class.java)

    override suspend fun register(label: String, file: Map<String, Any>, languageId: String?, extensionId: String): String {
        logger.info("출력 채널 등록: label=$label, file=$file, extensionId=$extensionId")
        // 현재는 채널의 레이블을 ID로 사용합니다.
        return label
    }
    
    override suspend fun update(channelId: String, mode: Int, till: Int?) {
        logger.info("출력 채널 업데이트: channelId=$channelId, mode=$mode, till=$till")
    }
    
    override suspend fun reveal(channelId: String, preserveFocus: Boolean) {
        logger.info("출력 채널 표시: channelId=$channelId, preserveFocus=$preserveFocus")
    }
    
    override suspend fun close(channelId: String) {
        logger.info("출력 채널 닫기: channelId=$channelId")
    }
    
    override suspend fun dispose(channelId: String) {
        logger.info("출력 채널 리소스 해제: channelId=$channelId")
    }

    /**
     * 모든 출력 채널 리소스를 해제합니다.
     */
    override fun dispose() {
        logger.info("모든 출력 채널 리소스 해제")
    }
}
