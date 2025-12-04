// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 프로토콜 메시지(Protocol Message)를 나타내는 클래스입니다.
 * VSCode의 `ProtocolMessage`에 해당하며, Extension Host와 주고받는 모든 메시지의 기본 구조입니다.
 */
class ProtocolMessage(
    /**
     * 메시지의 타입입니다. (예: 일반 메시지, ACK, 제어 메시지 등)
     */
    val type: ProtocolMessageType,

    /**
     * 메시지의 고유 ID입니다.
     * 메시지 순서를 보장하고 재전송을 관리하는 데 사용됩니다.
     */
    val id: Int,

    /**
     * 확인 응답(Acknowledgment) ID입니다.
     * 이 메시지를 보낸 측이 마지막으로 수신 확인한 메시지의 ID를 나타냅니다.
     */
    val ack: Int,

    /**
     * 메시지의 실제 데이터 (바이트 배열)입니다.
     */
    val data: ByteArray = ByteArray(0),
) {
    /**
     * 메시지가 소켓에 쓰여진 시간 (밀리초 타임스탬프)입니다.
     * 주로 타임아웃 감지에 사용됩니다.
     */
    var writtenTime: Long = 0

    /**
     * 메시지 데이터의 크기 (바이트 단위)입니다.
     */
    val size: Int
        get() = data.size

    /**
     * 두 `ProtocolMessage` 객체의 동등성을 비교합니다.
     * `type`, `id`, `ack`, `data` 필드가 모두 같으면 동일한 메시지로 간주합니다.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProtocolMessage

        if (type != other.type) return false
        if (id != other.id) return false
        if (ack != other.ack) return false
        if (!data.contentEquals(other.data)) return false // 바이트 배열 비교

        return true
    }

    /**
     * 객체의 해시 코드를 반환합니다.
     * `equals` 메소드와 일관성을 유지하기 위해 `type`, `id`, `ack`, `data` 필드를 사용하여 해시 코드를 계산합니다.
     */
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id
        result = 31 * result + ack
        result = 31 * result + data.contentHashCode() // 바이트 배열의 해시 코드
        return result
    }
}
