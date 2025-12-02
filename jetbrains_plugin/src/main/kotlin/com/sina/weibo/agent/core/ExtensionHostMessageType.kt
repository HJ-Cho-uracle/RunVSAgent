// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

/**
 * Extension Host와 주고받는 프로토콜 메시지의 타입을 정의하는 열거형 클래스입니다.
 * VSCode의 `extensionHostProtocol.MessageType`에 해당하며, 초기 연결 및 제어 신호에 사용됩니다.
 */
enum class ExtensionHostMessageType {
    /**
     * Extension Host가 초기화 데이터를 받고 성공적으로 설정되었음을 알리는 메시지입니다.
     * 이 메시지를 받은 후에야 RPC 통신을 시작할 수 있습니다.
     */
    Initialized,
    
    /**
     * Extension Host 프로세스가 시작되어 통신할 준비가 되었음을 알리는 첫 메시지입니다.
     * 이 메시지를 받으면 플러그인은 Extension Host에 초기화 데이터를 보내야 합니다.
     */
    Ready,
    
    /**
     * Extension Host 프로세스를 종료하라는 신호입니다.
     */
    Terminate;
    
    companion object {
        /**
         * 숫자 값으로부터 해당하는 메시지 타입을 찾습니다.
         * @param value 숫자 값
         * @return 해당하는 `ExtensionHostMessageType`, 없으면 null
         */
        fun fromValue(value: Int): ExtensionHostMessageType? {
            return when (value) {
                0 -> Initialized
                1 -> Ready
                2 -> Terminate
                else -> null
            }
        }
        
        /**
         * 프로토콜을 통해 받은 1바이트 데이터로부터 메시지 타입을 해석합니다.
         * @param data 1바이트 크기의 메시지 데이터
         * @return 해당하는 `ExtensionHostMessageType`, 없으면 null
         */
        fun fromData(data: ByteArray): ExtensionHostMessageType? {
            if (data.size != 1) {
                return null
            }
            
            // VSCode의 프로토콜 정의에 따라 특정 숫자 값에 매핑됩니다.
            return when (data[0].toInt()) {
                1 -> Initialized
                2 -> Ready
                3 -> Terminate
                else -> null
            }
        }
    }
}
