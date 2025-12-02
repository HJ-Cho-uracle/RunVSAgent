// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.theme.ThemeManager.Companion.getDefaultThemeResourceDir
import com.sina.weibo.agent.theme.ThemeManager.Companion.getThemeResourceDir
import com.sina.weibo.agent.util.PluginConstants.ConfigFiles.getUserConfigDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.io.BufferedInputStream

/**
 * 확장을 위한 VSIX 파일 관리자입니다.
 * VSIX 파일 설치, 관리 및 리소스 경로 해결을 담당합니다.
 */
class VsixManager {
    
    companion object {
        private val LOG = Logger.getInstance(VsixManager::class.java)

        /**
         * `VsixManager`의 인스턴스를 가져옵니다.
         */
        fun getInstance(): VsixManager = VsixManager()
        
        /**
         * VSIX 설치를 위한 기본 디렉터리 경로를 가져옵니다.
         * (예: `~/.roo-cline/plugins`)
         */
        fun getBaseDirectory(): String {
            return "${getUserConfigDir()}/plugins"
        }
    }
    
    /**
     * 확장을 위해 VSIX 파일을 설치합니다.
     * 기존 설치를 덮어쓰는 것을 지원합니다.
     * @param vsixFile 설치할 VSIX 파일
     * @param extensionId 설치할 확장의 ID
     * @return 설치 성공 여부
     */
    fun installVsix(vsixFile: File, extensionId: String): Boolean {
        return try {
            val targetDir = getExtensionDirectory(extensionId)
            LOG.info("확장 '$extensionId'를 위해 VSIX를 '$targetDir'에 설치 중")
            
            val existingInstallation = hasVsixInstallation(extensionId)
            if (existingInstallation) {
                LOG.info("확장 '$extensionId'가 이미 존재합니다. 기존 설치를 덮어씁니다.")
            }
            
            // 대상 디렉터리를 생성합니다. (extractVsixFile에서 기존 내용이 정리될 수 있음)
            val targetPath = Paths.get(targetDir)
            Files.createDirectories(targetPath)
            
            // VSIX 파일의 압축을 해제합니다. (기존 내용은 자동으로 정리됩니다.)
            val success = extractVsixFile(vsixFile, targetDir)
            if (success) {
                if (existingInstallation) {
                    LOG.info("확장 '$extensionId'에 대한 VSIX 설치가 성공적으로 업데이트되었습니다.")
                } else {
                    LOG.info("확장 '$extensionId'에 대한 VSIX 설치가 성공적으로 완료되었습니다.")
                }
            } else {
                LOG.error("확장 '$extensionId'에 대한 VSIX 압축 해제 실패")
            }
            success
        } catch (e: Exception) {
            LOG.error("확장 '$extensionId'에 대한 VSIX 설치 실패", e)
            false
        }
    }
    
    /**
     * 특정 확장의 설치 디렉터리 경로를 가져옵니다.
     * @param extensionId 확장의 ID
     * @return 확장의 설치 디렉터리 절대 경로
     */
    fun getExtensionDirectory(extensionId: String): String {
        return "${getBaseDirectory()}/$extensionId"
    }
    
    /**
     * 특정 확장이 VSIX를 통해 설치되었는지 확인합니다.
     * @param extensionId 확장의 ID
     * @return VSIX 설치가 존재하면 true
     */
    fun hasVsixInstallation(extensionId: String): Boolean {
        val extensionDir = getExtensionDirectory(extensionId)
        val dir = File(extensionDir)
        return dir.exists() && dir.isDirectory && dir.listFiles()?.isNotEmpty() == true
    }
    
    /**
     * 특정 확장의 VSIX 설치 경로를 가져옵니다.
     * @param extensionId 확장의 ID
     * @return VSIX 설치 경로, 없으면 null
     */
    fun getVsixInstallationPath(extensionId: String): String? {
        return if (hasVsixInstallation(extensionId)) {
            getExtensionDirectory(extensionId)
        } else {
            null
        }
    }
    
    /**
     * 확장의 VSIX 설치를 제거합니다.
     * @param extensionId 제거할 확장의 ID
     * @return 제거 성공 여부
     */
    fun uninstallVsix(extensionId: String): Boolean {
        return try {
            val extensionDir = getExtensionDirectory(extensionId)
            val dir = File(extensionDir)
            if (dir.exists()) {
                deleteDirectory(dir)
                LOG.info("확장 '$extensionId'에 대한 VSIX 제거 완료")
                true
            } else {
                LOG.info("확장 '$extensionId'에 대한 VSIX 설치를 찾을 수 없습니다.")
                true
            }
        } catch (e: Exception) {
            LOG.error("확장 '$extensionId'에 대한 VSIX 제거 실패", e)
            false
        }
    }
    
    /**
     * 설치된 모든 VSIX 확장 목록을 가져옵니다.
     * @return 설치된 확장의 ID 리스트
     */
    fun listInstalledExtensions(): List<String> {
        val baseDir = File(getBaseDirectory())
        if (!baseDir.exists() || !baseDir.isDirectory) {
            return emptyList()
        }
        
        return baseDir.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.map { it.name }
            ?: emptyList()
    }
    
    /**
     * VSIX 파일을 대상 디렉터리에 압축 해제합니다.
     * 먼저 임시 디렉터리에 압축을 해제한 후, 'extension' 디렉터리 내용만 대상 디렉터리로 이동합니다.
     * @param vsixFile 압축 해제할 VSIX 파일
     * @param targetDir 압축 해제될 최종 대상 디렉터리
     * @return 압축 해제 성공 여부
     */
    private fun extractVsixFile(vsixFile: File, targetDir: String): Boolean {
        var tempDir: File? = null
        
        return try {
            tempDir = Files.createTempDirectory("vsix-extract-").toFile()
            LOG.debug("임시 디렉터리 생성: ${tempDir.absolutePath}")
            
            // VSIX 파일의 압축을 임시 디렉터리에 해제합니다.
            ZipInputStream(BufferedInputStream(FileInputStream(vsixFile))).use { zis ->
                var entry = zis.nextEntry
                var extractedCount = 0
                
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryPath = tempDir!!.toPath().resolve(entry.name)
                        Files.createDirectories(entryPath.parent) // 부모 디렉터리 생성
                        Files.copy(zis, entryPath) // 파일 추출
                        extractedCount++
                    }
                    entry = zis.nextEntry
                }
                LOG.debug("임시 디렉터리에 $extractedCount 개 파일 추출 완료")
            }
            
            // 임시 디렉터리 내의 'extension' 폴더를 찾습니다.
            val extensionDir = File(tempDir, "extension")
            if (!extensionDir.exists() || !extensionDir.isDirectory) {
                LOG.error("VSIX 파일에서 'extension' 디렉터리를 찾을 수 없습니다.")
                return false
            }
            
            // 대상 디렉터리가 존재하면 내용을 지웁니다.
            val targetPath = Paths.get(targetDir)
            if (Files.exists(targetPath)) {
                deleteDirectory(targetPath.toFile())
            }
            Files.createDirectories(targetPath) // 대상 디렉터리 생성
            
            // 'extension' 디렉터리 내용을 대상 디렉터리로 이동합니다.
            val extensionFiles = extensionDir.listFiles()
            if (extensionFiles == null || extensionFiles.isEmpty()) {
                LOG.warn("'extension' 디렉터리가 비어 있습니다.")
                return false
            }
            
            var movedCount = 0
            for (file in extensionFiles) {
                val targetFile = targetPath.resolve(file.name).toFile()
                if (file.isDirectory) {
                    moveDirectory(file, targetFile) // 디렉터리 재귀 이동
                } else {
                    Files.move(file.toPath(), targetFile.toPath()) // 파일 이동
                }
                movedCount++
            }
            LOG.info("VSIX 압축 해제 완료. 'extension' 디렉터리에서 '$targetDir'로 $movedCount 개 항목 이동.")
            
            // 테마 파일을 통합 디렉터리로 복사합니다.
            copyThemesToIntegrations(targetDir)
            
            // 실제로 파일이 이동되었는지 확인합니다.
            val targetFiles = targetPath.toFile().listFiles()
            if (targetFiles == null || targetFiles.isEmpty()) {
                LOG.warn("대상 디렉터리로 파일이 이동되지 않았습니다.")
                return false
            }
            
            true
        } catch (e: Exception) {
            LOG.error("VSIX 파일 압축 해제 실패", e)
            false
        } finally {
            // 임시 디렉터리 정리
            tempDir?.let { temp ->
                try {
                    deleteDirectory(temp)
                    LOG.debug("임시 디렉터리 정리 완료: ${temp.absolutePath}")
                } catch (e: Exception) {
                    LOG.warn("임시 디렉터리 정리 실패", e)
                }
            }
        }
    }
    
    /**
     * 디렉터리를 재귀적으로 이동합니다.
     */
    private fun moveDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                moveDirectory(file, targetFile)
            } else {
                Files.move(file.toPath(), targetFile.toPath())
            }
        }
        source.delete() // 원본 디렉터리 삭제
    }
    
    /**
     * 플러그인 테마를 확장의 `integrations/theme/default-themes` 디렉터리로 복사합니다.
     */
    private fun copyThemesToIntegrations(extensionDir: String) {
        try {
            val pluginThemesDir = getPluginThemesDirectory()
            if (pluginThemesDir == null) {
                LOG.warn("플러그인 테마 디렉터리를 찾을 수 없어 테마 복사를 건너뜁니다.")
                return
            }

            var integrationsThemeDir = getThemeResourceDir(extensionDir)
            if (integrationsThemeDir == null) {
                integrationsThemeDir = getDefaultThemeResourceDir(extensionDir)
                LOG.warn("플러그인 테마 디렉터리를 찾을 수 없어 새로 생성합니다.")
            }
            
            Files.createDirectories(integrationsThemeDir) // 통합 테마 디렉터리 생성
            
            val themeFiles = pluginThemesDir.listFiles { file -> 
                file.isFile && file.extension?.lowercase() == "css"
            }
            
            if (themeFiles != null) {
                var copiedCount = 0
                for (themeFile in themeFiles) {
                    val targetFile = integrationsThemeDir.resolve(themeFile.name).toFile()
                    Files.copy(themeFile.toPath(), targetFile.toPath())
                    copiedCount++
                    LOG.debug("테마 파일 복사: ${themeFile.name} -> 통합 디렉터리")
                }
                LOG.info("통합 디렉터리에 $copiedCount 개 테마 파일 복사 완료")
            } else {
                LOG.warn("플러그인 테마 디렉터리에서 테마 파일을 찾을 수 없습니다.")
            }
            
        } catch (e: Exception) {
            LOG.warn("통합 디렉터리로 테마 복사 실패", e)
        }
    }
    
    /**
     * 플러그인 테마 디렉터리 경로를 가져옵니다.
     */
    private fun getPluginThemesDirectory(): File? {
        return try {
            val pluginThemesPath = com.sina.weibo.agent.util.PluginResourceUtil.getResourcePath(
                com.sina.weibo.agent.util.PluginConstants.PLUGIN_ID,
                "themes"
            )
            
            if (pluginThemesPath != null) {
                val themesDir = File(pluginThemesPath)
                if (themesDir.exists() && themesDir.isDirectory) {
                    return themesDir
                }
            }
            
            // 플러그인 리소스에서 찾지 못하면 현재 작업 디렉터리에서 찾습니다.
            val currentDir = File(System.getProperty("user.dir") ?: "")
            val themesDir = File(currentDir, "src/main/resources/themes")
            if (themesDir.exists() && themesDir.isDirectory) {
                return themesDir
            }
            
            LOG.warn("플러그인 테마 디렉터리를 찾을 수 없습니다.")
            null
        } catch (e: Exception) {
            LOG.warn("플러그인 테마 디렉터리 가져오기 실패", e)
            null
        }
    }
    
    /**
     * 디렉터리와 그 내용을 모두 삭제합니다.
     */
    private fun deleteDirectory(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                deleteDirectory(file)
            }
        }
        dir.delete()
    }
    
    /**
     * VSIX 지원을 포함하여 확장의 리소스 경로를 가져옵니다.
     * 우선순위: 프로젝트 경로 > VSIX 설치 경로 > 플러그인 리소스
     */
    fun getExtensionResourcePath(
        extensionId: String,
        codeDir: String,
        projectPath: String?
    ): String? {
        // 1. 프로젝트 경로 확인
        if (projectPath != null) {
            val possiblePaths = listOf(
                "$projectPath/$codeDir",
                "$projectPath/../$codeDir",
                "$projectPath/../../$codeDir"
            )
            
            for (path in possiblePaths) {
                if (File(path).exists()) {
                    LOG.debug("프로젝트 경로에서 확장 리소스 찾음: $path")
                    return path
                }
            }
        }
        
        // 2. VSIX 설치 경로 확인
        val vsixPath = getVsixInstallationPath(extensionId)
        if (vsixPath != null) {
            // 확장 내용이 대상 디렉터리에 직접 추출되므로 'extension' 하위 디렉터리를 확인할 필요가 없습니다.
            if (File(vsixPath).exists()) {
                LOG.debug("VSIX 설치 경로에서 확장 리소스 찾음: $vsixPath")
                return vsixPath
            }
        }
        
        // 3. 플러그인 리소스 확인
        try {
            val pluginPath = com.sina.weibo.agent.util.PluginResourceUtil.getResourcePath(
                com.sina.weibo.agent.util.PluginConstants.PLUGIN_ID,
                codeDir
            )
            if (pluginPath != null && File(pluginPath).exists()) {
                LOG.debug("플러그인 리소스에서 확장 리소스 찾음: $pluginPath")
                return pluginPath
            }
        } catch (e: Exception) {
            LOG.debug("확장 '$extensionId'에 대한 플러그인 리소스 경로 가져오기 실패", e)
        }
        
        LOG.debug("확장 '$extensionId'에 대한 확장 리소스를 찾을 수 없습니다.")
        return null
    }
}
