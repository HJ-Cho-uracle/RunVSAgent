// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.model

import com.sina.weibo.agent.util.URI

/**
 * 작업 공간의 기본 데이터를 나타내는 데이터 클래스입니다.
 * VSCode의 `IStaticWorkspaceData`에 해당합니다.
 */
data class StaticWorkspaceData(
    val id: String, // 작업 공간의 고유 ID
    val name: String, // 작업 공간의 이름
    val transient: Boolean? = null, // 임시 작업 공간인지 여부 (선택 사항)
    val configuration: URI? = null, // 작업 공간 설정 파일의 URI (선택 사항)
    val isUntitled: Boolean? = null, // 제목 없는(Untitled) 작업 공간인지 여부 (선택 사항)
)

/**
 * 작업 공간 내의 단일 폴더를 나타내는 데이터 클래스입니다.
 * VSCode의 `IWorkspaceData.folders` 요소에 해당합니다.
 */
data class WorkspaceFolder(
    val uri: URI, // 폴더의 URI
    val name: String, // 폴더의 이름
    val index: Int, // 폴더의 인덱스 (순서)
)

/**
 * 작업 공간의 전체 데이터를 나타내는 데이터 클래스입니다.
 * VSCode의 `IWorkspaceData`에 해당합니다.
 */
data class WorkspaceData(
    val id: String, // 작업 공간의 고유 ID
    val name: String, // 작업 공간의 이름
    val transient: Boolean? = null, // 임시 작업 공간인지 여부 (선택 사항)
    val configuration: URI? = null, // 작업 공간 설정 파일의 URI (선택 사항)
    val isUntitled: Boolean? = null, // 제목 없는 작업 공간인지 여부 (선택 사항)
    val folders: List<WorkspaceFolder> = emptyList(), // 작업 공간에 포함된 폴더 목록
) {
    /**
     * `StaticWorkspaceData`와 폴더 목록을 사용하여 `WorkspaceData`를 생성하는 보조 생성자입니다.
     */
    constructor(staticData: StaticWorkspaceData, folders: List<WorkspaceFolder> = emptyList()) : this(
        id = staticData.id,
        name = staticData.name,
        transient = staticData.transient,
        configuration = staticData.configuration,
        isUntitled = staticData.isUntitled,
        folders = folders,
    )
}
