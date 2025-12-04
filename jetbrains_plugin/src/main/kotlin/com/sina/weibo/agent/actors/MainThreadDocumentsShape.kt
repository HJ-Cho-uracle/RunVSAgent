// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.sina.weibo.agent.editor.EditorAndDocManager
import com.sina.weibo.agent.editor.createURI
import com.sina.weibo.agent.service.DocumentSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * IntelliJ 메인 스레드에서 문서(Document) 관련 작업을 처리하기 위한 인터페이스입니다.
 * 문서를 생성, 열기, 저장하는 기능을 정의합니다.
 */
interface MainThreadDocumentsShape {
    /**
     * 새로운 문서를 생성하려고 시도합니다.
     * @param options 문서 생성에 필요한 옵션 (예: 내용, 언어 등)
     * @return 생성된 문서의 정보를 담은 Map
     */
    suspend fun tryCreateDocument(options: Map<String, Any?>?): Map<String, Any?>

    /**
     * 지정된 URI의 문서를 열려고 시도합니다.
     * @param uri 열고자 하는 문서의 URI 정보
     * @param options 문서 열기에 필요한 추가 옵션
     * @return 열린 문서의 정보를 담은 Map
     */
    suspend fun tryOpenDocument(uri: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?>

    /**
     * 지정된 URI의 문서를 저장하려고 시도합니다.
     * @param uri 저장할 문서의 URI 정보
     * @return 저장 성공 여부
     */
    suspend fun trySaveDocument(uri: Map<String, Any?>): Boolean

    /**
     * VSCode 타입 시스템의 문제를 우회하기 위한 오버로드 함수입니다.
     * 문자열 인자가 실수로 `options: {}` 객체로 처리되는 것을 방지합니다.
     */
    suspend fun tryOpenDocument(map: Map<String, Any?>, options: String?): Map<String, Any?>
}

/**
 * `MainThreadDocumentsShape` 인터페이스의 구현 클래스입니다.
 * 문서의 생성, 열기, 저장을 처리하고, 문서 저장 이벤트를 감지하여 Extension Host와 상태를 동기화합니다.
 *
 * @property project 현재 IntelliJ 프로젝트 컨텍스트
 */
class MainThreadDocuments(var project: Project) : MainThreadDocumentsShape {
    private val logger = Logger.getInstance(MainThreadDocuments::class.java)

    // IntelliJ의 메시지 버스에 연결하기 위한 커넥션 객체
    private var messageBusConnection: MessageBusConnection? = null

    // 문서 동기화 로직을 처리하는 서비스
    private val documentSyncService = DocumentSyncService(project)

    /** 이 인스턴스의 생명주기에 맞춰 관리되는 코루틴 스코프. dispose() 시 취소됩니다. */
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 클래스 초기화 시 문서 저장 이벤트 리스너를 설정합니다.
        setupDocumentSaveListener()
    }

    /**
     * IntelliJ의 메시지 버스를 사용하여 문서 저장 이벤트를 감지하는 리스너를 등록합니다.
     */
    private fun setupDocumentSaveListener() {
        try {
            // 어플리케이션의 메시지 버스에 연결합니다.
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect()

            // 문서 저장 이벤트(FileDocumentManagerListener)를 구독합니다.
            messageBusConnection?.subscribe(
                FileDocumentManagerListener.TOPIC,
                object : FileDocumentManagerListener {
                    // 문서가 저장되기 직전에 호출됩니다.
                    override fun beforeDocumentSaving(document: Document) {
                        handleDocumentSaving(document)
                    }
                },
            )
            logger.info("문서 저장 리스너가 성공적으로 등록되었습니다.")
        } catch (e: Exception) {
            logger.error("문서 저장 리스너 설정에 실패했습니다.", e)
        }
    }

    /**
     * 문서 저장 이벤트가 발생했을 때 실제 처리 로직을 수행합니다.
     * @param document 저장된 문서 객체
     */
    private fun handleDocumentSaving(document: Document) {
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        logger.info("문서 저장 이벤트 처리 시작: ${virtualFile?.path}")

        if (virtualFile != null && documentSyncService.shouldHandleFileEvent(virtualFile)) {
            // 이 인스턴스 전용 코루틴 스코프에서 비동기 작업을 실행합니다.
            coroutineScope.launch {
                try {
                    // 저장 작업이 완전히 완료될 시간을 벌기 위해 잠시 대기합니다.
                    delay(50)
                    if (!project.isDisposed) {
                        // 문서 상태를 Extension Host와 동기화합니다.
                        documentSyncService.syncDocumentStateOnSave(virtualFile, document)
                    }
                } catch (e: ProcessCanceledException) {
                    // 프로젝트가 종료되는 등 정상적인 취소 상황에서는 무시합니다.
                    logger.debug("프로젝트가 종료되어 문서 저장 이벤트 처리가 취소되었습니다.")
                } catch (e: Exception) {
                    logger.error("문서 저장 이벤트 처리 중 오류가 발생했습니다.", e)
                }
            }
        }
    }

    override suspend fun tryCreateDocument(options: Map<String, Any?>?): Map<String, Any?> {
        logger.info("tryCreateDocument 호출됨: $options")
        // TODO: 실제 문서 생성 로직 구현 필요
        return mapOf()
    }

    override suspend fun tryOpenDocument(map: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?> {
        val uri = createURI(map)
        logger.info("tryOpenDocument 호출됨: ${uri.path}")

        // 파일이 존재하지 않으면 새로 생성합니다.
        val file = File(uri.path)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        // EditorAndDocManager 서비스를 통해 문서를 엽니다.
        project.getService(EditorAndDocManager::class.java).openDocument(uri)

        logger.info("tryOpenDocument 실행 완료: ${uri.path}")
        return map
    }

    override suspend fun tryOpenDocument(map: Map<String, Any?>, options: String?): Map<String, Any?> {
        // 타입 문제 우회를 위한 오버로드 함수. 실제 로직은 다른 오버로드 함수에 위임합니다.
        return tryOpenDocument(map, HashMap())
    }

    override suspend fun trySaveDocument(map: Map<String, Any?>): Boolean {
        val uri = createURI(map)
        logger.info("trySaveDocument 호출됨: ${uri.path}")

        // 해당 URI의 에디터 핸들을 찾아 'dirty' 상태(수정되었지만 저장되지 않은 상태)를 false로 변경합니다.
        project.getService(EditorAndDocManager::class.java).getEditorHandleByUri(uri, true)?.updateDocumentDirty(false) ?: run {
            logger.info("trySaveDocument: ${uri.path} 에 해당하는 핸들을 찾을 수 없습니다.")
            return false
        }
        logger.info("trySaveDocument 실행 완료: ${uri.path}")
        return true
    }

    /**
     * 리소스를 해제합니다. 메시지 버스 연결을 끊고 코루틴 스코프를 취소합니다.
     */
    fun dispose() {
        try {
            messageBusConnection?.disconnect()
            messageBusConnection = null
            documentSyncService.dispose()
            coroutineScope.cancel() // 모든 자식 코루틴을 취소합니다.
            logger.info("문서 저장 리스너가 해제되었습니다.")
        } catch (e: Exception) {
            logger.error("문서 저장 리스너 해제 중 오류가 발생했습니다.", e)
        }
    }
}
