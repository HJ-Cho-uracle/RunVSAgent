// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

/**
 * 확장(Extension)을 위한 공통 유틸리티 메소드를 제공하는 객체입니다.
 */
object ExtensionUtils {
    /**
     * 소켓 서버 포트(정수) 또는 UDS(Unix Domain Socket) 경로(문자열)가 유효한지 확인합니다.
     * @param portOrPath 포트 번호(Int) 또는 UDS 경로(String)
     * @return 유효하면 true, 그렇지 않으면 false
     */
    @JvmStatic
    fun isValidPortOrPath(portOrPath: Any?): Boolean {
        return when (portOrPath) {
            is Int -> portOrPath > 0 // 포트 번호는 0보다 커야 유효합니다.
            is String -> portOrPath.isNotEmpty() // UDS 경로는 비어있지 않아야 유효합니다.
            else -> false // 다른 타입은 유효하지 않습니다.
        }
    }
}
