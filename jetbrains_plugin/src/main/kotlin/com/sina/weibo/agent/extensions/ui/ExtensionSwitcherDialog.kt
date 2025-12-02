// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.event.ListSelectionListener
import com.sina.weibo.agent.extensions.core.ExtensionManager
import com.sina.weibo.agent.extensions.core.ExtensionSwitcher
import com.sina.weibo.agent.extensions.core.ExtensionConfigurationManager
import com.sina.weibo.agent.extensions.core.VsixManager
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.util.PluginResourceUtil
import com.sina.weibo.agent.util.PluginConstants

/**
 * 확장 제공자를 전환하기 위한 간소화된 다이얼로그입니다.
 * 사용자가 사용 가능한 확장 목록을 보고, 선택하고, 전환할 수 있도록 합니다.
 */
class ExtensionSwitcherDialog(private val project: Project) : DialogWrapper(project) {

    // === 상태 및 서비스 ====================================================================
    private val extensionManager = ExtensionManager.getInstance(project)
    private val extensionSwitcher = ExtensionSwitcher.getInstance(project)
    private val configManager = ExtensionConfigurationManager.getInstance(project)

    // === UI 컴포넌트 선언 ====================================================================
    private lateinit var extensionList: JBList<ExtensionListItem>
    private lateinit var descriptionLabel: JBLabel
    private lateinit var statusLabel: JBLabel
    private lateinit var switchButton: JButton
    private lateinit var setAsDefaultCheckBox: JBCheckBox
    private lateinit var installButton: JButton
    private lateinit var refreshButton: JButton

    // === 데이터 모델 =========================================================================
    private val extensionListItems = mutableListOf<ExtensionListItem>()
    private var selectedExtensionId: String? = null
    private var isSwitching = false // 전환 작업 진행 중 여부

    init {
        title = "확장 제공자 전환" // 다이얼로그 제목
        init() // 다이얼로그 초기화
        loadExtensions() // 확장 목록 로드
        setupUI() // UI 설정
    }

    /**
     * 확장 목록의 각 항목을 나타내는 데이터 클래스입니다.
     */
    private data class ExtensionListItem(
        val id: String,             // 확장의 고유 ID
        val displayName: String,    // 확장의 표시 이름
        val description: String,    // 확장의 설명
        val isAvailable: Boolean,   // 확장이 사용 가능한지 여부
        val isCurrent: Boolean,     // 현재 활성화된 확장인지 여부
        val resourceStatus: ResourceStatus // 리소스 상태 정보
    )

    /**
     * 확장의 리소스 존재 여부 및 경로를 나타내는 데이터 클래스입니다.
     */
    private data class ResourceStatus(
        val projectResourceExists: Boolean, // 프로젝트 경로에 리소스가 있는지 여부
        val projectResourcePath: String?,   // 프로젝트 경로의 리소스 경로
        val pluginResourceExists: Boolean,  // 플러그인 리소스에 있는지 여부
        val pluginResourcePath: String?,    // 플러그인 리소스 경로
        val vsixResourceExists: Boolean,    // VSIX를 통해 설치되었는지 여부
        val vsixResourcePath: String?,      // VSIX 설치 경로
    )

    // === UI 빌드 =============================================================================
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(600, 400)
            border = JBUI.Borders.empty(16)
        }

        // 왼쪽 패널 - 확장 목록
        val leftPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(300, 0)
            border = BorderFactory.createTitledBorder("확장 목록")
        }

        extensionList = JBList<ExtensionListItem>().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION // 단일 선택 모드
            setCellRenderer(listCellRenderer()) // 커스텀 셀 렌더러 설정
            addListSelectionListener(listSelectionListener()) // 선택 변경 리스너 설정
        }
        leftPanel.add(JScrollPane(extensionList), BorderLayout.CENTER)

        // 오른쪽 패널 - 상세 정보 및 액션
        val rightPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("상세 정보")
            preferredSize = Dimension(270, 0)
            minimumSize = Dimension(270, 0)
            maximumSize = Dimension(270, Int.MAX_VALUE)
        }

        val detailsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(16)
        }

        descriptionLabel = JBLabel("상세 정보를 보려면 확장을 선택하세요.")
        statusLabel = JBLabel("")
        switchButton = JButton("전환").apply { 
            isEnabled = false
            addActionListener { performSwitch() } // 전환 버튼 액션
        }
        setAsDefaultCheckBox = JBCheckBox("기본값으로 설정").apply { 
            isEnabled = false 
        }

        detailsPanel.add(descriptionLabel)
        detailsPanel.add(Box.createVerticalStrut(8))
        detailsPanel.add(statusLabel)
        detailsPanel.add(Box.createVerticalStrut(16))
        detailsPanel.add(switchButton)
        detailsPanel.add(Box.createVerticalStrut(8))
//        detailsPanel.add(setAsDefaultCheckBox) // 현재 사용되지 않음

        rightPanel.add(detailsPanel, BorderLayout.CENTER)

        // 하단 버튼들
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        installButton = JButton("VSIX 설치").apply { addActionListener { uploadVsixFile() } }
        refreshButton = JButton("새로고침").apply { addActionListener { loadExtensions() } }
        buttonPanel.add(installButton)
        buttonPanel.add(refreshButton)

        rightPanel.add(buttonPanel, BorderLayout.SOUTH)

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(Box.createHorizontalStrut(16), BorderLayout.CENTER)
        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    /**
     * UI 초기 상태를 설정합니다.
     */
    private fun setupUI() {
        updateUI(null)
    }

    // === 리스트 렌더링 ======================================================================
    /**
     * 확장 목록 항목을 렌더링하기 위한 커스텀 셀 렌더러입니다.
     * 각 항목의 이름과 상태를 표시합니다.
     */
    private fun listCellRenderer(): ListCellRenderer<ExtensionListItem> = ListCellRenderer { _, value, _, isSelected, _ ->
        JPanel(BorderLayout()).apply {
            isOpaque = true
            background = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
            border = JBUI.Borders.empty(4, 8)
            
            val nameLabel = JBLabel(value.displayName).apply {
                foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                if (value.isCurrent) font = font.deriveFont(Font.BOLD) // 현재 활성화된 확장은 굵게 표시
            }
            
            val statusLabel = JBLabel(statusText(value)).apply {
                foreground = Color.WHITE
                background = statusColor(value)
                border = JBUI.Borders.empty(2, 6)
                isOpaque = true
            }
            
            add(nameLabel, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }
    }

    /**
     * 리스트 선택 변경 리스너입니다.
     * 선택된 항목에 따라 상세 정보 패널을 업데이트합니다.
     */
    private fun listSelectionListener(): ListSelectionListener = ListSelectionListener { e ->
        if (!e.valueIsAdjusting) { // 마우스 드래그 중이 아닐 때만 처리
            val idx = extensionList.selectedIndex
            if (idx >= 0) {
                val item = extensionList.model.getElementAt(idx) as ExtensionListItem
                selectedExtensionId = item.id
                updateUI(item)
            } else {
                selectedExtensionId = null
                updateUI(null)
            }
        }
    }

    /**
     * 확장 항목의 상태 텍스트를 반환합니다.
     */
    private fun statusText(item: ExtensionListItem): String = getExtensionStatus(item).status

    /**
     * 확장 항목의 상태에 따른 색상을 반환합니다.
     */
    private fun statusColor(item: ExtensionListItem): Color = when (getExtensionStatus(item).status) {
        "Current" -> JBColor.GREEN
        "Next Startup" -> JBColor.BLUE
        "Uninstalled" -> JBColor.RED
        "Installed" -> JBColor.BLUE
        else -> JBColor.ORANGE
    }

    // === 동작 ============================================================================
    
    /**
     * 확장의 상태 정보를 담는 데이터 클래스입니다.
     */
    private data class ExtensionStatus(
        val status: String,     // 짧은 상태 문자열 (예: "Current")
        val displayText: String,// 사용자에게 표시될 상세 상태 텍스트
        val icon: String,       // 상태를 나타내는 아이콘 문자열
        val buttonText: String  // 전환 버튼에 표시될 텍스트
    )
    
    /**
     * 확장 항목의 현재 상태를 판단하여 `ExtensionStatus` 객체를 반환합니다.
     */
    private fun getExtensionStatus(item: ExtensionListItem): ExtensionStatus = when {
        item.isCurrent -> ExtensionStatus(
            status = "Current",
            displayText = "🔄 현재 실행 중",
            icon = "🔄",
            buttonText = "재로드"
        )
        isConfiguredForNextStartup(item.id) -> ExtensionStatus(
            status = "Next Startup", 
            displayText = "⏭️ 다음 시작 시 활성화",
            icon = "⏭️",
            buttonText = "다음 시작 시 활성화 예정"
        )
        !item.isAvailable -> ExtensionStatus(
            status = "Uninstalled",
            displayText = "설치되지 않음",
            icon = "",
            buttonText = "전환"
        )
        item.resourceStatus.projectResourceExists || item.resourceStatus.vsixResourceExists -> ExtensionStatus(
            status = "Installed",
            displayText = "✅ 설치됨",
            icon = "✅",
            buttonText = "전환"
        )
        else -> ExtensionStatus(
            status = "Built-in",
            displayText = "📦 내장됨",
            icon = "📦",
            buttonText = "전환"
        )
    }
    
    /**
     * 특정 확장이 다음 시작 시 활성화되도록 설정되었는지 확인합니다.
     */
    private fun isConfiguredForNextStartup(extensionId: String): Boolean {
        return extensionId == configManager.getCurrentExtensionId()
    }
    
    /**
     * 특정 확장이 현재 실행 중인지 확인합니다.
     */
    private fun isCurrentlyRunning(extensionId: String): Boolean {
        val currentProvider = extensionManager.getCurrentProvider()
        return currentProvider?.getExtensionId() == extensionId
    }
    
    /**
     * 사용 가능한 확장 목록을 로드하고 UI를 업데이트합니다.
     */
    private fun loadExtensions() {
        extensionListItems.clear()
        val current = extensionManager.getCurrentProvider()
        val providers = extensionManager.getAllProviders()
        
        providers.forEach { p ->
            val rs = checkResources(p) // 각 확장의 리소스 상태 확인
            val item = ExtensionListItem(
                id = p.getExtensionId(),
                displayName = p.getDisplayName(),
                description = p.getDescription(),
                isAvailable = p.isAvailable(project),
                isCurrent = isCurrentlyRunning(p.getExtensionId()),
                resourceStatus = rs
            )
            extensionListItems.add(item)
        }
        
        extensionListItems.sortBy { it.displayName } // 이름순으로 정렬
        extensionList.setListData(extensionListItems.toTypedArray()) // 리스트 데이터 설정
        
        // 현재 활성화된 확장을 선택 상태로 만듭니다.
        val currentIndex = extensionListItems.indexOfFirst { it.isCurrent }
        if (currentIndex >= 0) {
            extensionList.selectedIndex = currentIndex
            selectedExtensionId = extensionListItems[currentIndex].id
            updateUI(extensionListItems[currentIndex])
        }
    }

    /**
     * 선택된 확장 항목에 따라 UI 컴포넌트들을 업데이트합니다.
     */
    private fun updateUI(item: ExtensionListItem?) {
        if (item == null) {
            descriptionLabel.text = "상세 정보를 보려면 확장을 선택하세요."
            statusLabel.text = ""
            switchButton.isEnabled = false
            setAsDefaultCheckBox.isEnabled = false
            return
        }

        descriptionLabel.text = item.description
        
        val extensionStatus = getExtensionStatus(item)
        statusLabel.text = "상태: ${extensionStatus.displayText}"
        
        // 전환 버튼 활성화 조건
        val canSwitch = item.isAvailable && 
                       !item.isCurrent && 
                       !isConfiguredForNextStartup(item.id) && 
                       !isSwitching
        switchButton.isEnabled = canSwitch
        
        switchButton.text = extensionStatus.buttonText // 버튼 텍스트 업데이트
        
        setAsDefaultCheckBox.isEnabled = item.isAvailable && !item.isCurrent
    }

    // === 액션 ==============================================================================
    /**
     * VSIX 파일 업로드 다이얼로그를 엽니다.
     */
    private fun uploadVsixFile() {
        val selected = selectedExtensionId?.let { id -> extensionListItems.find { it.id == id } } ?: run {
            Messages.showWarningDialog("먼저 확장을 선택해주세요.", "확장 선택 안됨")
            return
        }
        val success = VsixUploadDialog.show(project, selected.id, selected.displayName)
        if (success) {
            loadExtensions() // 업로드 후 확장 목록 새로고침
            Messages.showInfoMessage("VSIX 파일 업로드 성공: ${selected.displayName}", "업로드 완료")
        }
    }

    /**
     * 확장 전환을 수행합니다.
     */
    private fun performSwitch() {
        val target = selectedExtensionId ?: return
        val currentProvider = extensionManager.getCurrentProvider()
        val currentId = currentProvider?.getExtensionId()
        
        // 이미 다음 시작 시 활성화되도록 설정된 확장인 경우
        if (isConfiguredForNextStartup(target)) {
            Messages.showInfoMessage(
                "확장 '$target'은(는) 이미 다음 시작 시 활성화되도록 설정되었습니다.\n\n" +
                "별도의 조치가 필요하지 않습니다.",
                "이미 설정됨"
            )
            return
        }
        
        // 현재 실행 중인 확장과 동일한 확장을 선택한 경우 (재로드)
        if (currentId == target) {
            performReload(target)
            return
        }
        
        // 전환 확인 다이얼로그
        val confirm = Messages.showYesNoDialog(
            "'$currentId'에서 '$target'(으)로 전환하시겠습니까?\n\n" +
            "⚠️ 중요: 확장은 IntelliJ IDEA의 다음 시작 시 적용됩니다.\n" +
            "현재 세션은 기존 확장을 계속 사용합니다.\n\n" +
            "계속하시겠습니까?",
            "확장 전환 확인",
            "전환",
            "취소",
            Messages.getQuestionIcon()
        )
        
        if (confirm == Messages.YES) {
            if (setAsDefaultCheckBox.isSelected) {
                // TODO: 프로젝트 기본값으로 영속화하는 로직 구현
            }
            doSwitch(target)
        }
    }

    /**
     * 확장을 재로드합니다.
     */
    private fun performReload(extensionId: String) {
        // 이미 다음 시작 시 활성화되도록 설정된 확장인 경우
        if (isConfiguredForNextStartup(extensionId)) {
            Messages.showInfoMessage(
                "확장 '$extensionId'은(는) 이미 다음 시작 시 활성화되도록 설정되었습니다.\n\n" +
                "재로드 작업이 필요하지 않습니다.",
                "이미 설정됨"
            )
            return
        }
        
        isSwitching = true
        setSwitchingUI(true)
        
        extensionSwitcher.switchExtension(extensionId, true).whenComplete { success, err ->
            SwingUtilities.invokeLater {
                isSwitching = false
                setSwitchingUI(false)
                if (success) {
                    Messages.showInfoMessage(
                        "확장 설정이 성공적으로 업데이트되었습니다: $extensionId\n\n" +
                        "참고: 확장은 IntelliJ IDEA의 다음 시작 시 적용됩니다.",
                        "설정 업데이트됨"
                    )
                    loadExtensions()
                } else {
                    val errorMsg = err?.message ?: "알 수 없는 오류 발생"
                    Messages.showErrorDialog("확장 설정 업데이트 실패: $errorMsg", "업데이트 실패")
                }
            }
        }
    }

    /**
     * 실제 확장 전환 로직을 수행합니다.
     */
    private fun doSwitch(target: String) {
        isSwitching = true
        setSwitchingUI(true)
        
        extensionSwitcher.switchExtension(target, true).whenComplete { success, err ->
            SwingUtilities.invokeLater {
                isSwitching = false
                setSwitchingUI(false)
                if (success) {
                    Messages.showInfoMessage(
                        "확장 전환 설정이 성공적으로 저장되었습니다!\n\n" +
                        "✅ 확장: $target\n" +
                        "⚠️ 확장은 IntelliJ IDEA의 다음 시작 시 적용됩니다.\n" +
                        "🔄 새 확장을 활성화하려면 IntelliJ IDEA를 다시 시작해주세요.",
                        "확장 전환 완료"
                    )
                    loadExtensions() // UI를 새로고침하여 새 구성을 표시
                    // 다이얼로그를 닫지 않고 사용자가 업데이트된 상태를 볼 수 있도록 합니다.
                } else {
                    val errorMsg = err?.message ?: "알 수 없는 오류 발생"
                    Messages.showErrorDialog("확장 전환 설정 저장 실패: $errorMsg", "설정 저장 실패")
                    loadExtensions()
                }
            }
        }
    }

    /**
     * 전환 작업 진행 상태에 따라 UI를 업데이트합니다.
     */
    private fun setSwitchingUI(switching: Boolean) {
        switchButton.isEnabled = !switching
        installButton.isEnabled = !switching
        refreshButton.isEnabled = !switching
        
        if (switching) {
            switchButton.text = "설정 저장 중..."
        } else {
            val selected = selectedExtensionId?.let { id -> extensionListItems.find { it.id == id } }
            if (selected != null) {
                switchButton.text = getExtensionStatus(selected).buttonText
            } else {
                switchButton.text = "전환"
            }
        }
    }

    /**
     * 다이얼로그의 "Cancel" 액션을 처리합니다.
     * 전환 작업이 진행 중인 경우 취소 여부를 확인합니다.
     */
    override fun doCancelAction() {
        if (isSwitching) {
            val result = Messages.showYesNoDialog(
                "설정 저장 중입니다. 정말로 취소하시겠습니까?",
                "설정 저장 취소",
                "저장 취소",
                "계속 대기",
                Messages.getQuestionIcon()
            )
            if (result == Messages.YES) {
                extensionSwitcher.cancelSwitching()
                super.doCancelAction()
            }
        } else {
            super.doCancelAction()
        }
    }

    // === 리소스 확인 =====================================================================
    /**
     * 특정 확장 제공자의 리소스 상태를 확인합니다.
     * @param provider 리소스 상태를 확인할 확장 제공자
     * @return `ResourceStatus` 객체
     */
    private fun checkResources(provider: ExtensionProvider): ResourceStatus {
        val cfg = provider.getConfiguration(project)
        val base = project.basePath
        
        var projExists = false
        var projPath: String? = null
        if (base != null) {
            listOf("$base/${cfg.getCodeDir()}", "$base/../${cfg.getCodeDir()}", "$base/../../${cfg.getCodeDir()}").forEach { p ->
                if (!projExists && File(p).exists()) {
                    projExists = true
                    projPath = p
                }
            }
        }
        
        var pluginExists = false
        var pluginPath: String? = null
        try {
            PluginResourceUtil.getResourcePath(PluginConstants.PLUGIN_ID, cfg.getCodeDir())?.let { path ->
                if (File(path).exists()) {
                    pluginExists = true
                    pluginPath = path
                }
            }
        } catch (_: Exception) {} // 예외 무시
        
        val vsixMgr = VsixManager.getInstance()
        val extId = provider.getExtensionId()
        val vsixExists = vsixMgr.hasVsixInstallation(extId)
        val vsixPath = if (vsixExists) vsixMgr.getVsixInstallationPath(extId) else null
        
        return ResourceStatus(projExists, projPath, pluginExists, pluginPath, vsixExists, vsixPath)
    }
}
