// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.sina.weibo.agent.extensions.core.VsixManager
import java.io.File

/**
 * 확장을 위한 VSIX 파일 업로드 다이얼로그입니다.
 * 확장 리소스를 찾을 수 없을 때 사용자가 VSIX 파일을 업로드할 수 있도록 합니다.
 */
class VsixUploadDialog(
    private val project: Project,
    private val extensionId: String, // 업로드할 확장의 ID
    private val extensionName: String, // 업로드할 확장의 표시 이름
) : DialogWrapper(project) {

    private var selectedVsixFile: File? = null // 사용자가 선택한 VSIX 파일

    init {
        title = "$extensionName 을 위한 VSIX 업로드" // 다이얼로그 제목 설정
        init() // 다이얼로그 초기화
        setSize(500, 150) // 다이얼로그 크기 설정
    }

    /**
     * 다이얼로그의 중앙 패널 UI를 생성합니다.
     * Kotlin UI DSL Builder를 사용하여 UI를 구성합니다.
     */
    override fun createCenterPanel() = panel {
        row {
            label("확장: $extensionName") // 현재 확장 이름 표시
        }

        row {
            label("VSIX 파일 선택:")
        }

        row {
            val fileField = JBTextField().apply {
                isEditable = false // 파일 경로를 직접 편집할 수 없도록 설정
                columns = 50
            }

            cell(fileField).resizableColumn() // 파일 경로 필드를 크기 조절 가능하게 설정

            button("찾아보기") { // 파일 선택 버튼
                selectVsixFile(fileField) // 파일 선택 로직 호출
            }
        }
    }

    /**
     * 파일 선택 다이얼로그를 열어 VSIX 파일을 선택하도록 합니다.
     * @param fileField 선택된 파일 경로를 표시할 텍스트 필드
     */
    private fun selectVsixFile(fileField: JBTextField) {
        // VSIX 파일만 선택할 수 있도록 파일 선택기 디스크립터를 설정합니다.
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { file -> file.extension?.lowercase() == "vsix" } // .vsix 확장자만 허용
            .withTitle("VSIX 파일 선택")

        // 파일 선택 다이얼로그를 엽니다.
        FileChooser.chooseFile(descriptor, project, null) { file ->
            selectedVsixFile = file.toNioPath().toFile() // 선택된 파일을 File 객체로 저장
            fileField.text = file.path // 텍스트 필드에 파일 경로 표시
        }
    }

    /**
     * "OK" 버튼 클릭 시 실행되는 액션입니다.
     * 선택된 VSIX 파일을 설치하고 다이얼로그를 닫습니다.
     */
    override fun doOKAction() {
        if (selectedVsixFile == null) {
            Messages.showErrorDialog("업로드할 VSIX 파일을 선택해주세요.", "파일 선택 안됨")
            return
        }

        if (!selectedVsixFile!!.exists()) {
            Messages.showErrorDialog("선택된 파일이 존재하지 않습니다.", "파일을 찾을 수 없음")
            return
        }

        try {
            val vsixManager = VsixManager.getInstance()
            val success = vsixManager.installVsix(selectedVsixFile!!, extensionId) // VSIX 설치
            if (success) {
                Messages.showInfoMessage("VSIX 파일이 성공적으로 업로드되었습니다!", "업로드 완료")
                super.doOKAction() // 기본 "OK" 액션 수행 (다이얼로그 닫기)
            } else {
                Messages.showErrorDialog(
                    "VSIX 파일 압축 해제에 실패했습니다. 파일 형식을 확인하고 다시 시도해주세요.",
                    "압축 해제 실패",
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "업로드 중 오류 발생: ${e.message}",
                "업로드 오류",
            )
        }
    }

    companion object {
        /**
         * VSIX 업로드 다이얼로그를 표시합니다.
         * @param project 현재 IntelliJ 프로젝트
         * @param extensionId 업로드할 확장의 ID
         * @param extensionName 업로드할 확장의 표시 이름
         * @return 다이얼로그가 "OK"로 닫혔으면 true, "Cancel"로 닫혔으면 false
         */
        fun show(project: Project, extensionId: String, extensionName: String): Boolean {
            val dialog = VsixUploadDialog(project, extensionId, extensionName)
            return dialog.showAndGet()
        }
    }
}
