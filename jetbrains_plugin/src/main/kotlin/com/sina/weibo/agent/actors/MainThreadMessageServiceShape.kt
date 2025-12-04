// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import java.util.concurrent.atomic.AtomicReference

/**
 * IntelliJ 메인 스레드에서 사용자에게 메시지를 보여주는 서비스를 위한 인터페이스입니다.
 * 정보, 경고, 오류 등을 모달 다이얼로그나 알림(Notification) 형태로 표시하는 기능을 정의합니다.
 */
interface MainThreadMessageServiceShape : Disposable {
    /**
     * 사용자에게 메시지를 표시합니다.
     * @param severity 메시지의 심각도 (1: 정보, 2: 경고, 3: 오류)
     * @param message 표시할 주 메시지 내용
     * @param options 메시지 표시 방식에 대한 옵션 (예: 모달 여부)
     * @param commands 메시지 박스에 포함될 버튼(액션) 목록
     * @return 사용자가 클릭한 버튼에 해당하는 핸들(handle) 값. 알림의 경우 null.
     */
    fun showMessage(severity: Int, message: String, options: Map<String, Any>, commands: List<Map<String, Any>>): Int?
}

/**
 * `MainThreadMessageServiceShape` 인터페이스의 구현 클래스입니다.
 * 전달받은 옵션에 따라 IntelliJ의 모달 다이얼로그 또는 알림(Notification)을 사용하여 메시지를 표시합니다.
 */
class MainThreadMessageService : MainThreadMessageServiceShape {
    private val logger = Logger.getInstance(MainThreadMessageService::class.java)

    /**
     * 메시지 표시 요청을 처리합니다. `modal` 옵션에 따라 `showModalMessage` 또는 `showNotificationMessage`를 호출합니다.
     */
    override fun showMessage(
        severity: Int,
        message: String,
        options: Map<String, Any>,
        commands: List<Map<String, Any>>,
    ): Int? {
        logger.info("showMessage - severity: $severity, message: $message, options: $options, commands: $commands")

        val project = ProjectManager.getInstance().defaultProject
        val isModal = options["modal"] as? Boolean ?: false
        val detail = options["detail"] as? String

        return if (isModal) {
            // 모달 다이얼로그로 표시하고, 사용자가 클릭한 버튼의 핸들을 반환합니다.
            showModalMessage(project, severity, message, detail, options, commands)
        } else {
            // 간단한 알림(풍선 도움말)으로 표시합니다. 사용자와의 상호작용 결과는 반환하지 않습니다.
            showNotificationMessage(project, severity, message)
            null
        }
    }

    /**
     * 모달(Modal) 형태의 다이얼로그를 생성하여 메시지를 표시합니다.
     * 사용자가 버튼 중 하나를 클릭할 때까지 다른 UI 상호작용을 차단합니다.
     */
    private fun showModalMessage(
        project: com.intellij.openapi.project.Project,
        severity: Int,
        message: String,
        detail: String?,
        options: Map<String, Any>,
        commands: List<Map<String, Any>>,
    ): Int? {
        // '닫기' 역할을 하는 버튼(취소 버튼)이 있는지 찾습니다.
        var cancelIdx = commands.indexOfFirst { it["isCloseAffordance"] == true }

        // 닫기 버튼이 없으면, "Cancel" 버튼을 자동으로 추가합니다.
        val commandsWithCancel = if (cancelIdx < 0) {
            val cancelHandle = commands.size
            commands + mapOf("title" to "Cancel", "handle" to cancelHandle, "isCloseAffordance" to true)
        } else {
            commands
        }

        // 다이얼로그에 표시될 버튼들의 제목 배열을 만듭니다.
        val buttonTitles = commandsWithCancel.map { it["title"].toString() }
        // 버튼 인덱스와 실제 핸들 값을 매핑하여, 나중에 사용자가 클릭한 버튼의 핸들을 찾을 수 있게 합니다.
        val handleMap = commandsWithCancel.mapIndexed { idx, cmd -> idx to (cmd["handle"] as? Number)?.toInt() }.toMap()

        // 최종 취소 버튼 인덱스를 다시 찾습니다.
        val cancelIdxFinal = commandsWithCancel.indexOfFirst { it["isCloseAffordance"] == true }

        // 다이얼로그에 표시될 주 메시지와 상세 메시지를 조합합니다.
        val dialogMessage = if (detail.isNullOrBlank()) message else "$message\n\n$detail"

        // 사용자가 선택한 버튼의 인덱스를 스레드 안전하게 저장하기 위한 AtomicReference
        val selectedIdxRef = AtomicReference<Int>()

        // 모든 UI 작업은 반드시 EDT(Event Dispatch Thread)에서 실행되어야 합니다.
        ApplicationManager.getApplication().invokeAndWait {
            val selectedIdx = Messages.showDialog(
                project,
                dialogMessage,
                // 다이얼로그 제목 설정
                options["source"]?.let { (it as? Map<*, *>)?.get("label")?.toString() } ?: "RunVSAgent",
                buttonTitles.toTypedArray(),
                // 기본으로 선택될 버튼 인덱스 (취소 버튼이 있으면 그것으로 설정)
                if (cancelIdxFinal >= 0) cancelIdxFinal else 0,
                // 심각도에 따라 다른 아이콘을 표시합니다.
                when (severity) {
                    1 -> Messages.getInformationIcon()
                    2 -> Messages.getWarningIcon()
                    3 -> Messages.getErrorIcon()
                    else -> Messages.getInformationIcon()
                },
            )
            selectedIdxRef.set(selectedIdx)
        }

        // 사용자가 클릭한 버튼의 인덱스를 가져와 해당하는 핸들 값을 반환합니다.
        val selectedIdx = selectedIdxRef.get()
        return if (selectedIdx >= 0) handleMap[selectedIdx] else null
    }

    /**
     * 화면 우측 하단에 나타나는 알림(Notification) 형태로 메시지를 표시합니다.
     * 사용자의 작업을 방해하지 않는 비차단 방식입니다.
     */
    private fun showNotificationMessage(
        project: com.intellij.openapi.project.Project,
        severity: Int,
        message: String,
    ) {
        // 심각도에 따라 알림 타입을 결정합니다.
        val notificationType = when (severity) {
            1 -> NotificationType.INFORMATION
            2 -> NotificationType.WARNING
            3 -> NotificationType.ERROR
            else -> NotificationType.INFORMATION
        }
        // "RunVSAgent" 알림 그룹에 속한 알림을 생성합니다.
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("RunVSAgent").createNotification(
            message,
            notificationType,
        )
        // 알림을 표시합니다.
        notification.notify(project)
    }

    override fun dispose() {
        logger.info("dispose")
    }
}
