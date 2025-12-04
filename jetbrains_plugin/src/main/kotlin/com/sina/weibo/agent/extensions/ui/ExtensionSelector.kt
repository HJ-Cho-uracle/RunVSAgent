// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.extensions.core.ExtensionManager
import java.awt.Dimension
import javax.swing.DefaultListModel

/**
 * 확장 선택기 다이얼로그 클래스입니다.
 * 사용자가 플러그인에서 지원하는 다양한 확장 제공자 중에서 하나를 선택하고 전환할 수 있도록 합니다.
 */
class ExtensionSelector(private val project: Project) : DialogWrapper(project) {

    // ExtensionManager 인스턴스를 가져옵니다.
    private val extensionManager = ExtensionManager.getInstance(project)

    // 확장 목록을 표시하기 위한 리스트 모델
    private val listModel = DefaultListModel<ExtensionProvider>()

    // 확장 목록을 보여주는 JBList 컴포넌트
    private val extensionList = JBList(listModel)

    init {
        title = "확장 선택" // 다이얼로그 제목 설정
        init() // 다이얼로그 초기화
        loadExtensions() // 확장 목록 로드
    }

    /**
     * `ExtensionManager`로부터 사용 가능한 확장 목록을 로드하여 UI에 표시합니다.
     * 현재 활성화된 확장이 있으면 해당 항목을 선택 상태로 만듭니다.
     */
    private fun loadExtensions() {
        listModel.clear() // 기존 목록 초기화
        val availableProviders = extensionManager.getAvailableProviders() // 사용 가능한 제공자 가져오기
        availableProviders.forEach { listModel.addElement(it) } // 리스트 모델에 추가

        // 현재 활성화된 확장 제공자를 선택 상태로 만듭니다.
        val currentProvider = extensionManager.getCurrentProvider()
        if (currentProvider != null) {
            extensionList.setSelectedValue(currentProvider, true)
        }
    }

    /**
     * 다이얼로그의 중앙 패널 UI를 생성합니다.
     * Kotlin UI DSL Builder를 사용하여 UI를 구성합니다.
     */
    override fun createCenterPanel() = panel {
        row {
            label("사용할 확장을 선택하세요:")
        }

        row {
            // 확장 목록을 스크롤 가능한 패널에 담아 표시합니다.
            cell(
                JBScrollPane(extensionList).apply {
                    preferredSize = Dimension(400, 200) // 선호하는 크기 설정
                },
            ).resizableColumn() // 컬럼이 크기 조절 가능하도록 설정
        }

        row {
            label("설명:").bold() // "설명:" 레이블을 굵게 표시
        }

        row {
            val descriptionLabel = JBLabel("") // 선택된 확장의 설명을 표시할 레이블
            // 리스트 선택 변경 리스너를 추가하여 선택된 확장의 설명을 업데이트합니다.
            extensionList.addListSelectionListener {
                val selectedProvider = extensionList.selectedValue
                if (selectedProvider != null) {
                    descriptionLabel.setText(selectedProvider.getDescription())
                }
            }
            cell(descriptionLabel).resizableColumn()
        }
    }

    /**
     * "OK" 버튼 클릭 시 실행되는 액션입니다.
     * 선택된 확장을 현재 활성화된 확장으로 설정하고 다이얼로그를 닫습니다.
     */
    override fun doOKAction() {
        val selectedProvider = extensionList.selectedValue
        if (selectedProvider != null) {
            // ExtensionManager를 통해 선택된 확장을 현재 활성화된 확장으로 설정합니다.
            extensionManager.setCurrentProvider(selectedProvider.getExtensionId())
            super.doOKAction() // 기본 "OK" 액션 수행
        }
    }

    /**
     * 다이얼로그의 유효성 검사를 수행합니다.
     * 확장이 선택되지 않았으면 경고 메시지를 반환합니다.
     */
    override fun doValidate(): ValidationInfo? {
        val selectedProvider = extensionList.selectedValue
        if (selectedProvider == null) {
            return ValidationInfo("확장을 선택해주세요.")
        }
        return null
    }

    companion object {
        /**
         * 확장 선택 다이얼로그를 표시합니다.
         * @param project 현재 IntelliJ 프로젝트
         * @return 다이얼로그가 "OK"로 닫혔으면 true, "Cancel"로 닫혔으면 false
         */
        fun show(project: Project): Boolean {
            val dialog = ExtensionSelector(project)
            return dialog.showAndGet()
        }
    }
}
