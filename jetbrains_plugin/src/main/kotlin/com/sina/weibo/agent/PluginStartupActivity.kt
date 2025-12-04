package com.sina.weibo.agent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.MessageBusConnection
import com.sina.weibo.agent.core.PluginContext
import com.sina.weibo.agent.core.ServiceProxyRegistry
import com.sina.weibo.agent.core.WorkspaceManager
import com.sina.weibo.agent.events.FileChangeType
import com.sina.weibo.agent.events.ProjectEventBus
import com.sina.weibo.agent.events.WorkspaceDirectoriesChangeEvent
import com.sina.weibo.agent.events.WorkspaceDirectoryChangeEvent
import com.sina.weibo.agent.events.WorkspaceFileChangeData
import com.sina.weibo.agent.events.WorkspaceFileChangeEvent
import com.sina.weibo.agent.events.WorkspaceFilesChangeData
import com.sina.weibo.agent.events.WorkspaceFilesChangeEvent
import com.sina.weibo.agent.events.WorkspaceRootChangeData
import com.sina.weibo.agent.events.WorkspaceRootChangeEvent
import com.sina.weibo.agent.ipc.proxy.interfaces.FileSystemEvents
import java.util.concurrent.ConcurrentHashMap

private val logger = Logger.getInstance(PluginStartupActivity::class.java)
val vfsConnections = ConcurrentHashMap<Project, MessageBusConnection>()
val projectWorkspacePaths = ConcurrentHashMap<Project, String>()

/**
 * 파일 리스너를 등록합니다.
 * @param project 리스너를 등록할 프로젝트
 */
fun registerFileListener(project: Project) {
    if (vfsConnections.containsKey(project)) {
        logger.info("프로젝트 '${project.name}'에 대한 파일 리스너가 이미 존재하여 등록을 건너뜁니다.")
        return
    }

    logger.info("프로젝트 '${project.name}'에 대한 파일 리스너 등록")

    try {
        val connection = project.messageBus.connect()

        // 가상 파일 시스템(VFS) 변경 이벤트를 구독합니다.
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    processBulkFileEvents(events, project)
                }
            },
        )

        // 나중에 정리하기 위해 연결을 저장합니다.
        vfsConnections[project] = connection
    } catch (e: Exception) {
        logger.error("프로젝트 '${project.name}'에 대한 파일 리스너 등록 실패", e)
    }
}

/**
 * 대량 파일 이벤트를 처리합니다.
 * @param events 파일 이벤트 목록
 * @param project 관련 프로젝트
 */
fun processBulkFileEvents(events: List<VFileEvent>, project: Project) {
    if (events.isEmpty()) {
        return
    }

    val fileChanges = mutableListOf<WorkspaceFileChangeData>()
    val directoryChanges = mutableListOf<WorkspaceFileChangeData>()

    events.forEach { event ->
        val file = when (event) {
            is VFileCreateEvent -> event.file
            is VFileDeleteEvent -> event.file
            is VFileMoveEvent -> event.file
            is VFileCopyEvent -> event.file
            is VFilePropertyChangeEvent -> event.file
            is VFileContentChangeEvent -> event.file
            else -> null
        }

        if (file != null) {
            // 변경 유형 결정
            val changeType = when (event) {
                is VFileCreateEvent -> FileChangeType.CREATED
                is VFileDeleteEvent -> FileChangeType.DELETED
                else -> FileChangeType.UPDATED
            }

            // 프로젝트와 관련 없는 파일 또는 디렉터리 건너뛰기
            if (isRelevantFileSystemItem(file)) {
                val changeData = WorkspaceFileChangeData(file, changeType)

                // 유형별로 변경 사항 저장
                if (file.isDirectory) {
                    directoryChanges.add(changeData)
                    triggerDirectoryChangeEvent(project, changeData) // 각 디렉터리 변경에 대한 이벤트 트리거
                } else {
                    fileChanges.add(changeData)
                    triggerFileChangeEvent(project, changeData) // 각 파일 변경에 대한 이벤트 트리거
                }
            }
        }
    }

    // 대량 파일 변경 이벤트 트리거
    if (fileChanges.isNotEmpty()) {
        triggerBulkFileChangeEvent(fileChanges, project)
    }

    // 대량 디렉터리 변경 이벤트 트리거
    if (directoryChanges.isNotEmpty()) {
        triggerBulkDirectoryChangeEvent(directoryChanges, project)
    }
}

/**
 * 단일 파일 변경 이벤트를 트리거합니다.
 * @param fileChangeData 파일 변경 데이터
 */
private fun triggerFileChangeEvent(project: Project, fileChangeData: WorkspaceFileChangeData) {
    logger.debug("파일 변경됨: ${fileChangeData.file.path}, 유형: ${fileChangeData.changeType}")

    // EventBus를 통해 단일 파일 변경 이벤트를 전송합니다.
    project.getService(ProjectEventBus::class.java).emitInApplication(WorkspaceFileChangeEvent, fileChangeData)
}

/**
 * 단일 디렉터리 변경 이벤트를 트리거합니다.
 * @param directoryChangeData 디렉터리 변경 데이터
 */
private fun triggerDirectoryChangeEvent(project: Project, directoryChangeData: WorkspaceFileChangeData) {
    logger.debug("디렉터리 변경됨: ${directoryChangeData.file.path}, 유형: ${directoryChangeData.changeType}")

    // EventBus를 통해 단일 디렉터리 변경 이벤트를 전송합니다.
    project.getService(ProjectEventBus::class.java).emitInApplication(WorkspaceDirectoryChangeEvent, directoryChangeData)
}

/**
 * 대량 파일 변경 이벤트를 트리거하고 Extension Host에 알립니다.
 * @param fileChanges 파일 변경 목록
 * @param project 관련 프로젝트
 */
private fun triggerBulkFileChangeEvent(fileChanges: List<WorkspaceFileChangeData>, project: Project) {
    logger.debug("대량 파일 변경, 총 ${fileChanges.size}개 파일")

    val proxy = PluginContext.getInstance(project).getRPCProtocol()?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostFileSystemEventService)
    proxy?.let {
        val createdFiles = mutableListOf<Map<String, Any?>>()
        val changedFiles = mutableListOf<Map<String, Any?>>()
        val deletedFiles = mutableListOf<Map<String, Any?>>()

        fileChanges.forEach { fileChange ->
            val uriComponents = fileToUriComponents(fileChange.file)
            when (fileChange.changeType) {
                FileChangeType.CREATED -> createdFiles.add(uriComponents)
                FileChangeType.UPDATED -> changedFiles.add(uriComponents)
                FileChangeType.DELETED -> deletedFiles.add(uriComponents)
            }
        }

        val fileSystemEvents = FileSystemEvents(
            session = fileChanges[0].timestamp.toString(),
            created = createdFiles,
            changed = changedFiles,
            deleted = deletedFiles,
        )

        it.onFileEvent(fileSystemEvents) // Extension Host에 파일 이벤트 전송
    }

    val bulkChangeData = WorkspaceFilesChangeData(fileChanges)
    project.getService(ProjectEventBus::class.java).emitInApplication(WorkspaceFilesChangeEvent, bulkChangeData)
}

/**
 * 대량 디렉터리 변경 이벤트를 트리거하고 Extension Host에 알립니다.
 * @param directoryChanges 디렉터리 변경 목록
 * @param project 관련 프로젝트
 */
private fun triggerBulkDirectoryChangeEvent(directoryChanges: List<WorkspaceFileChangeData>, project: Project) {
    logger.debug("대량 디렉터리 변경, 총 ${directoryChanges.size}개 디렉터리")

    val proxy = PluginContext.getInstance(project).getRPCProtocol()?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostFileSystemEventService)
    proxy?.let {
        val createdDirs = mutableListOf<Map<String, Any?>>()
        val changedDirs = mutableListOf<Map<String, Any?>>()
        val deletedDirs = mutableListOf<Map<String, Any?>>()

        directoryChanges.forEach { dirChange ->
            val uriComponents = fileToUriComponents(dirChange.file)
            when (dirChange.changeType) {
                FileChangeType.CREATED -> createdDirs.add(uriComponents)
                FileChangeType.UPDATED -> changedDirs.add(uriComponents)
                FileChangeType.DELETED -> deletedDirs.add(uriComponents)
            }
        }

        val fileSystemEvents = FileSystemEvents(
            session = directoryChanges[0].timestamp.toString(),
            created = createdDirs,
            changed = changedDirs,
            deleted = deletedDirs,
        )

        it.onFileEvent(fileSystemEvents) // Extension Host에 파일 이벤트 전송
    }

    val bulkChangeData = WorkspaceFilesChangeData(directoryChanges)
    project.getService(ProjectEventBus::class.java).emitInApplication(WorkspaceDirectoriesChangeEvent, bulkChangeData)
}

/**
 * `VirtualFile`을 URI 구성 요소 맵으로 변환합니다.
 * @param file `VirtualFile` 객체
 * @return URI 구성 요소 맵
 */
private fun fileToUriComponents(file: VirtualFile): Map<String, Any?> {
    return mapOf(
        "scheme" to "file",
        "path" to file.path,
        "authority" to "",
        "query" to "",
        "fragment" to "",
    )
}

/**
 * 파일 또는 디렉터리가 프로젝트와 관련이 있는지 확인합니다.
 * 숨김 파일, 임시 파일 등을 제외합니다.
 * @param file 확인할 파일 또는 디렉터리
 * @return 파일 또는 디렉터리가 프로젝트와 관련이 있으면 true
 */
private fun isRelevantFileSystemItem(file: VirtualFile): Boolean {
    // 숨김 파일 및 디렉터리 무시
    if (file.name.startsWith(".") || file.path.contains("/.")) {
        return false
    }

    // 파일인 경우 임시 파일 무시
    if (!file.isDirectory && (file.name.endsWith("~") || file.name.endsWith(".tmp"))) {
        return false
    }

    return true
}

/**
 * 작업 공간 루트 변경 이벤트를 트리거합니다.
 * @param project 이벤트가 발생한 프로젝트
 * @param oldPath 이전 작업 공간 디렉터리 경로
 * @param newPath 새 작업 공간 디렉터리 경로
 */
fun triggerWorkspaceRootChangeEvent(project: Project, oldPath: String?, newPath: String) {
    logger.debug("작업 공간 루트 변경 이벤트 트리거: ${project.name}, 이전 경로: $oldPath, 새 경로: $newPath")

    val workspaceChangeData = WorkspaceRootChangeData(project, oldPath, newPath)

    // EventBus를 통해 작업 공간 루트 변경 이벤트를 전송합니다.
    project.getService(ProjectEventBus::class.java).emitInApplication(WorkspaceRootChangeEvent, workspaceChangeData)

    // ExtHostWorkspace 프록시를 통해 Extension Host에 작업 공간 데이터 변경을 알립니다.
    val extHostWorkspace = PluginContext.getInstance(project).getRPCProtocol()?.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostWorkspace)

    val workspaceData = project.getService(WorkspaceManager::class.java).getProjectWorkspaceData(project)

    extHostWorkspace?.let {
        if (workspaceData != null) {
            logger.debug("확장 프로세스로 작업 공간 루트 변경 전송: ${workspaceData.name}, 폴더: ${workspaceData.folders.size}")
            it.acceptWorkspaceData(workspaceData)
        }
    }
}

class PluginStartupActivity : ProjectActivity {

    // 등록된 파일 리스너 연결을 기록합니다.

    override suspend fun execute(project: Project) {
        /**
         * 파일 리스너를 등록하고, 작업 공간 루트 변경 이벤트를 트리거합니다.
         */
        registerFileListener(project)
        project.basePath?.let { projectWorkspacePaths[project] = it }
        triggerWorkspaceRootChangeEvent(project, null, project.basePath ?: "")
    }
}
