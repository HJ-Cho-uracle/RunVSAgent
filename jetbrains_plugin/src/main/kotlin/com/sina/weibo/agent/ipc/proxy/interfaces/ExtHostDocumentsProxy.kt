// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.editor.ModelChangedEvent
import com.sina.weibo.agent.util.URI

/**
 * Extension Host 문서 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 문서 변경사항을 수신하기 위해 사용됩니다.
 */
interface ExtHostDocumentsProxy {
    /**
     * 문서의 언어 ID가 변경되었음을 Extension Host에 알립니다.
     * @param strURL 변경된 문서의 URI
     * @param newLanguageId 새로운 언어 ID
     */
    fun acceptModelLanguageChanged(strURL: URI, newLanguageId: String)

    /**
     * 문서가 저장되었음을 Extension Host에 알립니다.
     * @param strURL 저장된 문서의 URI
     */
    fun acceptModelSaved(strURL: URI)

    /**
     * 문서의 'dirty' 상태(수정되었지만 저장되지 않은 상태)가 변경되었음을 Extension Host에 알립니다.
     * @param strURL 변경된 문서의 URI
     * @param isDirty 새로운 'dirty' 상태
     */
    fun acceptDirtyStateChanged(strURL: URI, isDirty: Boolean)

    /**
     * 문서의 인코딩이 변경되었음을 Extension Host에 알립니다.
     * @param strURL 변경된 문서의 URI
     * @param encoding 새로운 인코딩
     */
    fun acceptEncodingChanged(strURL: URI, encoding: String)

    /**
     * 문서 모델의 내용이 변경되었음을 Extension Host에 알립니다.
     * @param strURL 변경된 문서의 URI
     * @param e 변경 이벤트를 담은 `ModelChangedEvent` 객체
     * @param isDirty 변경 후 문서의 'dirty' 상태
     */
    fun acceptModelChanged(strURL: URI, e: ModelChangedEvent, isDirty: Boolean)
}
