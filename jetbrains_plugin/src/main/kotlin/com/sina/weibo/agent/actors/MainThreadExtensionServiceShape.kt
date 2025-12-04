// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.core.ExtensionManager
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol
import java.net.URI

/**
 * IntelliJ 메인 스레드에서 확장(Extension) 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 확장의 생명주기 관리, 활성화, 오류 처리, 유틸리티 작업 등을 위한 메소드를 정의합니다.
 */
interface MainThreadExtensionServiceShape : Disposable {
    /**
     * 확장 ID를 사용하여 확장 정보를 가져옵니다.
     * @param extensionId 확장 식별자 (일반적으로 "value" 키를 가진 Map 형태)
     * @return 확장에 대한 메타데이터를 담고 있는 설명 객체. 확장을 찾지 못하면 null을 반환합니다.
     */
    fun getExtension(extensionId: Any): Any?

    /**
     * 지정된 ID와 이유로 확장을 활성화합니다.
     * 이 메소드는 확장 활성화 프로세스를 트리거하고 완료될 때까지 기다립니다.
     * @param extensionId 활성화할 확장의 식별자
     * @param reason 활성화 이유 또는 컨텍스트 정보 (선택 사항)
     * @return 활성화 성공 여부를 나타내는 Boolean (성공 시 true, 실패 시 false)
     */
    fun activateExtension(extensionId: Any, reason: Any?): Any

    /**
     * 확장이 활성화되기 직전에 호출됩니다.
     * 활성화 전 설정이나 로깅을 위한 후크(hook)를 제공합니다.
     * @param extensionId 활성화될 확장의 식별자
     */
    fun onWillActivateExtension(extensionId: Any)

    /**
     * 확장이 성공적으로 활성화된 후에 호출됩니다.
     * 활성화 과정에 대한 상세한 시간 정보를 제공합니다.
     * @param extensionId 활성화된 확장의 식별자
     * @param codeLoadingTime 확장 코드를 로드하는 데 걸린 시간 (밀리초)
     * @param activateCallTime 활성화 호출에 걸린 시간 (밀리초)
     * @param activateResolvedTime 활성화를 해결하는 데 걸린 시간 (밀리초)
     * @param activationReason 활성화 이유 또는 컨텍스트
     */
    fun onDidActivateExtension(
        extensionId: Any,
        codeLoadingTime: Double,
        activateCallTime: Double,
        activateResolvedTime: Double,
        activationReason: Any?,
    )

    /**
     * 확장 활성화 오류를 처리합니다.
     * 오류나 누락된 의존성으로 인해 확장이 활성화되지 못했을 때 호출됩니다.
     * @param extensionId 활성화에 실패한 확장의 식별자
     * @param error 오류 정보 또는 예외 상세 내용
     * @param missingExtensionDependency 해당되는 경우, 누락된 의존성에 대한 정보
     * @return Unit (void) - 메소드가 내부적으로 오류를 처리합니다.
     */
    fun onExtensionActivationError(
        extensionId: Any,
        error: Any?,
        missingExtensionDependency: Any?,
    ): Any

    /**
     * 확장 실행 중 발생하는 런타임 오류를 처리합니다.
     * 성공적으로 활성화된 확장이 런타임에 오류를 만났을 때 호출됩니다.
     * @param extensionId 런타임 오류가 발생한 확장의 식별자
     * @param error 오류 정보 또는 예외 상세 내용
     */
    fun onExtensionRuntimeError(extensionId: Any, error: Any?)

    /**
     * 확장 프로파일링 및 모니터링을 위한 성능 마크를 설정합니다.
     * 확장 생명주기 이벤트 전반에 걸쳐 성능 메트릭을 추적하는 데 사용됩니다.
     * @param marks 시간 정보를 담고 있는 성능 마크 객체 리스트
     * @return Unit (void) - 메소드가 내부적으로 마크를 처리합니다.
     */
    fun setPerformanceMarks(marks: List<Any>)

    /**
     * 표준 URI를 브라우저 호환 URI 형식으로 변환합니다.
     * 이 메소드는 웹 브라우저 컨텍스트에 맞게 URI 형식이 올바르게 지정되도록 보장합니다.
     * @param uri 변환할 원본 URI
     * @return 브라우저 호환 URI 객체
     */
    fun asBrowserUri(uri: URI): URI
}

/**
 * `MainThreadExtensionServiceShape` 인터페이스의 구현 클래스입니다.
 * 확장의 생명주기 이벤트, 활성화, 오류 관리를 처리합니다.
 *
 * @param extensionManager 확장 관련 작업을 책임지는 핵심 관리자
 * @param rpcProtocol 확장과의 프로세스 간 통신(IPC)을 위한 RPC 프로토콜
 */
class MainThreadExtensionService(
    private val extensionManager: ExtensionManager,
    private val rpcProtocol: IRPCProtocol,
) : MainThreadExtensionServiceShape {
    private val logger = Logger.getInstance(MainThreadExtensionService::class.java)

    /**
     * 확장 ID로 확장 정보를 가져옵니다.
     * 다양한 입력 형식에서 확장 ID를 안전하게 추출하고 `extensionManager`에 질의합니다.
     */
    override fun getExtension(extensionId: Any): Any? {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.info("확장 정보 조회: $extensionIdStr")
        return extensionManager.getExtensionDescription(extensionIdStr.toString())
    }

    /**
     * 지정된 ID와 이유로 확장을 활성화합니다.
     * `Future`를 사용하여 비동기 활성화를 수행하고 완료를 기다립니다.
     */
    override fun activateExtension(extensionId: Any, reason: Any?): Any {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.info("확장 활성화 중: $extensionIdStr, 이유: $reason")

        // 비동기 활성화 결과를 얻기 위해 Future를 사용합니다.
        val future = extensionManager.activateExtension(extensionIdStr.toString(), rpcProtocol)

        return try {
            // Future가 완료될 때까지 기다리고 결과를 반환합니다.
            val result = future.get()
            logger.info("확장 $extensionIdStr 활성화 ${if (result) "성공" else "실패"}")
            true
        } catch (e: Exception) {
            logger.error("확장 $extensionIdStr 활성화 중 예외 발생", e)
            false
        }
    }

    /**
     * 확장 활성화가 시작되기 직전에 호출됩니다.
     * 활성화 전 상태 추적을 위해 로깅을 제공합니다.
     */
    override fun onWillActivateExtension(extensionId: Any) {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.info("확장 $extensionIdStr 가 활성화될 예정입니다.")
    }

    /**
     * 확장 활성화가 성공적으로 완료된 후 호출됩니다.
     * 상세한 시간 정보와 함께 활성화 완료를 로깅합니다.
     */
    override fun onDidActivateExtension(
        extensionId: Any,
        codeLoadingTime: Double,
        activateCallTime: Double,
        activateResolvedTime: Double,
        activationReason: Any?,
    ) {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.info("확장 $extensionIdStr 활성화됨, 이유: $activationReason")
    }

    /**
     * 상세한 로깅과 함께 확장 활성화 오류를 처리합니다.
     */
    override fun onExtensionActivationError(
        extensionId: Any,
        error: Any?,
        missingExtensionDependency: Any?,
    ): Any {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.error("확장 $extensionIdStr 활성화 오류: $error, 누락된 의존성: $missingExtensionDependency")
        return Unit
    }

    /**
     * 확장 실행 중 발생하는 런타임 오류를 처리합니다.
     */
    override fun onExtensionRuntimeError(extensionId: Any, error: Any?) {
        val extensionIdStr = try {
            (extensionId as? Map<*, *>)?.get("value") as? String
        } catch (e: Exception) {
            "$extensionId"
        }
        logger.warn("확장 $extensionIdStr 런타임 오류: $error")
    }

    /**
     * 확장 프로파일링 및 모니터링을 위한 성능 마크를 설정합니다.
     */
    override fun setPerformanceMarks(marks: List<Any>) {
        logger.info("성능 마크 설정: $marks")
    }

    /**
     * 표준 URI를 브라우저 호환 형식으로 변환합니다.
     */
    override fun asBrowserUri(uri: URI): URI {
        logger.info("브라우저 URI로 변환: $uri")
        return uri // 현재는 원본 URI를 그대로 반환합니다.
    }

    /**
     * 서비스가 더 이상 필요 없을 때 리소스를 해제합니다.
     */
    override fun dispose() {
        logger.info("Disposing MainThreadExtensionService")
    }
}
