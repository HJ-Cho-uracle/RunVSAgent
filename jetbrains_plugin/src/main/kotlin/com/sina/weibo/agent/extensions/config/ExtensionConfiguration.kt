// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.common.ExtensionType
import java.util.Properties
import java.io.File
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.ConfigFileUtils

/**
 * Roo Code 플러그인을 위한 확장 설정 관리자입니다.
 * 다양한 확장 타입에 대한 설정을 관리하고, 현재 활성화된 확장 타입을 추적합니다.
 */
@Service(Service.Level.PROJECT)
class ExtensionConfiguration(private val project: Project) {
    private val LOG = Logger.getInstance(ExtensionConfiguration::class.java)
    
    // 현재 활성화된 확장 타입 (기본값은 ROO_CODE)
    @Volatile
    private var currentExtensionType: ExtensionType = ExtensionType.getDefault()
    
    // 확장 설정 캐시 (확장 타입 -> 설정 객체)
    private val extensionConfigs = mutableMapOf<ExtensionType, ExtensionConfig>()
    
    companion object {
        /**
         * `ExtensionConfiguration`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): ExtensionConfiguration {
            return project.getService(ExtensionConfiguration::class.java)
                ?: error("ExtensionConfiguration 서비스를 찾을 수 없습니다.")
        }
    }
    
    /**
     * 확장 설정을 초기화합니다.
     * 모든 확장 타입에 대한 설정을 로드하고, 저장된 설정에 따라 현재 확장 타입을 설정합니다.
     */
    fun initialize() {
        LOG.info("확장 설정 초기화 중")
        
        // 모든 확장 타입에 대한 설정을 로드합니다.
        ExtensionType.getAllTypes().forEach { extensionType ->
            loadConfiguration(extensionType)
        }
        
        // 설정 파일에서 현재 확장 타입을 읽어오거나, 기본값을 사용합니다.
        val configuredType = getConfiguredExtensionType()
        currentExtensionType = configuredType ?: ExtensionType.getDefault()
        
        LOG.info("확장 설정 초기화 완료, 현재 타입: ${currentExtensionType.code}")
    }
    
    /**
     * 현재 활성화된 확장 타입을 가져옵니다.
     */
    fun getCurrentExtensionType(): ExtensionType {
        return currentExtensionType
    }
    
    /**
     * 현재 활성화된 확장 타입을 설정하고, 이를 설정 파일에 저장합니다.
     * @param extensionType 새로 설정할 확장 타입
     */
    fun setCurrentExtensionType(extensionType: ExtensionType) {
        LOG.info("확장 타입 변경: ${currentExtensionType.code} -> ${extensionType.code}")
        currentExtensionType = extensionType
        saveCurrentExtensionType()
    }
    
    /**
     * 현재 활성화된 확장 타입에 대한 설정 객체를 가져옵니다.
     */
    fun getCurrentConfig(): ExtensionConfig {
        return getConfig(currentExtensionType)
    }
    
    /**
     * 특정 확장 타입에 대한 설정 객체를 가져옵니다.
     * @param extensionType 설정을 가져올 확장 타입
     * @return 해당 확장 타입의 설정 객체, 캐시에 없으면 기본값 반환
     */
    fun getConfig(extensionType: ExtensionType): ExtensionConfig {
        return extensionConfigs[extensionType] ?: ExtensionConfig.getDefault(extensionType)
    }
    
    /**
     * 특정 확장 타입에 대한 설정을 로드하여 캐시에 저장합니다.
     */
    private fun loadConfiguration(extensionType: ExtensionType) {
        try {
            val config = ExtensionConfig.loadFromProperties(extensionType)
            extensionConfigs[extensionType] = config
            LOG.info("${extensionType.code}에 대한 설정 로드 완료")
        } catch (e: Exception) {
            LOG.warn("${extensionType.code}에 대한 설정 로드 실패, 기본값 사용", e)
            extensionConfigs[extensionType] = ExtensionConfig.getDefault(extensionType)
        }
    }
    
    /**
     * 설정 파일에서 현재 활성화된 확장 타입을 읽어옵니다.
     */
    private fun getConfiguredExtensionType(): ExtensionType? {
        return try {
            val properties = ConfigFileUtils.loadMainConfig()
            val typeCode = properties.getProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY)
            if (typeCode != null) {
                ExtensionType.fromCode(typeCode)
            } else null
        } catch (e: Exception) {
            LOG.warn("확장 타입 설정 읽기 실패", e)
            null
        }
    }
    
    /**
     * 현재 활성화된 확장 타입을 설정 파일에 저장합니다.
     */
    private fun saveCurrentExtensionType() {
        try {
            val properties = Properties()
            properties.setProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY, currentExtensionType.code)
            
            ConfigFileUtils.saveMainConfig(properties, "VSCode Agent Configuration")
            
            LOG.info("확장 타입 설정 저장 완료: ${currentExtensionType.code}")
        } catch (e: Exception) {
            LOG.error("확장 타입 설정 저장 실패", e)
        }
    }
}

/**
 * 확장의 설정 데이터를 담는 데이터 클래스입니다.
 */
data class ExtensionConfig(
    val extensionType: ExtensionType, // 확장의 타입
    val codeDir: String,              // 확장 코드가 위치한 디렉터리 이름
    val displayName: String,          // 확장의 표시 이름
    val description: String,          // 확장의 설명
    val publisher: String,            // 확장의 게시자
    val version: String,              // 확장의 버전
    val mainFile: String,             // 확장의 메인 진입점 파일 (예: "./dist/extension.js")
    val activationEvents: List<String>, // 확장 활성화 이벤트 목록
    val engines: Map<String, String>, // 호환되는 엔진 정보 (예: "vscode" 버전)
    val capabilities: Map<String, Any>, // 확장의 기능 목록
    val extensionDependencies: List<String> // 확장의 의존성 목록
) {
    companion object {
        /**
         * 특정 확장 타입에 대한 기본 설정 객체를 가져옵니다.
         */
        fun getDefault(extensionType: ExtensionType): ExtensionConfig {
            return when (extensionType) {
                ExtensionType.ROO_CODE -> ExtensionConfig(
                    extensionType = extensionType,
                    codeDir = "roo-code",
                    displayName = "Roo Code",
                    description = "AI 기반 코드 어시스턴트",
                    publisher = "WeCode-AI",
                    version = "1.0.0",
                    mainFile = "./dist/extension.js",
                    activationEvents = listOf("onStartupFinished"),
                    engines = mapOf("vscode" to "^1.0.0"),
                    capabilities = emptyMap(),
                    extensionDependencies = emptyList()
                )
                ExtensionType.CLINE -> ExtensionConfig(
                    extensionType = extensionType,
                    codeDir = "cline",
                    displayName = "Cline",
                    description = "고급 기능을 갖춘 AI 기반 코딩 어시스턴트",
                    publisher = "Cline-AI",
                    version = "1.0.0",
                    mainFile = "./dist/extension.js",
                    activationEvents = listOf("onStartupFinished"),
                    engines = mapOf("vscode" to "^1.0.0"),
                    capabilities = emptyMap(),
                    extensionDependencies = emptyList()
                )
                ExtensionType.KILO_CODE -> ExtensionConfig(
                    extensionType = extensionType,
                    codeDir = "kilo-code",
                    displayName = "Kilo Code",
                    description = "고급 기능을 갖춘 AI 기반 코드 어시스턴트",
                    publisher = "Kilo-AI",
                    version = "1.0.0",
                    mainFile = "./dist/extension.js",
                    activationEvents = listOf("onStartupFinished"),
                    engines = mapOf("vscode" to "^1.0.0"),
                    capabilities = emptyMap(),
                    extensionDependencies = emptyList()
                )
                ExtensionType.COSTRICT -> ExtensionConfig(
                    extensionType = extensionType,
                    codeDir = "costrict",
                    displayName = "Costrict",
                    description = "고급 기능을 갖춘 AI 기반 코드 어시스턴트",
                    publisher = "zgsm-ai",
                    version = "1.6.5",
                    mainFile = "./dist/extension.js",
                    activationEvents = listOf("onStartupFinished"),
                    engines = mapOf("vscode" to "^1.0.0"),
                    capabilities = emptyMap(),
                    extensionDependencies = emptyList()
                )
            }
        }
        
        /**
         * (현재는 구현되지 않음) 프로퍼티 파일로부터 설정을 로드합니다.
         */
        fun loadFromProperties(extensionType: ExtensionType): ExtensionConfig {
            // 현재는 프로퍼티 파일에서 로드하는 대신 기본 설정을 반환합니다.
            return getDefault(extensionType)
        }
    }
}
