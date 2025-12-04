// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

import com.intellij.openapi.Disposable

/**
 * RPC(Remote Procedure Call) 프로토콜 인터페이스입니다.
 * VSCode의 `IRPCProtocol`에 해당하며, 원격 서비스 호출을 위한 기능을 정의합니다.
 */
interface IRPCProtocol : Disposable {
    /**
     * 현재 프로토콜의 응답 상태를 나타냅니다.
     * (예: 연결 상태, 메시지 처리 지연 등)
     */
    val responsiveState: ResponsiveState

    /**
     * 지정된 식별자에 해당하는 프록시 객체를 가져옵니다.
     * 이 프록시를 통해 원격 서비스의 메소드를 로컬에서 호출하는 것처럼 사용할 수 있습니다.
     * @param identifier 프록시를 식별하는 `ProxyIdentifier`
     * @return 원격 서비스의 프록시 객체
     */
    fun <T> getProxy(identifier: ProxyIdentifier<T>): T

    /**
     * 지정된 식별자에 해당하는 로컬 객체 인스턴스를 설정합니다.
     * 이 객체는 원격에서 호출될 메소드를 실제로 구현합니다.
     * @param identifier 프록시를 식별하는 `ProxyIdentifier`
     * @param instance 로컬에서 구현된 서비스 인스턴스
     * @return 설정된 인스턴스 객체
     */
    fun <T, R : T> set(identifier: ProxyIdentifier<T>, instance: R): R

    /**
     * 지정된 식별자들이 모두 등록되었는지 확인합니다.
     * @param identifiers 확인할 `ProxyIdentifier` 리스트
     */
    fun assertRegistered(identifiers: List<ProxyIdentifier<*>>)

    /**
     * 쓰기 버퍼(있는 경우)가 비워질 때까지 기다립니다.
     * 모든 메시지가 전송될 때까지 대기하는 비동기 작업입니다.
     */
    suspend fun drain()
}
