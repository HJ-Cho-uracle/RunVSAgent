// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.sina.weibo.agent.editor.EditorAndDocManager
import com.sina.weibo.agent.editor.Range
import com.sina.weibo.agent.editor.createURI
import kotlinx.coroutines.delay
import java.io.File

/**
 * IntelliJ 메인 스레드에서 텍스트 에디터 관련 작업을 처리하기 위한 인터페이스입니다.
 * 에디터에 문서를 표시하고, 데코레이션을 적용하며, 선택 영역을 변경하는 등 다양한 UI 조작 기능을 정의합니다.
 */
interface MainThreadTextEditorsShape : Disposable {
    /**
     * 지정된 리소스(파일)를 텍스트 에디터에 표시하려고 시도합니다.
     * @param resource 표시할 문서의 URI 정보
     * @param options 에디터를 어떻게 표시할지에 대한 옵션 (예: 특정 컬럼에 표시)
     * @return 생성된 에디터의 고유 ID 또는 null
     */
    suspend fun tryShowTextDocument(resource: Map<String,Any?>, options: Any?): Any?
    
    /**
     * 텍스트 에디터에 사용될 데코레이션 타입(예: 특정 색상, 밑줄 등)을 등록합니다.
     * @param extensionId 이 데코레이션을 등록하는 확장의 ID
     * @param key 데코레이션 타입을 식별하는 고유 키
     * @param options 데코레이션 렌더링에 대한 상세 옵션
     */
    fun registerTextEditorDecorationType(extensionId: Map<String, String>, key: String, options: Any)
    
    /**
     * 등록된 텍스트 에디터 데코레이션 타입을 제거합니다.
     * @param key 제거할 데코레이션 타입의 키
     */
    fun removeTextEditorDecorationType(key: String)
    
    /**
     * 지정된 ID의 에디터를 사용자에게 보여줍니다. (예: 해당 탭을 활성화)
     * @param id 에디터의 고유 ID
     * @param position 에디터를 표시할 위치 정보
     */
    fun tryShowEditor(id: String, position: Any?): Any
    
    /**
     * 지정된 ID의 에디터를 숨깁니다.
     * @param id 에디터의 고유 ID
     */
    fun tryHideEditor(id: String): Any
    
    /**
     * 지정된 ID의 에디터에 대한 옵션을 설정합니다. (예: 탭 크기, 들여쓰기 스타일 등)
     * @param id 에디터의 고유 ID
     * @param options 적용할 설정 변경사항
     */
    fun trySetOptions(id: String, options: Any): Any
    
    /**
     * 지정된 ID의 에디터에 데코레이션을 적용합니다.
     * @param id 에디터의 고유 ID
     * @param key 적용할 데코레이션 타입의 키
     * @param ranges 데코레이션을 적용할 범위(Range) 목록
     */
    fun trySetDecorations(id: String, key: String, ranges: List<Any>): Any
    
    /**
     * 지정된 ID의 에디터에 데코레이션을 빠르게 적용합니다. (성능 최적화 버전일 수 있음)
     */
    fun trySetDecorationsFast(id: String, key: String, ranges: List<Any>): Any
    
    /**
     * 지정된 ID의 에디터에서 특정 범위를 사용자에게 보여주도록 스크롤합니다.
     * @param id 에디터의 고유 ID
     * @param range 보여줄 범위
     * @param revealType 어떻게 보여줄지에 대한 방식 (예: 중앙, 상단)
     */
    fun tryRevealRange(id: String, range: Map<String,Any?>, revealType: Int): Any
    
    /**
     * 지정된 ID의 에디터에서 선택 영역(커서 위치 포함)을 설정합니다.
     * @param id 에디터의 고유 ID
     * @param selections 적용할 선택 영역 정보의 목록
     */
    fun trySetSelections(id: String, selections: List<Any>): Any
    
    /**
     * 지정된 ID의 에디터에 여러 텍스트 편집 작업을 일괄 적용합니다.
     * @param id 에디터의 고유 ID
     * @param modelVersionId 편집을 적용할 문서의 버전 ID (동시성 제어용)
     * @param edits 적용할 편집 작업(삽입, 삭제, 교체) 목록
     * @param opts 적용 옵션 (예: 실행 취소 그룹화)
     * @return 성공 여부
     */
    fun tryApplyEdits(id: String, modelVersionId: Int, edits: List<Any>, opts: Any?): Boolean
    
    /**
     * 지정된 ID의 에디터에 코드 스니펫을 삽입합니다.
     * @param id 에디터의 고유 ID
     * @param modelVersionId 문서 버전 ID
     * @param template 삽입할 스니펫 템플릿 문자열
     * @param selections 스니펫을 삽입할 위치(선택 영역) 목록
     * @param opts 실행 취소 옵션
     * @return 성공 여부
     */
    fun tryInsertSnippet(id: String, modelVersionId: Int, template: String, selections: List<Any>, opts: Any?): Boolean
    
    /**
     * 지정된 ID의 에디터에 대한 diff 정보를 가져옵니다. (예: Git 변경사항)
     * @param id 에디터의 고유 ID
     * @return Diff 정보
     */
    fun getDiffInformation(id: String): Any?
}

/**
 * `MainThreadTextEditorsShape` 인터페이스의 구현 클래스입니다.
 * 현재는 일부 기능만 구현되어 있으며, 대부분의 메소드는 로깅만 수행합니다.
 */
class MainThreadTextEditors(var project: Project) : MainThreadTextEditorsShape {
    private val logger = Logger.getInstance(MainThreadTextEditors::class.java)

    override suspend fun tryShowTextDocument(resource: Map<String, Any?>, options: Any?): Any? {
        logger.info("텍스트 문서 표시 시도: resource=$resource, options=$options")
        val path = resource["path"] as String? ?: ""

        // 가상 파일 시스템을 새로고침하여 최신 파일 상태를 반영합니다.
        val vfs = LocalFileSystem.getInstance()
        vfs.refreshIoFiles(listOf(File(path)))
        
        val resourceURI = createURI(resource)
        // EditorAndDocManager 서비스를 통해 에디터를 엽니다.
        val editorHandle = project.getService(EditorAndDocManager::class.java).openEditor(resourceURI)
        logger.info("텍스트 문서 표시 시도 완료: resource=$resource")
        return editorHandle.id
    }
    
    override fun registerTextEditorDecorationType(extensionId: Map<String, String>, key: String, options: Any) {
        logger.info("텍스트 에디터 데코레이션 타입 등록: extensionId=$extensionId, key=$key, options=$options")
    }

    override fun removeTextEditorDecorationType(key: String) {
        logger.info("텍스트 에디터 데코레이션 타입 제거: $key")
    }

    override fun tryShowEditor(id: String, position: Any?): Any {
        logger.info("에디터 표시 시도: id=$id, position=$position")
        return Unit
    }

    override fun tryHideEditor(id: String): Any {
        logger.info("에디터 숨기기 시도: $id")
        return Unit
    }

    override fun trySetOptions(id: String, options: Any): Any {
        logger.info("옵션 설정 시도: id=$id, options=$options")
        return Unit
    }

    override fun trySetDecorations(id: String, key: String, ranges: List<Any>): Any {
        logger.info("데코레이션 설정 시도: id=$id, key=$key, ranges=${ranges.size}")
        return Unit
    }

    override fun trySetDecorationsFast(id: String, key: String, ranges: List<Any>): Any {
        logger.info("데코레이션 빠르게 설정 시도: id=$id, key=$key, ranges=${ranges.size}")
        return Unit
    }

    override fun tryRevealRange(id: String, range: Map<String,Any?>, revealType: Int): Any {
        logger.info("범위 표시 시도: id=$id, range=$range, revealType=$revealType")
        val handle = project.getService(EditorAndDocManager::class.java).getEditorHandleById(id)
        handle?.let {
            val rang = createRanges(range)
            // 에디터 핸들을 통해 실제 범위 표시를 요청합니다.
            handle.revealRange(rang)
        }
        return Unit
    }

    /**
     * Map 형태의 데이터를 `Range` 데이터 클래스로 변환하는 헬퍼 함수입니다.
     */
    private fun createRanges(range: Map<String,Any?>): Range {
        val startLineNumber = (range["startLineNumber"] as? Number)?.toInt() ?: 0
        val startColumn = (range["startColumn"] as? Number)?.toInt() ?: 0
        val endLineNumber = (range["endLineNumber"] as? Number)?.toInt() ?: startLineNumber
        val endColumn = (range["endColumn"] as? Number)?.toInt() ?: startColumn
        return Range(startLineNumber, startColumn, endLineNumber, endColumn)
    }

    override fun trySetSelections(id: String, selections: List<Any>): Any {
        logger.info("선택 영역 설정 시도: id=$id, selections=$selections")
        return Unit
    }

    override fun tryApplyEdits(id: String, modelVersionId: Int, edits: List<Any>, opts: Any?): Boolean {
        logger.info("편집 적용 시도: id=$id, modelVersionId=$modelVersionId, edits=$edits, opts=$opts")
        return true
    }

    override fun tryInsertSnippet(id: String, modelVersionId: Int, template: String, selections: List<Any>, opts: Any?): Boolean {
        logger.info("스니펫 삽입 시도: id=$id, modelVersionId=$modelVersionId, template=$template, selections=$selections, opts=$opts")
        return true
    }

    override fun getDiffInformation(id: String): Any? {
        logger.info("Diff 정보 가져오기: $id")
        return null
    }

    override fun dispose() {
        logger.info("Dispose MainThreadTextEditors")
    }
}
