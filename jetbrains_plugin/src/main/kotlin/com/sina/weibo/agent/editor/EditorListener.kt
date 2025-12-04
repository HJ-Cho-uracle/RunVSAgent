// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.editor

import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sina.weibo.agent.util.URI

/**
 * IntelliJ 에디터의 생성 및 해제 이벤트를 감지하는 리스너 클래스입니다.
 * `EditorFactoryListener` 인터페이스를 구현하여 에디터의 생명주기 이벤트를 처리합니다.
 */
class EditorListener : EditorFactoryListener {
    /**
     * 에디터가 생성되었을 때 호출됩니다.
     * 특히 Diff 에디터가 생성되었을 경우, `EditorAndDocManager`에 해당 이벤트를 알립니다.
     * @param event 에디터 생성 이벤트 객체
     */
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        // 생성된 에디터가 Diff 에디터인지 확인합니다.
        if (DiffUtil.isDiffEditor(editor)) {
            // 에디터의 문서와 연결된 VirtualFile을 가져옵니다.
            FileDocumentManager.getInstance().getFile(editor.document)?.let { file ->
                // 프로젝트 서비스에서 EditorAndDocManager 인스턴스를 가져옵니다.
                val manager = editor.project?.getService(EditorAndDocManager::class.java)
                val url = URI.file(file.path)
                // Diff 에디터가 생성되었음을 manager에 알립니다.
                manager?.onIdeaDiffEditorCreated(url, editor)
            }
        }
        super.editorCreated(event) // 부모 클래스의 메소드 호출
    }

    /**
     * 에디터가 해제되었을 때 호출됩니다.
     * 특히 Diff 에디터가 해제되었을 경우, `EditorAndDocManager`에 해당 이벤트를 알립니다.
     * @param event 에디터 해제 이벤트 객체
     */
    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        // 해제된 에디터가 Diff 에디터인지 확인합니다.
        if (DiffUtil.isDiffEditor(editor)) {
            // 에디터의 문서와 연결된 VirtualFile을 가져옵니다.
            FileDocumentManager.getInstance().getFile(editor.document)?.let { file ->
                // 프로젝트 서비스에서 EditorAndDocManager 인스턴스를 가져옵니다.
                val manager = editor.project?.getService(EditorAndDocManager::class.java)
                val url = URI.file(file.path)
                // Diff 에디터가 해제되었음을 manager에 알립니다.
                manager?.onIdeaDiffEditorReleased(url, editor)
            }
        }
        super.editorReleased(event) // 부모 클래스의 메소드 호출
    }
}
