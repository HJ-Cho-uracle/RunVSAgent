// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import java.io.File
import java.util.Properties
import java.io.IOException

/**
 * 설정 파일 유틸리티 클래스입니다.
 * 플러그인의 설정 파일(메인 설정 및 확장별 설정)을 읽고, 쓰고, 관리하는 통합 메소드를 제공합니다.
 */
object ConfigFileUtils {

    /**
     * 현재 활성화된 확장의 ID를 설정 파일에서 읽어옵니다.
     * @return 현재 확장의 ID 문자열, 또는 설정되지 않았으면 null
     */
    fun getCurrentExtensionId(): String? {
        val properties = loadMainConfig()
        val currentExtensionId = properties.getProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY)
        return currentExtensionId
    }
    
    /**
     * 설정 디렉터리가 존재하는지 확인하고, 없으면 생성합니다.
     * @throws IOException 설정 디렉터리 생성에 실패할 경우
     */
    fun ensureConfigDirExists() {
        try {
            val configDir = File(PluginConstants.ConfigFiles.getUserConfigDir())
            if (!configDir.exists()) {
                configDir.mkdirs()
                println("설정 디렉터리 생성됨: ${configDir.absolutePath}")
            }
        } catch (e: Exception) {
            throw IOException("설정 디렉터리 생성 실패", e)
        }
    }
    
    /**
     * 메인 설정 파일에서 속성(Properties)을 로드합니다.
     * @return 로드된 `Properties` 객체
     * @throws IOException 설정 파일 로드에 실패할 경우
     */
    fun loadMainConfig(): Properties {
        val properties = Properties()
        try {
            val configFile = File(PluginConstants.ConfigFiles.getMainConfigPath())
            if (configFile.exists()) {
                properties.load(configFile.inputStream())
            }
        } catch (e: IOException) {
            throw IOException("메인 설정 파일 로드 실패", e)
        }
        return properties
    }
    
    /**
     * 메인 설정 파일에 속성(Properties)을 저장합니다.
     * @param properties 저장할 `Properties` 객체
     * @param comment 설정 파일에 추가할 주석
     * @throws IOException 설정 파일 저장에 실패할 경우
     */
    fun saveMainConfig(properties: Properties, comment: String = "RunVSAgent Configuration") {
        try {
            ensureConfigDirExists() // 설정 디렉터리가 있는지 확인하고 없으면 생성
            
            val configFile = File(PluginConstants.ConfigFiles.getMainConfigPath())
            properties.store(configFile.outputStream(), comment)
        } catch (e: IOException) {
            throw IOException("메인 설정 파일 저장 실패", e)
        }
    }
    
    /**
     * 확장별 설정 파일에서 속성(Properties)을 로드합니다.
     * @param extensionId 설정을 로드할 확장의 ID
     * @return 로드된 `Properties` 객체
     * @throws IOException 확장 설정 파일 로드에 실패할 경우
     */
    fun loadExtensionConfig(extensionId: String): Properties {
        val properties = Properties()
        try {
            val configFile = File(PluginConstants.ConfigFiles.getExtensionConfigPath(extensionId))
            if (configFile.exists()) {
                properties.load(configFile.inputStream())
            }
        } catch (e: IOException) {
            throw IOException("확장 설정 파일 로드 실패: $extensionId", e)
        }
        return properties
    }
    
    /**
     * 확장별 설정 파일에 속성(Properties)을 저장합니다.
     * @param extensionId 설정을 저장할 확장의 ID
     * @param properties 저장할 `Properties` 객체
     * @param comment 설정 파일에 추가할 주석
     * @throws IOException 확장 설정 파일 저장에 실패할 경우
     */
    fun saveExtensionConfig(extensionId: String, properties: Properties, comment: String = "Extension Configuration for $extensionId") {
        try {
            ensureConfigDirExists() // 설정 디렉터리가 있는지 확인하고 없으면 생성
            
            val configFile = File(PluginConstants.ConfigFiles.getExtensionConfigPath(extensionId))
            properties.store(configFile.outputStream(), comment)
        } catch (e: IOException) {
            throw IOException("확장 설정 파일 저장 실패: $extensionId", e)
        }
    }
    
    /**
     * 메인 설정 파일의 경로를 가져옵니다.
     * @return 메인 설정 파일의 절대 경로 문자열
     */
    fun getMainConfigPath(): String {
        return PluginConstants.ConfigFiles.getMainConfigPath()
    }
    
    /**
     * 확장별 설정 파일의 경로를 가져옵니다.
     * @param extensionId 확장의 ID
     * @return 확장 설정 파일의 절대 경로 문자열
     */
    fun getExtensionConfigPath(extensionId: String): String {
        return PluginConstants.ConfigFiles.getExtensionConfigPath(extensionId)
    }
    
    /**
     * 메인 설정 파일이 존재하는지 확인합니다.
     * @return 메인 설정 파일이 존재하면 true
     */
    fun mainConfigExists(): Boolean {
        val configFile = File(PluginConstants.ConfigFiles.getMainConfigPath())
        return configFile.exists()
    }
    
    /**
     * 확장별 설정 파일이 존재하는지 확인합니다.
     * @param extensionId 확장의 ID
     * @return 확장 설정 파일이 존재하면 true
     */
    fun extensionConfigExists(extensionId: String): Boolean {
        val configFile = File(PluginConstants.ConfigFiles.getExtensionConfigPath(extensionId))
        return configFile.exists()
    }
    
    /**
     * 메인 설정 파일에서 특정 키의 값을 가져옵니다.
     * @param key 가져올 값의 키
     * @param defaultValue 키가 없을 경우 반환할 기본값 (선택 사항)
     * @return 키에 해당하는 값, 또는 기본값, 또는 null
     */
    fun getConfigValue(key: String, defaultValue: String? = null): String? {
        return try {
            val properties = loadMainConfig()
            properties.getProperty(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    /**
     * 메인 설정 파일에 특정 키의 값을 설정합니다.
     * @param key 설정할 값의 키
     * @param value 설정할 값
     * @throws IOException 설정 값 설정에 실패할 경우
     */
    fun setConfigValue(key: String, value: String) {
        try {
            val properties = loadMainConfig()
            properties.setProperty(key, value)
            saveMainConfig(properties)
        } catch (e: Exception) {
            throw IOException("설정 값 설정 실패: $key", e)
        }
    }
    
    /**
     * 기본 메인 설정 파일을 생성합니다.
     * `EXTENSION_TYPE_KEY`를 "roo-code"로 설정하고 주석을 추가합니다.
     */
    fun createDefaultMainConfig() {
        val properties = Properties()
        properties.setProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY, "roo-code")
        properties.setProperty("# Available extension types:", "")
        properties.setProperty("# - roo-code: Roo Code extension", "")
        properties.setProperty("# - cline: Cline AI extension", "")
        properties.setProperty("# - custom: Custom extension", "")
        
        saveMainConfig(properties, "RunVSAgent 확장 설정 - 기본 템플릿")
    }
    
    /**
     * 모든 확장 설정 파일의 ID 목록을 가져옵니다.
     * @return 확장 ID 문자열 리스트
     */
    fun listExtensionConfigFiles(): List<String> {
        val extensionIds = mutableListOf<String>()
        try {
            val configDir = File(PluginConstants.ConfigFiles.getUserConfigDir())
            if (configDir.exists() && configDir.isDirectory) {
                val files = configDir.listFiles { file ->
                    PluginConstants.ConfigFiles.isExtensionConfigFile(file.name)
                }
                files?.forEach { file ->
                    val extensionId = PluginConstants.ConfigFiles.getExtensionIdFromFilename(file.name)
                    if (extensionId != null) {
                        extensionIds.add(extensionId)
                    }
                }
            }
        } catch (e: Exception) {
            // 오류 로깅 (예외를 던지지 않음)
        }
        return extensionIds
    }
}
