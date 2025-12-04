// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

/**
 * 확장의 고유 식별자(ID)를 나타내는 클래스입니다.
 * VSCode의 `ExtensionIdentifier`에 해당하며, 확장을 구별하고 관리하는 데 사용됩니다.
 *
 * @property value 원본 확장 ID 문자열 (예: "wecode-ai.runvsagent")
 */
class ExtensionIdentifier(val value: String) {
    /**
     * 비교 및 인덱싱을 위해 ID를 소문자로 변환하여 저장합니다.
     * 이를 통해 대소문자를 구분하지 않는(case-insensitive) 비교를 효율적으로 수행할 수 있습니다.
     */
    private val _lower: String = value.lowercase()

    companion object {
        /**
         * 두 개의 `ExtensionIdentifier`가 동일한지 비교합니다.
         * @param a 첫 번째 확장 식별자
         * @param b 두 번째 확장 식별자
         * @return 동일하면 true, 그렇지 않으면 false
         */
        fun equals(
            a: ExtensionIdentifier?,
            b: ExtensionIdentifier?,
        ): Boolean {
            if (a == null) return b == null
            if (b == null) return false
            return a._lower == b._lower
        }

        /**
         * `ExtensionIdentifier`와 문자열이 동일한지 비교합니다.
         * @param a 확장 식별자
         * @param b 비교할 문자열
         * @return 값이 (대소문자 구분 없이) 동일하면 true, 그렇지 않으면 false
         */
        fun equals(
            a: ExtensionIdentifier?,
            b: String?,
        ): Boolean {
            if (a == null) return b == null
            if (b == null) return false
            return a._lower == b.lowercase()
        }

        /**
         * 문자열과 `ExtensionIdentifier`가 동일한지 비교합니다.
         * @param a 비교할 문자열
         * @param b 확장 식별자
         * @return 값이 (대소문자 구분 없이) 동일하면 true, 그렇지 않으면 false
         */
        fun equals(
            a: String?,
            b: ExtensionIdentifier?,
        ): Boolean {
            if (a == null) return b == null
            if (b == null) return false
            return a.lowercase() == b._lower
        }

        /**
         * 맵(Map)의 키나 인덱싱에 사용할 수 있는 소문자 키를 생성합니다.
         * @param id 확장 식별자
         * @return 소문자로 변환된 키
         */
        fun toKey(id: ExtensionIdentifier): String {
            return id._lower
        }

        /**
         * 맵(Map)의 키나 인덱싱에 사용할 수 있는 소문자 키를 생성합니다.
         * @param id ID 문자열
         * @return 소문자로 변환된 키
         */
        fun toKey(id: String): String {
            return id.lowercase()
        }
    }

    /**
     * 객체의 동등성을 비교합니다. `_lower` 값을 기준으로 대소문자 구분 없이 비교합니다.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ExtensionIdentifier) return false
        return _lower == other._lower
    }

    /**
     * 객체의 해시 코드를 반환합니다. `_lower` 값의 해시 코드를 사용하여,
     * 대소문자가 다른 동일한 ID가 해시 기반 컬렉션(예: HashMap, HashSet)에서
     * 동일한 키로 취급되도록 보장합니다.
     */
    override fun hashCode(): Int {
        return _lower.hashCode()
    }

    /**
     * 객체를 문자열로 표현할 때 원본 `value`를 반환합니다.
     */
    override fun toString(): String {
        return value
    }
}
