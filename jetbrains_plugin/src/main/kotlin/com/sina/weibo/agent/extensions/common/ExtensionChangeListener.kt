package com.sina.weibo.agent.extensions.common

import com.intellij.util.messages.Topic

/**
 * 확장 변경 리스너 인터페이스입니다.
 * 현재 활성화된 확장(Extension)이 변경될 때 알림을 받기 위해 컴포넌트들이 이 인터페이스를 구현할 수 있습니다.
 */
interface ExtensionChangeListener {

    /**
     * 현재 활성화된 확장이 변경되었을 때 호출됩니다.
     *
     * @param newExtensionId 새로 활성화된 확장의 ID
     */
    fun onExtensionChanged(newExtensionId: String)

    companion object {
        /**
         * 확장 변경 이벤트를 위한 `Topic`입니다.
         * 컴포넌트들은 이 `Topic`을 구독하여 확장 변경 알림을 수신할 수 있습니다.
         * `Topic`은 IntelliJ 플랫폼의 메시지 버스 시스템에서 특정 유형의 이벤트를 식별하는 데 사용됩니다.
         */
        val EXTENSION_CHANGE_TOPIC = Topic.create("Extension Change", ExtensionChangeListener::class.java)
    }
}
