// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.sina.weibo.agent.plugin.SystemObjectProvider
import com.sina.weibo.agent.plugin.WecoderPluginService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * IntelliJ 메인 스레드에서 창(Window) 관련 서비스를 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadWindowShape`에 해당합니다.
 */
interface MainThreadWindowShape : Disposable {
    /**
     * 창의 초기 상태(포커스 여부, 활성화 여부)를 가져옵니다.
     * @return 창의 상태 정보를 담은 Map
     */
    fun getInitialState(): Map<String, Boolean>
    
    /**
     * 주어진 URI를 시스템의 기본 브라우저나 어플리케이션으로 엽니다.
     * @param uri URI 구성 요소를 담은 Map
     * @param uriString 열고자 하는 URI의 전체 문자열
     * @param options 열기 옵션
     * @return 열기 성공 여부
     */
    fun openUri(uri: Map<String, Any?>, uriString: String?, options: Map<String, Any?>): Boolean
    
    /**
     * 내부 URI를 외부에서 접근 가능한 URI로 변환합니다.
     * @param uri 변환할 URI의 구성 요소
     * @param options 변환 옵션
     * @return 외부 URI의 구성 요소를 담은 Map
     */
    fun asExternalUri(uri: Map<String, Any?>, options: Map<String, Any?>): Map<String, Any?>
}

/**
 * `MainThreadWindowShape` 인터페이스의 구현 클래스입니다.
 * IntelliJ 플랫폼의 창 관련 API를 사용하여 실제 기능을 수행합니다.
 */
class MainThreadWindow(val project: Project) : MainThreadWindowShape {
    private val logger = Logger.getInstance(MainThreadWindow::class.java)

    /**
     * `WindowManager`를 통해 현재 프로젝트 창의 포커스 및 활성화 상태를 확인하여 반환합니다.
     */
    override fun getInitialState(): Map<String, Boolean> {
        try {
            logger.info("창 초기 상태 조회")

            // 현재 프로젝트의 프레임(창)을 가져옵니다.
            val frame = WindowManager.getInstance().getFrame(project)
            val isFocused = frame?.isFocused ?: false
            val isActive = frame?.isActive ?: false
            
            return mapOf(
                "isFocused" to isFocused,
                "isActive" to isActive
            )
        } catch (e: Exception) {
            logger.error("창 초기 상태 조회 실패", e)
            return mapOf("isFocused" to false, "isActive" to false)
        }
    }

    /**
     * `java.awt.Desktop`을 사용하여 주어진 URI를 엽니다.
     */
    override fun openUri(uri: Map<String, Any?>, uriString: String?, options: Map<String, Any?>): Boolean {
        try {
            logger.info("URI 여는 중: $uriString")
            
            // 전달받은 uriString 또는 uri 구성요소 Map으로부터 java.net.URI 객체를 생성합니다.
            val actualUri = if (uriString != null) {
                try {
                    URI(uriString)
                } catch (e: Exception) {
                    createUriFromComponents(uri)
                }
            } else {
                createUriFromComponents(uri)
            }

            return if (actualUri != null) {
                // 시스템이 브라우저 열기를 지원하는지 확인합니다.
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(actualUri)
                    true
                } else {
                    logger.warn("시스템이 URI 열기를 지원하지 않습니다.")
                    false
                }
            } else {
                logger.warn("유효한 URI를 생성할 수 없습니다.")
                false
            }
        } catch (e: Exception) {
            logger.error("URI 열기 실패", e)
            return false
        }
    }

    /**
     * URI를 외부 형식으로 변환합니다. (현재는 입력값을 그대로 반환)
     */
    override fun asExternalUri(uri: Map<String, Any?>, options: Map<String, Any?>): Map<String, Any?> {
        return try {
            logger.info("외부 URI로 변환 중: $uri")
            // 대부분의 경우, 동일한 URI 구성 요소를 그대로 반환합니다.
            // 실제 구현에서는 특정 프로토콜 변환이 필요할 수 있습니다.
            uri
        } catch (e: Exception) {
            logger.error("외부 URI로 변환 실패", e)
            uri // 오류 발생 시 원본 URI 반환
        }
    }

    /**
     * Map 형태의 구성 요소로부터 `java.net.URI` 객체를 생성하는 헬퍼 함수입니다.
     */
    private fun createUriFromComponents(components: Map<String, Any?>): URI? {
        return try {
            val scheme = components["scheme"] as? String ?: return null
            val authority = components["authority"] as? String ?: ""
            val path = components["path"] as? String ?: ""
            val query = components["query"] as? String ?: ""
            val fragment = components["fragment"] as? String ?: ""
            
            URI(scheme, authority, path, query, fragment)
        } catch (e: Exception) {
            logger.warn("URI 구성 요소로부터 URI 생성 실패: $components", e)
            null
        }
    }

    override fun dispose() {
        logger.info("Disposing MainThreadWindow")
    }
}
