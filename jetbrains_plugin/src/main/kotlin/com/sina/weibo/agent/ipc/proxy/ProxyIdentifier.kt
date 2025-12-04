// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy

/**
 * 프록시 식별자(Proxy Identifier) 클래스입니다.
 * RPC 통신에서 특정 서비스 프록시를 고유하게 식별하는 데 사용됩니다.
 * VSCode의 `ProxyIdentifier`에 해당합니다.
 *
 * @param T 프록시가 나타내는 서비스의 타입
 * @property sid 문자열 식별자 (예: "MainThreadCommands")
 * @property nid 숫자 식별자 (내부적으로 할당되는 고유 번호)
 */
class ProxyIdentifier<T> private constructor(
    val sid: String,
    val nid: Int,
) {
    companion object {
        /**
         * 식별자 카운터입니다.
         * 새로운 `ProxyIdentifier`가 생성될 때마다 증가합니다.
         */
        var count = 0
            private set // 외부에서는 읽기만 가능

        /**
         * 새로운 `ProxyIdentifier` 인스턴스를 생성합니다.
         * 이 메소드는 `count`를 증가시켜 고유한 `nid`를 할당합니다.
         * @param sid 문자열 식별자
         * @return 생성된 `ProxyIdentifier` 인스턴스
         */
        internal fun <T> create(sid: String): ProxyIdentifier<T> {
            return ProxyIdentifier<T>(sid, ++count)
        }

        /**
         * 플레이스홀더 `ProxyIdentifier`를 생성합니다.
         * `count`를 증가시키지 않으므로, 내부적으로 `identifiers` 리스트를 채울 때 사용됩니다.
         * @param sid 문자열 식별자
         * @param nid 숫자 식별자
         * @return 생성된 플레이스홀더 `ProxyIdentifier` 인스턴스
         */
        internal fun <T> createPlaceholder(sid: String, nid: Int): ProxyIdentifier<T> {
            return ProxyIdentifier<T>(sid, nid)
        }
    }

    /**
     * `ProxyIdentifier` 객체를 문자열로 표현할 때 `sid` 값을 반환합니다.
     */
    override fun toString(): String {
        return this.sid
    }
}

/**
 * 생성된 모든 `ProxyIdentifier` 인스턴스들을 저장하는 리스트입니다.
 * `nid`를 인덱스로 사용하여 `ProxyIdentifier`를 빠르게 조회할 수 있습니다.
 */
private val identifiers = mutableListOf<ProxyIdentifier<*>>()

/**
 * 새로운 `ProxyIdentifier`를 생성하고 전역 리스트에 등록합니다.
 * @param identifier 문자열 식별자
 * @return 생성된 `ProxyIdentifier` 인스턴스
 */
fun <T> createProxyIdentifier(identifier: String): ProxyIdentifier<T> {
    val result = ProxyIdentifier.create<T>(identifier)
    // `nid`에 해당하는 인덱스까지 리스트를 확장하고 플레이스홀더로 채웁니다.
    while (identifiers.size <= result.nid) {
        identifiers.add(ProxyIdentifier.createPlaceholder<Any>("placeholder", identifiers.size))
    }
    // 실제 `ProxyIdentifier`를 해당 `nid` 위치에 저장합니다.
    identifiers[result.nid] = result
    return result
}

/**
 * 프록시 ID(숫자 식별자)를 사용하여 문자열 식별자(`sid`)를 가져옵니다.
 * @param nid 프록시 ID
 * @return 문자열 식별자
 */
fun getStringIdentifierForProxy(nid: Int): String {
    return identifiers[nid].sid
}

/**
 * 버퍼를 포함하는 직렬화 가능한 객체를 나타내는 클래스입니다.
 * VSCode의 `SerializableObjectWithBuffers`에 해당하며,
 * 객체와 함께 바이너리 버퍼를 전송해야 할 때 사용됩니다.
 * @param T 직렬화할 값의 타입
 * @property value 직렬화할 실제 값
 */
class SerializableObjectWithBuffers<T>(val value: T)
