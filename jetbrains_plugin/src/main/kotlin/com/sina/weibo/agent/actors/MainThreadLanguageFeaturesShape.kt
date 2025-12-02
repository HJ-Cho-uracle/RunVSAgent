// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.core.ExtensionIdentifier

/**
 * IntelliJ 메인 스레드에서 언어 관련 지능형 기능을 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadLanguageFeaturesShape`에 해당하며,
 * 코드 완성, 빠른 수정, 정의로 이동 등 다양한 언어 기능 제공자(Provider)를 등록하는 메소드를 정의합니다.
 */
interface MainThreadLanguageFeaturesShape : Disposable {
    /**
     * 등록된 서비스(제공자)를 해제합니다.
     * @param handle 해제할 제공자의 고유 핸들
     */
    fun unregister(handle: Int)

    // --- 심볼 및 탐색 관련 기능 ---

    /** 문서 내의 심볼(클래스, 함수, 변수 등) 정보를 제공하는 `DocumentSymbolProvider`를 등록합니다. */
    fun registerDocumentSymbolProvider(handle: Int, selector: List<Map<String, Any?>>, label: String)

    /** 정의(definition)로 이동 기능을 제공하는 `DefinitionProvider`를 등록합니다. */
    fun registerDefinitionSupport(handle: Int, selector: List<Map<String, Any?>>)

    /** 선언(declaration)으로 이동 기능을 제공하는 `DeclarationProvider`를 등록합니다. */
    fun registerDeclarationSupport(handle: Int, selector: List<Map<String, Any?>>)

    /** 구현(implementation)으로 이동 기능을 제공하는 `ImplementationProvider`를 등록합니다. */
    fun registerImplementationSupport(handle: Int, selector: List<Map<String, Any?>>)

    /** 타입 정의(type definition)로 이동 기능을 제공하는 `TypeDefinitionProvider`를 등록합니다. */
    fun registerTypeDefinitionSupport(handle: Int, selector: List<Map<String, Any?>>)

    /** 심볼의 모든 참조(reference)를 찾아주는 `ReferenceProvider`를 등록합니다. */
    fun registerReferenceSupport(handle: Int, selector: List<Map<String, Any?>>)

    /** 호출 계층(Call Hierarchy)을 분석하고 보여주는 `CallHierarchyProvider`를 등록합니다. */
    fun registerCallHierarchyProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 타입 계층(Type Hierarchy)을 분석하고 보여주는 `TypeHierarchyProvider`를 등록합니다. */
    fun registerTypeHierarchyProvider(handle: Int, selector: List<Map<String, Any?>>)

    // --- 에디터 UI 및 정보 표시 기능 ---

    /** 마우스를 올렸을 때(hover) 추가 정보를 보여주는 `HoverProvider`를 등록합니다. */
    fun registerHoverProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 코드 중간에 유용한 정보나 액션을 표시하는 `CodeLensProvider`를 등록합니다. */
    fun registerCodeLensSupport(handle: Number, selector: List<Map<String, Any?>>, eventHandle: Number?)
    fun emitCodeLensEvent(eventHandle: Int, event: Any?)

    /** 코드 라인 위에 추가 정보(예: 변수 값)를 표시하는 `InlineValuesProvider`를 등록합니다. */
    fun registerInlineValuesProvider(handle: Int, selector: List<Map<String, Any?>>, eventHandle: Int?)
    fun emitInlineValuesEvent(eventHandle: Int, event: Any?)

    /** 문서 내에서 특정 심볼과 관련된 다른 부분을 하이라이트하는 `DocumentHighlightProvider`를 등록합니다. */
    fun registerDocumentHighlightProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 여러 문서에 걸쳐 하이라이트를 제공하는 `MultiDocumentHighlightProvider`를 등록합니다. */
    fun registerMultiDocumentHighlightProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 코드 자동 완성을 제공하는 `CompletionItemProvider`를 등록합니다. */
    fun registerCompletionsProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        triggerCharacters: List<String>,
        supportsResolveDetails: Boolean,
        extensionId: ExtensionIdentifier
    )

    /** 인라인 코드 자동 완성을 제공하는 `InlineCompletionsProvider`를 등록합니다. */
    fun registerInlineCompletionsSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsHandleDidShowCompletionItem: Boolean,
        extensionId: String,
        yieldsToExtensionIds: List<String>,
        displayName: String?,
        debounceDelayMs: Int?
    )

    /** 함수나 메소드 호출 시 파라미터 정보를 보여주는 `SignatureHelpProvider`를 등록합니다. */
    fun registerSignatureHelpProvider(handle: Int, selector: List<Map<String, Any?>>, metadata: Map<String, Any?>)

    /** 코드 에디터 내에 추가적인 정보(힌트)를 표시하는 `InlayHintsProvider`를 등록합니다. */
    fun registerInlayHintsProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsResolve: Boolean,
        eventHandle: Int?,
        displayName: String?
    )

    fun emitInlayHintsEvent(eventHandle: Int)

    // --- 코드 수정 및 리팩토링 기능 ---

    /** 빠른 수정(Quick Fix)이나 리팩토링 등 코드 액션을 제공하는 `CodeActionProvider`를 등록합니다. */
    fun registerCodeActionSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        metadata: Map<String, Any?>,
        displayName: String,
        extensionID: String,
        supportsResolve: Boolean
    )

    /** 붙여넣기 시 텍스트를 변환하거나 추가 동작을 수행하는 `PasteEditProvider`를 등록합니다. */
    fun registerPasteEditProvider(handle: Int, selector: List<Map<String, Any?>>, metadata: Map<String, Any?>)

    /** 문서 전체의 서식을 맞춰주는 `DocumentFormattingEditProvider`를 등록합니다. */
    fun registerDocumentFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String
    )

    /** 선택된 영역의 서식을 맞춰주는 `DocumentRangeFormattingEditProvider`를 등록합니다. */
    fun registerRangeFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String,
        supportRanges: Boolean
    )

    /** 타이핑 시 자동으로 서식을 맞춰주는 `OnTypeFormattingEditProvider`를 등록합니다. */
    fun registerOnTypeFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        autoFormatTriggerCharacters: List<String>,
        extensionId: ExtensionIdentifier
    )

    /** 심볼의 이름을 변경(rename)하는 `RenameProvider`를 등록합니다. */
    fun registerRenameSupport(handle: Int, selector: List<Map<String, Any?>>, supportsResolveInitialValues: Boolean)

    /** 이름 변경 시 새로운 심볼 이름을 제안하는 `NewSymbolNamesProvider`를 등록합니다. */
    fun registerNewSymbolNamesProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 인라인 수정을 제공하는 `InlineEditProvider`를 등록합니다. */
    fun registerInlineEditProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String
    )

    // --- 기타 언어 기능 ---

    /** 디버깅 시 표현식을 평가하는 `EvaluatableExpressionProvider`를 등록합니다. */
    fun registerEvaluatableExpressionProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 연결된 심볼(예: HTML 태그)을 동시에 편집하는 `LinkedEditingRangeProvider`를 등록합니다. */
    fun registerLinkedEditingRangeProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 'Go to Type'와 같은 타입 탐색 기능을 제공하는 `NavigateTypeSupport`를 등록합니다. */
    fun registerNavigateTypeSupport(handle: Int, supportsResolve: Boolean)

    /** 시맨틱 토큰(변수, 함수, 타입 등을 종류별로 색상화) 정보를 제공하는 `DocumentSemanticTokensProvider`를 등록합니다. */
    fun registerDocumentSemanticTokensProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        legend: Map<String, Any?>,
        eventHandle: Int?
    )

    fun emitDocumentSemanticTokensEvent(eventHandle: Int)

    /** 선택된 범위의 시맨틱 토큰 정보를 제공하는 `DocumentRangeSemanticTokensProvider`를 등록합니다. */
    fun registerDocumentRangeSemanticTokensProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        legend: Map<String, Any?>
    )

    /** 문서 내의 URL 등을 클릭 가능한 링크로 만들어주는 `DocumentLinkProvider`를 등록합니다. */
    fun registerDocumentLinkProvider(handle: Int, selector: List<Map<String, Any?>>, supportsResolve: Boolean)

    /** 코드 내의 색상 값을 시각적으로 보여주고 편집할 수 있게 하는 `DocumentColorProvider`를 등록합니다. */
    fun registerDocumentColorProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 코드 블록을 접고 펼 수 있는 영역을 계산하는 `FoldingRangeProvider`를 등록합니다. */
    fun registerFoldingRangeProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        eventHandle: Int?
    )

    fun emitFoldingRangeEvent(eventHandle: Int, event: Any?)

    /** 의미적으로 연관된 코드 블록을 선택(확대/축소)할 수 있게 하는 `SelectionRangeProvider`를 등록합니다. */
    fun registerSelectionRangeProvider(handle: Int, selector: List<Map<String, Any?>>)

    /** 파일 드롭 시 편집 동작을 제공하는 `DocumentOnDropEditProvider`를 등록합니다. */
    fun registerDocumentOnDropEditProvider(handle: Int, selector: List<Map<String, Any?>>, metadata: Map<String, Any?>?)

    /** 붙여넣기 시 파일 데이터를 해석합니다. */
    fun resolvePasteFileData(handle: Int, requestId: Int, dataId: String): ByteArray

    /** 파일 드롭 시 파일 데이터를 해석합니다. */
    fun resolveDocumentOnDropFileData(handle: Int, requestId: Int, dataId: String): ByteArray

    /** 특정 언어에 대한 설정(예: 주석 문자, 괄호 쌍 등)을 구성합니다. */
    fun setLanguageConfiguration(handle: Int, languageId: String, configuration: Map<String, Any?>)
}

/**
 * `MainThreadLanguageFeaturesShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며, 실제 기능은 구현되어 있지 않습니다.
 * 향후 IntelliJ의 언어 서비스 API와 연동하여 각 기능 제공자를 등록하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadLanguageFeatures : MainThreadLanguageFeaturesShape {
    private val logger = Logger.getInstance(MainThreadLanguageFeatures::class.java)

    // 아래의 모든 메소드는 현재 로깅만 수행합니다.
    override fun unregister(handle: Int) {
        logger.info("서비스 해제: handle=$handle")
    }

    override fun registerDocumentSymbolProvider(handle: Int, selector: List<Map<String, Any?>>, label: String) {
        logger.info("DocumentSymbolProvider 등록: handle=$handle")
    }

    override fun registerCodeLensSupport(handle: Number, selector: List<Map<String, Any?>>, eventHandle: Number?) {
        logger.info("CodeLensProvider 등록: handle=$handle")
    }

    override fun emitCodeLensEvent(eventHandle: Int, event: Any?) {
        logger.info("CodeLens 이벤트 발생: eventHandle=$eventHandle, event=$event")
    }

    override fun registerDefinitionSupport(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("DefinitionProvider 등록: handle=$handle, selector=$selector")
    }

    override fun registerDeclarationSupport(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("DeclarationProvider 등록: handle=$handle, selector=$selector")
    }

    override fun registerImplementationSupport(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("ImplementationProvider 등록: handle=$handle, selector=$selector")
    }

    override fun registerTypeDefinitionSupport(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("TypeDefinitionProvider 등록: handle=$handle, selector=$selector")
    }

    override fun registerHoverProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("HoverProvider 등록: handle=$handle, selector=$selector")
    }

    override fun registerEvaluatableExpressionProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("EvaluatableExpression provider 등록: handle=$handle, selector=$selector")
    }

    override fun registerInlineValuesProvider(handle: Int, selector: List<Map<String, Any?>>, eventHandle: Int?) {
        logger.info("Inline values provider 등록: handle=$handle, selector=$selector, eventHandle=$eventHandle")
    }

    override fun emitInlineValuesEvent(eventHandle: Int, event: Any?) {
        logger.info("Emitting inline values event: eventHandle=$eventHandle, event=$event")
    }

    override fun registerDocumentHighlightProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering document highlight provider: handle=$handle, selector=$selector")
    }

    override fun registerMultiDocumentHighlightProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering multi-document highlight provider: handle=$handle, selector=$selector")
    }

    override fun registerLinkedEditingRangeProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering linked editing range provider: handle=$handle, selector=$selector")
    }

    override fun registerReferenceSupport(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering reference support: handle=$handle, selector=$selector")
    }

    override fun registerCodeActionSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        metadata: Map<String, Any?>,
        displayName: String,
        extensionID: String,
        supportsResolve: Boolean
    ) {
        logger.info("Registering code action support: handle=$handle, selector=$selector, metadata=$metadata, displayName=$displayName, extensionID=$extensionID, supportsResolve=$supportsResolve")
    }

    override fun registerPasteEditProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        metadata: Map<String, Any?>
    ) {
        logger.info("Registering paste edit provider: handle=$handle, selector=$selector, metadata=$metadata")
    }

    override fun registerDocumentFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String
    ) {
        logger.info("Registering document formatting support: handle=$handle, selector=$selector, extensionId=${extensionId.value}, displayName=$displayName")
    }

    override fun registerRangeFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String,
        supportRanges: Boolean
    ) {
        logger.info("Registering range formatting support: handle=$handle, selector=$selector, extensionId=${extensionId.value}, displayName=$displayName, supportRanges=$supportRanges")
    }

    override fun registerOnTypeFormattingSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        autoFormatTriggerCharacters: List<String>,
        extensionId: ExtensionIdentifier
    ) {
        logger.info("Registering on-type formatting support: handle=$handle, selector=$selector, autoFormatTriggerCharacters=$autoFormatTriggerCharacters, extensionId=${extensionId.value}")
    }

    override fun registerNavigateTypeSupport(handle: Int, supportsResolve: Boolean) {
        logger.info("Registering navigate type support: handle=$handle, supportsResolve=$supportsResolve")
    }

    override fun registerRenameSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsResolveInitialValues: Boolean
    ) {
        logger.info("Registering rename support: handle=$handle, selector=$selector, supportsResolveInitialValues=$supportsResolveInitialValues")
    }

    override fun registerNewSymbolNamesProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering new symbol names provider: handle=$handle, selector=$selector")
    }

    override fun registerDocumentSemanticTokensProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        legend: Map<String, Any?>,
        eventHandle: Int?
    ) {
        logger.info("Registering document semantic tokens provider: handle=$handle, selector=$selector, legend=$legend, eventHandle=$eventHandle")
    }

    override fun emitDocumentSemanticTokensEvent(eventHandle: Int) {
        logger.info("Emitting document semantic tokens event: eventHandle=$eventHandle")
    }

    override fun registerDocumentRangeSemanticTokensProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        legend: Map<String, Any?>
    ) {
        logger.info("Registering document range semantic tokens provider: handle=$handle, selector=$selector, legend=$legend")
    }

    override fun registerCompletionsProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        triggerCharacters: List<String>,
        supportsResolveDetails: Boolean,
        extensionId: ExtensionIdentifier
    ) {
        logger.info("Registering completions provider: handle=$handle, selector=$selector, triggerCharacters=$triggerCharacters, supportsResolveDetails=$supportsResolveDetails, extensionId=${extensionId.value}")
    }

    override fun registerInlineCompletionsSupport(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsHandleDidShowCompletionItem: Boolean,
        extensionId: String,
        yieldsToExtensionIds: List<String>,
        displayName: String?,
        debounceDelayMs: Int?
    ) {
        logger.info("Registering inline completions support: handle=$handle, selector=$selector, supportsHandleDidShowCompletionItem=$supportsHandleDidShowCompletionItem, extensionId=$extensionId, yieldsToExtensionIds=$yieldsToExtensionIds, displayName=$displayName, debounceDelayMs=$debounceDelayMs")
    }

    override fun registerInlineEditProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        displayName: String
    ) {
        logger.info("Registering inline edit provider: handle=$handle, selector=$selector, extensionId=${extensionId.value}, displayName=$displayName")
    }

    override fun registerSignatureHelpProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        metadata: Map<String, Any?>
    ) {
        logger.info("Registering signature help provider: handle=$handle, selector=$selector, metadata=$metadata")
    }

    override fun registerInlayHintsProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsResolve: Boolean,
        eventHandle: Int?,
        displayName: String?
    ) {
        logger.info("Registering inlay hints provider: handle=$handle, selector=$selector, supportsResolve=$supportsResolve, eventHandle=$eventHandle, displayName=$displayName")
    }

    override fun emitInlayHintsEvent(eventHandle: Int) {
        logger.info("Emitting inlay hints event: eventHandle=$eventHandle")
    }

    override fun registerDocumentLinkProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        supportsResolve: Boolean
    ) {
        logger.info("Registering document link provider: handle=$handle, selector=$selector, supportsResolve=$supportsResolve")
    }

    override fun registerDocumentColorProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering document color provider: handle=$handle, selector=$selector")
    }

    override fun registerFoldingRangeProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        extensionId: ExtensionIdentifier,
        eventHandle: Int?
    ) {
        logger.info("Registering folding range provider: handle=$handle, selector=$selector, extensionId=${extensionId.value}, eventHandle=$eventHandle")
    }

    override fun emitFoldingRangeEvent(eventHandle: Int, event: Any?) {
        logger.info("Emitting folding range event: eventHandle=$eventHandle, event=$event")
    }

    override fun registerSelectionRangeProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering selection range provider: handle=$handle, selector=$selector")
    }

    override fun registerCallHierarchyProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering call hierarchy provider: handle=$handle, selector=$selector")
    }

    override fun registerTypeHierarchyProvider(handle: Int, selector: List<Map<String, Any?>>) {
        logger.info("Registering type hierarchy provider: handle=$handle, selector=$selector")
    }

    override fun registerDocumentOnDropEditProvider(
        handle: Int,
        selector: List<Map<String, Any?>>,
        metadata: Map<String, Any?>?
    ) {
        logger.info("Registering document on drop edit provider: handle=$handle, selector=$selector, metadata=$metadata")
    }

    override fun resolvePasteFileData(handle: Int, requestId: Int, dataId: String): ByteArray {
        logger.info("Resolving paste file data: handle=$handle, requestId=$requestId, dataId=$dataId")
        return ByteArray(0) // Return empty array, actual implementation needs to handle real file data
    }

    override fun resolveDocumentOnDropFileData(handle: Int, requestId: Int, dataId: String): ByteArray {
        logger.info("Resolving document on drop file data: handle=$handle, requestId=$requestId, dataId=$dataId")
        return ByteArray(0) // Return empty array, actual implementation needs to handle real file data
    }

    override fun setLanguageConfiguration(handle: Int, languageId: String, configuration: Map<String, Any?>) {
        logger.info("Setting language configuration: handle=$handle, languageId=$languageId, configuration=$configuration")
    }

    override fun dispose() {
        logger.info("Disposing MainThreadLanguageFeatures resources")
    }
}
