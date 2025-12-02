// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.editor.DocumentsAndEditorsDelta

/**
 * Extension Host 문서 및 에디터 프록시 인터페이스입니다.
 * Extension Host가 IntelliJ 플러그인의 문서와 에디터 상태 변경사항(델타)을 수신하기 위해 사용됩니다.
 */
interface ExtHostDocumentsAndEditorsProxy {
    /**
     * 문서 및 에디터의 변경사항(추가, 제거, 활성 에디터 변경 등)을 Extension Host에 전달합니다.
     * @param d 변경사항을 담은 `DocumentsAndEditorsDelta` 객체
     */
    fun acceptDocumentsAndEditorsDelta(d: DocumentsAndEditorsDelta)
}
