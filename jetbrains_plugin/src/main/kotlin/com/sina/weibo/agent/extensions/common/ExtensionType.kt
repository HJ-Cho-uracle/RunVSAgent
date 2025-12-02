package com.sina.weibo.agent.extensions.common

/**
 * Roo Code 플러그인에서 지원하는 확장(Extension)의 타입을 정의하는 열거형입니다.
 * 각 확장은 고유한 코드, 표시 이름, 설명을 가집니다.
 */
enum class ExtensionType(val code: String, val displayName: String, val description: String) {
    ROO_CODE("roo-code", "Roo Code", "AI 기반 코드 어시스턴트"),
    CLINE("cline", "Cline AI", "고급 기능을 갖춘 AI 기반 코딩 어시스턴트"),
    KILO_CODE("kilo-code", "Kilo Code", "고급 기능을 갖춘 AI 기반 코드 어시스턴트"),
    COSTRICT("costrict", "Costrict", "고급 기능을 갖춘 AI 기반 코드 어시스턴트"),
    ; // 열거형 상수 목록의 끝을 나타냅니다.

    companion object {
        /**
         * 코드(code)를 사용하여 해당하는 `ExtensionType`을 찾습니다.
         * @param code 찾을 확장의 코드 문자열
         * @return 해당하는 `ExtensionType` 객체, 찾지 못하면 null
         */
        fun fromCode(code: String): ExtensionType? {
            return values().find { it.code == code }
        }

        /**
         * 기본 확장 타입을 가져옵니다.
         * @return 기본 확장 타입 (`ROO_CODE`)
         */
        fun getDefault(): ExtensionType {
            return ROO_CODE
        }

        /**
         * 지원되는 모든 확장 타입의 목록을 가져옵니다.
         * @return 모든 `ExtensionType` 객체의 리스트
         */
        fun getAllTypes(): List<ExtensionType> {
            return values().toList()
        }
    }
}
