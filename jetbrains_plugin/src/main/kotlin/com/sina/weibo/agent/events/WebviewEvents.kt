// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.events

/**
 * WebView 뷰 제공자 등록 이벤트를 나타내는 객체입니다.
 * `EventType<WebviewViewProviderData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WebviewViewProviderRegisterEvent : EventType<WebviewViewProviderData>

/**
 * WebView 뷰 제공자 등록 시 전달되는 데이터를 담는 데이터 클래스입니다.
 * @property extension 제공자를 등록하는 확장에 대한 정보
 * @property viewType 뷰의 고유 타입 (예: "my-extension.sidebar-view")
 * @property options 뷰에 대한 추가 설정
 */
data class WebviewViewProviderData(
    val extension: Map<String, Any?>,
    val viewType: String,
    val options: Map<String, Any?>,
)

/**
 * WebView HTML 내용 업데이트 이벤트를 나타내는 객체입니다.
 * `EventType<WebviewHtmlUpdateData>`를 구현하여 `EventBus`를 통해 전달될 수 있습니다.
 */
object WebviewHtmlUpdateEvent : EventType<WebviewHtmlUpdateData>

/**
 * WebView HTML 내용 업데이트 시 전달되는 데이터를 담는 데이터 클래스입니다.
 * @property handle 업데이트할 WebView의 핸들
 * @property htmlContent 업데이트할 HTML 내용
 */
data class WebviewHtmlUpdateData(
    val handle: String,
    var htmlContent: String,
)
