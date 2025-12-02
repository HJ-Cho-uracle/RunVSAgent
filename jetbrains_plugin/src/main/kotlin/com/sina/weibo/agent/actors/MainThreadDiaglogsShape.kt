// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.sina.weibo.agent.util.URI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 파일 열기 다이얼로그의 설정을 담는 데이터 클래스입니다.
 * 파일 선택기의 동작을 커스터마이징하는 데 필요한 모든 파라미터를 캡슐화합니다.
 *
 * @property defaultUri 다이얼로그가 처음 열릴 때 보여줄 기본 경로(URI)
 * @property openLabel 다이얼로그의 '열기' 또는 '선택' 버튼에 표시될 사용자 정의 텍스트
 * @property canSelectFiles 파일 선택을 허용할지 여부
 * @property canSelectFolders 폴더 선택을 허용할지 여부
 * @property canSelectMany 여러 항목을 동시에 선택할 수 있는지 여부
 * @property filters 표시할 파일을 필터링하기 위한 확장자 필터 (예: {"이미지": ["jpg", "png"]})
 * @property title 다이얼로그 창의 사용자 정의 제목
 * @property allowUIResources UI 리소스 선택을 허용할지 여부 (일반적으로 false)
 */
data class MainThreadDialogOpenOptions(
    val defaultUri: Map<String, String?>?,
    val openLabel: String?,
    val canSelectFiles: Boolean?,
    val canSelectFolders: Boolean?,
    val canSelectMany: Boolean?,
    val filters: MutableMap<String, MutableList<String>>?,
    val title: String?,
    val allowUIResources: Boolean?,
)

/**
 * IntelliJ 메인 UI 스레드에서 다이얼로그 관련 작업을 처리하기 위한 인터페이스입니다.
 * 파일 열기 및 저장 다이얼로그를 띄우는 메소드를 정의합니다.
 */
interface MainThreadDiaglogsShape : Disposable {
    /**
     * 파일 열기 다이얼로그를 띄우고, 사용자가 선택한 파일들의 URI 목록을 반환합니다.
     *
     * @param options 다이얼로그의 동작을 커스터마이징하기 위한 설정 옵션
     * @return 선택된 파일들의 URI 리스트. 사용자가 다이얼로그를 취소하면 null을 반환할 수 있습니다.
     */
    suspend fun showOpenDialog(options: Map<String, Any?>?): MutableList<URI>?

    /**
     * 파일 저장 다이얼로그를 띄우고, 사용자가 선택한 파일의 URI를 반환합니다.
     *
     * @param options 다이얼로그의 동작을 커스터마이징하기 위한 설정 옵션
     * @return 저장하기 위해 선택된 파일의 URI. 사용자가 다이얼로그를 취소하면 null을 반환합니다.
     */
    suspend fun showSaveDialog(options: Map<String, Any?>?): URI?
}

/**
 * `MainThreadDiaglogsShape` 인터페이스의 구현 클래스입니다.
 * IntelliJ 플랫폼의 파일 선택 API를 사용하여 메인 UI 스레드에서 파일 다이얼로그 기능을 제공합니다.
 */
class MainThreadDiaglogs : MainThreadDiaglogsShape {
    private val logger = Logger.getInstance(MainThreadDiaglogs::class.java)

    /**
     * 지정된 옵션으로 파일 열기 다이얼로그를 보여줍니다.
     * 이 메소드는 IntelliJ의 `invokeLater`를 사용하여 모든 UI 작업을 메인 스레드에서 실행하도록 보장합니다.
     * 코루틴을 사용하여 비동기적인 다이얼로그 결과를 동기적인 코드처럼 처리할 수 있게 합니다.
     *
     * @param map 다이얼로그 옵션을 담고 있는 Map
     * @return 선택된 파일 URI의 변경 가능한 리스트, 또는 취소 시 null
     */
    override suspend fun showOpenDialog(map: Map<String, Any?>?): MutableList<URI>? {
        val options = create(map)
        
        // 파일 선택기의 동작을 정의하는 디스크립터를 생성합니다.
        val descriptor = FileChooserDescriptor(
            /* chooseFiles = */ options?.canSelectFiles ?: true,
            /* chooseFolders = */ options?.canSelectFolders ?: true,
            /* chooseJars = */ false,
            /* chooseJarsAsFiles = */ false,
            /* chooseMultipleJars = */ false,
            /* chooseMultiple = */ options?.canSelectMany ?: true
        )
            .withTitle(options?.title ?: "Open")
            .withDescription(options?.openLabel ?: "Select files")
        
        // 파일 확장자 필터를 적용합니다.
        options?.filters?.forEach { (name, extensions) ->
            descriptor.withFileFilter { file ->
                extensions.any { file.extension?.equals(it, true) ?: false }
            }
        }

        // 코루틴을 사용하여 비동기 다이얼로그 작업을 일시 중단하고 결과를 기다립니다.
        return suspendCancellableCoroutine { continuation ->
            // UI 작업은 반드시 메인 스레드에서 실행해야 합니다.
            ApplicationManager.getApplication().invokeLater({
                try {
                    // IntelliJ 파일 선택 다이얼로그를 띄웁니다.
                    val files = FileChooser.chooseFiles(descriptor, null, null)
                    
                    // 선택된 VirtualFile 객체를 우리가 사용하는 URI 객체로 변환합니다.
                    val result = files.map { file ->
                        URI.file(file.path)
                    }.toMutableList()
                    
                    // 코루틴을 결과와 함께 재개합니다.
                    continuation.resume(result)
                } catch (e: Exception) {
                    // 오류 발생 시 예외와 함께 코루틴을 재개합니다.
                    continuation.resumeWithException(e)
                }
            }, ModalityState.defaultModalityState())
        }
    }

    /**
     * 지정된 옵션으로 파일 저장 다이얼로그를 보여줍니다.
     *
     * @param map 다이얼로그 옵션을 담고 있는 Map
     * @return 저장 위치의 URI, 또는 취소 시 null
     */
    override suspend fun showSaveDialog(map: Map<String, Any?>?): URI? {
        val options = create(map)
        
        val descriptor = FileSaverDescriptor("Save", options?.openLabel ?: "Select save location")

        options?.filters?.forEach { (name, extensions) ->
            descriptor.withFileFilter { file ->
                extensions.any { file.extension?.equals(it, true) ?: false }
            }
        }

        // 옵션에서 기본 경로와 파일 이름을 추출합니다.
        val path = options?.defaultUri?.get("path")
        var fileName: String? = null
        
        val virtualFile = path?.let { filePath ->
            val file = File(filePath)
            fileName = file.name
            Path.of(file.parentFile.absolutePath)
        }

        return suspendCancellableCoroutine { continuation ->
            ApplicationManager.getApplication().invokeLater({
                try {
                    // IntelliJ 파일 저장 다이얼로그를 띄웁니다.
                    val file = FileChooserFactory.getInstance()
                        .createSaveFileDialog(descriptor, null)
                        .save(virtualFile, fileName)
                    
                    val result = file?.let { URI.file(it.file.absolutePath) }
                    
                    continuation.resume(result)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ModalityState.defaultModalityState())
        }
    }

    /**
     * `Map` 형태의 설정 정보를 `MainThreadDialogOpenOptions` 데이터 클래스 인스턴스로 변환하는 헬퍼 메소드입니다.
     *
     * @param map 다이얼로그 옵션을 키-값 쌍으로 담고 있는 Map
     * @return `MainThreadDialogOpenOptions` 인스턴스, 또는 map이 null이면 null
     */
    private fun create(map: Map<String, Any?>?): MainThreadDialogOpenOptions? {
        map?.let {
            return MainThreadDialogOpenOptions(
                defaultUri = it["defaultUri"] as? Map<String, String?>,
                openLabel = it["openLabel"] as? String,
                canSelectFiles = it["canSelectFiles"] as? Boolean,
                canSelectFolders = it["canSelectFolders"] as? Boolean,
                canSelectMany = it["canSelectMany"] as? Boolean,
                filters = it["filters"] as? MutableMap<String, MutableList<String>>,
                title = it["title"] as? String,
                allowUIResources = it["allowUIResources"] as? Boolean
            )
        } ?: return null
    }

    /**
     * 이 다이얼로그 핸들러가 사용하던 리소스를 해제합니다.
     */
    override fun dispose() {
        logger.info("Disposing MainThreadDiaglogs")
    }
}
