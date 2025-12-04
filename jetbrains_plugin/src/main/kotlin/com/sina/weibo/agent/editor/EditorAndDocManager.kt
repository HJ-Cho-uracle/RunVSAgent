// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.FileDocumentContentImpl
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.sina.weibo.agent.util.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * IntelliJ 프로젝트 내에서 에디터와 문서의 생명주기를 관리하고,
 * Extension Host와 에디터/문서 상태를 동기화하는 서비스입니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class EditorAndDocManager(val project: Project) : Disposable {

    private val logger = Logger.getInstance(EditorAndDocManager::class.java)
    private val messageBusConnection = project.messageBus.connect()

    // 현재 문서 및 에디터의 상태를 저장하는 객체
    private var state = DocumentsAndEditorsState()

    // 마지막으로 Extension Host에 알린 문서 및 에디터의 상태
    private var lastNotifiedState = DocumentsAndEditorsState()

    // 에디터 ID를 키로 하는 EditorHolder 객체 맵
    private var editorHandles = ConcurrentHashMap<String, EditorHolder>()

    // IntelliJ에서 열려있는 에디터 맵 (주로 Diff 에디터용)
    private val ideaOpenedEditor = ConcurrentHashMap<String, Editor>()

    // 탭 상태를 관리하는 매니저
    private var tabManager: TabStateManager = TabStateManager(project)

    private var job: Job? = null // 업데이트 스케줄링을 위한 코루틴 Job
    private val editorStateService: EditorStateService = EditorStateService(project) // 에디터 상태를 Extension Host에 알리는 서비스

    // IntelliJ의 파일 에디터 관리자 리스너
    private val ideaEditorListener: FileEditorManagerListener = object : FileEditorManagerListener {
        /**
         * 파일이 열렸을 때 호출됩니다.
         * 열린 파일이 Diff 에디터인지 일반 에디터인지 판단하여 EditorHolder를 생성하고 Extension Host와 동기화합니다.
         */
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            source.getEditorList(file).forEach { editor ->
                if (file == editor.file) {
                    // Diff 에디터인지 확인
                    if (isSubClassof(editor, "com.intellij.diff.editor.DiffEditorBase") || isSubClassof(editor, "com.intellij.diff.editor.DiffFileEditorBase")) {
                        // Diff 에디터 처리 로직
                        if (editor.filesToRefresh.size == 1) {
                            val reffile = editor.filesToRefresh[0]
                            val uri = URI.file(reffile.path)
                            val older = getEditorHandleByUri(uri, true)
                            if (older != null && older.ideaEditor == null) {
                                older.ideaEditor = editor
                            }
                        }
                    } else {
                        // 일반 에디터 처리 로직
                        val older = getEditorHandleByUri(URI.file(file.path), false)
                        if (older == null) {
                            val uri = URI.file(editor.file.path)
                            val isText = FileDocumentManager.getInstance().getDocument(file) != null
                            CoroutineScope(Dispatchers.IO).launch {
                                val handle = sync2ExtHost(uri, false, isText)
                                handle.ideaEditor = editor
                                val group = tabManager.createTabGroup(EditorGroupColumn.BESIDE.value, true)
                                val options = TabOptions(isActive = true)
                                val tab = group.addTab(EditorTabInput(uri, uri.path, ""), options)
                                handle.tab = tab
                                handle.group = group
                            }
                        }
                    }
                }
            }
        }

        /**
         * 주어진 FileEditor가 특정 클래스의 서브클래스인지 확인합니다.
         * @param editor 확인할 FileEditor 인스턴스
         * @param s 확인할 클래스의 전체 이름 (문자열)
         * @return 서브클래스이면 true
         */
        private fun isSubClassof(editor: FileEditor?, s: String): Boolean {
            if (editor == null) return false
            var clazz: Class<*>? = editor.javaClass
            while (clazz != null) {
                if (clazz.name == s) {
                    return true
                }
                clazz = clazz.superclass
            }
            return false
        }

        /**
         * 파일이 닫혔을 때 호출됩니다.
         * 해당 파일에 연결된 EditorHolder를 제거하고 상태를 업데이트합니다.
         */
        override fun fileClosed(source: FileEditorManager, cFile: VirtualFile) {
            logger.info("파일 닫힘: $cFile")
            var diff = false
            var path = cFile.path
            // Diff 에디터인 경우 실제 파일 경로를 추출합니다.
            if (cFile is ChainDiffVirtualFile) {
                (cFile.chain.requests[0] as? SimpleDiffRequest).let {
                    it?.contents?.forEach { content ->
                        if (content is FileDocumentContentImpl) {
                            path = content.file.path
                            diff = true
                        }
                    }
                }
            }
            getEditorHandleByUri(URI.file(path), diff)?.let { handle ->
                handle.setActive(false)
                logger.info("파일 닫힘 핸들: $handle")
                removeEditor(handle.id)
            }
        }
    }

    init {
        // 파일 에디터 관리자 리스너를 등록합니다.
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, ideaEditorListener)
    }

    /**
     * 현재 IntelliJ에 열려있는 모든 에디터를 초기화하고 Extension Host와 동기화합니다.
     */
    fun initCurrentIdeaEditor() {
        CoroutineScope(Dispatchers.Default).launch {
            FileEditorManager.getInstance(project).allEditors.forEach { editor ->
                if (editor is FileEditor) {
                    val uri = URI.file(editor.file.path)
                    val handle = sync2ExtHost(uri, false)
                    handle.ideaEditor = editor
                    val group = tabManager.createTabGroup(EditorGroupColumn.BESIDE.value, true)
                    val options = TabOptions(isActive = true)
                    val tab = group.addTab(EditorTabInput(uri, uri.path, ""), options)
                    handle.tab = tab
                    handle.group = group
                }
            }
        }
    }

    /**
     * Extension Host와 에디터/문서 상태를 동기화합니다.
     * @param documentUri 동기화할 문서의 URI
     * @param diff Diff 에디터인지 여부
     * @param isText 텍스트 파일인지 여부
     * @param options 에디터 설정 옵션
     * @return 동기화된 `EditorHolder` 객체
     */
    suspend fun sync2ExtHost(documentUri: URI, diff: Boolean, isText: Boolean = true, options: ResolvedTextEditorConfiguration = ResolvedTextEditorConfiguration()): EditorHolder {
        val eh = getEditorHandleByUri(documentUri, diff)
        if (eh != null) return eh

        val id = java.util.UUID.randomUUID().toString() // 고유 ID 생성
        val documentState = openDocument(documentUri, isText) // 문서 상태 열기

        val editorState = TextEditorAddData(
            id = id,
            documentUri = documentUri,
            options = options,
            selections = emptyList(),
            visibleRanges = emptyList(),
            editorPosition = null,
        )
        val handle = EditorHolder(id, editorState, documentState, diff, this)
        state.documents[documentUri] = documentState
        state.editors[id] = editorState
        editorHandles[id] = handle
        handle.setActive(true)
        processUpdates() // 상태 업데이트를 Extension Host에 알립니다.
        return handle
    }

    /**
     * URI를 바탕으로 DiffContent 객체를 생성합니다.
     * @param uri 문서의 URI
     * @param project 현재 프로젝트
     * @param type 파일 타입 (선택 사항)
     * @return 생성된 DiffContent 객체 또는 null
     */
    fun createContent(uri: URI, project: Project, type: FileType? = null): DiffContent? {
        val path = uri.path
        val scheme = uri.scheme
        val query = uri.query
        if (scheme != null && scheme.isNotEmpty()) {
            val contentFactory = DiffContentFactory.getInstance()
            if (scheme == "file") {
                val vfs = LocalFileSystem.getInstance()
                val fileIO = File(path)
                if (!fileIO.exists()) {
                    fileIO.createNewFile()
                    vfs.refreshIoFiles(listOf(fileIO.parentFile))
                }
                val file = vfs.refreshAndFindFileByPath(path) ?: run {
                    logger.warn("파일을 찾을 수 없음: $path")
                    return null
                }
                return contentFactory.create(project, file)
            } else if (scheme == "cline-diff") {
                val string = if (query != null) {
                    val bytes = java.util.Base64.getDecoder().decode(query)
                    String(bytes)
                } else {
                    ""
                }
                val content = contentFactory.create(project, string, type)
                return content
            }
            return null
        } else {
            return null
        }
    }

    /**
     * 지정된 URI의 문서를 에디터로 엽니다.
     * @param documentUri 열 문서의 URI
     * @param options 에디터 설정 옵션
     * @return 열린 문서에 대한 `EditorHolder` 객체
     */
    suspend fun openEditor(documentUri: URI, options: ResolvedTextEditorConfiguration = ResolvedTextEditorConfiguration()): EditorHolder {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val path = documentUri.path
        var ideaEditor: Array<FileEditor?>? = null

        val vfs = LocalFileSystem.getInstance()
        val file = vfs.findFileByPath(path)
        file?.let {
            ApplicationManager.getApplication().invokeAndWait {
                ideaEditor = fileEditorManager.openFile(it, true) // IntelliJ 에디터로 파일 열기
            }
        }
        val eh = getEditorHandleByUri(documentUri, false)
        if (eh != null) return eh
        val handle = sync2ExtHost(documentUri, false, true, options)
        ideaEditor?.let {
            if (it.isNotEmpty()) {
                handle.ideaEditor = it[0]
            }
        }
        val group = tabManager.createTabGroup(EditorGroupColumn.BESIDE.value, true)
        val options = TabOptions(isActive = true)
        val tab = group.addTab(EditorTabInput(documentUri, documentUri.path, ""), options)
        handle.tab = tab
        handle.group = group
        return handle
    }

    /**
     * Diff 에디터를 엽니다.
     * @param left 왼쪽 패널에 표시할 문서의 URI
     * @param documentUri 오른쪽 패널에 표시할 문서의 URI
     * @param title Diff 에디터의 제목
     * @param options 에디터 설정 옵션
     * @return 열린 Diff 에디터에 대한 `EditorHolder` 객체
     */
    suspend fun openDiffEditor(left: URI, documentUri: URI, title: String, options: ResolvedTextEditorConfiguration = ResolvedTextEditorConfiguration()): EditorHolder {
        val content2 = createContent(documentUri, project)
        val content1 = createContent(left, project, content2?.contentType)
        if (content1 != null && content2 != null) {
            val request = SimpleDiffRequest(title, content1, content2, left.path, documentUri.path)
            var ideaEditor: Array<out FileEditor?>? = null
            ApplicationManager.getApplication().invokeAndWait {
                // 기존 파일을 닫고 Diff 에디터를 엽니다.
                LocalFileSystem.getInstance().findFileByPath(documentUri.path)
                    ?.let {
                        ApplicationManager.getApplication().runReadAction { FileEditorManager.getInstance(project).closeFile(it) }
                    }

                val diffEditorTabFilesManager = DiffEditorTabFilesManager.getInstance(project)
                val requestChain: DiffRequestChain = SimpleDiffRequestChain(request)
                val diffFile = ChainDiffVirtualFile(requestChain, DiffBundle.message("label.default.diff.editor.tab.name", *arrayOfNulls<Any>(0)))
                ideaEditor = diffEditorTabFilesManager.showDiffFile(diffFile, true)
            }
            ideaEditor?.let {
                val handle = sync2ExtHost(documentUri, true, true, options)
                if (it.isNotEmpty()) {
                    handle.ideaEditor = it[0]
                }
                handle.title = title

                val group = tabManager.createTabGroup(EditorGroupColumn.BESIDE.value, true)
                val options = TabOptions(isActive = true)
                val tab = group.addTab(TextDiffTabInput(left, documentUri), options)
                handle.tab = tab
                handle.group = group
                return handle
            } ?: run {
                val handle = sync2ExtHost(documentUri, true, true, options)
                return handle
            }
        } else {
            val handle = sync2ExtHost(documentUri, true, true, options)
            return handle
        }
    }

    /**
     * URI와 Diff 여부를 기준으로 `EditorHolder`를 찾습니다.
     * @param resource 찾을 문서의 URI
     * @param diff Diff 에디터인지 여부
     * @return 해당하는 `EditorHolder` 객체 또는 null
     */
    fun getEditorHandleByUri(resource: URI, diff: Boolean): EditorHolder? {
        val values = editorHandles.values
        for (handle in values) {
            if (handle.document.uri.path == resource.path && handle.diff == diff) {
                return handle
            }
        }
        return null
    }

    /**
     * URI를 기준으로 모든 `EditorHolder`를 찾습니다.
     * @param resource 찾을 문서의 URI
     * @return 해당하는 `EditorHolder` 객체 리스트
     */
    fun getEditorHandleByUri(resource: URI): List<EditorHolder> {
        val list = mutableListOf<EditorHolder>()
        val values = editorHandles.values
        for (handle in values) {
            if (handle.document.uri.path == resource.path) {
                list.add(handle)
            }
        }
        return list
    }

    /**
     * ID를 기준으로 `EditorHolder`를 찾습니다.
     * @param id 찾을 에디터의 ID
     * @return 해당하는 `EditorHolder` 객체 또는 null
     */
    fun getEditorHandleById(id: String): EditorHolder? {
        return editorHandles[id]
    }

    /**
     * 지정된 URI의 문서를 열고 `ModelAddedData` 객체를 생성합니다.
     * @param uri 열 문서의 URI
     * @param isText 텍스트 파일인지 여부
     * @return 열린 문서의 `ModelAddedData` 객체
     */
    suspend fun openDocument(uri: URI, isText: Boolean = true): ModelAddedData {
        val text = if (isText) {
            ApplicationManager.getApplication().runReadAction<String> {
                val vfs = LocalFileSystem.getInstance()
                val file = vfs.findFileByPath(uri.path)
                if (file != null) {
                    // 파일 크기가 3MB를 초과하면 일부만 읽어옵니다.
                    val len = file.length
                    if (len > 3 * 1024 * 1024) {
                        val buffer = ByteArray(3 * 1024 * 1024)
                        val inputStream = FileInputStream(File(file.path))
                        val bytesRead = inputStream.read(buffer)
                        inputStream.close()
                        String(buffer, 0, bytesRead, Charsets.UTF_8)
                    } else {
                        file.readText()
                    }
                } else {
                    ""
                }
            }
        } else {
            "bin" // 텍스트 파일이 아니면 "bin"이라는 더미 텍스트를 사용
        }
        if (state.documents[uri] == null) {
            val document = ModelAddedData(
                uri = uri,
                versionId = 1,
                lines = text.lines(),
                EOL = "\n",
                languageId = "",
                isDirty = false,
                encoding = "utf8",
            )
            state.documents[uri] = document
            processUpdates() // 상태 업데이트를 Extension Host에 알립니다.
        }
        return state.documents[uri]!!
    }

    /**
     * 지정된 ID의 에디터를 제거합니다.
     * @param id 제거할 에디터의 ID
     */
    fun removeEditor(id: String) {
        state.editors.remove(id)
        val handler = editorHandles.remove(id)
        var needDeleteDoc = true
        // 해당 문서에 연결된 다른 에디터가 없으면 문서도 제거합니다.
        editorHandles.values.forEach { value ->
            if (value.document.uri == handler?.document?.uri) {
                needDeleteDoc = false
            }
        }
        if (needDeleteDoc) {
            state.documents.remove(handler?.document?.uri)
        }
        if (state.activeEditorId == id) {
            state.activeEditorId = null
        }
        scheduleUpdate() // 상태 업데이트를 스케줄링합니다.

        handler?.tab?.let {
            tabManager.removeTab(it.id)
        }
        handler?.group?.let {
            tabManager.removeGroup(it.groupId)
        }
    }

    /**
     * Extension Host로부터 탭 닫기 요청을 받아 처리합니다.
     * @param id 닫을 탭의 ID
     */
    fun closeTab(id: String) {
        val tab = tabManager.removeTab(id)
        tab?.let { tab ->
            val handler = getEditorHandleByTabId(id)
            handler?.let {
                state.editors.remove(it.id)
                val removedHandler = editorHandles.remove(it.id)
                this.state.documents.remove(it.document.uri)
                if (state.activeEditorId == it.id) {
                    state.activeEditorId = null
                }
                removedHandler?.let { h ->
                    // IntelliJ 에디터를 닫습니다.
                    if (h.ideaEditor != null) {
                        ApplicationManager.getApplication().invokeAndWait {
                            h.ideaEditor?.dispose()
                        }
                    } else {
                        // Diff 에디터인 경우 특별 처리
                        ApplicationManager.getApplication().invokeAndWait {
                            FileEditorManager.getInstance(project).allEditors.forEach {
                                if (it is DiffRequestProcessorEditor && h.diff) {
                                    val differ = it
                                    differ.processor.activeRequest?.let { req ->
                                        for (filesToRefresh in req.filesToRefresh) {
                                            if (filesToRefresh.path == h.document.uri.path) {
                                                differ.dispose()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                scheduleUpdate()
            }
        }
    }

    /**
     * 탭 그룹을 닫습니다.
     * @param id 닫을 그룹의 ID
     */
    fun closeGroup(id: Int) {
        tabManager.removeGroup(id)
    }

    /**
     * 탭 ID를 기준으로 `EditorHolder`를 찾습니다.
     * @param id 탭 ID
     * @return 해당하는 `EditorHolder` 객체 또는 null
     */
    private fun getEditorHandleByTabId(id: String): EditorHolder? {
        for ((_, handle) in editorHandles) {
            if (handle.tab != null && handle.tab?.id == id) {
                return handle
            }
        }
        return null
    }

    /**
     * 리소스를 해제합니다. 메시지 버스 연결을 끊습니다.
     */
    override fun dispose() {
        messageBusConnection.dispose()
    }

    /**
     * 에디터의 활성 상태가 변경되었을 때 호출됩니다.
     * @param handle 활성 상태가 변경된 `EditorHolder`
     */
    fun didUpdateActive(handle: EditorHolder) {
        if (handle.isActive) {
            setActiveEditor(id = handle.id)
        } else if (state.activeEditorId == handle.id) {
            // 현재 활성 에디터가 비활성화되면, 다른 활성 에디터를 찾아 활성화합니다.
            editorHandles.values.firstOrNull { it.isActive }?.let {
                setActiveEditor(id = it.id)
            }
        }
    }

    /**
     * 활성 에디터를 설정하고 상태 업데이트를 스케줄링합니다.
     * @param id 활성화할 에디터의 ID
     */
    private fun setActiveEditor(id: String) {
        state.activeEditorId = id
        scheduleUpdate()
    }

    /**
     * 상태 업데이트를 스케줄링합니다. (디바운싱 적용)
     */
    private fun scheduleUpdate() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            delay(10) // 10ms 지연 후 업데이트
            processUpdates()
        }
    }

    /**
     * 현재 상태를 복사합니다.
     */
    private fun copy(state: DocumentsAndEditorsState): DocumentsAndEditorsState {
        val rst = DocumentsAndEditorsState(
            editors = ConcurrentHashMap(),
            documents = ConcurrentHashMap(),
            activeEditorId = state.activeEditorId,
        )
        rst.editors.putAll(state.editors)
        rst.documents.putAll(state.documents)
        return rst
    }

    /**
     * 현재 상태와 마지막으로 알린 상태를 비교하여 변경사항(델타)을 계산하고 Extension Host에 알립니다.
     */
    private suspend fun processUpdates() {
        val delta = state.delta(lastNotifiedState)

        lastNotifiedState = copy(state) // 마지막 알림 상태 업데이트

        delta.itemsDelta?.let { itemsDelta ->
            editorStateService.acceptDocumentsAndEditorsDelta(itemsDelta)
        }

        if (delta.editorDeltas.isNotEmpty()) {
            editorStateService.acceptEditorPropertiesChanged(delta.editorDeltas)
        }

        if (delta.documentDeltas.isNotEmpty()) {
            editorStateService.acceptModelChanged(delta.documentDeltas)
        }
    }

    /**
     * 문서 내용을 비동기적으로 업데이트하고 Extension Host에 알립니다.
     */
    suspend fun updateDocumentAsync(document: ModelAddedData) {
        if (state.documents[document.uri] != null) {
            state.documents[document.uri] = document
            processUpdates()
        }
    }

    /**
     * 문서 내용을 동기적으로 업데이트하고 Extension Host에 알립니다.
     */
    fun updateDocument(document: ModelAddedData) {
        if (state.documents[document.uri] != null) {
            state.documents[document.uri] = document
            scheduleUpdate()
        }
    }

    /**
     * 모든 업데이트를 즉시 동기화합니다.
     */
    suspend fun syncUpdates() {
        job?.cancel()
        processUpdates()
    }

    /**
     * 에디터 상태를 업데이트하고 Extension Host에 알립니다.
     */
    fun updateEditor(state: TextEditorAddData) {
        if (this.state.editors[state.id] != null) {
            this.state.editors[state.id] = state
            scheduleUpdate()
        }
    }

    /**
     * IntelliJ의 Diff 에디터 인스턴스를 가져옵니다.
     */
    fun getIdeaDiffEditor(uri: URI): WeakReference<Editor>? {
        val editor = ideaOpenedEditor[uri.path] ?: return null
        return WeakReference(editor)
    }

    /**
     * IntelliJ Diff 에디터가 생성되었을 때 호출됩니다.
     */
    fun onIdeaDiffEditorCreated(url: URI, editor: Editor) {
        ideaOpenedEditor[url.path] = editor
    }

    /**
     * IntelliJ Diff 에디터가 해제되었을 때 호출됩니다.
     */
    fun onIdeaDiffEditorReleased(url: URI, editor: Editor) {
        ideaOpenedEditor.remove(url.path)
    }
}

/**
 * 현재 열려있는 문서와 에디터의 전체 상태를 나타내는 데이터 클래스입니다.
 */
data class DocumentsAndEditorsState(
    var editors: MutableMap<String, TextEditorAddData> = ConcurrentHashMap(),
    var documents: MutableMap<URI, ModelAddedData> = ConcurrentHashMap(),
    var activeEditorId: String? = null,
) {
    /**
     * 현재 상태와 이전 상태를 비교하여 변경사항(델타)을 계산합니다.
     * @param lastState 이전 상태
     * @return 변경사항을 담은 `Delta` 객체
     */
    fun delta(lastState: DocumentsAndEditorsState): Delta {
        // 문서 변경사항 계산
        val currentDocumentUrls = documents.keys.toSet()
        val lastDocumentUrls = lastState.documents.keys.toSet()

        val removedUrls = lastDocumentUrls - currentDocumentUrls
        val addedUrls = currentDocumentUrls - lastDocumentUrls

        val addedDocuments = addedUrls.mapNotNull { documents[it] }

        // 에디터 변경사항 계산
        val addedEditors = mutableListOf<TextEditorAddData>()
        val editorDeltas = mutableMapOf<String, EditorPropertiesChangeData>()

        val currentEditorIds = editors.keys.toSet()
        val lastEditorIds = lastState.editors.keys.toSet()

        val removedIds = lastEditorIds - currentEditorIds

        // 현재 에디터들을 순회하며 추가 및 속성 변경을 처리합니다.
        editors.forEach { (id, editor) ->
            lastState.editors[id]?.let { lastEditor ->
                // 옵션, 선택 영역, 가시 범위 변경 여부 확인
                val optionsChanged = editor.options != lastEditor.options
                val selectionsChanged = editor.selections != lastEditor.selections
                val visibleRangesChanged = editor.visibleRanges != lastEditor.visibleRanges

                // 변경사항이 있으면 EditorPropertiesChangeData를 생성합니다.
                if (optionsChanged || selectionsChanged || visibleRangesChanged) {
                    editorDeltas[id] = EditorPropertiesChangeData(
                        options = if (optionsChanged) editor.options else null,
                        selections = if (selectionsChanged) {
                            SelectionChangeEvent(
                                selections = editor.selections,
                                source = null,
                            )
                        } else {
                            null
                        },
                        visibleRanges = if (visibleRangesChanged) editor.visibleRanges else null,
                    )
                }
            } ?: run {
                // 새로 추가된 에디터
                addedEditors.add(editor)
            }
        }

        // 문서 내용 변경사항 계산
        val documentDeltas = mutableMapOf<URI, ModelChangedEvent>()

        documents.forEach { (uri, document) ->
            lastState.documents[uri]?.let { lastDocument ->
                val hasChanges = document.lines != lastDocument.lines ||
                    document.EOL != lastDocument.EOL ||
                    document.languageId != lastDocument.languageId ||
                    document.isDirty != lastDocument.isDirty ||
                    document.encoding != lastDocument.encoding

                if (hasChanges) {
                    // 내용이 변경되었으면 전체 문서를 변경된 것으로 간주합니다.
                    val changes = listOf(
                        ModelContentChange(
                            range = Range(
                                startLineNumber = 1,
                                startColumn = 1,
                                endLineNumber = max(1, lastDocument.lines.size),
                                endColumn = max(1, (lastDocument.lines.lastOrNull()?.length ?: 0) + 1),
                            ),
                            rangeOffset = 0,
                            rangeLength = lastDocument.lines.joinToString(lastDocument.EOL).length,
                            text = document.lines.joinToString(document.EOL),
                        ),
                    )

                    documentDeltas[uri] = ModelChangedEvent(
                        changes = changes,
                        eol = document.EOL,
                        versionId = document.versionId,
                        isUndoing = false,
                        isRedoing = false,
                        isDirty = document.isDirty,
                    )
                }
            }
        }

        val itemsDelta = DocumentsAndEditorsDelta(
            removedDocuments = removedUrls.toList(),
            addedDocuments = addedDocuments,
            removedEditors = removedIds.toList(),
            addedEditors = addedEditors,
            newActiveEditor = if (activeEditorId != lastState.activeEditorId) activeEditorId else null,
        )

        return Delta(
            itemsDelta = if (itemsDelta.isEmpty()) null else itemsDelta,
            editorDeltas = editorDeltas,
            documentDeltas = documentDeltas,
        )
    }
}

/**
 * 문서 및 에디터 상태의 변경사항(델타)을 나타내는 데이터 클래스입니다.
 */
data class Delta(
    val itemsDelta: DocumentsAndEditorsDelta?,
    val editorDeltas: MutableMap<String, EditorPropertiesChangeData>,
    val documentDeltas: MutableMap<URI, ModelChangedEvent>,
)
