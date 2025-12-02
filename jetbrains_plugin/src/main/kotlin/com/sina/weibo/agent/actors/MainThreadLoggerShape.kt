// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.util.URI

/**
 * IntelliJ 메인 스레드에서 로거(Logger) 관련 작업을 처리하기 위한 인터페이스입니다.
 * Extension Host의 로깅 시스템을 생성, 등록, 관리하는 기능을 정의합니다.
 */
interface MainThreadLoggerShape : Disposable {
    /**
     * 지정된 로그 파일에 메시지를 기록합니다.
     * @param file 로그 파일의 URI
     * @param messages 기록할 로그 메시지 목록
     */
    fun log(file: URI, messages: List<String>)
    
    /**
     * 지정된 로그 파일의 버퍼를 비워, 모든 내용이 파일에 쓰여지도록 합니다.
     * @param file 플러시할 로그 파일의 URI
     */
    fun flush(file: URI)
    
    /**
     * 새로운 로거를 생성합니다.
     * @param file 이 로거가 사용할 로그 파일의 URI
     * @param options 로거 생성에 필요한 추가 옵션 (예: 로그 레벨, 이름 등)
     * @return 생성 결과 (일반적으로 Unit 또는 성공 여부)
     */
    fun createLogger(file: URI, options: Map<String, Any?>): Any
    
    /**
     * 로거를 시스템에 등록합니다.
     * @param logger 등록할 로거의 정보를 담은 Map
     * @return 등록 결과
     */
    fun registerLogger(logger: Map<String, Any?>): Any
    
    /**
     * 등록된 로거를 해제합니다.
     * @param resource 해제할 로거와 연결된 리소스 URI
     * @return 해제 결과
     */
    fun deregisterLogger(resource: String): Any
    
    /**
     * 특정 로거의 출력을 보이거나 숨깁니다.
     * @param resource 가시성을 설정할 로거와 연결된 리소스 URI
     * @param visible 보이게 할지 여부
     * @return 설정 결과
     */
    fun setVisibility(resource: String, visible: Boolean): Any

}

/**
 * `MainThreadLoggerShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며,
 * 향후 IntelliJ의 로그 관리 시스템과 연동하여 실제 로거를 생성하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadLogger : MainThreadLoggerShape {
    private val logger = Logger.getInstance(MainThreadLogger::class.java)

    override fun log(file: URI, messages: List<String>) {
        logger.info("파일에 로깅: $file")
    }

    override fun flush(file: URI) {
        logger.info("로그 파일 플러시: $file")
    }

    override fun createLogger(file: URI, options: Map<String, Any?>): Any {
        logger.info("파일에 대한 로거 생성: $file, 옵션: $options")
        return Unit // 실제 로거 객체 대신 플레이스홀더 반환
    }

    override fun registerLogger(log: Map<String, Any?>): Any {
        logger.info("로거 등록: $log")
        return Unit // 실제 등록 결과 대신 플레이스홀더 반환
    }

    override fun deregisterLogger(resource: String): Any {
        logger.info("리소스에 대한 로거 해제: $resource")
        return Unit // 실제 해제 결과 대신 플레이스홀더 반환
    }

    override fun setVisibility(resource: String, visible: Boolean): Any {
        logger.info("리소스 가시성 설정: $resource -> $visible")
        return Unit // 실제 가시성 결과 대신 플레이스홀더 반환
    }

    override fun dispose() {
        logger.info("Disposing MainThreadLogger")
    }

}
