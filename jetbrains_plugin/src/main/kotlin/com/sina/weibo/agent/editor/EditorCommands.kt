// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.contents.DiffContent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.sina.weibo.agent.commands.CommandRegistry
import com.sina.weibo.agent.commands.ICommand
import com.sina.weibo.agent.util.URI
import com.sina.weibo.agent.util.URIComponents
import java.io.File

/**
 * 에디터 API 관련 명령들을 등록합니다.
 * 현재는 파일 비교를 위한 `_workbench.diff` 명령을 등록합니다.
 *
 * @param project 현재 IntelliJ 프로젝트
 * @param registry 명령을 등록할 `CommandRegistry` 인스턴스
 */
fun registerOpenEditorAPICommands(project: Project, registry: CommandRegistry) {
    registry.registerCommand(
        object : ICommand {
            override fun getId(): String {
                return "_workbench.diff" // 커맨드의 고유 ID
            }
            override fun getMethod(): String {
                return "workbench_diff" // 이 커맨드가 실행될 때 호출될 메소드 이름
            }

            override fun handler(): Any {
                return OpenEditorAPICommands(project) // 커맨드 로직을 담고 있는 핸들러 객체
            }

            override fun returns(): String? {
                return "void" // 반환 타입
            }
        },
    )
}

/**
 * 에디터 API 명령(예: Diff 에디터 열기)을 처리하는 클래스입니다.
 */
class OpenEditorAPICommands(val project: Project) {
    private val logger = Logger.getInstance(OpenEditorAPICommands::class.java)

    /**
     * 두 파일을 비교하는 Diff 에디터를 엽니다.
     * `_workbench.diff` 커맨드가 호출될 때 실행되는 실제 로직입니다.
     *
     * @param left 왼쪽 패널에 표시할 파일의 URI 구성 요소 Map
     * @param right 오른쪽 패널에 표시할 파일의 URI 구성 요소 Map
     * @param title Diff 에디터의 제목 (선택 사항)
     * @param columnOrOptions Diff 에디터의 컬럼 위치 또는 기타 옵션 (현재는 사용되지 않음)
     * @return 작업 완료 후 null
     */
    suspend fun workbench_diff(left: Map<String, Any?>, right: Map<String, Any?>, title: String?, columnOrOptions: Any?): Any? {
        val rightURI = createURI(right)
        val leftURI = createURI(left)
        logger.info("Diff 에디터 열기: ${rightURI.path}")

        // 각 URI로부터 DiffContent 객체를 생성합니다.
        val content1 = createContent(left, project)
        val content2 = createContent(right, project)

        if (content1 != null && content2 != null) {
            // EditorAndDocManager 서비스를 통해 Diff 에디터를 엽니다.
            project.getService(EditorAndDocManager::class.java).openDiffEditor(leftURI, rightURI, title ?: "File Comparison")
        }
        logger.info("Diff 에디터 열기 완료: ${rightURI.path}")
        return null
    }

    /**
     * URI 구성 요소 Map으로부터 `DiffContent` 객체를 생성합니다.
     * `file://` 스키마는 로컬 파일을, `cline-diff://` 스키마는 인코딩된 문자열을 내용으로 사용합니다.
     *
     * @param uri URI 구성 요소 Map
     * @param project 현재 IntelliJ 프로젝트
     * @return 생성된 `DiffContent` 객체 또는 생성 실패 시 null
     */
    fun createContent(uri: Map<String, Any?>, project: Project): DiffContent? {
        val path = uri["path"]
        val scheme = uri["scheme"]
        val query = uri["query"]
        // val fragment = uri["fragment"] // 현재 사용되지 않음

        if (scheme != null) {
            val contentFactory = DiffContentFactory.getInstance()
            if (scheme == "file") {
                // 로컬 파일 시스템의 파일을 DiffContent로 생성
                val vfs = LocalFileSystem.getInstance()
                val fileIO = File(path as String)
                if (!fileIO.exists()) {
                    fileIO.createNewFile()
                    vfs.refreshIoFiles(listOf(fileIO.parentFile))
                }

                val file = vfs.refreshAndFindFileByPath(path as String) ?: run {
                    logger.warn("파일을 찾을 수 없음: $path")
                    return null
                }
                return contentFactory.create(project, file)
            } else if (scheme == "cline-diff") {
                // 쿼리 파라미터에 Base64 인코딩된 문자열이 있는 경우, 이를 디코딩하여 내용으로 사용
                val string = if (query != null) {
                    val bytes = java.util.Base64.getDecoder().decode(query as String)
                    String(bytes)
                } else {
                    ""
                }
                val content = contentFactory.create(project, string)
                return content
            }
            return null
        } else {
            return null
        }
    }
}

/**
 * Map 형태의 URI 구성 요소로부터 `URI` 객체를 생성합니다.
 *
 * @param map URI 구성 요소 (scheme, authority, path, query, fragment)를 담은 Map
 * @return 구성 요소로부터 생성된 `URI` 객체
 */
fun createURI(map: Map<String, Any?>): URI {
    val authority = if (map["authority"] != null) map["authority"] as String else ""
    val query = if (map["query"] != null) map["query"] as String else ""
    val fragment = if (map["fragment"] != null) map["fragment"] as String else ""

    val uriComponents = object : URIComponents {
        override val scheme: String = map["scheme"] as String
        override val authority: String = authority
        override val path: String = map["path"] as String
        override val query: String = query
        override val fragment: String = fragment
    }
    return URI.from(uriComponents)
}
