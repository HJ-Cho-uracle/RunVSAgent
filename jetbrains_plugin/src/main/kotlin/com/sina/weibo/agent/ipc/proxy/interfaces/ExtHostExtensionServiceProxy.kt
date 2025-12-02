// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.ipc.proxy.LazyPromise

/**
 * Extension Host 확장 서비스 프록시 인터페이스입니다.
 * VSCode의 `ExtHostExtensionServiceShape`에 해당하며, Extension Host가
 * IntelliJ 플러그인의 확장 관련 기능을 호출하기 위해 사용됩니다.
 */
interface ExtHostExtensionServiceProxy {
    /**
     * 원격 권한(remote authority)을 해결(resolve)합니다.
     * @param remoteAuthority 원격 권한 식별자
     * @param resolveAttempt 해결 시도 횟수
     * @return 해결 결과를 담는 `LazyPromise`
     */
    fun resolveAuthority(remoteAuthority: String, resolveAttempt: Int): LazyPromise
    
    /**
     * 정식 URI(Canonical URI)를 가져옵니다.
     * 원격 권한에 대한 해결자를 찾을 수 없으면 null을 반환합니다.
     * @param remoteAuthority 원격 권한 식별자
     * @param uri URI 구성 요소
     * @return 정식 URI 구성 요소 또는 null을 담는 `LazyPromise`
     */
    fun getCanonicalURI(remoteAuthority: String, uri: Map<String, Any?>): LazyPromise
    
    /**
     * Extension Host를 시작합니다.
     * @param extensionsDelta 확장 설명 델타 (변경사항)
     * @return 시작 결과를 담는 `LazyPromise`
     */
    fun startExtensionHost(extensionsDelta: Map<String, Any?>): LazyPromise
    
    /**
     * 확장 테스트를 실행합니다.
     * @return 테스트 결과 코드를 담는 `LazyPromise`
     */
    fun extensionTestsExecute(): LazyPromise
    
    /**
     * 이벤트에 의해 확장을 활성화합니다.
     * @param activationEvent 활성화 이벤트
     * @param activationKind 활성화 종류
     * @return 활성화 결과를 담는 `LazyPromise`
     */
    fun activateByEvent(activationEvent: String, activationKind: Int): LazyPromise
    
    /**
     * 확장을 활성화합니다.
     * @param extensionId 활성화할 확장의 ID
     * @param reason 활성화 이유
     * @return 활성화 성공 여부를 담는 `LazyPromise`
     */
    fun activate(extensionId: String, reason: Map<String, Any?>): LazyPromise
    
    /**
     * 원격 환경 변수를 설정합니다.
     * @param env 환경 변수 맵
     * @return 설정 결과를 담는 `LazyPromise`
     */
    fun setRemoteEnvironment(env: Map<String, String?>): LazyPromise
    
    /**
     * 원격 연결 데이터를 업데이트합니다.
     * @param connectionData 연결 데이터
     * @return 업데이트 결과를 담는 `LazyPromise`
     */
    fun updateRemoteConnectionData(connectionData: Map<String, Any?>): LazyPromise
    
    /**
     * 확장을 델타 업데이트합니다.
     * @param extensionsDelta 확장 설명 델타 (변경사항)
     * @return 업데이트 결과를 담는 `LazyPromise`
     */
    fun deltaExtensions(extensionsDelta: Map<String, Any?>): LazyPromise
    
    /**
     * 지연 시간(latency) 테스트를 수행합니다.
     * @param n 테스트 파라미터
     * @return 지연 시간 값을 담는 `LazyPromise`
     */
    fun test_latency(n: Int): LazyPromise
    
    /**
     * 업로드 테스트를 수행합니다.
     * @param b 바이너리 버퍼
     * @return 결과를 담는 `LazyPromise`
     */
    fun test_up(b: ByteArray): LazyPromise
    
    /**
     * 다운로드 테스트를 수행합니다.
     * @param size 다운로드할 크기
     * @return 바이너리 버퍼를 담는 `LazyPromise`
     */
    fun test_down(size: Int): LazyPromise
}
