package com.sina.weibo.agent.extensions.ui.buttons

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

/**
 * 확장 버튼 제공자 인터페이스입니다.
 * 각 확장은 이 인터페이스를 구현하여 자신만의 특정 버튼 구성을 제공해야 합니다.
 */
interface ExtensionButtonProvider {

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
     * 이 확장을 위한 버튼 목록을 가져옵니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 버튼을 나타내는 `AnAction` 인스턴스 리스트
     */
    fun getButtons(project: Project): List<AnAction>

    /**
     * 이 확장을 위한 버튼 구성(`ButtonConfiguration`)을 가져옵니다.
     * @return 버튼 가시성을 정의하는 `ButtonConfiguration` 객체
     */
    fun getButtonConfiguration(): ButtonConfiguration
}
