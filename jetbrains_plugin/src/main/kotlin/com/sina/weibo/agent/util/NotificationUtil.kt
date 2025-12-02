// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

/**
 * 알림(Notification) 유틸리티 클래스입니다.
 * 플러그인을 위한 알림 기능을 캡슐화하여 사용자에게 메시지를 표시합니다.
 */
object NotificationUtil {
    
    // 알림 그룹 ID
    private const val NOTIFICATION_GROUP_ID = "RunVSAgent"
    
    /**
     * 오류 알림을 표시합니다.
     * @param title 알림 제목
     * @param content 알림 내용
     * @param project 프로젝트 인스턴스. null이면 기본 프로젝트가 사용됩니다.
     */
    fun showError(title: String, content: String, project: Project? = null) {
        showNotification(title, content, NotificationType.ERROR, project)
    }
    
    /**
     * 경고 알림을 표시합니다.
     * @param title 알림 제목
     * @param content 알림 내용
     * @param project 프로젝트 인스턴스. null이면 기본 프로젝트가 사용됩니다.
     */
    fun showWarning(title: String, content: String, project: Project? = null) {
        showNotification(title, content, NotificationType.WARNING, project)
    }
    
    /**
     * 정보 알림을 표시합니다.
     * @param title 알림 제목
     * @param content 알림 내용
     * @param project 프로젝트 인스턴스. null이면 기본 프로젝트가 사용됩니다.
     */
    fun showInfo(title: String, content: String, project: Project? = null) {
        showNotification(title, content, NotificationType.INFORMATION, project)
    }
    
    /**
     * 알림을 표시하는 내부 헬퍼 함수입니다.
     * @param title 알림 제목
     * @param content 알림 내용
     * @param type 알림 타입 (정보, 경고, 오류)
     * @param project 프로젝트 인스턴스. null이면 기본 프로젝트가 사용됩니다.
     */
    private fun showNotification(title: String, content: String, type: NotificationType, project: Project?) {
        // 알림을 표시할 대상 프로젝트를 결정합니다. (지정된 프로젝트 또는 기본 프로젝트)
        val targetProject = project ?: ProjectManager.getInstance().defaultProject
        // 알림 그룹을 가져옵니다.
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        
        // 알림을 생성하고 표시합니다.
        notificationGroup?.createNotification(title, content, type)?.notify(targetProject)
    }
    

}
