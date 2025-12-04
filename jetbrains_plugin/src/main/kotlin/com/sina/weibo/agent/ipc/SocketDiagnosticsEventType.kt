// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 소켓 진단 이벤트 타입(Socket Diagnostics Event Type)을 정의하는 열거형입니다.
 * VSCode의 `SocketDiagnosticsEventType`에 해당하며, 소켓 통신 과정에서 발생하는
 * 다양한 내부 이벤트들을 추적하고 디버깅하는 데 사용됩니다.
 */
enum class SocketDiagnosticsEventType {
    CREATED, // 소켓이 생성됨
    READ, // 소켓에서 데이터를 읽음
    WRITE, // 소켓에 데이터를 씀
    OPEN, // 소켓 연결이 열림
    ERROR, // 소켓에서 오류 발생
    CLOSE, // 소켓 연결이 닫힘

    BROWSER_WEB_SOCKET_BLOB_RECEIVED, // 브라우저 WebSocket에서 Blob 데이터 수신

    NODE_END_RECEIVED, // Node.js 소켓에서 END 신호 수신
    NODE_END_SENT, // Node.js 소켓에 END 신호 전송
    NODE_DRAIN_BEGIN, // Node.js 소켓 드레인(drain) 시작
    NODE_DRAIN_END, // Node.js 소켓 드레인(drain) 종료

    ZLIB_INFLATE_ERROR, // Zlib 압축 해제(inflate) 오류
    ZLIB_INFLATE_DATA, // Zlib 압축 해제 데이터
    ZLIB_INFLATE_INITIAL_WRITE, // Zlib 압축 해제 초기 쓰기
    ZLIB_INFLATE_INITIAL_FLUSH_FIRED, // Zlib 압축 해제 초기 플러시 발생
    ZLIB_INFLATE_WRITE, // Zlib 압축 해제 쓰기
    ZLIB_INFLATE_FLUSH_FIRED, // Zlib 압축 해제 플러시 발생
    ZLIB_DEFLATE_ERROR, // Zlib 압축(deflate) 오류
    ZLIB_DEFLATE_DATA, // Zlib 압축 데이터
    ZLIB_DEFLATE_WRITE, // Zlib 압축 쓰기
    ZLIB_DEFLATE_FLUSH_FIRED, // Zlib 압축 플러시 발생

    WEB_SOCKET_NODE_SOCKET_WRITE, // WebSocket Node.js 소켓 쓰기
    WEB_SOCKET_NODE_SOCKET_PEEKED_HEADER, // WebSocket Node.js 소켓 헤더 미리보기
    WEB_SOCKET_NODE_SOCKET_READ_HEADER, // WebSocket Node.js 소켓 헤더 읽기
    WEB_SOCKET_NODE_SOCKET_READ_DATA, // WebSocket Node.js 소켓 데이터 읽기
    WEB_SOCKET_NODE_SOCKET_UNMASKED_DATA, // WebSocket Node.js 소켓 언마스크된 데이터
    WEB_SOCKET_NODE_SOCKET_DRAIN_BEGIN, // WebSocket Node.js 소켓 드레인 시작
    WEB_SOCKET_NODE_SOCKET_DRAIN_END, // WebSocket Node.js 소켓 드레인 종료

    PROTOCOL_HEADER_READ, // 프로토콜 헤더 읽음
    PROTOCOL_MESSAGE_READ, // 프로토콜 메시지 읽음
    PROTOCOL_HEADER_WRITE, // 프로토콜 헤더 씀
    PROTOCOL_MESSAGE_WRITE, // 프로토콜 메시지 씀
    PROTOCOL_WRITE, // 프로토콜 쓰기
}
