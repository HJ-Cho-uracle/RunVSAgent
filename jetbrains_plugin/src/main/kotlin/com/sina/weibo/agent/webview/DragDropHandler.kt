// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.intellij.openapi.diagnostic.Logger
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File
import javax.swing.JComponent

/**
 * WebView로 파일 드래그 앤 드롭을 처리하는 핸들러입니다.
 * VSCode의 네이티브 드래그 앤 드롭 동작을 기반으로 합니다.
 */
class DragDropHandler(
    private val webViewInstance: WebViewInstance, // 드래그 앤 드롭 이벤트를 전달할 WebView 인스턴스
    private val targetComponent: JComponent       // 드래그 앤 드롭 이벤트를 수신할 대상 Swing 컴포넌트
) {
    private val logger = Logger.getInstance(DragDropHandler::class.java)
    
    /**
     * 드래그 앤 드롭 지원을 설정합니다.
     */
    fun setupDragAndDrop() {
        logger.info("WebView에 드래그 앤 드롭 지원 설정 중 (VSCode 호환)")
        
        // 대상 컴포넌트에 DropTarget을 설정하고 DropTargetAdapter를 구현합니다.
        val dropTarget = DropTarget(targetComponent, object : DropTargetAdapter() {
            
            /** 드래그가 대상 영역으로 진입했을 때 호출됩니다. */
            override fun dragEnter(dtde: DropTargetDragEvent) {
                logger.info("드래그 진입 감지됨")
                // Shift 키가 눌려 있고 파일 목록이 포함되어 있으면 드래그를 수락합니다.
                if (isShiftKeyPressed(dtde) && hasFileList(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY) // 복사 액션 수락
                    notifyDragState(true) // WebView에 드래그 상태 변경 알림
                    logger.info("드래그 수락됨 - Shift 키 눌림 및 파일 감지됨")
                } else {
                    dtde.rejectDrag() // 드래그 거부
                    logger.info("드래그 거부됨 - ${if (!isShiftKeyPressed(dtde)) "Shift 키가 눌리지 않음" else "파일이 감지되지 않음"}")
                }
            }
            
            /** 드래그가 대상 영역 위에서 이동할 때 호출됩니다. */
            override fun dragOver(dtde: DropTargetDragEvent) {
                if (isShiftKeyPressed(dtde) && hasFileList(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }
            
            /** 드래그가 대상 영역을 벗어났을 때 호출됩니다. */
            override fun dragExit(dte: DropTargetEvent) {
                logger.info("드래그 종료 감지됨")
                notifyDragState(false) // WebView에 드래그 상태 변경 알림
            }
            
            /** 드롭 이벤트가 발생했을 때 호출됩니다. */
            override fun drop(dtde: DropTargetDropEvent) {
                logger.info("드롭 이벤트 감지됨")
                handleFileDrop(dtde) // 파일 드롭 처리
            }
        })
        
        logger.info("드래그 앤 드롭 설정 완료")
    }
    
    /**
     * Shift 키가 눌렸는지 확인합니다.
     * VSCode의 네이티브 `if (!e.shiftKey)` 검사를 시뮬레이션합니다.
     *
     * 참고: Java AWT에서 Shift 키 상태 감지는 전역 키 리스너가 필요할 수 있습니다.
     * 현재는 편의를 위해 항상 true를 반환합니다.
     */
    private fun isShiftKeyPressed(dtde: DropTargetDragEvent): Boolean {
        // TODO: 필요하다면 실제 Shift 키 감지 로직 구현
        return true
    }
    
    /**
     * WebView에 드래그 상태 변경을 알립니다.
     * VSCode의 드래그 시각적 피드백(isDraggingOver 상태)을 시뮬레이션합니다.
     */
    private fun notifyDragState(isDragging: Boolean) {
        try {
            // WebView 내의 JavaScript를 실행하여 UI를 업데이트합니다.
            val jsCode = """
                (function() {
                    console.log('드래그 상태 설정:', $isDragging);
                    const textareas = document.querySelectorAll('textarea, [contenteditable="true"]');
                    if ($isDragging) {
                        textareas.forEach(textarea => {
                            textarea.style.border = '2px dashed #007acc';
                            textarea.style.backgroundColor = 'rgba(0, 122, 204, 0.1)';
                            textarea.setAttribute('data-dragging', 'true');
                        });
                    } else {
                        textareas.forEach(textarea => {
                            textarea.style.border = '';
                            textarea.style.backgroundColor = '';
                            textarea.removeAttribute('data-dragging');
                        });
                    }
                })();
            """.trimIndent()
            webViewInstance.executeJavaScript(jsCode)
        } catch (e: Exception) {
            logger.error("드래그 상태 알림 실패", e)
        }
    }
    
    /**
     * 파일 드롭 이벤트를 처리합니다.
     * VSCode의 `handleDrop` 함수를 기반으로 합니다.
     */
    private fun handleFileDrop(dtde: DropTargetDropEvent) {
        try {
            logger.info("드롭 이벤트 처리 중")
            
            if (!hasFileList(dtde)) {
                logger.info("드롭 거부됨: 전송 가능한 파일 목록 없음")
                dtde.rejectDrop()
                notifyDragState(false)
                return
            }
            
            dtde.acceptDrop(DnDConstants.ACTION_COPY) // 드롭 수락
            
            val transferable = dtde.transferable
            @Suppress("UNCHECKED_CAST")
            val fileList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File> // 파일 목록 가져오기
            
            logger.info("드롭된 파일: ${fileList.map { it.absolutePath }}")
            
            if (fileList.isNotEmpty()) {
                insertFilePathsIntoTextarea(fileList) // 파일 경로를 텍스트 영역에 삽입
                dtde.dropComplete(true) // 드롭 완료
            } else {
                logger.warn("드롭 이벤트에서 유효한 파일을 찾을 수 없습니다.")
                dtde.dropComplete(false)
            }
            
            notifyDragState(false) // 드래그 상태 초기화
            
        } catch (e: Exception) {
            logger.error("파일 드롭 처리 중 오류 발생", e)
            dtde.dropComplete(false)
            notifyDragState(false)
        }
    }
    
    /**
     * 파일 경로를 VSCode 네이티브 핸들러로 전달합니다.
     * VSCode 확장을 위한 네이티브 드래그 이벤트를 시뮬레이션합니다.
     */
    private fun insertFilePathsIntoTextarea(files: List<File>) {
        try {
            // 파일 경로 목록 생성 (VSCode 네이티브 핸들러는 절대 경로를 사용)
            val filePaths = files.map { it.absolutePath }
            
            logger.info("VSCode 네이티브 핸들러로 드래그 드롭 이벤트 전달 중: ${filePaths.size}개 파일")
            
            // VSCode 확장을 위한 모의 네이티브 드래그 이벤트를 생성하는 JavaScript 코드
            val jsCode = """
                (function() {
                    console.log('VSCode를 위한 네이티브 드래그 드롭 이벤트 시뮬레이션 중');
                    
                    // 대상 텍스트 영역 찾기
                    const textareas = document.querySelectorAll('textarea, [contenteditable="true"], input[type="text"]');
                    console.log('텍스트 영역 찾음:', textareas.length);
                    
                    if (textareas.length === 0) {
                        console.warn('적합한 텍스트 영역을 찾을 수 없습니다.');
                        return false;
                    }
                    
                    // 대상 텍스트 영역 선택 (활성화된 요소 또는 첫 번째 텍스트 영역)
                    let targetTextarea = document.activeElement;
                    if (!targetTextarea || !['TEXTAREA', 'INPUT'].includes(targetTextarea.tagName)) {
                        targetTextarea = textareas[0];
                    }
                    
                    if (!targetTextarea) {
                        console.warn('유효한 대상 텍스트 영역을 찾을 수 없습니다.');
                        return false;
                    }
                    
                    console.log('대상 텍스트 영역 찾음:', targetTextarea.tagName);
                    
                    // 파일 경로 데이터 구성
                    const filePaths = [${filePaths.joinToString(", ") { "\"$it\"" }}];
                    
                    console.log('삽입할 파일 경로:', filePaths);
                    
                    // 모의 File 객체 생성
                    const mockFiles = filePaths.map(path => ({
                        name: path.split('/').pop() || path.split('\\\\').pop() || 'unknown',
                        path: path,
                        type: '',
                        size: 0,
                        lastModified: Date.now(),
                        webkitRelativePath: ''
                    }));
                    
                    // 모의 FileList 객체 생성
                    const mockFileList = {
                        length: mockFiles.length,
                        item: function(index) {
                            return mockFiles[index] || null;
                        },
                        [Symbol.iterator]: function* () {
                            for (let i = 0; i < this.length; i++) {
                                yield this.item(i);
                            }
                        }
                    };
                    
                    // FileList에 배열 인덱스 접근 추가
                    mockFiles.forEach((file, index) => {
                        mockFileList[index] = file;
                    });
                    
                    // 모의 DataTransferItem 객체 생성
                    const mockItems = mockFiles.map(file => ({
                        kind: 'file',
                        type: file.type,
                        getAsFile: function() {
                            return file;
                        },
                        getAsString: function(callback) {
                            if (callback) callback(file.path);
                        }
                    }));
                    
                    // 모의 DataTransferItemList 객체 생성
                    const mockItemList = {
                        length: mockItems.length,
                        item: function(index) {
                            return mockItems[index] || null;
                        },
                        [Symbol.iterator]: function* () {
                            for (let i = 0; i < this.length; i++) {
                                yield this.item(i);
                            }
                        }
                    };
                    
                    // ItemList에 배열 인덱스 접근 추가
                    mockItems.forEach((item, index) => {
                        mockItemList[index] = item;
                    });
                    
                    console.log('모의 FileList 생성됨, 파일 수:', mockFileList.length);
                    console.log('모의 ItemList 생성됨, 항목 수:', mockItemList.length);
                    
                    // 완전한 DataTransfer 객체 생성
                    const mockDataTransfer = {
                        files: mockFileList,
                        items: mockItemList,
                        types: ['Files', 'text/uri-list', 'text/plain'],
                        getData: function(format) {
                            console.log('DataTransfer.getData 호출됨, 형식:', format);
                            if (format === 'text' || format === 'text/plain') {
                                return filePaths.join('\n');
                            }
                            if (format === 'text/uri-list' || format === 'application/vnd.code.uri-list') {
                                return filePaths.map(path => 'file://' + path).join('\n');
                            }
                            return '';
                        },
                        setData: function(format, data) {
                            // 모의 구현
                        },
                        clearData: function(format) {
                            // 모의 구현
                        },
                        effectAllowed: 'copy',
                        dropEffect: 'copy'
                    };
                    
                    // 모의 drop 이벤트 생성
                    const mockDragEvent = new Event('drop', {
                        bubbles: true,
                        cancelable: true
                    });
                    
                    // 필요한 속성 추가
                    Object.defineProperty(mockDragEvent, 'dataTransfer', {
                        value: mockDataTransfer,
                        writable: false
                    });
                    
                    // Shift 키가 눌린 것을 시뮬레이션 (VSCode에서 필요)
                    Object.defineProperty(mockDragEvent, 'shiftKey', {
                        value: true,
                        writable: false
                    });
                    
                    console.log('모의 drop 이벤트를 텍스트 영역에 디스패치 중');
                    
                    // 텍스트 영역에 포커스 설정
                    targetTextarea.focus();
                    
                    // VSCode 네이티브 핸들러를 위해 텍스트 영역에 이벤트 디스패치
                    const result = targetTextarea.dispatchEvent(mockDragEvent);
                    
                    console.log('모의 drop 이벤트 디스패치됨, 결과:', result);
                    
                    return true;
                })();
            """.trimIndent()
            
            webViewInstance.executeJavaScript(jsCode)
            
        } catch (e: Exception) {
            logger.error("VSCode로 드래그 드롭 이벤트 전달 실패", e)
        }
    }
    
    /**
     * 드래그 데이터에 파일 목록이 포함되어 있는지 확인합니다.
     */
    private fun hasFileList(dtde: DropTargetDragEvent): Boolean {
        return dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }
    
    /**
     * 드롭 데이터에 파일 목록이 포함되어 있는지 확인합니다.
     */
    private fun hasFileList(dtde: DropTargetDropEvent): Boolean {
        return dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }
}
