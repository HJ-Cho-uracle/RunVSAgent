// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.uri

import java.net.URI

/**
 * URI의 구성 요소(Parts)를 나타내는 데이터 클래스입니다.
 * VSCode의 `UriParts`에 해당합니다.
 */
data class UriParts(
    val scheme: String,         // 스키마 (예: "file", "http")
    val authority: String? = null, // 권한 (예: "localhost:8080")
    val path: String,           // 경로 (예: "/path/to/file.txt")
    val query: String? = null,  // 쿼리 문자열 (예: "key=value")
    val fragment: String? = null // 프래그먼트 (예: "section1")
)

/**
 * 원시 URI 변환기(Raw URI Transformer) 인터페이스입니다.
 * URI의 구성 요소(`UriParts`)를 직접 변환하는 기능을 정의합니다.
 */
interface IRawURITransformer {
    /**
     * 수신되는 URI를 변환합니다.
     * @param uri 변환할 `UriParts` 객체
     * @return 변환된 `UriParts` 객체
     */
    fun transformIncoming(uri: UriParts): UriParts
    
    /**
     * 발신되는 URI를 변환합니다.
     * @param uri 변환할 `UriParts` 객체
     * @return 변환된 `UriParts` 객체
     */
    fun transformOutgoing(uri: UriParts): UriParts
    
    /**
     * 발신되는 URI의 스키마만 변환합니다.
     * @param scheme 변환할 스키마 문자열
     * @return 변환된 스키마 문자열
     */
    fun transformOutgoingScheme(scheme: String): String
}

/**
 * URI 변환기(URI Transformer) 인터페이스입니다.
 * `java.net.URI` 객체를 직접 변환하는 기능을 정의합니다.
 */
interface IURITransformer {
    /**
     * 수신되는 `URI` 객체를 변환합니다.
     * @param uri 변환할 `URI` 객체
     * @return 변환된 `URI` 객체
     */
    fun transformIncoming(uri: URI): URI
    
    /**
     * 발신되는 `URI` 객체를 변환합니다.
     * @param uri 변환할 `URI` 객체
     * @return 변환된 `URI` 객체
     */
    fun transformOutgoing(uri: URI): URI
    
    /**
     * 발신되는 URI 문자열을 변환합니다.
     * @param uri 변환할 URI 문자열
     * @return 변환된 URI 문자열
     */
    fun transformOutgoingURI(uri: String): String
}

/**
 * `IURITransformer` 인터페이스의 구현 클래스입니다.
 * `IRawURITransformer`를 사용하여 `java.net.URI` 객체를 구성 요소별로 분리하고 변환한 후 다시 조합합니다.
 *
 * @param transformer 실제 구성 요소 변환 로직을 담고 있는 `IRawURITransformer` 인스턴스
 */
class URITransformer(private val transformer: IRawURITransformer) : IURITransformer {
    
    override fun transformIncoming(uri: URI): URI {
        // URI를 구성 요소로 분리합니다.
        val uriParts = UriParts(
            scheme = uri.scheme,
            authority = uri.authority,
            path = uri.path,
            query = uri.query,
            fragment = uri.fragment
        )
        
        // `IRawURITransformer`를 사용하여 구성 요소를 변환합니다.
        val transformedParts = transformer.transformIncoming(uriParts)
        
        // 변환된 구성 요소로부터 새로운 URI를 빌드합니다.
        return buildURI(transformedParts)
    }
    
    override fun transformOutgoing(uri: URI): URI {
        val uriParts = UriParts(
            scheme = uri.scheme,
            authority = uri.authority,
            path = uri.path,
            query = uri.query,
            fragment = uri.fragment
        )
        
        val transformedParts = transformer.transformOutgoing(uriParts)
        
        return buildURI(transformedParts)
    }
    
    override fun transformOutgoingURI(uri: String): String {
        try {
            return transformOutgoing(URI(uri)).toString()
        } catch (e: Exception) {
            // URI가 유효하지 않은 경우, 스키마 부분만 변환을 시도합니다.
            val schemeEndIndex = uri.indexOf(':')
            if (schemeEndIndex > 0) {
                val scheme = uri.substring(0, schemeEndIndex)
                val transformedScheme = transformer.transformOutgoingScheme(scheme)
                if (transformedScheme !== scheme) {
                    return transformedScheme + uri.substring(schemeEndIndex)
                }
            }
            return uri
        }
    }
    
    /**
     * `UriParts` 객체로부터 `java.net.URI` 객체를 생성합니다.
     */
    private fun buildURI(parts: UriParts): URI {
        val builder = StringBuilder()
        
        // 스키마 추가
        builder.append(parts.scheme).append(":")
        
        // 권한 추가 (존재하는 경우)
        if (!parts.authority.isNullOrEmpty()) {
            builder.append("//").append(parts.authority)
        }
        
        // 경로 추가
        builder.append(parts.path)
        
        // 쿼리 문자열 추가 (존재하는 경우)
        if (!parts.query.isNullOrEmpty()) {
            builder.append("?").append(parts.query)
        }
        
        // 프래그먼트 추가 (존재하는 경우)
        if (!parts.fragment.isNullOrEmpty()) {
            builder.append("#").append(parts.fragment)
        }
        
        return URI(builder.toString())
    }
}
