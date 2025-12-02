// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.sina.weibo.agent.editor.EditorAndDocManager
import com.sina.weibo.agent.editor.EditorHolder
import com.sina.weibo.agent.editor.WorkspaceEdit
import com.sina.weibo.agent.ipc.proxy.SerializableObjectWithBuffers
import java.io.File
import java.nio.file.Files

/**
 * IntelliJ의 메인 스레드에서 대량의 편집 작업을 처리하기 위한 인터페이스입니다.
 * 이 인터페이스는 VSCode의 Extension Host로부터 전달받은 여러 파일 및 텍스트 변경사항을
 * 작업 공간(Workspace)에 일괄적으로 적용하는 기능을 제공합니다.
 * RPC(Remote Procedure Call)를 통해 Extension Host와 통신하는 액터(Actor)의 일부로 사용됩니다.
 */
interface MainThreadBulkEditsShape {
    /**
     * 작업 공간 편집(Workspace Edit)을 적용합니다.
     *
     * @param workspaceEditDto 직렬화된 작업 공간 편집 데이터입니다. 파일 생성/삭제/이름 변경 및 텍스트 편집 정보를 담고 있습니다.
     * @param undoRedoGroupId (선택 사항) 실행 취소/다시 실행 그룹을 위한 ID입니다. 여러 편집을 하나의 작업으로 묶을 때 사용됩니다.
     * @param respectAutoSaveConfig (선택 사항) 자동 저장 설정을 존중할지 여부입니다.
     * @return 모든 편집이 성공적으로 적용되었으면 true, 하나라도 실패하면 false를 반환합니다.
     */
    suspend fun tryApplyWorkspaceEdit(workspaceEditDto: SerializableObjectWithBuffers<Any>, undoRedoGroupId: Int?, respectAutoSaveConfig: Boolean?): Boolean
}

/**
 * `MainThreadBulkEditsShape` 인터페이스의 구현 클래스입니다.
 * 파일 생성/삭제/이름 변경과 같은 파일 시스템 작업과 문서의 텍스트 편집을 실제로 처리합니다.
 *
 * @property project 현재 IntelliJ 프로젝트 컨텍스트입니다. 파일 및 에디터 관련 서비스에 접근하기 위해 사용됩니다.
 */
class MainThreadBulkEdits(val project: Project) : MainThreadBulkEditsShape {
    private val logger = Logger.getInstance(MainThreadBulkEditsShape::class.java)
    
    /**
     * 전달받은 `workspaceEditDto`를 파싱하여 파일 작업과 텍스트 편집을 순차적으로 처리합니다.
     */
    override suspend fun tryApplyWorkspaceEdit(workspaceEditDto: SerializableObjectWithBuffers<Any>, undoRedoGroupId: Int?, respectAutoSaveConfig: Boolean?): Boolean {
        // DTO에서 JSON 문자열을 추출하여 WorkspaceEdit 객체로 변환합니다.
        val json = workspaceEditDto.value as String
        logger.info("[Bulk Edit] 작업 시작: $json")
        val cto = WorkspaceEdit.from(json)
        var allSuccess = true // 모든 작업의 성공 여부를 추적하는 플래그

        // --- 1. 파일 관련 작업 처리 (이름 변경, 삭제, 생성) ---
        // 파일 I/O는 메인 스레드를 차단할 수 있으므로, VFS 갱신 등 일부 작업은 백그라운드 스레드에서 처리합니다.
        cto.files.forEach { fileEdit ->
            // 파일 이름 변경
            if (fileEdit.oldResource != null && fileEdit.newResource != null) {
                val oldResource = File(fileEdit.oldResource.path)
                val newResource = File(fileEdit.newResource.path)
                try {
                    Files.move(oldResource.toPath(), newResource.toPath())
                    // IntelliJ의 가상 파일 시스템(VFS)에 변경사항을 알립니다.
                    ApplicationManager.getApplication().executeOnPooledThread {
                        LocalFileSystem.getInstance().refreshIoFiles(listOf(oldResource, newResource))
                    }
                    logger.info("[Bulk Edit] 파일 이름 변경 성공: ${oldResource.path} -> ${newResource.path}")
                } catch (e: Exception) {
                    logger.error("[Bulk Edit] 파일 이름 변경 실패: ${oldResource.path} -> ${newResource.path}", e)
                    allSuccess = false
                }
            } 
            // 파일 삭제
            else if (fileEdit.oldResource != null) {
                val oldResource = File(fileEdit.oldResource.path)
                try {
                    oldResource.delete()
                    ApplicationManager.getApplication().executeOnPooledThread {
                        LocalFileSystem.getInstance().refreshIoFiles(listOf(oldResource.parentFile))
                    }
                    logger.info("[Bulk Edit] 파일 삭제 성공: ${oldResource.path}")
                } catch (e: Exception) {
                    logger.error("[Bulk Edit] 파일 삭제 실패: ${oldResource.path}", e)
                    allSuccess = false
                }
            } 
            // 파일 생성
            else if (fileEdit.newResource != null) {
                val newResource = File(fileEdit.newResource.path)
                try {
                    val parentDir = newResource.parentFile
                    if (!parentDir.exists()) {
                        parentDir.mkdirs()
                    }
                    // 파일 내용이 있으면 함께 기록하고, 없으면 빈 파일만 생성합니다.
                    if (fileEdit.options?.contents != null) {
                        Files.write(newResource.toPath(), fileEdit.options.contents.toByteArray(Charsets.UTF_8))
                    } else {
                        newResource.createNewFile()
                    }
                    ApplicationManager.getApplication().executeOnPooledThread {
                        LocalFileSystem.getInstance().refreshIoFiles(listOf(newResource))
                    }
                    logger.info("[Bulk Edit] 파일 생성 성공: ${newResource.path}")
                } catch (e: Exception) {
                    logger.error("[Bulk Edit] 파일 생성 실패: ${newResource.path}", e)
                    allSuccess = false
                }
            }
        }

        // --- 2. 텍스트 편집 작업 처리 ---
        cto.texts.forEach { textEdit ->
            logger.info("[Bulk Edit] 텍스트 편집 처리 시작: ${textEdit.resource.path}")
            // 'file://' 스키마만 지원합니다. (예: 'untitled://' 등은 지원하지 않음)
            if (textEdit.resource.scheme != "file") {
                logger.error("[Bulk Edit] 지원하지 않는 리소스 스키마: ${textEdit.resource.path}")
                allSuccess = false
                return@forEach
            }
            
            var handle: EditorHolder? = null
            try {
                // URI에 해당하는 에디터 핸들(EditorHolder)을 가져옵니다.
                // 핸들이 없으면 Extension Host와 동기화하여 새로 생성합니다.
                handle = project.getService(EditorAndDocManager::class.java).getEditorHandleByUri(textEdit.resource, true)
                if (handle == null) {
                    handle = project.getService(EditorAndDocManager::class.java).sync2ExtHost(textEdit.resource, true)
                }
            } catch (e: Exception) {
                logger.info("[Bulk Edit] 에디터 핸들을 가져오는 데 실패: ${textEdit.resource.path}", e)
            }

            if (handle == null) {
                logger.info("[Bulk Edit] 에디터 핸들을 찾을 수 없음: ${textEdit.resource.path}")
                allSuccess = false
                return@forEach
            }

            try {
                // 에디터 핸들을 통해 실제 텍스트 편집을 적용합니다.
                val result = handle.applyEdit(textEdit)
                if (!result) {
                    logger.info("[Bulk Edit] 텍스트 편집 적용 실패: ${textEdit.resource.path}")
                    allSuccess = false
                } else {
                    logger.info("[Bulk Edit] 파일 내용 업데이트 성공: ${textEdit.resource.path}")
                }
            } catch (e: Exception) {
                logger.error("[Bulk Edit] 텍스트 편집 적용 중 예외 발생: ${textEdit.resource.path}", e)
                allSuccess = false
            }
        }
        
        return allSuccess
    }
}
