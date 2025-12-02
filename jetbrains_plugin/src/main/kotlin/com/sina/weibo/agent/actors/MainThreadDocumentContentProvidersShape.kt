// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import kotlinx.coroutines.CompletableDeferred

/**
 * IntelliJ 메인 스레드에서 문서 내용 제공자(Document Content Provider) 관련 작업을 처리하기 위한 인터페이스입니다.
 * 특정 스키마(예: 'git', 'http')를 가진 URI에 대한 파일 내용을 동적으로 생성하여 제공하는 기능을 관리합니다.
 * 예를 들어, 특정 Git 커밋의 파일 내용이나 웹 URL의 내용을 에디터에 표시할 때 사용될 수 있습니다.
 */
interface MainThreadDocumentContentProvidersShape : Disposable {
    /**
     * 텍스트 내용 제공자를 등록합니다.
     * Extension Host는 이 메소드를 호출하여 특정 URI 스키마를 처리할 수 있음을 IntelliJ 플러그인에 알립니다.
     * @param handle 제공자를 식별하는 고유 핸들
     * @param scheme 이 제공자가 처리할 URI 스키마 (예: "git", "readonly-fs")
     */
    fun registerTextContentProvider(handle: Int, scheme: String)

    /**
     * 등록된 텍스트 내용 제공자를 해제합니다.
     * @param handle 해제할 제공자의 고유 핸들
     */
    fun unregisterTextContentProvider(handle: Int)

    /**
     * 가상 문서의 내용이 변경되었을 때 Extension Host로부터 호출됩니다.
     * 예를 들어, 사용자가 가상 문서를 편집했을 때 그 변경사항을 Extension Host에 알리는 데 사용될 수 있습니다.
     * @param uri 내용이 변경된 가상 문서의 URI
     * @param value 새로운 문서 내용
     * @return 작업 완료를 나타내는 비동기 결과
     */
    suspend fun onVirtualDocumentChange(uri: Map<String, Any?>, value: String): Any
}

/**
 * `MainThreadDocumentContentProvidersShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며, 실제 기능은 구현되어 있지 않습니다.
 * 향후 IntelliJ의 가상 파일 시스템과 연동하여 실제 문서 내용을 제공하는 로직이 추가될 수 있습니다.
 */
class MainThreadDocumentContentProviders : MainThreadDocumentContentProvidersShape {
    private val logger = Logger.getInstance(MainThreadDocumentContentProviders::class.java)

    override fun registerTextContentProvider(handle: Int, scheme: String) {
        logger.info("텍스트 내용 제공자 등록: handle=$handle, scheme=$scheme")
    }

    override fun unregisterTextContentProvider(handle: Int) {
        logger.info("텍스트 내용 제공자 해제: handle=$handle")
    }

    override suspend fun onVirtualDocumentChange(uri: Map<String, Any?>, value: String): Any {
        logger.info("가상 문서 내용 변경됨: uri=$uri")
        // 비동기 작업이 즉시 완료되었음을 나타내는 CompletableDeferred를 반환합니다.
        return CompletableDeferred<Unit>().also { it.complete(Unit) }.await()
    }

    override fun dispose() {
        logger.info("Disposing MainThreadDocumentContentProviders resources")
    }
}
