// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.sina.weibo.agent.registerFileListener

/**
 * 작업 공간 파일 변경 관리자입니다.
 * 작업 공간 내 파일의 생성, 수정, 삭제 및 기타 변경 사항을 감시하고, 해당 이벤트를 전송합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class WorkspaceFileChangeManager(val project: Project) : Disposable {
    private val logger = Logger.getInstance(WorkspaceFileChangeManager::class.java)

    // 등록된 파일 리스너 연결을 기록합니다.
    private val vfsConnections = com.sina.weibo.agent.vfsConnections

    // 프로젝트별 작업 공간 디렉터리 경로를 기록합니다.
    private val projectWorkspacePaths = com.sina.weibo.agent.projectWorkspacePaths

    // 프로젝트 리스너 연결
    private var projectConnection: MessageBusConnection? = null

    // 프로젝트 열기/닫기 이벤트를 처리하는 리스너
    private val projectListener = object : ProjectManagerListener {

        /**
         * 프로젝트가 닫혔을 때 호출됩니다.
         * 파일 리스너를 등록 해제하고, 프로젝트 작업 공간 경로 기록을 제거합니다.
         */
        override fun projectClosed(project: Project) {
            unregisterFileListener(project)
            projectWorkspacePaths.remove(project)
        }
    }

    init {
        logger.info("작업 공간 파일 변경 관리자 초기화 중")

        // 프로젝트 열기/닫기 이벤트를 수신하기 위한 리스너 등록
        projectConnection = ApplicationManager.getApplication().messageBus.connect(this)
        projectConnection?.subscribe(ProjectManager.TOPIC, projectListener)

        // 이미 열려있는 프로젝트들에 대해 파일 리스너를 등록합니다.
        val openProjects = ProjectManager.getInstance().openProjects
        for (project in openProjects) {
            registerFileListener(project)
            project.basePath?.let { projectWorkspacePaths[project] = it }
        }
    }

    /**
     * 파일 리스너를 등록 해제합니다.
     * @param project 리스너를 등록 해제할 프로젝트
     */
    private fun unregisterFileListener(project: Project) {
        val connection = vfsConnections.remove(project)
        if (connection != null) {
            logger.info("프로젝트 '${project.name}'에 대한 파일 리스너 등록 해제")

            try {
                connection.disconnect()
            } catch (e: Exception) {
                logger.error("프로젝트 '${project.name}'에 대한 파일 리스너 등록 해제 실패", e)
            }
        }
    }

    override fun dispose() {
        logger.info("작업 공간 파일 변경 관리자 리소스 해제")

        try {
            projectConnection?.disconnect() // 프로젝트 리스너 연결 해제
            projectConnection = null

            // 모든 파일 리스너 연결 해제
            vfsConnections.forEach { (project, connection) ->
                try {
                    logger.info("프로젝트 '${project.name}'에 대한 파일 리스너 등록 해제")
                    connection.disconnect()
                } catch (e: Exception) {
                    logger.error("프로젝트 '${project.name}'에 대한 파일 리스너 등록 해제 실패", e)
                }
            }

            vfsConnections.clear()
        } catch (e: Exception) {
            logger.error("작업 공간 파일 변경 관리자 리소스 해제 실패", e)
        }
    }
}
