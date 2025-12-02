// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.sina.weibo.agent.util.URI

/**
 * 문서의 내용을 나타내는 데이터 클래스입니다.
 * Extension Host와 IntelliJ 플러그인 간에 문서 상태를 동기화하는 데 사용됩니다.
 */
data class ModelAddedData(
    val uri: URI,           // 문서의 URI
    var versionId: Int,     // 문서의 버전 ID (변경될 때마다 증가)
    var lines: List<String>,// 문서의 각 라인 내용
    val EOL: String,        // 줄바꿈 문자 (예: "\n", "\r\n")
    var languageId: String, // 문서의 언어 ID (예: "typescript", "java")
    var isDirty: Boolean,   // 문서가 수정되었지만 저장되지 않았는지 여부
    var encoding: String    // 문서의 인코딩 (예: "utf8")
)

/**
 * 에디터의 선택 영역(Selection)을 나타내는 데이터 클래스입니다.
 */
data class Selection(
    val selectionStartLineNumber: Int, // 선택 시작 라인 번호 (1-based)
    val selectionStartColumn: Int,     // 선택 시작 컬럼 번호 (1-based)
    val positionLineNumber: Int,       // 커서의 현재 라인 번호 (1-based)
    val positionColumn: Int            // 커서의 현재 컬럼 번호 (1-based)
)

/**
 * 문서 내의 범위(Range)를 나타내는 데이터 클래스입니다.
 * 시작 라인/컬럼부터 끝 라인/컬럼까지를 정의합니다.
 */
data class Range(
    val startLineNumber: Int, // 시작 라인 번호 (1-based)
    val startColumn: Int,     // 시작 컬럼 번호 (1-based)
    val endLineNumber: Int,   // 끝 라인 번호 (1-based)
    val endColumn: Int        // 끝 컬럼 번호 (1-based)
)

/**
 * 텍스트 에디터의 설정 옵션을 나타내는 데이터 클래스입니다.
 */
data class ResolvedTextEditorConfiguration(
    val tabSize: Int = 4,       // 탭 크기
    val indentSize: Int = 4,    // 들여쓰기 크기
    val originalIndentSize: Int = 4, // 원래 들여쓰기 크기
    val insertSpaces: Boolean = true, // 스페이스로 들여쓰기 할지 여부
    val cursorStyle: Int = 1,   // 커서 스타일
    val lineNumbers: Int = 1    // 라인 번호 표시 방식
)

/**
 * 에디터의 추가/제거 시 전달되는 데이터를 나타내는 데이터 클래스입니다.
 */
data class TextEditorAddData(
    val id: String,                         // 에디터의 고유 ID
    val documentUri: URI,                   // 에디터가 표시하는 문서의 URI
    var options: ResolvedTextEditorConfiguration, // 에디터 설정
    var selections: List<Selection>,        // 현재 선택 영역
    var visibleRanges: List<Range>,         // 현재 보이는 범위
    var editorPosition: Int?                // 에디터의 스크롤 위치 (선택 사항)
)

/**
 * 문서 내용 변경을 나타내는 데이터 클래스입니다.
 */
data class ModelContentChange(
    val range: Range,       // 변경된 범위
    val rangeOffset: Int,   // 변경된 범위의 시작 오프셋
    val rangeLength: Int,   // 변경된 범위의 길이
    val text: String        // 변경된 내용 (새로운 텍스트)
)

/**
 * 문서 모델의 변경 이벤트를 나타내는 데이터 클래스입니다.
 */
data class ModelChangedEvent(
    val changes: List<ModelContentChange>, // 변경된 내용 목록
    val eol: String,                       // 줄바꿈 문자
    val versionId: Int,                    // 변경 후 문서의 버전 ID
    val isUndoing: Boolean,                // 실행 취소 작업으로 인한 변경인지 여부
    val isRedoing: Boolean,                // 다시 실행 작업으로 인한 변경인지 여부
    val isDirty: Boolean                   // 변경 후 문서가 'dirty' 상태인지 여부
)

/**
 * 선택 영역 변경 이벤트를 나타내는 데이터 클래스입니다.
 */
data class SelectionChangeEvent(
    val selections: List<Selection>, // 새로운 선택 영역 목록
    val source: String?              // 변경의 원인 (예: "mouse", "keyboard")
)

/**
 * 에디터 속성 변경 데이터를 나타내는 데이터 클래스입니다.
 */
data class EditorPropertiesChangeData(
    val options: ResolvedTextEditorConfiguration?, // 변경된 에디터 옵션
    val selections: SelectionChangeEvent?,         // 변경된 선택 영역
    val visibleRanges: List<Range>?                // 변경된 가시 범위
)

/**
 * 문서 및 에디터의 추가/제거/활성화 변경사항(델타)을 나타내는 데이터 클래스입니다.
 */
data class DocumentsAndEditorsDelta(
    val removedDocuments: List<URI>?,           // 제거된 문서의 URI 목록
    val addedDocuments: List<ModelAddedData>?,  // 추가된 문서의 데이터 목록
    val removedEditors: List<String>?,          // 제거된 에디터의 ID 목록
    val addedEditors: List<TextEditorAddData>?, // 추가된 에디터의 데이터 목록
    val newActiveEditor: String?                // 새로 활성화된 에디터의 ID
) {
    /**
     * 델타가 비어있는지(아무런 변경사항이 없는지) 확인합니다.
     */
    fun isEmpty(): Boolean {
        return removedDocuments.isNullOrEmpty() &&
               addedDocuments.isNullOrEmpty() &&
               removedEditors.isNullOrEmpty() &&
               addedEditors.isNullOrEmpty() &&
               newActiveEditor.isNullOrEmpty()
    }
}

/**
 * 텍스트 에디터의 변경 사항을 나타내는 데이터 클래스입니다. (Diff 정보용)
 */
data class TextEditorChange(
    val originalStartLineNumber: Int,      // 원본의 시작 라인 번호
    val originalEndLineNumberExclusive: Int, // 원본의 끝 라인 번호 (배타적)
    val modifiedStartLineNumber: Int,      // 수정본의 시작 라인 번호
    val modifiedEndLineNumberExclusive: Int // 수정본의 끝 라인 번호 (배타적)
)

/**
 * 텍스트 에디터의 Diff 정보를 나타내는 데이터 클래스입니다.
 */
data class TextEditorDiffInformation(
    val documentVersion: Int, // 문서 버전
    val original: URI?,       // 원본 문서의 URI
    val modified: URI,        // 수정된 문서의 URI
    val changes: List<TextEditorChange> // 변경 사항 목록
)

/**
 * 에디터 그룹(컬럼)의 위치를 나타내는 열거형 클래스입니다.
 */
enum class EditorGroupColumn(val value: Int) {
    active(-1), // 현재 활성화된 그룹
    beside(-2), // 현재 그룹 옆
    one(1),     // 첫 번째 그룹
    two(2),     // 두 번째 그룹
    three(3),   // 세 번째 그룹
    four(4),    // 네 번째 그룹
    five(5),    // 다섯 번째 그룹
    six(6),     // 여섯 번째 그룹
    seven(7),   // 일곱 번째 그룹
    eight(8),   // 여덟 번째 그룹
    nine(9);    // 아홉 번째 그룹

    val groupIndex: Int
        get() {
            return when (this) {
                active -> -1
                beside -> -2
                else -> this.value - 1 // 1-based value를 0-based 인덱스로 변환
            }
        }

    companion object {
        /**
         * 정수 값으로부터 `EditorGroupColumn` 열거형 상수를 찾습니다.
         */
        fun fromValue(value: Int): EditorGroupColumn {
            return when (value) {
                -2 -> beside
                -1 -> active
                1 -> one
                2 -> two
                3 -> three
                4 -> four
                5 -> five
                6 -> six
                7 -> seven
                8 -> eight
                9 -> nine
                else -> active // 기본값은 active
            }
        }
    }
}
