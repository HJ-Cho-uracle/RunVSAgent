// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ 메인 스레드에서 상태 표시줄(Status Bar) 관련 작업을 처리하기 위한 인터페이스입니다.
 * 상태 표시줄에 항목을 추가하거나 업데이트하는 기능을 정의합니다.
 */
interface MainThreadStatusBarShape : Disposable {
    /**
     * 상태 표시줄에 항목을 설정(추가 또는 업데이트)합니다.
     *
     * @param entryId 상태 표시줄 항목의 고유 ID
     * @param id 항목을 식별하는 또 다른 ID
     * @param extensionId 이 항목을 추가한 확장의 ID (선택 사항)
     * @param name 항목의 이름 (주로 접근성 용도로 사용)
     * @param text 상태 표시줄에 표시될 실제 텍스트
     * @param tooltip 마우스를 올렸을 때 표시될 툴팁 텍스트 또는 객체
     * @param hasTooltipProvider 동적으로 툴팁을 제공하는 제공자가 있는지 여부
     * @param command 클릭 시 실행될 커맨드 정보
     * @param color 텍스트 색상
     * @param backgroundColor 배경 색상
     * @param alignLeft 상태 표시줄의 왼쪽에 정렬할지 여부 (true이면 왼쪽, false이면 오른쪽)
     * @param priority 항목의 우선순위. 숫자가 높을수록 더 왼쪽에 표시됩니다.
     * @param accessibilityInformation 접근성을 위한 추가 정보
     */
    fun setEntry(
        entryId: String,
        id: String,
        extensionId: String?,
        name: String,
        text: String,
        tooltip: Any?,
        hasTooltipProvider: Boolean,
        command: Map<Any, Any>?,
        color: Any?,
        backgroundColor: Any?,
        alignLeft: Boolean,
        priority: Number?,
        accessibilityInformation: Any?,
    )
}

/**
 * `MainThreadStatusBarShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하는 역할만 수행하며,
 * 향후 IntelliJ의 `StatusBar` API와 연동하여 실제 상태 표시줄 위젯을 생성하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadStatusBar : MainThreadStatusBarShape {
    private val logger = Logger.getInstance(MainThreadStatusBar::class.java)

    override fun setEntry(
        entryId: String,
        id: String,
        extensionId: String?,
        name: String,
        text: String,
        tooltip: Any?,
        hasTooltipProvider: Boolean,
        command: Map<Any, Any>?,
        color: Any?,
        backgroundColor: Any?,
        alignLeft: Boolean,
        priority: Number?,
        accessibilityInformation: Any?,
    ) {
        logger.info("상태 표시줄 항목 설정: $entryId")
        // TODO: IntelliJ의 StatusBar.addWidget API를 사용하여 실제 위젯을 추가/업데이트하는 로직 구현 필요
    }

    override fun dispose() {
        logger.info("Disposing main thread status bar resources")
    }
}
