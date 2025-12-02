package com.sina.weibo.agent.extensions.ui.contextmenu

/**
 * 컨텍스트 메뉴 구성 인터페이스입니다.
 * 특정 확장에 대해 어떤 컨텍스트 메뉴 액션이 표시되어야 하는지 정의합니다.
 */
interface ContextMenuConfiguration {
    
    /**
     * 특정 컨텍스트 메뉴 액션이 표시되어야 하는지 확인합니다.
     * @param actionType 컨텍스트 메뉴 액션의 타입
     * @return 액션이 표시되어야 하면 true, 그렇지 않으면 false
     */
    fun isActionVisible(actionType: ContextMenuActionType): Boolean
    
    /**
     * 표시될 모든 컨텍스트 메뉴 액션 목록을 가져옵니다.
     * @return 표시될 액션 타입의 리스트
     */
    fun getVisibleActions(): List<ContextMenuActionType>
}

/**
 * 구성 가능한 컨텍스트 메뉴 액션 타입들을 정의하는 열거형입니다.
 * 이들은 마우스 오른쪽 클릭 컨텍스트 메뉴에서 제공될 수 있는 다양한 액션들을 나타냅니다.
 */
enum class ContextMenuActionType {
    EXPLAIN_CODE,   // 코드 설명
    FIX_CODE,       // 코드 수정
    FIX_LOGIC,      // 논리 수정
    IMPROVE_CODE,   // 코드 개선
    ADD_TO_CONTEXT, // 컨텍스트에 추가
    NEW_TASK        // 새 작업
}
