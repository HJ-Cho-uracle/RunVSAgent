package com.sina.weibo.agent.extensions.ui.contextmenu

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

/**
 * 확장 컨텍스트 메뉴 제공자 인터페이스입니다.
 * 각 확장은 이 인터페이스를 구현하여 자신만의 특정 컨텍스트 메뉴 액션(동작)을 제공해야 합니다.
 * 이를 통해 다른 확장들이 다른 마우스 오른쪽 클릭 컨텍스트 메뉴를 가질 수 있습니다.
 */
interface ExtensionContextMenuProvider {

    /**
     * 확장의 고유 식별자(ID)를 가져옵니다.
     * @return 고유한 확장 식별자 문자열
     */
    fun getExtensionId(): String

    /**
     * 확장의 표시 이름(Display Name)을 가져옵니다.
     * @return 사람이 읽을 수 있는 확장 이름
     */
    fun getDisplayName(): String

    /**
     * 확장에 대한 설명을 가져옵니다.
     * @return 확장의 기능이나 목적에 대한 설명
     */
    fun getDescription(): String

    /**
     * 확장이 현재 사용 가능한 상태인지 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    fun isAvailable(project: Project): Boolean

    /**
     * 이 확장을 위한 컨텍스트 메뉴 액션 목록을 가져옵니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 컨텍스트 메뉴 액션을 나타내는 `AnAction` 인스턴스 리스트
     */
    fun getContextMenuActions(project: Project): List<AnAction>

    /**
     * 이 확장을 위한 컨텍스트 메뉴 구성(`ContextMenuConfiguration`)을 가져옵니다.
     * @return 액션 가시성을 정의하는 `ContextMenuConfiguration` 객체
     */
    fun getContextMenuConfiguration(): ContextMenuConfiguration
}
