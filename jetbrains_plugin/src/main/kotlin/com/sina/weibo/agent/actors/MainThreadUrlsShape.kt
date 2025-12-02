// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CompletableDeferred

/**
 * IntelliJ 메인 스레드에서 URL 관련 작업을 처리하기 위한 인터페이스입니다.
 * 특정 스키마를 가진 URI(예: `vscode://...`)를 처리하는 핸들러를 등록하고 관리하는 기능을 정의합니다.
 */
interface MainThreadUrlsShape : Disposable {
    /**
     * 특정 URI를 처리할 핸들러를 등록합니다.
     * 예를 들어, 외부 어플리케이션에서 `vscode://wecode-ai.runvsagent/some/path`와 같은 링크를 클릭했을 때,
     * 이 플러그인이 해당 요청을 받아 처리할 수 있도록 합니다.
     *
     * @param handle 핸들러의 고유 식별자
     * @param extensionId 이 핸들러를 등록하는 확장의 ID
     * @param extensionDisplayName 확장의 표시 이름
     * @return 등록 작업의 비동기 결과
     */
    suspend fun registerUriHandler(handle: Int, extensionId: Map<String, String>, extensionDisplayName: String): Any

    /**
     * 등록된 URI 핸들러를 해제합니다.
     * @param handle 해제할 핸들러의 고유 식별자
     * @return 해제 작업의 비동기 결과
     */
    suspend fun unregisterUriHandler(handle: Int): Any

    /**
     * 어플리케이션에서 사용할 수 있는 URI를 생성합니다.
     * @param uri 생성할 URI의 구성 요소
     * @return 생성된 URI의 구성 요소를 담은 Map
     */
    suspend fun createAppUri(uri: Map<String, Any?>): Map<String, Any?>
}

/**
 * `MainThreadUrlsShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며,
 * 향후 IntelliJ의 URL 처리 메커니즘과 연동하여 실제 핸들러를 등록하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadUrls : MainThreadUrlsShape {
    private val logger = Logger.getInstance(MainThreadUrls::class.java)

    override suspend fun registerUriHandler(handle: Int, extensionId: Map<String, String>, extensionDisplayName: String): Any {
        logger.info("URI 핸들러 등록: handle=$handle, extensionId=$extensionId, displayName=$extensionDisplayName")
        // 비동기 작업이 즉시 완료되었음을 나타냅니다.
        return CompletableDeferred<Unit>().also { it.complete(Unit) }.await()
    }

    override suspend fun unregisterUriHandler(handle: Int): Any {
        logger.info("URI 핸들러 등록 해제: handle=$handle")
        return CompletableDeferred<Unit>().also { it.complete(Unit) }.await()
    }

    override suspend fun createAppUri(uri: Map<String, Any?>): Map<String, Any?> {
        logger.info("어플리케이션 URI 생성: uri=$uri")
        // 현재는 전달받은 URI를 그대로 반환합니다.
        return uri
    }

    override fun dispose() {
        logger.info("Disposing MainThreadUrls resources")
    }
}
