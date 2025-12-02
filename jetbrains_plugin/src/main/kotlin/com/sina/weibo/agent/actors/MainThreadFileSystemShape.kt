// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * 파일의 종류를 나타내는 열거형 클래스입니다.
 */
enum class FileType {
    UNKNOWN,
    FILE,
    DIRECTORY,
    SYMBOLIC_LINK
}

/**
 * 파일의 통계 정보를 담는 데이터 클래스입니다.
 * 파일 종류, 생성 시간, 수정 시간, 크기 등의 메타데이터를 포함합니다.
 */
data class FileStat(
    val type: FileType,
    val ctime: Long, // 생성 시간 (epoch 밀리초)
    val mtime: Long, // 마지막 수정 시간 (epoch 밀리초)
    val size: Long   // 파일 크기 (바이트)
)

/**
 * 파일 시스템 제공자의 기능(Capabilities)을 정의하는 데이터 클래스입니다.
 */
data class FileSystemProviderCapabilities(
    val isCaseSensitive: Boolean, // 대소문자 구분 여부
    val isReadonly: Boolean,      // 읽기 전용 여부
    // ... (기타 기능들)
)

/**
 * 파일 덮어쓰기 옵션을 정의하는 데이터 클래스입니다.
 */
data class FileOverwriteOptions(
    val overwrite: Boolean // 기존 파일을 덮어쓸지 여부
)

/**
 * 파일 삭제 옵션을 정의하는 데이터 클래스입니다.
 */
data class FileDeleteOptions(
    val recursive: Boolean, // 디렉터리를 재귀적으로 삭제할지 여부
    val useTrash: Boolean   // 파일을 영구 삭제하는 대신 휴지통으로 이동할지 여부
)

/**
 * 파일 변경 이벤트를 나타내는 데이터 클래스입니다.
 */
data class FileChangeDto(
    val type: Int, // 변경 유형: 1=생성, 2=수정, 3=삭제
    val resource: Map<String, Any?> // 변경된 리소스 정보
)

/**
 * 마크다운 형식의 문자열을 나타내는 인터페이스입니다.
 */
interface MarkdownString {
    val value: String
    val isTrusted: Boolean
}

/**
 * IntelliJ 메인 스레드에서 파일 시스템 관련 작업을 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadFileSystemShape`에 해당합니다.
 */
interface MainThreadFileSystemShape : Disposable {
    /**
     * 지정된 핸들과 스키마로 파일 시스템 제공자를 등록합니다.
     * @param handle 제공자의 고유 식별자
     * @param scheme 이 제공자가 처리할 URI 스키마 (예: "file", "ftp")
     */
    fun registerFileSystemProvider(handle: Int, scheme: String)

    /**
     * 파일 시스템 제공자를 등록 해제합니다.
     * @param handle 등록 해제할 제공자의 핸들
     */
    fun unregisterProvider(handle: Int)

    /**
     * 지정된 리소스의 파일 상태 정보를 가져옵니다.
     * @param resource 정보를 가져올 파일 또는 디렉터리의 URI
     * @return 파일 메타데이터를 담은 `FileStat` 객체
     */
    fun stat(resource: URI): FileStat

    /**
     * 디렉터리의 내용을 읽습니다.
     * @param resource 읽을 디렉터리의 URI
     * @return (파일 이름, 파일 타입) 쌍의 리스트
     */
    fun readdir(resource: URI): List<Pair<String, String>>

    /**
     * 파일 내용을 읽습니다.
     * @param uri 읽을 파일의 URI
     * @return 파일 내용을 담은 바이트 배열
     */
    fun readFile(uri: URI): ByteArray

    /**
     * 파일에 내용을 씁니다.
     * @param uri 내용을 쓸 파일의 URI
     * @param content 쓸 내용을 담은 바이트 배열
     * @param overwrite 덮어쓰기 여부
     * @return 쓰여진 내용을 담은 바이트 배열
     */
    fun writeFile(uri: URI, content: ByteArray, overwrite: Boolean): ByteArray

    /**
     * 파일 또는 디렉터리의 이름을 변경합니다.
     * @param source 원본 파일/디렉터리의 URI
     * @param target 대상 위치의 URI
     * @param options 이름 변경 작업에 대한 추가 옵션
     */
    fun rename(source: URI, target: URI, options: Map<String, Any>)

    /**
     * 파일 또는 디렉터리를 복사합니다.
     * @param source 원본 파일/디렉터리의 URI
     * @param target 대상 위치의 URI
     * @param options 복사 작업에 대한 추가 옵션
     */
    fun copy(source: URI, target: URI, options: Map<String, Any>)

    /**
     * 디렉터리를 생성합니다.
     * @param uri 생성할 디렉터리의 URI
     */
    fun mkdir(uri: URI)

    /**
     * 파일 또는 디렉터리를 삭제합니다.
     * @param uri 삭제할 파일/디렉터리의 URI
     * @param options 삭제 작업에 대한 추가 옵션
     */
    fun delete(uri: URI, options: Map<String, Any>)
    
    /**
     * 지정된 스키마의 파일 시스템 제공자가 활성화되도록 보장합니다.
     */
    fun ensureActivation(scheme: String)

    /**
     * 파일 시스템 변경 알림을 처리합니다.
     * @param handle 변경 알림을 보낸 제공자의 핸들
     * @param resources 처리할 파일 변경사항 리스트
     */
    fun onFileSystemChange(handle: Int, resources: List<FileChangeDto>)
}

/**
 * `MainThreadFileSystemShape` 인터페이스의 구현 클래스입니다.
 * IntelliJ 플랫폼의 파일 시스템 API를 사용하여 실제 파일 작업을 수행합니다.
 */
class MainThreadFileSystem : MainThreadFileSystemShape {
    private val logger = Logger.getInstance(MainThreadFileSystem::class.java)
    
    // 등록된 파일 시스템 제공자들을 핸들(Int)을 키로 하여 관리합니다.
    private val providers = ConcurrentHashMap<Int, String>()

    override fun registerFileSystemProvider(handle: Int, scheme: String) {
        logger.info("파일 시스템 제공자 등록: handle=$handle, scheme=$scheme")
        providers[handle] = scheme
        // 실제 구현에서는 scheme에 따라 IntelliJ의 VFS와 연동하는 로직이 필요합니다.
    }

    override fun unregisterProvider(handle: Int) {
        logger.info("파일 시스템 제공자 등록 해제: handle=$handle")
        providers.remove(handle)
    }

    override fun stat(resource: URI): FileStat {
        logger.info("파일 상태 정보 조회: $resource")
        val file = File(resource.path)
        if (!file.exists()) throw Exception("파일이 존재하지 않음: ${resource.path}")
        
        val type = when {
            file.isDirectory -> FileType.DIRECTORY
            Files.isSymbolicLink(file.toPath()) -> FileType.SYMBOLIC_LINK
            else -> FileType.FILE
        }
        
        return FileStat(type, file.lastModified(), file.lastModified(), file.length())
    }

    override fun readdir(resource: URI): List<Pair<String, String>> {
        logger.info("디렉터리 내용 읽기: $resource")
        val file = File(resource.path)
        if (!file.isDirectory) throw Exception("디렉터리가 아님: ${resource.path}")
        
        return file.listFiles()?.map { 
            Pair(it.name, if (it.isDirectory) FileType.DIRECTORY.ordinal.toString() else FileType.FILE.ordinal.toString())
        } ?: emptyList()
    }

    override fun readFile(uri: URI): ByteArray {
        logger.info("파일 내용 읽기: $uri")
        val file = File(uri.path)
        if (!file.isFile) throw Exception("파일이 아니거나 존재하지 않음: ${uri.path}")
        return file.readBytes()
    }

    override fun writeFile(uri: URI, content: ByteArray, overwrite: Boolean): ByteArray {
        logger.info("파일 내용 쓰기: $uri, 크기: ${content.size} 바이트")
        val file = File(uri.path)
        if (file.exists() && !overwrite) throw Exception("파일이 이미 존재하며 덮어쓰기가 허용되지 않음: ${uri.path}")
        
        file.parentFile?.mkdirs()
        file.writeBytes(content)
        return content
    }

    override fun rename(source: URI, target: URI, options: Map<String, Any>) {
        logger.info("이름 변경: $source -> $target")
        val sourceFile = File(source.path)
        val targetFile = File(target.path)
        val overwrite = options["overwrite"] as? Boolean ?: false
        
        if (!sourceFile.exists()) throw Exception("원본 파일이 존재하지 않음: ${source.path}")
        if (targetFile.exists() && !overwrite) throw Exception("대상 파일이 이미 존재하며 덮어쓰기가 허용되지 않음: ${target.path}")
        
        targetFile.parentFile?.mkdirs()
        if (!sourceFile.renameTo(targetFile)) {
            // renameTo가 실패할 경우(예: 다른 파일 시스템 간 이동), Files.move를 시도합니다.
            Files.move(sourceFile.toPath(), targetFile.toPath(), if (overwrite) StandardCopyOption.REPLACE_EXISTING else StandardCopyOption.ATOMIC_MOVE)
        }
    }

    override fun copy(source: URI, target: URI, options: Map<String, Any>) {
        logger.info("복사: $source -> $target")
        val sourceFile = File(source.path)
        val targetFile = File(target.path)
        val overwrite = options["overwrite"] as? Boolean ?: false

        if (!sourceFile.exists()) throw Exception("원본 파일이 존재하지 않음: ${source.path}")
        if (targetFile.exists() && !overwrite) throw Exception("대상 파일이 이미 존재하며 덮어쓰기가 허용되지 않음: ${target.path}")

        targetFile.parentFile?.mkdirs()
        if (sourceFile.isDirectory) {
            sourceFile.copyRecursively(targetFile, overwrite)
        } else {
            sourceFile.copyTo(targetFile, overwrite)
        }
    }

    override fun mkdir(uri: URI) {
        logger.info("디렉터리 생성: $uri")
        val file = File(uri.path)
        if (file.exists()) throw Exception("파일 또는 디렉터리가 이미 존재함: ${uri.path}")
        if (!file.mkdirs()) throw Exception("디렉터리 생성 실패: ${uri.path}")
    }

    override fun delete(uri: URI, options: Map<String, Any>) {
        logger.info("삭제: $uri, 옵션: $options")
        val file = File(uri.path)
        val recursive = options["recursive"] as? Boolean ?: false
        val useTrash = options["useTrash"] as? Boolean ?: false

        if (!file.exists()) return // 파일이 없으면 성공으로 간주
        
        if (useTrash) {
            // TODO: 실제 휴지통으로 이동하는 로직 구현 필요
            logger.warn("휴지통 기능은 구현되지 않았습니다. 직접 삭제를 수행합니다.")
        }
        
        if (file.isDirectory && recursive) {
            file.deleteRecursively()
        } else if (file.isDirectory && !recursive) {
            throw Exception("비어있지 않은 디렉터리는 recursive=true 옵션 없이 삭제할 수 없음: ${uri.path}")
        } else {
            file.delete()
        }
    }

    override fun ensureActivation(scheme: String) {
        logger.info("활성화 보장: $scheme")
        // 실제 구현에서는 IntelliJ의 VFS에 새로고침을 알리는 등의 작업이 필요할 수 있습니다.
    }

    override fun onFileSystemChange(handle: Int, resources: List<FileChangeDto>) {
        logger.info("파일 시스템 변경 알림: handle=$handle, resources=${resources.joinToString { it.resource.toString() }}")
        // 실제 구현에서는 이 변경사항을 IntelliJ의 다른 부분에 전파하는 로직이 필요합니다.
    }
    
    private fun getPathFromUriComponents(uri: URI): String {
        return File(uri).path
    }
    
    override fun dispose() {
        logger.info("Disposing MainThreadFileSystem resources")
        providers.clear()
    }
}
