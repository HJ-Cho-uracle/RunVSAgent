// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ 메인 스레드에서 파일 시스템 이벤트 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 특정 파일이나 디렉터리의 변경사항(생성, 수정, 삭제)을 감시하는 기능을 제공합니다.
 * VSCode Extension Host의 `MainThreadFileSystemEventServiceShape`에 해당합니다.
 */
interface MainThreadFileSystemEventServiceShape : Disposable {
    /**
     * 특정 리소스에 대한 파일 시스템 변경 감시(watch)를 시작합니다.
     *
     * @param extensionId 감시를 요청하는 확장의 식별자
     * @param session 감시 작업을 식별하는 고유 세션 ID
     * @param resource 감시할 리소스(파일 또는 디렉터리)의 URI 정보
     * @param opts 감시 옵션 (예: 재귀적 감시 여부, 특정 이벤트 무시 등)
     * @param correlate 이벤트를 상호 연관시킬지 여부
     */
    fun watch(
        extensionId: String,
        session: Int,
        resource: Map<String, Any?>,
        opts: Map<String, Any?>,
        correlate: Boolean
    )

    /**
     * 지정된 세션 ID에 해당하는 파일 시스템 변경 감시를 중지합니다.
     *
     * @param session 중지할 감시 작업의 세션 ID
     */
    fun unwatch(session: Int)
}

/**
 * `MainThreadFileSystemEventServiceShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며,
 * 향후 IntelliJ의 가상 파일 시스템(VFS) 리스너와 연동하여 실제 파일 감시 기능을 구현할 수 있습니다.
 */
class MainThreadFileSystemEventService : MainThreadFileSystemEventServiceShape {
    private val logger = Logger.getInstance(MainThreadFileSystemEventService::class.java)

    /**
     * 파일 시스템 변경 감시 시작 요청을 로깅합니다.
     */
    override fun watch(
        extensionId: String,
        session: Int,
        resource: Map<String, Any?>,
        opts: Map<String, Any?>,
        correlate: Boolean
    ) {
        logger.info("파일 시스템 감시 시작: extensionId=$extensionId, session=$session, resource=$resource, opts=$opts, correlate=$correlate")
    }

    /**
     * 파일 시스템 변경 감시 중지 요청을 로깅합니다.
     */
    override fun unwatch(session: Int) {
        logger.info("파일 시스템 감시 중지: session=$session")
    }

    /**
     * 리소스를 해제합니다.
     */
    override fun dispose() {
        logger.info("Releasing MainThreadFileSystemEventService resources")
    }
}
