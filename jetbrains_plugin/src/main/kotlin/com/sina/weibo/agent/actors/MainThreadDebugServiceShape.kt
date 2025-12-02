// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.net.URI

/**
 * IntelliJ 메인 스레드에서 디버그 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 디버그 세션, 브레이크포인트, 디버그 어댑터(DA)와의 통신 등을 관리하는 메소드를 정의합니다.
 * VSCode Extension Host의 `MainThreadDebugServiceShape`에 해당합니다.
 */
interface MainThreadDebugServiceShape : Disposable {
    /**
     * 이 서비스가 처리할 수 있는 디버그 유형을 등록합니다.
     * @param debugTypes 디버그 유형 식별자 리스트 (예: "java", "python", "node")
     */
    fun registerDebugTypes(debugTypes: List<String>)
    
    /**
     * 디버그 세션이 나중에 사용하기 위해 캐시/저장되었음을 알립니다.
     * @param sessionID 디버그 세션의 고유 식별자
     */
    fun sessionCached(sessionID: String)
    
    /**
     * 디버그 어댑터(Debug Adapter)로부터 받은 메시지를 수락하고 처리합니다.
     * @param handle 디버그 어댑터 연결을 식별하는 고유 핸들
     * @param message 디버그 어댑터로부터 받은 프로토콜 메시지
     */
    fun acceptDAMessage(handle: Int, message: Any)
    
    /**
     * 디버그 어댑터가 보고한 오류를 수락하고 처리합니다.
     * @param handle 디버그 어댑터 연결을 식별하는 고유 핸들
     * @param name 오류의 이름/유형
     * @param message 사람이 읽을 수 있는 오류 메시지
     * @param stack 오류의 스택 트레이스 (선택 사항)
     */
    fun acceptDAError(handle: Int, name: String, message: String, stack: String?)
    
    /**
     * 디버그 어댑터가 종료되었음을 알리는 통지를 수락합니다.
     * @param handle 디버그 어댑터 연결을 식별하는 고유 핸들
     * @param code 종료 코드 (시그널에 의해 종료된 경우 null)
     * @param signal 종료를 유발한 시그널 이름 (정상 종료된 경우 null)
     */
    fun acceptDAExit(handle: Int, code: Int?, signal: String?)
    
    /**
     * 특정 디버그 유형에 대한 디버그 설정 제공자(Configuration Provider)를 등록합니다.
     * @param type 이 제공자가 처리할 디버그 유형
     * @param triggerKind 이 제공자가 언제 트리거되어야 하는지 (1=초기, 2=동적)
     * @param hasProvideMethod `provideDebugConfigurations` 메소드를 가지고 있는지 여부
     * @param hasResolveMethod `resolveDebugConfiguration` 메소드를 가지고 있는지 여부
     * @param hasResolve2Method `resolveDebugConfigurationWithSubstitutedVariables` 메소드를 가지고 있는지 여부
     * @param handle 이 제공자 등록을 위한 고유 핸들
     * @return 등록 결과 (일반적으로 Unit 또는 성공 여부)
     */
    fun registerDebugConfigurationProvider(
        type: String,
        triggerKind: Int,
        hasProvideMethod: Boolean,
        hasResolveMethod: Boolean,
        hasResolve2Method: Boolean,
        handle: Int
    ): Any
    
    /**
     * 특정 디버그 유형에 대한 디버그 어댑터 디스크립터 팩토리(Descriptor Factory)를 등록합니다.
     * @param type 이 팩토리가 어댑터를 생성할 디버그 유형
     * @param handle 이 팩토리 등록을 위한 고유 핸들
     * @return 등록 결과
     */
    fun registerDebugAdapterDescriptorFactory(type: String, handle: Int): Any
    
    /**
     * 디버그 설정 제공자를 등록 해제합니다.
     * @param handle 등록 해제할 제공자의 핸들
     */
    fun unregisterDebugConfigurationProvider(handle: Int)
    
    /**
     * 디버그 어댑터 디스크립터 팩토리를 등록 해제합니다.
     * @param handle 등록 해제할 팩토리의 핸들
     */
    fun unregisterDebugAdapterDescriptorFactory(handle: Int)
    
    /**
     * 새로운 디버깅 세션을 시작합니다.
     * @param folder 디버그 세션을 위한 워크스페이스 폴더 URI (선택 사항)
     * @param nameOrConfig 미리 정의된 설정의 이름 또는 설정 객체 자체
     * @param options 디버그 세션을 위한 실행 옵션
     * @return 디버깅이 성공적으로 시작되었으면 true
     */
    fun startDebugging(folder: URI?, nameOrConfig: Any, options: Any): Any
    
    /**
     * 활성화된 디버깅 세션을 중지합니다.
     * @param sessionId 중지할 세션 ID (null이면 모든 세션 중지)
     * @return 작업 결과
     */
    fun stopDebugging(sessionId: String?): Any
    
    /**
     * 디버그 세션에 사용자 정의 이름을 설정합니다.
     * @param id 이름을 지정할 세션 ID
     * @param name 세션에 표시될 이름
     */
    fun setDebugSessionName(id: String, name: String)
    
    /**
     * 디버그 어댑터에 사용자 정의 요청을 보냅니다.
     * @param id 요청을 보낼 세션 ID
     * @param command 디버그 어댑터 프로토콜의 커맨드
     * @param args 커맨드에 대한 인자
     * @return 디버그 어댑터로부터의 응답
     */
    fun customDebugAdapterRequest(id: String, command: String, args: Any): Any
    
    /**
     * 디버그 프로토콜로부터 특정 브레이크포인트에 대한 정보를 가져옵니다.
     * @param id 세션 ID
     * @param breakpoinId 조회할 브레이크포인트 ID
     * @return 브레이크포인트 정보 또는 찾지 못하면 null
     */
    fun getDebugProtocolBreakpoint(id: String, breakpoinId: String): Any?
    
    /**
     * 디버그 콘솔 출력에 텍스트를 추가합니다.
     * @param value 콘솔에 추가할 텍스트
     */
    fun appendDebugConsole(value: String)
    
    /**
     * 디버그 서비스에 새로운 브레이크포인트를 등록합니다.
     * @param breakpoints 등록할 브레이크포인트 객체 리스트
     * @return 등록 결과
     */
    fun registerBreakpoints(breakpoints: List<Any>): Any
    
    /**
     * 기존 브레이크포인트를 등록 해제합니다.
     * @param breakpointIds 제거할 일반 브레이크포인트 ID 리스트
     * @param functionBreakpointIds 제거할 함수 브레이크포인트 ID 리스트
     * @param dataBreakpointIds 제거할 데이터 브레이크포인트 ID 리스트
     * @return 등록 해제 결과
     */
    fun unregisterBreakpoints(
        breakpointIds: List<String>,
        functionBreakpointIds: List<String>,
        dataBreakpointIds: List<String>
    ): Any
    
    /**
     * 디버그 시각화 도우미(Visualizer) 확장을 등록합니다.
     * @param extensionId 시각화 도우미를 제공하는 확장의 ID
     * @param id 확장 내에서 시각화 도우미의 고유 ID
     */
    fun registerDebugVisualizer(extensionId: String, id: String)
    
    /**
     * 디버그 시각화 도우미 확장을 등록 해제합니다.
     * @param extensionId 시각화 도우미를 제공하는 확장의 ID
     * @param id 확장 내에서 시각화 도우미의 고유 ID
     */
    fun unregisterDebugVisualizer(extensionId: String, id: String)
    
    /**
     * 디버그 시각화 도우미의 트리 구조를 등록합니다.
     * @param treeId 트리의 고유 식별자
     * @param canEdit 사용자가 트리 구조를 편집할 수 있는지 여부
     */
    fun registerDebugVisualizerTree(treeId: String, canEdit: Boolean)
    
    /**
     * 디버그 시각화 도우미의 트리 구조를 등록 해제합니다.
     * @param treeId 등록 해제할 트리의 고유 식별자
     */
    fun unregisterDebugVisualizerTree(treeId: String)
}

/**
 * `MainThreadDebugServiceShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며, 실제 기능은 구현되어 있지 않습니다.
 * 향후 IntelliJ의 디버깅 프레임워크와 연동하는 코드가 추가될 수 있습니다.
 */
class MainThreadDebugService : MainThreadDebugServiceShape {
    private val logger = Logger.getInstance(MainThreadDebugService::class.java)

    override fun registerDebugTypes(debugTypes: List<String>) {
        logger.info("디버그 유형 등록: $debugTypes")
    }
    
    override fun sessionCached(sessionID: String) {
        logger.info("세션 캐시됨: $sessionID")
    }
    
    override fun acceptDAMessage(handle: Int, message: Any) {
        logger.info("디버그 어댑터 메시지 수신: handle=$handle, message=$message")
    }
    
    override fun acceptDAError(handle: Int, name: String, message: String, stack: String?) {
        logger.info("디버그 어댑터 오류 수신: handle=$handle, name=$name, message=$message, stack=$stack")
    }
    
    override fun acceptDAExit(handle: Int, code: Int?, signal: String?) {
        logger.info("디버그 어댑터 종료 수신: handle=$handle, code=$code, signal=$signal")
    }
    
    override fun registerDebugConfigurationProvider(
        type: String,
        triggerKind: Int,
        hasProvideMethod: Boolean,
        hasResolveMethod: Boolean,
        hasResolve2Method: Boolean,
        handle: Int
    ): Any {
        logger.info("디버그 설정 제공자 등록: type=$type, triggerKind=$triggerKind, " +
                "hasProvideMethod=$hasProvideMethod, hasResolveMethod=$hasResolveMethod, " +
                "hasResolve2Method=$hasResolve2Method, handle=$handle")
        return Unit
    }
    
    override fun registerDebugAdapterDescriptorFactory(type: String, handle: Int): Any {
        logger.info("디버그 어댑터 디스크립터 팩토리 등록: type=$type, handle=$handle")
        return Unit
    }
    
    override fun unregisterDebugConfigurationProvider(handle: Int) {
        logger.info("디버그 설정 제공자 등록 해제: handle=$handle")
    }
    
    override fun unregisterDebugAdapterDescriptorFactory(handle: Int) {
        logger.info("디버그 어댑터 디스크립터 팩토리 등록 해제: handle=$handle")
    }
    
    override fun startDebugging(folder: URI?, nameOrConfig: Any, options: Any): Any {
        logger.info("디버깅 시작: folder=$folder, nameOrConfig=$nameOrConfig, options=$options")
        return true
    }
    
    override fun stopDebugging(sessionId: String?): Any {
        logger.info("디버깅 중지: sessionId=$sessionId")
        return Unit
    }
    
    override fun setDebugSessionName(id: String, name: String) {
        logger.info("디버그 세션 이름 설정: id=$id, name=$name")
    }
    
    override fun customDebugAdapterRequest(id: String, command: String, args: Any): Any {
        logger.info("사용자 정의 디버그 어댑터 요청: id=$id, command=$command, args=$args")
        return Unit
    }
    
    override fun getDebugProtocolBreakpoint(id: String, breakpoinId: String): Any? {
        logger.info("디버그 프로토콜 브레이크포인트 가져오기: id=$id, breakpoinId=$breakpoinId")
        return Unit
    }
    
    override fun appendDebugConsole(value: String) {
        logger.info("디버그 콘솔에 추가: $value")
    }
    
    override fun registerBreakpoints(breakpoints: List<Any>): Any {
        logger.info("브레이크포인트 등록: 총 ${breakpoints.size}개")
        return Unit
    }
    
    override fun unregisterBreakpoints(
        breakpointIds: List<String>,
        functionBreakpointIds: List<String>,
        dataBreakpointIds: List<String>
    ): Any {
        logger.info("브레이크포인트 등록 해제: 일반 ${breakpointIds.size}개, " +
                "함수 ${functionBreakpointIds.size}개, " +
                "데이터 ${dataBreakpointIds.size}개")
        return Unit
    }
    
    override fun registerDebugVisualizer(extensionId: String, id: String) {
        logger.info("디버그 시각화 도우미 등록: extensionId=$extensionId, id=$id")
    }
    
    override fun unregisterDebugVisualizer(extensionId: String, id: String) {
        logger.info("디버그 시각화 도우미 등록 해제: extensionId=$extensionId, id=$id")
    }
    
    override fun registerDebugVisualizerTree(treeId: String, canEdit: Boolean) {
        logger.info("디버그 시각화 도우미 트리 등록: treeId=$treeId, canEdit=$canEdit")
    }
    
    override fun unregisterDebugVisualizerTree(treeId: String) {
        logger.info("디버그 시각화 도우미 트리 등록 해제: treeId=$treeId")
    }

    override fun dispose() {
        logger.info("Disposing MainThreadDebugService")
    }
}
