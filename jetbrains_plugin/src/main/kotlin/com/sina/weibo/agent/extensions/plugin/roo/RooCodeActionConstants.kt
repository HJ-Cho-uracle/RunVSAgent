// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.roo

/** 프롬프트 타입 식별자를 위한 타입 별칭 */
typealias RooCodeSupportPromptType = String

/** 프롬프트 파라미터 맵을 위한 타입 별칭 */
typealias RooCodePromptParams = Map<String, Any?>

/**
 * 프롬프트 템플릿 문자열을 포함하는 프롬프트 설정을 나타내는 데이터 클래스입니다.
 * 템플릿은 동적 콘텐츠를 위한 플레이스홀더를 포함합니다.
 */
data class RooCodeSupportPromptConfig(val template: String)

/**
 * Roo Code 확장에서 사용되는 미리 정의된 프롬프트 설정들의 모음입니다.
 * 각 설정은 동적 콘텐츠를 위한 플레이스홀더를 포함하는 템플릿을 가집니다.
 */
object RooCodeSupportPromptConfigs {
    /**
     * 사용자 프롬프트를 개선하기 위한 템플릿입니다.
     * AI에게 사용자 입력의 개선된 버전을 생성하도록 지시합니다.
     */
    val ENHANCE = RooCodeSupportPromptConfig(
        """이 프롬프트의 개선된 버전을 생성하세요 (개선된 프롬프트만 회신 - 대화, 설명, 서론, 글머리 기호, 플레이스홀더 또는 따옴표 없이):

${'$'}{userInput}""",
    )

    /**
     * 코드 설명을 위한 템플릿입니다.
     * 파일 경로 및 라인 정보를 포함하여 코드 설명 요청을 위한 구조를 제공합니다.
     */
    val EXPLAIN = RooCodeSupportPromptConfig(
        """파일 경로 ${'$'}{filePath}:${'$'}{startLine}-${'$'}{endLine}의 다음 코드를 설명하세요.
${'$'}{userInput}

```
${'$'}{selectedText}
```

이 코드가 무엇을 하는지에 대한 명확하고 간결한 설명을 제공하세요. 다음을 포함합니다:
1. 목적 및 기능
2. 주요 구성 요소 및 상호 작용
3. 사용된 중요한 패턴 또는 기술""",
    )

    /**
     * 코드 문제 수정을 위한 템플릿입니다.
     * 진단 정보와 문제 해결을 위한 구조화된 형식을 포함합니다.
     */
    val FIX = RooCodeSupportPromptConfig(
        """파일 경로 ${'$'}{filePath}:${'$'}{startLine}-${'$'}{endLine}의 다음 코드에서 문제를 수정하세요.
${'$'}{diagnosticText}
${'$'}{userInput}

```
${'$'}{selectedText}
```

다음 사항을 지켜주세요:
1. 위에 나열된 모든 감지된 문제(있는 경우)를 해결하세요.
2. 다른 잠재적인 버그나 문제를 식별하세요.
3. 수정된 코드를 제공하세요.
4. 무엇이 수정되었고 그 이유를 설명하세요.""",
    )

    /**
     * 코드 품질 개선을 위한 템플릿입니다.
     * 가독성, 성능, 모범 사례 및 오류 처리에 중점을 둡니다.
     */
    val IMPROVE = RooCodeSupportPromptConfig(
        """파일 경로 ${'$'}{filePath}:${'$'}{startLine}-${'$'}{endLine}의 다음 코드를 개선하세요.
${'$'}{userInput}

```
${'$'}{selectedText}
```

다음에 대한 개선 사항을 제안하세요:
1. 코드 가독성 및 유지 보수성
2. 성능 최적화
3. 모범 사례 및 패턴
4. 오류 처리 및 엣지 케이스

각 개선 사항에 대한 설명과 함께 개선된 코드를 제공하세요.""",
    )

    /**
     * 코드를 컨텍스트에 추가하기 위한 템플릿입니다.
     * 파일 경로, 라인 범위 및 선택된 코드를 포함하는 간단한 형식입니다.
     */
    val ADD_TO_CONTEXT = RooCodeSupportPromptConfig(
        """${'$'}{filePath}:${'$'}{startLine}-${'$'}{endLine}
```
${'$'}{selectedText}
```""",
    )

    /**
     * 터미널 출력을 컨텍스트에 추가하기 위한 템플릿입니다.
     * 사용자 입력 및 터미널 콘텐츠를 포함합니다.
     */
    val TERMINAL_ADD_TO_CONTEXT = RooCodeSupportPromptConfig(
        """${'$'}{userInput}
터미널 출력:
```
${'$'}{terminalContent}
```""",
    )

    /**
     * 터미널 명령 수정을 위한 템플릿입니다.
     * 명령 문제 식별 및 해결을 위한 구조화된 형식입니다.
     */
    val TERMINAL_FIX = RooCodeSupportPromptConfig(
        """${'$'}{userInput}
이 터미널 명령을 수정하세요:
```
${'$'}{terminalContent}
```

다음 사항을 지켜주세요:
1. 명령의 문제를 식별하세요.
2. 수정된 명령을 제공하세요.
3. 무엇이 수정되었고 그 이유를 설명하세요.""",
    )

    /**
     * 터미널 명령 설명을 위한 템플릿입니다.
     * 기능 및 동작에 중점을 둔 명령 설명을 위한 구조를 제공합니다.
     */
    val TERMINAL_EXPLAIN = RooCodeSupportPromptConfig(
        """${'$'}{userInput}
이 터미널 명령을 설명하세요:
```
${'$'}{terminalContent}
```

다음을 제공하세요:
1. 명령이 하는 일
2. 각 부분/플래그에 대한 설명
3. 예상되는 출력 및 동작""",
    )

    /**
     * 새 작업을 생성하기 위한 템플릿입니다.
     * 사용자 입력을 직접 전달하는 간단한 형식입니다.
     */
    val NEW_TASK = RooCodeSupportPromptConfig(
        """${'$'}{userInput}""",
    )

    /**
     * 사용 가능한 모든 프롬프트 설정을 해당 타입 식별자로 인덱싱한 맵입니다.
     * 프롬프트 생성 시 조회를 위해 사용됩니다.
     */
    val configs = mapOf(
        "ENHANCE" to ENHANCE,
        "EXPLAIN" to EXPLAIN,
        "FIX" to FIX,
        "IMPROVE" to IMPROVE,
        "ADD_TO_CONTEXT" to ADD_TO_CONTEXT,
        "TERMINAL_ADD_TO_CONTEXT" to TERMINAL_ADD_TO_CONTEXT,
        "TERMINAL_FIX" to TERMINAL_FIX,
        "TERMINAL_EXPLAIN" to TERMINAL_EXPLAIN,
        "NEW_TASK" to NEW_TASK,
    )
}

/**
 * Roo Code 지원 프롬프트 작업을 위한 유틸리티 객체입니다.
 * 템플릿을 기반으로 프롬프트를 생성하고 사용자 정의하는 메소드를 제공합니다.
 */
object RooCodeSupportPrompt {
    /**
     * 진단 항목 목록으로부터 형식화된 진단 텍스트를 생성합니다.
     *
     * @param diagnostics 소스, 메시지, 코드 정보를 포함하는 진단 항목 목록
     * @return 진단 메시지의 형식화된 문자열, 진단이 없으면 빈 문자열
     */
    private fun generateDiagnosticText(diagnostics: List<Map<String, Any?>>?): String {
        if (diagnostics.isNullOrEmpty()) return ""
        return "\n현재 감지된 문제:\n" + diagnostics.joinToString("\n") { d ->
            val source = d["source"] as? String ?: "오류"
            val message = d["message"] as? String ?: ""
            val code = d["code"] as? String
            "- [$source] $message${code?.let { " ($it)" } ?: ""}"
        }
    }

    /**
     * 템플릿의 플레이스홀더를 실제 값으로 대체하여 프롬프트를 생성합니다.
     *
     * @param template 플레이스홀더가 있는 프롬프트 템플릿
     * @param params 플레이스홀더를 대체할 파라미터 값 맵
     * @return 플레이스홀더가 실제 값으로 대체된 처리된 프롬프트
     */
    private fun createPrompt(template: String, params: RooCodePromptParams): String {
        val pattern = Regex("""\$\{(.*?)}""") // ${placeholder} 패턴을 찾습니다.
        return pattern.replace(template) { matchResult ->
            val key = matchResult.groupValues[1] // 플레이스홀더 키 (예: "userInput")
            if (key == "diagnosticText") {
                generateDiagnosticText(params["diagnostics"] as? List<Map<String, Any?>>)
            } else if (params.containsKey(key)) {
                // 값을 문자열로 처리하여 대체합니다.
                val value = params[key]
                when (value) {
                    is String -> value
                    else -> {
                        // 문자열이 아닌 값은 문자열로 변환하여 대체합니다.
                        value?.toString() ?: ""
                    }
                }
            } else {
                // 플레이스홀더 키가 파라미터에 없으면 빈 문자열로 대체합니다.
                ""
            }
        }
    }

    /**
     * 특정 프롬프트 타입에 대한 템플릿을 가져옵니다. 사용자 정의 템플릿으로 재정의할 수 있습니다.
     *
     * @param customSupportPrompts 사용자 정의 프롬프트 템플릿 맵 (선택 사항)
     * @param type 가져올 프롬프트의 타입
     * @return 지정된 프롬프트 타입에 대한 템플릿 문자열
     */
    fun get(customSupportPrompts: Map<String, String>?, type: RooCodeSupportPromptType): String {
        return customSupportPrompts?.get(type) ?: RooCodeSupportPromptConfigs.configs[type]?.template ?: ""
    }

    /**
     * 템플릿을 가져오고 플레이스홀더를 대체하여 완전한 프롬프트를 생성합니다.
     *
     * @param type 생성할 프롬프트의 타입
     * @param params 템플릿에 대체할 파라미터
     * @param customSupportPrompts 사용자 정의 프롬프트 템플릿 (선택 사항)
     * @return 모든 플레이스홀더가 대체된 최종 프롬프트
     */
    fun create(type: RooCodeSupportPromptType, params: RooCodePromptParams, customSupportPrompts: Map<String, String>? = null): String {
        val template = get(customSupportPrompts, type)
        return createPrompt(template, params)
    }
}
