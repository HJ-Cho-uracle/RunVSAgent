// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.cline

import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.config.ExtensionConfiguration
import com.sina.weibo.agent.extensions.core.ExtensionManagerFactory
import com.sina.weibo.agent.extensions.core.VsixManager
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.extensions.common.ExtensionType
import com.sina.weibo.agent.extensions.config.ExtensionMetadata
import com.sina.weibo.agent.extensions.core.VsixManager.Companion.getBaseDirectory
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.PluginResourceUtil
import java.io.File

/**
 * Cline AI 확장 제공자 구현체입니다.
 * `ExtensionProvider` 인터페이스를 구현하여 Cline AI 확장에 대한 메타데이터와 생명주기 메소드를 제공합니다.
 */
class ClineExtensionProvider : ExtensionProvider {
    
    // 확장의 고유 ID를 반환합니다.
    override fun getExtensionId(): String = "cline"
    
    // 확장의 표시 이름을 반환합니다.
    override fun getDisplayName(): String = "Cline AI"
    
    // 확장에 대한 설명을 반환합니다.
    override fun getDescription(): String = "고급 기능을 갖춘 AI 기반 코딩 어시스턴트"
    
    /**
     * Cline 확장을 초기화합니다.
     * @param project 현재 IntelliJ 프로젝트
     */
    override fun initialize(project: Project) {
        // Cline 확장 설정을 초기화합니다.
        val extensionConfig = ExtensionConfiguration.getInstance(project)
        extensionConfig.initialize()
        
        // ExtensionManagerFactory를 초기화합니다. (필요한 경우)
        try {
            val extensionManagerFactory = ExtensionManagerFactory.getInstance(project)
            extensionManagerFactory.initialize()
        } catch (e: Exception) {
            // ExtensionManagerFactory를 사용할 수 없어도 Cline 확장은 독립적으로 작동할 수 있습니다.
        }
    }

    /**
     * Cline 확장이 사용 가능한지 여부를 확인합니다.
     * 확장의 코드 디렉터리가 프로젝트 경로, VSIX 설치 경로, 플러그인 리소스 경로 중 하나에 존재하는지 확인합니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    override fun isAvailable(project: Project): Boolean {
        val extensionConfig = ExtensionConfiguration.getInstance(project)
        val config = extensionConfig.getConfig(ExtensionType.CLINE)

        // 1. VSIX 설치 경로 확인
        val vsixPath = VsixManager.getInstance().getVsixInstallationPath(getExtensionId())
        if (vsixPath != null && File(vsixPath).exists()) {
            return true
        }

        // 2. 플러그인 리소스 경로 확인 (내장 확장용)
        try {
            val pluginResourcePath = PluginResourceUtil.getResourcePath(
                PluginConstants.PLUGIN_ID,
                config.codeDir
            )
            if (pluginResourcePath != null && File(pluginResourcePath).exists()) {
                return true
            }
        } catch (e: Exception) {
            // 플러그인 리소스 확인 중 예외 발생 시 무시합니다.
        }

        // 3. 프로젝트 경로 확인 (개발/테스트용)
        val projectPath = project.basePath
        if (projectPath != null) {
            val possiblePaths = listOf(
                "$projectPath/${config.codeDir}",
                "$projectPath/../${config.codeDir}",
                "$projectPath/../../${config.codeDir}"
            )
            if (possiblePaths.any { File(it).exists() }) {
                return true
            }
        }

        // 모든 경로에서 찾지 못하면 사용 불가능
        return false
    }
    
    /**
     * Cline 확장의 설정 메타데이터를 가져옵니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return `ExtensionMetadata` 객체
     */
    override fun getConfiguration(project: Project): ExtensionMetadata {
        val extensionConfig = ExtensionConfiguration.getInstance(project)
        val config = extensionConfig.getConfig(ExtensionType.CLINE)
        
        // `ExtensionMetadata` 인터페이스를 구현하는 익명 객체를 반환합니다.
        return object : ExtensionMetadata {
            override fun getCodeDir(): String = config.codeDir
            override fun getPublisher(): String = config.publisher
            override fun getVersion(): String = config.version
            override fun getMainFile(): String = config.mainFile
            override fun getActivationEvents(): List<String> = config.activationEvents
            override fun getEngines(): Map<String, String> = config.engines
            override fun getCapabilities(): Map<String, Any> = config.capabilities
            override fun getExtensionDependencies(): List<String> = config.extensionDependencies
        }
    }
    
    /**
     * Cline 확장 리소스를 해제합니다. (필요한 경우 구현)
     */
    override fun dispose() {
        // 필요한 리소스 정리 로직을 여기에 추가할 수 있습니다.
    }
}
