// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import java.util.concurrent.CompletableFuture

/**
 * 파일 시스템 이벤트 정보를 담는 데이터 클래스입니다.
 */
data class FileSystemEvents(
    val session: String? = null,
    val created: List<Map<String, Any?>>, // 생성된 파일들의 URI 구성 요소
    val changed: List<Map<String, Any?>>, // 변경된 파일들의 URI 구성 요소
    val deleted: List<Map<String, Any?>>, // 삭제된 파일들의 URI 구성 요소
)

/**
 * 원본-대상 파일 쌍을 나타내는 데이터 클래스입니다.
 */
data class SourceTargetPair(
    val source: Map<String, Any?>? = null, // 원본 파일의 URI 구성 요소
    val target: Map<String, Any?>, // 대상 파일의 URI 구성 요소
)

/**
 * 파일 작업 참여 응답을 나타내는 데이터 클래스입니다.
 */
data class FileOperationParticipation(
    val edit: Map<String, Any?>, // 워크스페이스 편집 DTO
    val extensionNames: List<String>,
)

/**
 * 파일 작업 유형을 나타내는 열거형입니다.
 */
enum class FileOperation {
    CREATE, // 생성
    DELETE, // 삭제
    RENAME, // 이름 변경
    COPY, // 복사
    MOVE, // 이동
}

/**
 * Extension Host 파일 시스템 이벤트 서비스 프록시 인터페이스입니다.
 * VSCode의 `ExtHostFileSystemEventServiceShape`에 해당하며, Extension Host가
 * IntelliJ 플러그인의 파일 시스템 이벤트 관련 기능을 호출하기 위해 사용됩니다.
 */
interface ExtHostFileSystemEventServiceProxy {
    /**
     * 파일 이벤트 발생을 Extension Host에 알립니다.
     * @param events 파일 시스템 이벤트 정보
     */
    fun onFileEvent(events: FileSystemEvents)

    /**
     * 파일 작업이 실행되기 전에 Extension Host에 알리고, 작업 참여 응답을 받습니다.
     * @param operation 파일 작업 유형
     * @param files 원본-대상 파일 쌍 목록
     * @param timeout 타임아웃 시간
     * @param token 취소 토큰
     * @return 파일 작업 참여 응답을 담는 `CompletableFuture`
     */
    fun onWillRunFileOperation(
        operation: FileOperation,
        files: List<SourceTargetPair>,
        timeout: Int,
        token: Any?,
    ): CompletableFuture<FileOperationParticipation?>

    /**
     * 파일 작업이 실행된 후에 Extension Host에 알립니다.
     * @param operation 파일 작업 유형
     * @param files 원본-대상 파일 쌍 목록
     */
    fun onDidRunFileOperation(
        operation: FileOperation,
        files: List<SourceTargetPair>,
    )
}
