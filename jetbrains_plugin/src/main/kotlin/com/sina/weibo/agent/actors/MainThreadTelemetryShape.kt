// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ 메인 스레드에서 원격 측정(Telemetry) 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 플러그인 사용 현황, 성능 데이터, 오류 등을 수집하기 위한 이벤트를 수신하는 기능을 정의합니다.
 */
interface MainThreadTelemetryShape : Disposable {
    /**
     * 공개적인 원격 측정 이벤트를 기록합니다.
     * @param eventName 이벤트의 이름
     * @param data 이벤트와 관련된 데이터
     */
    fun publicLog(eventName: String, data: Any?)
    
    /**
     * 공개적인 원격 측정 이벤트를 기록합니다. (분류된 이벤트를 지원할 수 있는 추가 메소드)
     * @param eventName 이벤트의 이름
     * @param data 이벤트와 관련된 데이터
     */
    fun publicLog2(eventName: String, data: Any?)
}

/**
 * `MainThreadTelemetryShape` 인터페이스의 구현 클래스입니다.
 * 현재는 전달받은 원격 측정 이벤트를 IntelliJ 로그 시스템에 정보(info) 레벨로 기록하는 역할만 수행합니다.
 */
class MainThreadTelemetry : MainThreadTelemetryShape {
    private val logger = Logger.getInstance(MainThreadTelemetry::class.java)

    /**
     * 전달받은 이벤트 이름과 데이터를 로그에 기록합니다.
     */
    override fun publicLog(eventName: String, data: Any?) {
        logger.info("[Telemetry] $eventName: $data")
    }
    
    /**
     * 전달받은 이벤트 이름과 데이터를 로그에 기록합니다.
     */
    override fun publicLog2(eventName: String, data: Any?) {
        logger.info("[Telemetry] $eventName: $data")
    }

    override fun dispose() {
        logger.info("Dispose MainThreadTelemetry")
    }
}
