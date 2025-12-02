// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.ipc.proxy.LazyPromise

/**
 * 터미널 명령 정보를 담는 데이터 클래스입니다.
 */
data class TerminalCommandDto(
    val commandLine: String?, // 실행된 명령어 라인
    val cwd: String?,         // 명령어가 실행된 현재 작업 디렉터리
    val exitCode: Int?,       // 명령어의 종료 코드
    val output: String?       // 명령어의 출력
)

/**
 * 터미널 탭 액션 정보를 담는 데이터 클래스입니다.
 */
data class TerminalTabAction(
    val id: String,     // 액션의 고유 ID
    val label: String,  // 액션의 표시 레이블
    val icon: Any?      // 액션의 아이콘
)

/**
 * 재연결 속성을 담는 데이터 클래스입니다.
 */
data class ReconnectionProperties(
    val ownerId: String, // 소유자 ID
    val data: Any?       // 재연결 데이터
)

/**
 * 셸 실행 설정 정보를 담는 데이터 클래스입니다.
 */
data class ShellLaunchConfigDto(
    val name: String?,                  // 터미널 이름
    val executable: String?,            // 실행 파일 경로
    val args: List<String>?,            // 실행 인자
    val cwd: String?,                   // 현재 작업 디렉터리
    val env: Map<String, String>?,      // 환경 변수
    val useShellEnvironment: Boolean?,  // 셸 환경 사용 여부
    val hideFromUser: Boolean?,         // 사용자에게 숨길지 여부
    val reconnectionProperties: Map<String, ReconnectionProperties>?, // 재연결 속성
    val type: String?,                  // 터미널 타입
    val isFeatureTerminal: Boolean?,    // 기능 터미널 여부
    val tabActions: List<TerminalTabAction>?, // 탭 액션 목록
    val shellIntegrationEnvironmentReporting: Boolean? // 셸 통합 환경 보고 여부
)

/**
 * 터미널 크기 정보를 담는 데이터 클래스입니다.
 */
data class TerminalDimensionsDto(
    val columns: Int, // 컬럼 수
    val rows: Int     // 행 수
)

/**
 * 터미널 실행 오류 정보를 담는 데이터 클래스입니다.
 */
data class TerminalLaunchError(
    val message: String, // 오류 메시지
    val code: Int?       // 오류 코드
)

/**
 * 터미널 프로필 정보를 담는 데이터 클래스입니다.
 */
data class TerminalProfile(
    val profileName: String, // 프로필 이름
    val path: String,        // 실행 파일 경로
    val isDefault: Boolean,  // 기본 프로필 여부
    val isUnsafePath: Boolean?, // 안전하지 않은 경로 여부
    val requiresUnsafePath: String?, // 안전하지 않은 경로 요구 여부
    val isAutoDetected: Boolean?, // 자동 감지 여부
    val isFromPath: Boolean?, // PATH 환경 변수에서 찾았는지 여부
    val args: List<String>?, // 인자
    val env: Map<String, String>?, // 환경 변수
    val overrideName: Boolean?, // 이름 재정의 여부
    val color: String?,      // 색상
    val icon: Any?           // 아이콘
)

/**
 * Extension Host 터미널 서비스 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 터미널 서비스와 상호작용하기 위해 사용됩니다.
 */
interface ExtHostTerminalServiceProxy {
    /** 터미널이 닫혔음을 알립니다. */
    fun acceptTerminalClosed(id: Int, exitCode: Int?, exitReason: Int)
    /** 터미널이 열렸음을 알립니다. */
    fun acceptTerminalOpened(id: Int, extHostTerminalId: String?, name: String, shellLaunchConfig: ShellLaunchConfigDto)
    /** 활성 터미널이 변경되었음을 알립니다. */
    fun acceptActiveTerminalChanged(id: Int?)
    /** 터미널 프로세스 ID를 알립니다. */
    fun acceptTerminalProcessId(id: Int, processId: Int)
    /** 터미널 프로세스 데이터를 알립니다. */
    fun acceptTerminalProcessData(id: Int, data: String)
    /** 명령어가 실행되었음을 알립니다. */
    fun acceptDidExecuteCommand(id: Int, command: TerminalCommandDto)
    /** 터미널 제목이 변경되었음을 알립니다. */
    fun acceptTerminalTitleChange(id: Int, name: String)
    /** 터미널 크기가 변경되었음을 알립니다. */
    fun acceptTerminalDimensions(id: Int, cols: Int, rows: Int)
    /** 터미널 최대 크기가 변경되었음을 알립니다. */
    fun acceptTerminalMaximumDimensions(id: Int, cols: Int, rows: Int)
    /** 터미널 상호작용이 발생했음을 알립니다. */
    fun acceptTerminalInteraction(id: Int)
    /** 터미널 선택 영역이 변경되었음을 알립니다. */
    fun acceptTerminalSelection(id: Int, selection: String?)
    /** 터미널 셸 타입이 변경되었음을 알립니다. */
    fun acceptTerminalShellType(id: Int, shellType: String?)
    /** 확장 터미널을 시작합니다. */
    fun startExtensionTerminal(id: Int, initialDimensions: TerminalDimensionsDto?): LazyPromise
    /** 프로세스 ACK 데이터 이벤트를 수락합니다. */
    fun acceptProcessAckDataEvent(id: Int, charCount: Int)
    /** 프로세스 입력을 수락합니다. */
    fun acceptProcessInput(id: Int, data: String)
    /** 프로세스 크기 조정을 수락합니다. */
    fun acceptProcessResize(id: Int, cols: Int, rows: Int)
    /** 프로세스 종료를 수락합니다. */
    fun acceptProcessShutdown(id: Int, immediate: Boolean)
    /** 프로세스 초기 CWD(Current Working Directory) 요청을 수락합니다. */
    fun acceptProcessRequestInitialCwd(id: Int)
    /** 프로세스 CWD 요청을 수락합니다. */
    fun acceptProcessRequestCwd(id: Int)
    /** 프로세스 지연 시간(latency) 요청을 수락합니다. */
    fun acceptProcessRequestLatency(id: Int): LazyPromise
    /** 링크를 제공합니다. */
    fun provideLinks(id: Int, line: String): LazyPromise
    /** 링크를 활성화합니다. */
    fun activateLink(id: Int, linkId: Int)
    /** 환경 변수 컬렉션을 초기화합니다. */
    fun initEnvironmentVariableCollections(collections: List<Pair<String, Any>>)
    /** 기본 프로필을 수락합니다. */
    fun acceptDefaultProfile(profile: TerminalProfile, automationProfile: TerminalProfile)
    /** 기여된 프로필 터미널을 생성합니다. */
    fun createContributedProfileTerminal(id: String, options: Any): LazyPromise
    /** 터미널 빠른 수정(Quick Fix)을 제공합니다. */
    fun provideTerminalQuickFixes(id: String, matchResult: Any, token: Any): LazyPromise
    /** 터미널 자동 완성을 제공합니다. */
    fun provideTerminalCompletions(id: String, options: Any, token: Any): LazyPromise
}
