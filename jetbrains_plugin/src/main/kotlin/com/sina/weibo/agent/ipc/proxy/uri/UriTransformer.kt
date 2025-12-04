// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.uri

/**
 * 원시 URI 변환기(Raw URI Transformer)를 생성하는 헬퍼 함수입니다.
 * VSCode의 `createRawURITransformer`에 해당합니다.
 *
 * @param remoteAuthority 원격 권한 식별자 (예: "ssh-remote+host")
 * @return `IRawURITransformer` 인스턴스
 */
fun createRawURITransformer(remoteAuthority: String): IRawURITransformer {
    return object : IRawURITransformer {
        /**
         * 수신되는 URI를 변환합니다.
         * (예: "vscode-remote" 스키마를 "file"로, "file" 스키마를 "vscode-local"로 변환)
         */
        override fun transformIncoming(uri: UriParts): UriParts {
            return when (uri.scheme) {
                "vscode-remote" -> UriParts(
                    scheme = "file",
                    path = uri.path,
                    query = uri.query,
                    fragment = uri.fragment,
                )
                "file" -> UriParts(
                    scheme = "vscode-local",
                    path = uri.path,
                    query = uri.query,
                    fragment = uri.fragment,
                )
                else -> uri
            }
        }

        /**
         * 발신되는 URI를 변환합니다.
         * (예: "file" 스키마를 "vscode-remote"로, "vscode-local" 스키마를 "file"로 변환)
         */
        override fun transformOutgoing(uri: UriParts): UriParts {
            return when (uri.scheme) {
                "file" -> UriParts(
                    scheme = "vscode-remote",
                    authority = remoteAuthority,
                    path = uri.path,
                    query = uri.query,
                    fragment = uri.fragment,
                )
                "vscode-local" -> UriParts(
                    scheme = "file",
                    path = uri.path,
                    query = uri.query,
                    fragment = uri.fragment,
                )
                else -> uri
            }
        }

        /**
         * 발신되는 URI의 스키마만 변환합니다.
         */
        override fun transformOutgoingScheme(scheme: String): String {
            return when (scheme) {
                "file" -> "vscode-remote"
                "vscode-local" -> "file"
                else -> scheme
            }
        }
    }
}

/**
 * `IURITransformer`를 생성하는 헬퍼 함수입니다.
 * @param remoteAuthority 원격 권한 식별자
 * @return `IURITransformer` 인스턴스
 */
fun createURITransformer(remoteAuthority: String): IURITransformer {
    return URITransformer(createRawURITransformer(remoteAuthority))
}

/**
 * URI 변환을 위한 JSON 리플레이서(replacer) 클래스입니다.
 * JSON 직렬화 과정에서 특정 키(예: "uri", "documentUri")에 해당하는 문자열 값을
 * `IURITransformer`를 사용하여 변환합니다.
 */
class UriReplacer(private val transformer: IURITransformer) : (String, Any?) -> Any? {

    /**
     * JSON 직렬화 중 호출되어 값을 변환합니다.
     * @param key 현재 처리 중인 JSON 속성의 키
     * @param value 현재 처리 중인 JSON 속성의 값
     * @return 변환된 값 또는 원본 값
     */
    override fun invoke(key: String, value: Any?): Any? {
        // 값이 문자열이고 URI 관련 키(예: "uri", "documentUri")인 경우 변환을 시도합니다.
        if (value is String && (
                key == "uri" ||
                    key == "documentUri" ||
                    key == "targetUri" ||
                    key == "sourceUri" ||
                    key.endsWith("Uri")
                )
        ) {
            return transformer.transformOutgoingURI(value)
        }
        return value
    }
}
