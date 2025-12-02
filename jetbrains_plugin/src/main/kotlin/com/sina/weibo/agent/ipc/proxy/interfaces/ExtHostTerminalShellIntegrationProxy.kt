// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.util.URI

/**
 * Extension Host 터미널 셸 통합 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 터미널 셸 통합 서비스와 상호작용하기 위해 사용됩니다.
 * 셸의 상태 변경, 명령어 실행, 환경 변수 변경 등을 Extension Host에 알립니다.
 */
interface ExtHostTerminalShellIntegrationProxy {
    /**
     * 셸 통합 변경을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     */
    fun shellIntegrationChange(instanceId: Int)

    /**
     * 셸 명령어 실행 시작을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     * @param commandLineValue 실행된 명령어 라인
     * @param commandLineConfidence 명령어 라인의 신뢰도
     * @param isTrusted 신뢰할 수 있는 명령어인지 여부
     * @param cwd 명령어가 실행된 현재 작업 디렉터리 URI
     */
    fun shellExecutionStart(instanceId: Int, commandLineValue: String, commandLineConfidence: Int, isTrusted: Boolean, cwd: URI?)

    /**
     * 셸 명령어 실행 종료를 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     * @param commandLineValue 실행된 명령어 라인
     * @param commandLineConfidence 명령어 라인의 신뢰도
     * @param isTrusted 신뢰할 수 있는 명령어인지 여부
     * @param exitCode 명령어의 종료 코드
     */
    fun shellExecutionEnd(instanceId: Int, commandLineValue: String, commandLineConfidence: Int, isTrusted: Boolean, exitCode: Int?)

    /**
     * 셸 실행 중 데이터 변경을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     * @param data 변경된 데이터
     */
    fun shellExecutionData(instanceId: Int, data: String)

    /**
     * 셸 환경 변수 변경을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     * @param shellEnvKeys 변경된 환경 변수 키 목록
     * @param shellEnvValues 변경된 환경 변수 값 목록
     * @param isTrusted 신뢰할 수 있는 변경인지 여부
     */
    fun shellEnvChange(instanceId: Int, shellEnvKeys: Array<String>, shellEnvValues: Array<String>, isTrusted: Boolean)

    /**
     * 현재 작업 디렉터리(CWD) 변경을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     * @param cwd 변경된 현재 작업 디렉터리 URI
     */
    fun cwdChange(instanceId: Int, cwd: URI?)

    /**
     * 터미널 닫힘을 Extension Host에 알립니다.
     * @param instanceId 터미널 인스턴스 ID
     */
    fun closeTerminal(instanceId: Int)
}
