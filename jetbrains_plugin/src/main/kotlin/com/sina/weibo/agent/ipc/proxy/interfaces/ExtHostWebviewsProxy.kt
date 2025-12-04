// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.actors.WebviewHandle
import com.sina.weibo.agent.ipc.proxy.SerializableObjectWithBuffers

/**
 * Extension Host WebView 서비스 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 WebView 서비스와 상호작용하기 위해 사용됩니다.
 */
interface ExtHostWebviewsProxy {
    /**
     * WebView로부터 수신된 메시지를 처리합니다.
     * @param handle 메시지를 보낸 WebView의 핸들
     * @param jsonSerializedMessage JSON 직렬화된 메시지 내용
     * @param buffers 바이너리 버퍼 배열
     */
    fun onMessage(
        handle: WebviewHandle,
        jsonSerializedMessage: String,
        buffers: SerializableObjectWithBuffers<List<ByteArray>>,
    )

    /**
     * Content Security Policy (CSP)가 누락되었을 때 처리합니다.
     * @param handle WebView 핸들
     * @param extensionId 확장의 ID
     */
    fun onMissingCsp(
        handle: WebviewHandle,
        extensionId: String,
    )
}
