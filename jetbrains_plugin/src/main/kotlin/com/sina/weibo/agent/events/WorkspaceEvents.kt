// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.events

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 파일 변경 유형을 나타내는 열거형입니다.
 */
enum class FileChangeType {
    CREATED, // 파일 생성됨
    UPDATED, // 파일 수정됨
    DELETED, // 파일 삭제됨
}

/**
 * 파일 시스템 항목의 유형을 나타내는 열거형입니다.
 */
enum class FileSystemItemType {
    FILE, // 일반 파일
    DIRECTORY, // 디렉터리
}

/**
 * 작업 공간 파일 변경 이벤트를 나타내는 객체입니다.
 * `EventType<WorkspaceFileChangeData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WorkspaceFileChangeEvent : EventType<WorkspaceFileChangeData>

/**
 * 작업 공간 디렉터리 변경 이벤트를 나타내는 객체입니다.
 * `EventType<WorkspaceFileChangeData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WorkspaceDirectoryChangeEvent : EventType<WorkspaceFileChangeData>

/**
 * 작업 공간 파일 변경 시 전달되는 데이터를 담는 데이터 클래스입니다.
 * @property file 변경된 `VirtualFile` 객체
 * @property changeType 변경의 종류 (`FileChangeType`)
 * @property timestamp 이벤트 발생 시간의 타임스탬프
 * @property itemType 변경된 항목의 유형 (`FileSystemItemType`)
 */
data class WorkspaceFileChangeData(
    val file: VirtualFile,
    val changeType: FileChangeType,
    val timestamp: Long = System.currentTimeMillis(),
    val itemType: FileSystemItemType = if (file.isDirectory) FileSystemItemType.DIRECTORY else FileSystemItemType.FILE,
)

/**
 * 여러 파일 변경 이벤트를 나타내는 객체입니다.
 * `EventType<WorkspaceFilesChangeData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WorkspaceFilesChangeEvent : EventType<WorkspaceFilesChangeData>

/**
 * 여러 디렉터리 변경 이벤트를 나타내는 객체입니다.
 * `EventType<WorkspaceFilesChangeData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WorkspaceDirectoriesChangeEvent : EventType<WorkspaceFilesChangeData>

/**
 * 여러 파일 변경 시 전달되는 데이터를 담는 데이터 클래스입니다.
 * @property changes 변경된 파일들의 `WorkspaceFileChangeData` 리스트
 */
data class WorkspaceFilesChangeData(
    val changes: List<WorkspaceFileChangeData>,
)

/**
 * 작업 공간 루트 변경 시 전달되는 데이터를 담는 데이터 클래스입니다.
 * @param project 변경이 발생한 프로젝트
 * @param oldPath 이전 작업 공간 루트 경로
 * @param newPath 새로운 작업 공간 루트 경로
 */
data class WorkspaceRootChangeData(
    val project: Project,
    val oldPath: String?,
    val newPath: String,
)

/**
 * 작업 공간 루트 변경 이벤트를 나타내는 객체입니다.
 * `EventType<WorkspaceRootChangeData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WorkspaceRootChangeEvent : EventType<WorkspaceRootChangeData>
