// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.uri

import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.ipc.IMessagePassingProtocol
import com.sina.weibo.agent.ipc.ISocket
import com.sina.weibo.agent.ipc.PersistentProtocol
import com.sina.weibo.agent.ipc.proxy.RPCProtocol
import com.sina.weibo.agent.ipc.proxy.createProxyIdentifier
import java.net.URI

/**
 * URI 변환기 사용 예시를 보여주는 객체입니다.
 */
object UriTransformerExample {
    private val LOG = Logger.getInstance(UriTransformerExample::class.java)
    
    /**
     * 예시: URI 변환기 생성 및 사용
     */
    fun uriTransformerExample() {
        // 1. URI 변환기 생성
        val remoteAuthority = "your-remote-host.example.com" // 원격 호스트 권한 지정
        val uriTransformer = createURITransformer(remoteAuthority)
        
        // 2. URI 변환 테스트
        val localUri = URI("file:///path/to/file.txt") // 로컬 파일 URI
        val remoteUri = uriTransformer.transformOutgoing(localUri) // 발신 URI로 변환
        LOG.info("변환된 URI: $remoteUri") // 예: vscode-remote://your-remote-host.example.com/path/to/file.txt
        
        // 3. 다시 변환 (원래대로)
        val convertedBackUri = uriTransformer.transformIncoming(remoteUri) // 수신 URI로 변환
        LOG.info("다시 변환된 URI: $convertedBackUri") // 예: file:///path/to/file.txt
        
        // 4. JSON 객체 내의 URI 변환을 위한 UriReplacer 사용
        val uriReplacer = UriReplacer(uriTransformer)
        val result = uriReplacer("documentUri", "file:///path/to/document.txt")
        LOG.info("대체된 URI: $result") // "documentUri" 키에 해당하는 값이 변환됩니다.
    }
    
    /**
     * 예시: RPC 프로토콜에서 URI 변환기 사용
     * RPC 프로토콜에 URI 변환기를 전달하면, 메시지 직렬화/역직렬화 과정에서
     * URI가 자동으로 변환됩니다.
     */
    fun rpcWithUriTransformerExample(socket: ISocket) {
        // 1. URI 변환기 생성
        val remoteAuthority = "your-remote-host.example.com"
        val uriTransformer = createURITransformer(remoteAuthority)
        
        // 2. 하위 프로토콜 객체 생성
        val persistentProtocol = PersistentProtocol(PersistentProtocol.PersistentProtocolOptions(socket))
        
        // 3. RPC 프로토콜 객체 생성 시 URI 변환기 전달
        val rpcProtocol = RPCProtocol(persistentProtocol, null, uriTransformer)
        
        // 이제 RPC 프로토콜은 구성된 규칙에 따라 URI 변환을 자동으로 처리합니다.
        // 직렬화 및 역직렬화 중에 URI가 자동으로 변환됩니다.
    }
}
