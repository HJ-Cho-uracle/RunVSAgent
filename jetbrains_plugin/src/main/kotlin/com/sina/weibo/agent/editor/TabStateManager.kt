// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 탭 상태 관리자(Tab State Manager) 클래스입니다.
 * 에디터 탭과 탭 그룹의 상태를 관리하며, 탭 및 탭 그룹의 생성, 업데이트, 이동, 제거와 같은 작업을 처리합니다.
 */
class TabStateManager(var project: Project) {
    private val logger = Logger.getInstance(TabStateManager::class.java)

    // --- 상태 저장소 ---
    private var state = TabsState() // 모든 탭 그룹의 상태를 담는 객체
    private val tabHandles = ConcurrentHashMap<String, TabHandle>() // 탭 ID를 키로 하는 TabHandle 맵
    private val groupHandles = ConcurrentHashMap<Int, TabGroupHandle>() // 그룹 ID를 키로 하는 TabGroupHandle 맵
    private val tabStateService: TabStateService // 탭 상태 변경을 Extension Host에 알리는 서비스

    init {
        tabStateService = TabStateService(project)
    }

    // --- ID 생성 ---
    private var nextGroupId = 1 // 다음 그룹 ID를 생성하기 위한 카운터

    // --- 공개 메소드 ---

    /**
     * 새로운 탭 그룹을 생성합니다.
     *
     * @param viewColumn 그룹이 위치할 뷰 컬럼 (예: -1=active, -2=beside)
     * @param isActive 이 그룹을 활성 그룹으로 설정할지 여부
     * @return 생성된 탭 그룹에 대한 `TabGroupHandle`
     */
    fun createTabGroup(viewColumn: Int, isActive: Boolean = false): TabGroupHandle {
        val groupId = nextGroupId++ // 새 그룹 ID 생성

        // `EditorTabGroupDto` 객체를 생성합니다.
        val group = EditorTabGroupDto(
            groupId = groupId,
            isActive = isActive,
            viewColumn = viewColumn,
            tabs = emptyList(),
        )
        state.groups[groupId] = group // 상태에 그룹 추가

        // 이 그룹이 활성 그룹으로 설정되면, 다른 그룹들은 비활성화합니다.
        if (isActive) {
            state.groups.forEach { (id, otherGroup) ->
                if (id != groupId) {
                    state.groups[id] = otherGroup.copy(isActive = false)
                }
            }
        }

        // `TabGroupHandle`을 생성하고 저장합니다.
        val handle = TabGroupHandle(groupId = groupId, manager = this)
        groupHandles[groupId] = handle

        // 모든 그룹의 업데이트된 상태를 Extension Host에 알립니다.
        tabStateService.acceptEditorTabModel(state.groups.values.toList())

        return handle
    }

    /**
     * 탭 그룹을 제거합니다.
     *
     * @param groupId 제거할 그룹의 ID
     */
    fun removeGroup(groupId: Int) {
        state.groups.remove(groupId)
        groupHandles.remove(groupId)

        // 이 그룹에 속한 모든 탭 핸들을 제거합니다.
        tabHandles.entries.removeAll { it.value.groupId == groupId }

        // 모든 그룹의 업데이트된 상태를 Extension Host에 알립니다. (현재 주석 처리됨)
//        tabStateService.acceptEditorTabModel(state.groups.values.toList())
    }

    /**
     * 그룹 ID를 사용하여 `TabGroupHandle`을 가져옵니다.
     *
     * @param groupId 그룹의 ID
     * @return `TabGroupHandle` 또는 찾지 못하면 null
     */
    fun getTabGroupHandle(groupId: Int): TabGroupHandle? = groupHandles[groupId]

    // --- 내부 메소드 ---

    /**
     * 지정된 그룹에 새로운 탭을 생성합니다.
     *
     * @param groupId 탭을 생성할 그룹의 ID
     * @param input 탭 콘텐츠를 위한 입력 데이터
     * @param options 탭 생성 옵션
     * @return 생성된 탭에 대한 `TabHandle`
     */
    internal suspend fun createTab(groupId: Int, input: TabInputBase, options: TabOptions): TabHandle {
        // 입력 타입에 따라 로깅
        if (input is EditorTabInput) {
            logger.info("탭 생성 (에디터): " + input.uri?.path)
        }
        if (input is TextDiffTabInput) {
            logger.info("탭 생성 (Diff): " + input.modified.path)
        }

        val group = state.groups[groupId] ?: error("그룹을 찾을 수 없음: $groupId")

        // `EditorTabDto` 객체를 생성합니다.
        val tab = EditorTabDto(
            id = UUID.randomUUID().toString(),
            label = "", // Roocode 0.61+ 호환성을 위해 label 필드 추가
            input = input,
            isActive = options.isActive,
            isPinned = options.isPinned,
            isPreview = !options.isPinned,
            isDirty = false,
        )

        // 그룹에 탭을 추가하고 새로운 그룹 상태를 업데이트합니다.
        val newTabs = group.tabs + tab
        val newGroup = group.copy(tabs = newTabs)
        state.groups[groupId] = newGroup

        // `TabHandle`을 생성하고 저장합니다.
        val handle = TabHandle(id = tab.id, groupId = groupId, manager = this)
        tabHandles[tab.id] = handle

        // 탭 작업 이벤트와 그룹 업데이트 이벤트를 Extension Host에 알립니다.
        tabStateService.acceptTabOperation(
            TabOperation(
                groupId = groupId,
                index = newTabs.size - 1,
                tabDto = tab,
                kind = TabModelOperationKind.TAB_OPEN.value,
                oldIndex = null,
            ),
        )
        tabStateService.acceptTabGroupUpdate(newGroup)
        return handle
    }

    /**
     * 탭을 제거합니다.
     *
     * @param id 제거할 탭의 ID
     * @return 제거된 탭에 대한 `TabHandle` 또는 찾지 못하면 null
     */
    internal fun removeTab(id: String): TabHandle? {
        val handle = tabHandles[id] ?: return null
        val group = state.groups[handle.groupId] ?: return null

        val index = group.tabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val tab = group.tabs[index]

            // 입력 타입에 따라 로깅
            if (tab.input is EditorTabInput) {
                logger.info("탭 제거 (에디터): " + tab.input.uri?.path)
            }
            if (tab.input is TextDiffTabInput) {
                logger.info("탭 제거 (Diff): " + tab.input.modified.path)
            }

            // 그룹에서 탭을 제거하고 새로운 그룹 상태를 업데이트합니다.
            val newTabs = group.tabs.toMutableList().apply { removeAt(index) }
            val newGroup = group.copy(tabs = newTabs)
            state.groups[handle.groupId] = newGroup
            state.groups[handle.groupId]?.isActive = false // 그룹의 활성 상태를 false로 설정 (추가 확인 필요)
            tabHandles.remove(id)

            // 탭 작업 이벤트와 그룹 업데이트 이벤트를 Extension Host에 알립니다.
            tabStateService.acceptTabOperation(
                TabOperation(
                    groupId = handle.groupId,
                    index = index,
                    tabDto = tab,
                    kind = TabModelOperationKind.TAB_CLOSE.value,
                    oldIndex = null,
                ),
            )
            tabStateService.acceptTabGroupUpdate(newGroup)
        }
        return handle
    }

    /**
     * 제공된 업데이트 함수를 사용하여 탭을 업데이트합니다.
     *
     * @param id 업데이트할 탭의 ID
     * @param update 현재 탭을 받아 업데이트된 탭을 반환하는 함수
     */
    internal suspend fun updateTab(id: String, update: (EditorTabDto) -> EditorTabDto) {
        val handle = tabHandles[id] ?: return
        val group = state.groups[handle.groupId] ?: return

        val index = group.tabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val tab = update(group.tabs[index])
            val newTabs = group.tabs.toMutableList().apply { this[index] = tab }
            state.groups[handle.groupId] = group.copy(tabs = newTabs)

            // 탭 작업 이벤트와 그룹 업데이트 이벤트를 Extension Host에 알립니다.
            tabStateService.acceptTabOperation(
                TabOperation(
                    groupId = handle.groupId,
                    index = index,
                    tabDto = tab,
                    kind = TabModelOperationKind.TAB_UPDATE.value,
                    oldIndex = null,
                ),
            )
//            tabStateService.acceptEditorTabModel(state.groups.values.toList()) // 현재 주석 처리됨
        }
    }

    /**
     * 탭을 새로운 위치로 이동시킵니다. 다른 그룹으로 이동할 수도 있습니다.
     *
     * @param id 이동할 탭의 ID
     * @param toGroupId 대상 그룹의 ID
     * @param toIndex 대상 그룹 내의 인덱스
     */
    internal suspend fun moveTab(id: String, toGroupId: Int, toIndex: Int) {
        val handle = tabHandles[id] ?: return
        val fromGroup = state.groups[handle.groupId] ?: return
        val toGroup = state.groups[toGroupId] ?: return

        val fromIndex = fromGroup.tabs.indexOfFirst { it.id == id }
        if (fromIndex != -1) {
            val tab = fromGroup.tabs[fromIndex]

            // 같은 그룹 내에서 이동하는 경우
            if (handle.groupId == toGroupId) {
                val newTabs = fromGroup.tabs.toMutableList().apply {
                    removeAt(fromIndex)
                    add(toIndex, tab)
                }
                state.groups[handle.groupId] = fromGroup.copy(tabs = newTabs)

                tabStateService.acceptTabOperation(
                    TabOperation(
                        groupId = handle.groupId,
                        index = toIndex,
                        tabDto = tab,
                        kind = TabModelOperationKind.TAB_MOVE.value,
                        oldIndex = fromIndex,
                    ),
                )
            } else {
                // 그룹 간 이동하는 경우
                val newFromTabs = fromGroup.tabs.toMutableList().apply { removeAt(fromIndex) }
                val newToTabs = toGroup.tabs.toMutableList().apply { add(toIndex, tab) }
                state.groups[handle.groupId] = fromGroup.copy(tabs = newFromTabs)
                state.groups[toGroupId] = toGroup.copy(tabs = newToTabs)

                handle.groupId = toGroupId // 탭 핸들의 그룹 ID 업데이트

                tabStateService.acceptTabOperation(
                    TabOperation(
                        groupId = toGroupId,
                        index = toIndex,
                        tabDto = tab,
                        kind = TabModelOperationKind.TAB_MOVE.value,
                        oldIndex = fromIndex,
                    ),
                )
            }
        }
    }

    /**
     * 탭의 활성 상태, dirty 상태, 고정 상태 등을 업데이트합니다.
     *
     * @param id 업데이트할 탭의 ID
     * @param isActive 활성 상태 여부 (선택 사항)
     * @param isDirty dirty 상태 여부 (선택 사항)
     * @param isPinned 고정 상태 여부 (선택 사항)
     */
    internal suspend fun updateTab(
        id: String,
        isActive: Boolean? = null,
        isDirty: Boolean? = null,
        isPinned: Boolean? = null,
    ) {
        updateTab(id) { tab ->
            tab.copy(
                isActive = isActive ?: tab.isActive,
                isDirty = isDirty ?: tab.isDirty,
                isPinned = isPinned ?: tab.isPinned,
                isPreview = if (isPinned != null) !isPinned else tab.isPreview,
            )
        }
    }

    /**
     * 지정된 그룹을 활성 그룹으로 설정합니다.
     *
     * @param groupId 활성 그룹으로 설정할 그룹의 ID
     */
    fun setActiveGroup(groupId: Int) {
        state.groups.forEach { (id, group) ->
            state.groups[id] = group.copy(isActive = id == groupId)
        }

        // 모든 그룹의 업데이트된 상태를 Extension Host에 알립니다.
        tabStateService.acceptEditorTabModel(state.groups.values.toList())
    }

    /**
     * 탭 ID를 사용하여 `TabHandle`을 가져옵니다.
     *
     * @param id 탭의 ID
     * @return `TabHandle` 또는 찾지 못하면 null
     */
    fun getTabHandle(id: String): TabHandle? = tabHandles[id]

    /**
     * 그룹 ID를 사용하여 `EditorTabGroupDto`를 가져옵니다.
     *
     * @param groupId 그룹의 ID
     * @return `EditorTabGroupDto` 또는 찾지 못하면 null
     */
    internal fun getTabGroup(groupId: Int): EditorTabGroupDto? = state.groups[groupId]

    /**
     * 모든 탭 그룹을 가져옵니다.
     *
     * @return 모든 탭 그룹의 리스트
     */
    fun getAllGroups(): List<EditorTabGroupDto> = state.groups.values.toList()

    /**
     * 관리자를 닫고 리소스를 정리합니다. (현재는 주석 처리됨)
     */
    suspend fun close() {
//        tabOperationEvents.resetReplayCache()
//        groupUpdateEvents.resetReplayCache()
//        allGroupsUpdateEvents.resetReplayCache()
    }
}

/**
 * 모든 탭 그룹의 상태를 담는 데이터 클래스입니다.
 */
data class TabsState(
    val groups: MutableMap<Int, EditorTabGroupDto> = ConcurrentHashMap(),
)

/**
 * 탭 생성 옵션을 담는 데이터 클래스입니다.
 */
data class TabOptions(
    val isActive: Boolean = false, // 탭이 활성화될지 여부
    val isPinned: Boolean = false, // 탭이 고정될지 여부
    val isPreview: Boolean = false, // 탭이 미리보기 모드인지 여부
) {
    companion object {
        val DEFAULT = TabOptions() // 기본 옵션
    }
}

/**
 * 특정 탭 그룹에 대한 작업을 제공하는 핸들 클래스입니다.
 * @property groupId 이 탭 그룹의 고유 식별자
 * @property manager 이 그룹을 관리하는 `TabStateManager` 인스턴스
 */
class TabGroupHandle(
    val groupId: Int,
    private val manager: TabStateManager,
) {
    /**
     * 이 그룹의 탭 그룹 데이터를 가져옵니다.
     */
    val group: EditorTabGroupDto?
        get() = manager.getTabGroup(groupId)

    /**
     * 이 그룹에 탭을 추가합니다.
     *
     * @param input 탭 콘텐츠를 위한 입력 데이터
     * @param options 탭 옵션
     * @return 생성된 탭에 대한 `TabHandle`
     */
    suspend fun addTab(input: TabInputBase, options: TabOptions = TabOptions.DEFAULT): TabHandle? =
        manager.createTab(groupId, input, options)

    /**
     * 이 그룹 내에서 탭을 지정된 위치로 이동시킵니다.
     *
     * @param id 이동할 탭의 ID
     * @param toIndex 대상 인덱스
     */
    suspend fun moveTab(id: String, toIndex: Int) {
        manager.moveTab(id, groupId, toIndex)
    }

    /**
     * 이 그룹 내에서 탭 ID를 사용하여 `TabHandle`을 가져옵니다.
     *
     * @param id 탭의 ID
     * @return `TabHandle` 또는 찾지 못하면 null
     */
    suspend fun getTabHandle(id: String): TabHandle? = manager.getTabHandle(id)

    /**
     * 이 그룹의 모든 탭을 가져옵니다.
     */
    val tabs: List<EditorTabDto>
        get() = group?.tabs ?: emptyList()

    /**
     * 이 탭 그룹과 모든 탭을 제거합니다.
     */
    suspend fun remove() {
        group?.tabs?.forEach { tab ->
            getTabHandle(tab.id)?.close()
        }
        manager.removeGroup(groupId)
    }
}

/**
 * 특정 탭에 대한 작업을 제공하는 핸들 클래스입니다.
 * @property id 이 탭의 고유 식별자
 * @property groupId 이 탭이 속한 그룹의 ID
 * @property manager 이 탭을 관리하는 `TabStateManager` 인스턴스
 */
class TabHandle(
    val id: String,
    var groupId: Int,
    private val manager: TabStateManager,
) {

    /**
     * 이 탭을 닫습니다.
     */
    suspend fun close() {
        manager.removeTab(id)
    }

    /**
     * 제공된 업데이트 함수를 사용하여 이 탭을 업데이트합니다.
     *
     * @param update 현재 탭을 받아 업데이트된 탭을 반환하는 함수
     */
    suspend fun update(update: (EditorTabDto) -> EditorTabDto) {
        manager.updateTab(id, update)
    }
}
