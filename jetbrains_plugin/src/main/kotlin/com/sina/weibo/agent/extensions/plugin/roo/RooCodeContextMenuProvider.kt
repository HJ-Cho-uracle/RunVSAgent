// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.roo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuActionType
import com.sina.weibo.agent.extensions.ui.contextmenu.ContextMenuConfiguration
import com.sina.weibo.agent.extensions.ui.contextmenu.ExtensionContextMenuProvider
import com.sina.weibo.agent.webview.WebViewManager

/**
 * Roo Code 확장 컨텍스트 메뉴 제공자입니다.
 * Roo Code 확장에 특화된 컨텍스트 메뉴 액션(동작)을 제공합니다.
 * 이는 기존 roo-cline의 모든 기능을 포함합니다.
 */
class RooCodeContextMenuProvider : ExtensionContextMenuProvider {

    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "roo-code"

    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Roo Code"

    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "전체 컨텍스트 메뉴 기능을 갖춘 AI 기반 코드 어시스턴트"

    /**
     * Roo Code 확장이 사용 가능한지 여부를 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        // TODO: Roo Code 확장의 가용성 조건을 확인할 수 있습니다.
        return true
    }

    /**
     * Roo Code 확장을 위한 컨텍스트 메뉴 액션 목록을 생성하여 반환합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return `AnAction` 객체 리스트 형태의 컨텍스트 메뉴 액션 목록
     */
    override fun getContextMenuActions(project: Project): List<AnAction> {
        return listOf(
            ExplainCodeAction(),
            FixCodeAction(),
            FixLogicAction(),
            ImproveCodeAction(),
            AddToContextAction(),
        )
    }

    /**
     * Roo Code 확장을 위한 컨텍스트 메뉴 구성 정보를 반환합니다.
     */
    override fun getContextMenuConfiguration(): ContextMenuConfiguration {
        return RooCodeContextMenuConfiguration()
    }

    /**
     * Roo Code 컨텍스트 메뉴 구성 클래스입니다.
     * 모든 액션이 표시되도록 설정합니다. (모든 기능을 제공하는 확장)
     */
    private class RooCodeContextMenuConfiguration : ContextMenuConfiguration {
        /**
         * 특정 컨텍스트 메뉴 액션 타입이 표시되어야 하는지 여부를 반환합니다.
         * Roo Code의 경우 모든 액션이 표시됩니다.
         */
        override fun isActionVisible(actionType: ContextMenuActionType): Boolean {
            return true // Roo Code의 경우 모든 액션이 표시됩니다.
        }

        /**
         * 표시될 컨텍스트 메뉴 액션 타입 목록을 반환합니다.
         * Roo Code의 경우 모든 액션 타입을 반환합니다.
         */
        override fun getVisibleActions(): List<ContextMenuActionType> {
            return ContextMenuActionType.values().toList()
        }
    }

    /**
     * 선택된 코드를 설명하는 액션입니다.
     * 설명 요청과 함께 새 작업을 생성합니다.
     */
    class ExplainCodeAction : AnAction("코드 설명") {
        private val logger: Logger = Logger.getInstance(ExplainCodeAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            // 에디터에서 선택된 유효한 범위와 텍스트를 가져옵니다.
            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            // 명령 실행에 필요한 인자를 Map으로 구성합니다.
            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1 // 1-based 라인 번호
            args["endLine"] = effectiveRange.endLine + 1 // 1-based 라인 번호

            // `handleCodeAction` 헬퍼 함수를 호출하여 명령을 처리합니다.
            RooCodeContextMenuProvider.handleCodeAction("roo-cline.explainCode.InCurrentTask", "EXPLAIN", args, project)
        }
    }

    /**
     * 코드 문제를 수정하는 액션입니다.
     * 수정 요청과 함께 새 작업을 생성합니다.
     */
    class FixCodeAction : AnAction("코드 수정") {
        private val logger: Logger = Logger.getInstance(FixCodeAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1
            args["endLine"] = effectiveRange.endLine + 1

            RooCodeContextMenuProvider.handleCodeAction("roo-cline.fixCode.InCurrentTask", "FIX", args, project)
        }
    }

    /**
     * 코드의 논리적 문제를 수정하는 액션입니다.
     * 논리 수정 요청과 함께 새 작업을 생성합니다.
     */
    class FixLogicAction : AnAction("논리 수정") {
        private val logger: Logger = Logger.getInstance(FixLogicAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1
            args["endLine"] = effectiveRange.endLine + 1

            RooCodeContextMenuProvider.handleCodeAction("roo-cline.fixCode.InCurrentTask", "FIX", args, project)
        }
    }

    /**
     * 코드 품질을 개선하는 액션입니다.
     * 개선 요청과 함께 새 작업을 생성합니다.
     */
    class ImproveCodeAction : AnAction("코드 개선") {
        private val logger: Logger = Logger.getInstance(ImproveCodeAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1
            args["endLine"] = effectiveRange.endLine + 1

            RooCodeContextMenuProvider.handleCodeAction("roo-cline.improveCode.InCurrentTask", "IMPROVE", args, project)
        }
    }

    /**
     * 선택된 코드를 컨텍스트에 추가하는 액션입니다.
     * 코드를 현재 채팅 컨텍스트에 추가합니다.
     */
    class AddToContextAction : AnAction("컨텍스트에 추가") {
        private val logger: Logger = Logger.getInstance(AddToContextAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1
            args["endLine"] = effectiveRange.endLine + 1

            RooCodeContextMenuProvider.handleCodeAction("roo-cline.addToContext", "ADD_TO_CONTEXT", args, project)
        }
    }

    /**
     * 새 작업을 생성하는 액션입니다.
     * 선택된 코드와 함께 새 작업을 엽니다.
     */
    class NewTaskAction : AnAction("새 작업") {
        private val logger: Logger = Logger.getInstance(NewTaskAction::class.java)

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

            val effectiveRange = RooCodeContextMenuProvider.getEffectiveRange(editor)
            if (effectiveRange == null) return

            val args = mutableMapOf<String, Any?>()
            args["filePath"] = file.path
            args["selectedText"] = effectiveRange.text
            args["startLine"] = effectiveRange.startLine + 1
            args["endLine"] = effectiveRange.endLine + 1

            RooCodeContextMenuProvider.handleCodeAction("roo-cline.newTask", "NEW_TASK", args, project)
        }
    }

    /**
     * 선택된 텍스트의 유효한 범위와 내용을 나타내는 데이터 클래스입니다.
     * @property text 선택된 텍스트 내용
     * @property startLine 시작 라인 번호 (0-based)
     * @property endLine 끝 라인 번호 (0-based)
     */
    data class EffectiveRange(
        val text: String,
        val startLine: Int,
        val endLine: Int,
    )

    companion object {
        /**
         * 현재 에디터 선택 영역에서 유효한 범위와 텍스트를 가져옵니다.
         *
         * @param editor 현재 에디터 인스턴스
         * @return 선택된 텍스트와 라인 번호를 포함하는 `EffectiveRange` 객체, 선택 영역이 없으면 null
         */
        fun getEffectiveRange(editor: com.intellij.openapi.editor.Editor): EffectiveRange? {
            val document = editor.document
            val selectionModel = editor.selectionModel

            return if (selectionModel.hasSelection()) {
                val selectedText = selectionModel.selectedText ?: ""
                val startLine = document.getLineNumber(selectionModel.selectionStart)
                val endLine = document.getLineNumber(selectionModel.selectionEnd)
                EffectiveRange(selectedText, startLine, endLine)
            } else {
                null
            }
        }

        /**
         * 코드 액션을 처리하는 핵심 로직입니다.
         * 다양한 유형의 명령을 처리하고 웹뷰에 적절한 메시지를 보냅니다.
         *
         * @param command 실행할 명령 식별자
         * @param promptType 사용할 프롬프트 유형
         * @param params 액션에 대한 파라미터 (Map 또는 List)
         * @param project 현재 프로젝트
         */
        fun handleCodeAction(command: String, promptType: String, params: Any, project: Project?) {
            val latestWebView = project?.getService(WebViewManager::class.java)?.getLatestWebView()
            if (latestWebView == null) {
                return
            }

            // 명령 유형에 따라 메시지 내용을 생성합니다.
            val messageContent = when {
                // 컨텍스트에 추가 명령
                command.contains("addToContext") -> {
                    val promptParams = if (params is Map<*, *>) params as Map<String, Any?> else emptyMap()
                    mapOf(
                        "type" to "invoke",
                        "invoke" to "setChatBoxMessage",
                        "text" to RooCodeSupportPrompt.create("ADD_TO_CONTEXT", promptParams),
                    )
                }
                // 현재 작업에서 실행되는 명령
                command.endsWith("InCurrentTask") -> {
                    val promptParams = if (params is Map<*, *>) params as Map<String, Any?> else emptyMap()
                    val basePromptType = when {
                        command.contains("explain") -> "EXPLAIN"
                        command.contains("fix") -> "FIX"
                        command.contains("improve") -> "IMPROVE"
                        else -> promptType
                    }
                    mapOf(
                        "type" to "invoke",
                        "invoke" to "sendMessage",
                        "text" to RooCodeSupportPrompt.create(basePromptType, promptParams),
                    )
                }
                // 새 작업에서 실행되는 명령
                else -> {
                    val promptParams = if (params is List<*>) {
                        val argsList = params as List<Any>
                        if (argsList.size >= 4) {
                            mapOf(
                                "filePath" to argsList[0],
                                "selectedText" to argsList[1],
                                "startLine" to argsList[2],
                                "endLine" to argsList[3],
                            )
                        } else {
                            emptyMap()
                        }
                    } else if (params is Map<*, *>) {
                        params as Map<String, Any?>
                    } else {
                        emptyMap()
                    }

                    val basePromptType = when {
                        command.contains("explain") -> "EXPLAIN"
                        command.contains("fix") -> "FIX"
                        command.contains("improve") -> "IMPROVE"
                        else -> promptType
                    }

                    mapOf(
                        "type" to "invoke",
                        "invoke" to "initClineWithTask", // TODO: initClineWithTask 대신 initRooCodeWithTask로 변경 필요
                        "text" to RooCodeSupportPrompt.create(basePromptType, promptParams),
                    )
                }
            }

            // 메시지 내용을 JSON으로 변환하여 웹뷰에 전송합니다.
            val messageJson = com.google.gson.Gson().toJson(messageContent)
            latestWebView.postMessageToWebView(messageJson)
        }

        /**
         * 템플릿의 플레이스홀더를 실제 값으로 대체하여 프롬프트를 생성합니다.
         *
         * @param promptType 생성할 프롬프트의 타입
         * @param params 템플릿에 대체할 파라미터
         * @return 모든 플레이스홀더가 대체된 최종 프롬프트
         */
        fun createPrompt(promptType: String, params: Map<String, Any?>): String {
            val template = getPromptTemplate(promptType)
            return replacePlaceholders(template, params)
        }

        /**
         * 특정 프롬프트 타입에 대한 템플릿을 가져옵니다.
         *
         * @param type 가져올 프롬프트의 타입
         * @return 지정된 프롬프트 타입에 대한 템플릿 문자열
         */
        fun getPromptTemplate(type: String): String {
            return when (type) {
                "EXPLAIN" -> """코드 설명 템플릿""" // 실제 템플릿 내용
                "FIX" -> """코드 수정 템플릿""" // 실제 템플릿 내용
                "IMPROVE" -> """코드 개선 템플릿""" // 실제 템플릿 내용
                "ADD_TO_CONTEXT" -> """컨텍스트에 추가 템플릿""" // 실제 템플릿 내용
                "NEW_TASK" -> """새 작업 템플릿""" // 실제 템플릿 내용
                else -> ""
            }
        }

        /**
         * 템플릿의 플레이스홀더를 실제 값으로 대체합니다.
         *
         * @param template 플레이스홀더가 있는 프롬프트 템플릿
         * @param params 플레이스홀더를 대체할 파라미터 값 맵
         * @return 플레이스홀더가 실제 값으로 대체된 처리된 프롬프트
         */
        fun replacePlaceholders(template: String, params: Map<String, Any?>): String {
            val pattern = Regex("""\$\{(.*?)}""")
            return pattern.replace(template) { matchResult ->
                val key = matchResult.groupValues[1]
                params[key]?.toString() ?: ""
            }
        }
    }
}
