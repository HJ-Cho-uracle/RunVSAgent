// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.editor.EditorAndDocManager
import com.sina.weibo.agent.editor.ModelAddedData
import com.sina.weibo.agent.editor.createURI
import com.sina.weibo.agent.ipc.proxy.interfaces.ExtHostDocumentsProxy

/**
 * 문서 동기화 서비스 클래스입니다.
 * IntelliJ 문서의 변경 사항(특히 저장 이벤트)을 감지하고,
 * Extension Host와 문서 상태를 동기화하는 역할을 합니다.
 *
 * @param project 현재 IntelliJ 프로젝트
 */
class DocumentSyncService(private val project: Project) {

    private val logger = Logger.getInstance(DocumentSyncService::class.java)
    private var extHostDocumentsProxy: ExtHostDocumentsProxy? = null

    /**
     * `ExtHostDocumentsProxy` 인스턴스를 가져옵니다.
     * 프록시가 아직 초기화되지 않았으면 `PluginContext`를 통해 초기화합니다.
     */
    private fun getExtHostDocumentsProxy(): ExtHostDocumentsProxy? {
        if (extHostDocumentsProxy == null) {
            try {
                val protocol = PluginContext.getInstance(project).getRPCProtocol()
                extHostDocumentsProxy = protocol?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostDocuments)
                logger.debug("DocumentSyncService에서 ExtHostDocumentsProxy 초기화됨")
            } catch (e: Exception) {
                logger.error("DocumentSyncService에서 ExtHostDocumentsProxy 가져오기 실패", e)
            }
        }
        return extHostDocumentsProxy
    }

    /**
     * 문서 저장 시 문서 상태를 Extension Host와 동기화합니다.
     * @param virtualFile 저장된 문서의 `VirtualFile`
     * @param document 저장된 문서의 `Document` 객체
     */
    suspend fun syncDocumentStateOnSave(virtualFile: VirtualFile, document: Document) {
        logger.info("문서 저장 상태 동기화 시작: ${virtualFile.path}")
        try {
            // VirtualFile로부터 URI 객체를 생성합니다.
            val uriMap = mapOf(
                "scheme" to "file",
                "authority" to "",
                "path" to virtualFile.path,
                "query" to "",
                "fragment" to ""
            )
            val uri = createURI(uriMap)

            // EditorAndDocManager를 통해 문서 상태를 관리합니다.
            val editorAndDocManager = project.getService(EditorAndDocManager::class.java)

            // 해당 URI에 연결된 모든 EditorHolder를 찾습니다.
            val editorHandles = editorAndDocManager.getEditorHandleByUri(uri)

            if (editorHandles.isNotEmpty()) {
                // 해당 에디터가 존재하면 상태를 업데이트합니다.
                for (handle in editorHandles) {
                    // 최신 문서 내용을 읽어옵니다.
                    val text = ApplicationManager.getApplication().runReadAction<String> {
                        document.text
                    }

                    // 업데이트된 문서 데이터를 생성합니다.
                    val updatedDocument = ModelAddedData(
                        uri = handle.document.uri,
                        versionId = handle.document.versionId + 1, // 버전 ID 증가
                        lines = text.lines(),
                        EOL = handle.document.EOL,
                        languageId = handle.document.languageId,
                        isDirty = false, // 저장 후에는 dirty 상태가 아님
                        encoding = handle.document.encoding
                    )

                    // EditorHolder의 문서 상태를 업데이트합니다.
                    handle.document = updatedDocument

                    // Extension Host에 상태 동기화를 트리거합니다.
                    editorAndDocManager.updateDocumentAsync(updatedDocument)
                }

                // Extension Host에 문서 저장 이벤트를 전송합니다.
                getExtHostDocumentsProxy()?.let { proxy ->
                    proxy.acceptModelSaved(uri)
                    logger.info("문서 저장 이벤트 및 상태가 Extension Host에 동기화됨: ${virtualFile.path}")
                }
            }
        } catch (e: Exception) {
            logger.error("문서 저장 상태 동기화 중 오류 발생", e)
        }
    }

    /**
     * 주어진 `VirtualFile`이 파일 시스템 이벤트 처리에 적합한지 필터링합니다.
     * 디렉터리, 로컬 파일 시스템에 없는 파일, 특정 빌드/설정 파일, 너무 큰 파일 등을 제외합니다.
     * @param virtualFile 확인할 `VirtualFile`
     * @return 이벤트 처리에 적합하면 true
     */
    fun shouldHandleFileEvent(virtualFile: VirtualFile): Boolean {
        return !virtualFile.isDirectory && // 디렉터리가 아님
                virtualFile.isInLocalFileSystem && // 로컬 파일 시스템에 있음
                !virtualFile.path.contains("/.idea/") && // IDE 설정 파일 제외
                !virtualFile.path.contains("/target/") && // 빌드 출력 파일 제외
                !virtualFile.path.contains("/build/") &&
                !virtualFile.path.contains("/node_modules/") &&
                virtualFile.extension != null && // 확장자가 있어야 함
                !isTooLargeForSyncing(virtualFile) && // 너무 큰 파일 제외
                !isForSimpleWidget(virtualFile) // 특정 위젯 파일 제외
    }

    /**
     * 파일이 동기화하기에 너무 큰지 확인합니다.
     * VS Code 구현을 참조하여 2MB를 초과하는 파일을 제외합니다.
     * @param virtualFile 확인할 `VirtualFile`
     * @return 파일이 너무 크면 true
     */
    private fun isTooLargeForSyncing(virtualFile: VirtualFile): Boolean {
        return try {
            val maxSizeBytes = 2 * 1024 * 1024L // 2MB
            virtualFile.length > maxSizeBytes
        } catch (e: Exception) {
            logger.warn("파일 크기 확인 실패: ${virtualFile.path}", e)
            false
        }
    }

    /**
     * 파일이 간단한 위젯 사용을 위한 것인지 확인합니다.
     * 특정 목적의 파일(예: 임시 파일, 바이너리, 이미지 등)을 제외합니다.
     * @param virtualFile 확인할 `VirtualFile`
     * @return 파일이 간단한 위젯용이면 true
     */
    private fun isForSimpleWidget(virtualFile: VirtualFile): Boolean {
        return try {
            val fileName = virtualFile.name.lowercase()
            val extension = virtualFile.extension?.lowercase()
            
            // 임시 파일, 캐시 파일, 백업 파일 등
            fileName.startsWith(".") ||
            fileName.endsWith(".tmp") ||
            fileName.endsWith(".temp") ||
            fileName.endsWith(".bak") ||
            fileName.endsWith(".backup") ||
            fileName.contains("~") ||
            // 바이너리 파일 확장자
            extension in setOf(
                "exe", "dll", "so", "dylib", "bin", "obj", "o", "a", "lib",
                "zip", "tar", "gz", "rar", "7z", "jar", "war", "ear",
                "png", "jpg", "jpeg", "gif", "bmp", "ico", "tiff",
                "mp3", "mp4", "avi", "mov", "wav", "flv", "wmv",
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
            ) ||
            // 특수 경로
            virtualFile.path.contains("/.git/") ||
            virtualFile.path.contains("/.svn/") ||
            virtualFile.path.contains("/.hg/") ||
            virtualFile.path.contains("/vendor/") ||
            virtualFile.path.contains("/dist/") ||
            virtualFile.path.contains("/out/")
        } catch (e: Exception) {
            logger.warn("파일이 간단한 위젯용인지 확인 실패: ${virtualFile.path}", e)
            false
        }
    }

    /**
     * 리소스를 해제합니다.
     */
    fun dispose() {
        extHostDocumentsProxy = null
    }
}
