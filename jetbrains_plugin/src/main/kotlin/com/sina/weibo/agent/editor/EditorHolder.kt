// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 단일 에디터 인스턴스의 상태와 동작을 관리하는 클래스입니다.
 * IntelliJ 에디터와 VSCode 에디터 상태 간의 동기화를 처리합니다.
 *
 * @param id 이 에디터의 고유 식별자
 * @param state 이 에디터의 현재 상태 데이터 (`TextEditorAddData`)
 * @param document 이 에디터와 연결된 문서 데이터 (`ModelAddedData`)
 * @param diff 이 에디터가 Diff 에디터인지 여부
 * @param stateManager `EditorAndDocManager`에 대한 참조 (상태 업데이트를 위해 사용)
 */
class EditorHolder(
    val id: String,
    var state: TextEditorAddData,
    var document: ModelAddedData,
    val diff: Boolean,
    private val stateManager: EditorAndDocManager,
) {

    private val logger = Logger.getInstance(EditorHolder::class.java)

    /**
     * 이 에디터가 현재 활성화되어 있는지 여부를 나타냅니다.
     */
    var isActive: Boolean = false
        private set // 외부에서는 읽기만 가능

    /**
     * 이 에디터와 연결된 IntelliJ `Document` 객체입니다.
     */
    private var editorDocument: Document? = null

    /**
     * 이 에디터에 대한 IntelliJ `FileEditor` 인스턴스입니다.
     */
    var ideaEditor: FileEditor? = null

    /**
     * 에디터 탭의 제목 (있는 경우).
     */
    var title: String? = null

    /**
     * 이 에디터가 속한 탭 그룹 핸들입니다.
     */
    var group: TabGroupHandle? = null

    /**
     * 이 에디터에 대한 탭 핸들입니다.
     */
    var tab: TabHandle? = null

    // --- 지연된 업데이트(Debounced Update) 관련 필드 ---

    /** 에디터 상태 업데이트를 위한 디바운스 Job */
    private var editorUpdateJob: Job? = null

    /** 문서 상태 업데이트를 위한 디바운스 Job */
    private var documentUpdateJob: Job? = null

    /** 디바운스 업데이트를 위한 지연 시간 (밀리초) */
    private val updateDelay: Long = 30 // 30ms 지연

    /**
     * 에디터의 선택 영역을 업데이트하고 상태 업데이트를 트리거합니다.
     * @param selections 적용할 선택 영역 리스트
     */
    fun updateSelections(selections: List<Selection>) {
        state.selections = selections
        debouncedUpdateState()
    }

    /**
     * 에디터의 가시 범위(visible ranges)를 업데이트하고 상태 업데이트를 트리거합니다.
     * @param ranges 적용할 가시 범위 리스트
     */
    fun updateVisibleRanges(ranges: List<Range>) {
        state.visibleRanges = ranges
        debouncedUpdateState()
    }

    /**
     * 에디터의 스크롤 위치를 업데이트하고 상태 업데이트를 트리거합니다.
     * @param position 새로운 스크롤 위치
     */
    fun updatePosition(position: Int?) {
        state.editorPosition = position
        debouncedUpdateState()
    }

    /**
     * 에디터의 옵션(설정)을 업데이트하고 상태 업데이트를 트리거합니다.
     * @param options 적용할 에디터 설정
     */
    fun updateOptions(options: ResolvedTextEditorConfiguration) {
        state.options = options
        debouncedUpdateState()
    }

    /**
     * 에디터의 활성 상태를 설정합니다.
     * @param active 활성화 여부
     */
    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        // 에디터가 활성화될 때 Document 객체가 아직 로드되지 않았다면 로드합니다.
        if (editorDocument == null && active) {
            val vfs = LocalFileSystem.getInstance()
            val path = document.uri.path
            ApplicationManager.getApplication().runReadAction {
                val file = vfs.findFileByPath(path)
                editorDocument = file?.let { FileDocumentManager.getInstance().getDocument(it) }
            }
        }
        // 상태 변경을 stateManager에 알립니다.
        CoroutineScope(Dispatchers.IO).launch {
            delay(100)
            stateManager.didUpdateActive(this@EditorHolder)
        }
    }

    /**
     * 지정된 범위가 사용자에게 보이도록 에디터를 스크롤합니다.
     * @param range 보여줄 범위
     */
    fun revealRange(range: Range) {
        state.visibleRanges = listOf(range)
        // IntelliJ의 Diff 에디터에서 스크롤을 수행합니다.
        stateManager.getIdeaDiffEditor(document.uri)?.get()?.let { e ->
            ApplicationManager.getApplication().invokeLater {
                val target = LogicalPosition(range.startLineNumber, 0)
                e.scrollingModel.scrollTo(target, ScrollType.RELATIVE)
            }
        }
        debouncedUpdateState()
    }

    /**
     * 텍스트 편집(`TextEdit`)을 문서에 적용합니다.
     * @param edit 적용할 텍스트 편집 객체
     * @return 편집 적용 성공 여부
     */
    suspend fun applyEdit(edit: TextEdit): Boolean {
        val content = editorDocument?.text ?: ""
        val lines = content.lines()
        val lineCount = lines.size

        // Calculate range
        val startLine = max(0, edit.textEdit.range.startLineNumber - 1)
        val startColumn = max(0, edit.textEdit.range.startColumn - 1)
        val endLine = min(lineCount - 1, edit.textEdit.range.endLineNumber - 1)
        val endColumn = min(lines[endLine].length, edit.textEdit.range.endColumn - 1)

        // Calculate offsets
        var startOffset = 0
        var endOffset = 0
        for (i in 0 until lineCount) {
            if (i < startLine) {
                startOffset += lines[i].length + 1 // +1 for newline
            } else if (i == startLine) {
                startOffset += min(startColumn, lines[i].length)
            }

            if (i < endLine) {
                endOffset += lines[i].length + 1 // +1 for newline
            } else if (i == endLine) {
                endOffset += min(endColumn, lines[i].length)
            }
        }

        // Ensure range is valid
        val textLength = content.length
        if (startOffset < 0 || endOffset > textLength || startOffset > endOffset) {
            return false
        }
        val end = (endLine < (edit.textEdit.range.endLineNumber - 1))
        val newText = edit.textEdit.text.replace("\r\n", "\n")
        val newContent = content.substring(0, startOffset) + newText + (if (!end) content.substring(endOffset) else "")
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                editorDocument?.setText(newContent)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            val file = File(document.uri.path).parentFile
            if (file.exists()) {
                LocalFileSystem.getInstance().refreshIoFiles(listOf(file))
            }
        }
        val newDoc = ModelAddedData(
            uri = document.uri,
            versionId = document.versionId + 1,
            lines = newContent.lines(),
            EOL = document.EOL,
            languageId = document.languageId,
            isDirty = true,
            encoding = document.encoding,
        )
        document = newDoc
        stateManager.updateDocumentAsync(newDoc)
        return true
    }

    /**
     * 문서를 저장합니다.
     * @return 저장 성공 여부
     */
    suspend fun save(): Boolean {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                if (editorDocument != null) {
                    FileDocumentManager.getInstance().saveDocument(editorDocument!!)
                }
            }
        }
        // 문서 상태를 '저장됨'으로 업데이트하고 stateManager에 알립니다.
        val newDoc = ModelAddedData(
            uri = document.uri,
            versionId = document.versionId + 1,
            lines = document.lines,
            EOL = document.EOL,
            languageId = document.languageId,
            isDirty = false,
            encoding = document.encoding,
        )
        document = newDoc
        stateManager.updateDocumentAsync(newDoc)
        return true
    }

    /**
     * 문서 내용을 업데이트하고 디바운스된 문서 업데이트를 트리거합니다.
     * @param lines 새로운 문서 내용 (라인 리스트)
     * @param versionId 새로운 버전 ID (선택 사항)
     */
    fun updateDocumentContent(lines: List<String>, versionId: Int? = null) {
        document.lines = lines
        document.versionId = versionId ?: (document.versionId + 1)
        debouncedUpdateDocument()
    }

    /**
     * 문서의 언어 ID를 업데이트합니다.
     * @param languageId 새로운 언어 ID
     */
    fun updateDocumentLanguage(languageId: String) {
        document.languageId = languageId
        debouncedUpdateDocument()
    }

    /**
     * 문서의 인코딩을 업데이트합니다.
     * @param encoding 새로운 인코딩
     */
    fun updateDocumentEncoding(encoding: String) {
        document.encoding = encoding
        debouncedUpdateDocument()
    }

    /**
     * 문서의 'dirty' 상태(수정되었지만 저장되지 않은 상태)를 업데이트합니다.
     * @param isDirty 'dirty' 상태 여부
     */
    suspend fun updateDocumentDirty(isDirty: Boolean) {
        if (document.isDirty == isDirty) return

        val newDoc = ModelAddedData(
            uri = document.uri,
            versionId = document.versionId + 1,
            lines = document.lines,
            EOL = document.EOL,
            languageId = document.languageId,
            isDirty = isDirty,
            encoding = document.encoding,
        )
        document = newDoc
        // IntelliJ의 FileDocumentManager를 통해 문서 저장
        ApplicationManager.getApplication().invokeAndWait {
            val fileDocumentManager = FileDocumentManager.getInstance()
            editorDocument?.let { fileDocumentManager.saveDocument(it) }
        }

        debouncedUpdateDocument()
    }

    /**
     * 문서 상태를 즉시 동기화합니다.
     */
    suspend fun syncDocumentState() {
        documentUpdateJob?.cancel()
        stateManager.updateDocument(document)
        stateManager.syncUpdates()
    }

    // --- Private 메소드 ---

    /**
     * 과도한 업데이트를 피하기 위해 디바운싱(debouncing)을 적용하여 에디터 상태를 업데이트합니다.
     */
    private fun debouncedUpdateState() {
        editorUpdateJob?.cancel()
        editorUpdateJob = CoroutineScope(Dispatchers.Default).launch {
            delay(updateDelay)
            stateManager.updateEditor(state)
        }
    }

    /**
     * 과도한 업데이트를 피하기 위해 디바운싱을 적용하여 문서 상태를 업데이트합니다.
     */
    private fun debouncedUpdateDocument() {
        documentUpdateJob?.cancel()
        documentUpdateJob = CoroutineScope(Dispatchers.Default).launch {
            delay(updateDelay)
            stateManager.updateDocument(document)
        }
    }
}
