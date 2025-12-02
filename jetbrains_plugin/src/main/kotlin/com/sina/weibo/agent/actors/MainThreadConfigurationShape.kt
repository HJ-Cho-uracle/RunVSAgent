// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.sina.weibo.agent.util.URI
import com.sina.weibo.agent.util.URIComponents

/**
 * 설정이 적용될 범위를 정의하는 열거형 클래스입니다.
 * VSCode의 `ConfigurationTarget`에 해당하며, 설정이 어디에 저장되고 적용될지를 결정합니다.
 */
enum class ConfigurationTarget(val value: Int) {
    /** IDE 전역에 적용되는 어플리케이션 레벨 설정 */
    APPLICATION(1),

    /** 현재 사용자의 모든 프로젝트에 적용되는 사용자 레벨 설정 */
    USER(2),

    /** 현재 기기에만 한정되는 로컬 사용자 설정 */
    USER_LOCAL(3),

    /** 원격 개발 환경에 적용되는 원격 사용자 설정 */
    USER_REMOTE(4),

    /** 현재 작업 공간(프로젝트)에만 적용되는 워크스페이스 레벨 설정 */
    WORKSPACE(5),

    /** 워크스페이스 내의 특정 폴더에 적용되는 설정 */
    WORKSPACE_FOLDER(6),

    /** 특정 범위가 지정되지 않았을 때의 기본 설정 */
    DEFAULT(7),

    /** 메모리에만 임시로 저장되고 영속화되지 않는 설정 */
    MEMORY(8);

    companion object {
        /**
         * 정수 값으로부터 `ConfigurationTarget` 열거형 상수를 찾습니다.
         * @param value 설정 범위를 나타내는 정수 값
         * @return 해당하는 `ConfigurationTarget` 또는 찾지 못하면 null
         */
        fun fromValue(value: Int?): ConfigurationTarget? {
            return entries.find { it.value == value }
        }

        /**
         * `ConfigurationTarget`을 문자열로 변환합니다. (디버깅 및 로깅용)
         */
        fun toString(target: ConfigurationTarget): String {
            return when (target) {
                APPLICATION -> "APPLICATION"
                USER -> "USER"
                USER_LOCAL -> "USER_LOCAL"
                USER_REMOTE -> "USER_REMOTE"
                WORKSPACE -> "WORKSPACE"
                WORKSPACE_FOLDER -> "WORKSPACE_FOLDER"
                DEFAULT -> "DEFAULT"
                MEMORY -> "MEMORY"
            }
        }
    }
}

/**
 * 특정 컨텍스트(예: 특정 언어, 특정 파일)에 따라 설정을 오버라이드(재정의)하기 위한 정보를 담는 데이터 클래스입니다.
 * VSCode의 `IConfigurationOverrides`에 해당합니다.
 */
data class ConfigurationOverrides(
    /** 오버라이드 식별자. 주로 언어별 설정을 위해 사용됩니다. (예: "[typescript]") */
    val overrideIdentifier: String? = null,
    /** 설정이 적용될 리소스의 URI. 파일별 설정을 위해 사용됩니다. */
    val resource: URI? = null
)

/**
 * IntelliJ 메인 스레드에서 설정 관련 작업을 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadConfigurationShape`에 해당하며,
 * RPC를 통해 설정을 업데이트하거나 제거하는 기능을 정의합니다.
 */
interface MainThreadConfigurationShape : Disposable {
    /**
     * 특정 범위의 설정 값을 업데이트합니다.
     * @param target 설정이 적용될 범위 (ConfigurationTarget의 정수 값)
     * @param key 업데이트할 설정의 키 (예: "editor.fontSize")
     * @param value 설정할 값. null을 전달하면 해당 설정을 제거(unset)하는 효과를 가질 수 있습니다.
     * @param overrides 컨텍스트별 오버라이드 정보
     * @param scopeToLanguage 이 설정이 특정 언어에만 한정되는지 여부
     */
    fun updateConfigurationOption(
        target: Int,
        key: String,
        value: Any?,
        overrides: Map<String, Any>?,
        scopeToLanguage: Boolean?
    )

    /**
     * 특정 범위의 설정 값을 제거합니다.
     * @param target 설정을 제거할 범위
     * @param key 제거할 설정의 키
     * @param overrides 컨텍스트별 오버라이드 정보
     * @param scopeToLanguage 제거할 설정이 특정 언어에 한정되었었는지 여부
     */
    fun removeConfigurationOption(
        target: Int,
        key: String,
        overrides: Map<String, Any>?,
        scopeToLanguage: Boolean?
    )
}

/**
 * `MainThreadConfigurationShape` 인터페이스의 구현 클래스입니다.
 * IntelliJ의 `PropertiesComponent`를 사용하여 실제 설정 값을 저장하고 관리합니다.
 */
class MainThreadConfiguration : MainThreadConfigurationShape {
    private val logger = Logger.getInstance(MainThreadConfiguration::class.java)

    /**
     * 설정 값을 업데이트하는 로직을 구현합니다.
     * 전달받은 파라미터를 분석하여 적절한 저장소와 키를 결정하고 값을 저장합니다.
     */
    override fun updateConfigurationOption(
        target: Int,
        key: String,
        value: Any?,
        overrides: Map<String, Any>?,
        scopeToLanguage: Boolean?
    ) {
        val configTarget = ConfigurationTarget.fromValue(target)
        val configOverrides = convertToConfigurationOverrides(overrides)

        logger.info(
            "설정 업데이트 요청: target=${configTarget?.let { ConfigurationTarget.toString(it) }}, key=$key, value=$value, " +
                    "overrideIdentifier=${configOverrides?.overrideIdentifier}, resource=${configOverrides?.resource}, " +
                    "scopeToLanguage=$scopeToLanguage"
        )

        // 기본 키와 오버라이드 정보를 조합하여 최종 저장 키를 생성합니다.
        val fullKey = buildConfigurationKey(key, configOverrides, scopeToLanguage)

        // 설정 범위(Target)에 따라 다른 저장소(`PropertiesComponent`)에 값을 저장합니다.
        when (configTarget) {
            ConfigurationTarget.APPLICATION -> {
                // 어플리케이션 레벨: IDE 전역에 적용
                val properties = PropertiesComponent.getInstance()
                storeValue(properties, fullKey, value)
            }

            ConfigurationTarget.WORKSPACE, ConfigurationTarget.WORKSPACE_FOLDER -> {
                // 워크스페이스 레벨: 현재 프로젝트에만 적용
                val activeProject = getActiveProject()
                if (activeProject != null) {
                    val properties = PropertiesComponent.getInstance(activeProject)
                    storeValue(properties, fullKey, value)
                } else {
                    logger.warn("프로젝트 레벨 설정을 저장하지 못했습니다. 활성화된 프로젝트가 없습니다.")
                }
            }

            ConfigurationTarget.USER, ConfigurationTarget.USER_LOCAL -> {
                // 사용자 레벨: 모든 프로젝트에 걸쳐 현재 사용자에게 적용
                val properties = PropertiesComponent.getInstance()
                val userPrefixedKey = "user.$fullKey" // 다른 범위와 충돌을 피하기 위해 접두사 추가
                storeValue(properties, userPrefixedKey, value)
            }

            else -> {
                // 메모리 레벨: 영속화하지 않고 임시로만 사용
                val properties = PropertiesComponent.getInstance()
                val memoryPrefixedKey = "memory.$fullKey"
                storeValue(properties, memoryPrefixedKey, value)
            }
        }
    }

    /**
     * 설정 값을 제거하는 로직을 구현합니다.
     */
    override fun removeConfigurationOption(
        target: Int,
        key: String,
        overrides: Map<String, Any>?,
        scopeToLanguage: Boolean?
    ) {
        val configTarget = ConfigurationTarget.fromValue(target)
        val configOverrides = convertToConfigurationOverrides(overrides)

        logger.info(
            "설정 제거 요청: target=${configTarget?.let { ConfigurationTarget.toString(it) }}, key=$key, " +
                    "overrideIdentifier=${configOverrides?.overrideIdentifier}, resource=${configOverrides?.resource}, " +
                    "scopeToLanguage=$scopeToLanguage"
        )

        val fullKey = buildConfigurationKey(key, configOverrides, scopeToLanguage)

        when (configTarget) {
            ConfigurationTarget.APPLICATION -> {
                PropertiesComponent.getInstance().unsetValue(fullKey)
            }

            ConfigurationTarget.WORKSPACE, ConfigurationTarget.WORKSPACE_FOLDER -> {
                getActiveProject()?.let {
                    PropertiesComponent.getInstance(it).unsetValue(fullKey)
                } ?: logger.warn("프로젝트 레벨 설정을 제거하지 못했습니다. 활성화된 프로젝트가 없습니다.")
            }

            ConfigurationTarget.USER, ConfigurationTarget.USER_LOCAL -> {
                PropertiesComponent.getInstance().unsetValue("user.$fullKey")
            }

            else -> {
                PropertiesComponent.getInstance().unsetValue("memory.$fullKey")
            }
        }
    }

    /**
     * RPC를 통해 전달받은 `Map` 형태의 오버라이드 정보를 `ConfigurationOverrides` 데이터 클래스로 변환합니다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertToConfigurationOverrides(overridesMap: Map<String, Any>?): ConfigurationOverrides? {
        if (overridesMap.isNullOrEmpty()) {
            return null
        }

        try {
            val overrideIdentifier = overridesMap["overrideIdentifier"] as? String
            val resourceUri = when (val uriObj = overridesMap["resource"]) {
                is Map<*, *> -> { // URI가 Map 형태로 전달된 경우
                    val scheme = uriObj["scheme"] as? String ?: ""
                    val path = uriObj["path"] as? String ?: ""
                    val authority = uriObj["authority"] as? String ?: ""
                    val query = uriObj["query"] as? String ?: ""
                    val fragment = uriObj["fragment"] as? String ?: ""

                    if (path.isNotEmpty()) {
                        // Create URI instance using URI.from static method
                        val uriComponents = object : URIComponents {
                            override val scheme: String = scheme
                            override val authority: String = authority
                            override val path: String = path
                            override val query: String = query
                            override val fragment: String = fragment
                        }
                        URI.from(uriComponents)
                    } else {
                        null
                    }
                }

                is String -> URI.parse(uriObj) // URI가 문자열로 전달된 경우
                else -> null
            }
            return ConfigurationOverrides(overrideIdentifier, resourceUri)
        } catch (e: Exception) {
            logger.error("설정 오버라이드 변환 실패: $overridesMap", e)
            return null
        }
    }

    /**
     * 기본 키, 오버라이드, 언어 범위 등을 조합하여 저장소에 사용될 최종 키를 생성합니다.
     * 이를 통해 동일한 설정 키라도 다른 컨텍스트(예: 다른 파일, 다른 언어)에 대해 다른 값을 가질 수 있습니다.
     */
    private fun buildConfigurationKey(
        baseKey: String,
        overrides: ConfigurationOverrides?,
        scopeToLanguage: Boolean?
    ): String {
        val keyBuilder = StringBuilder(baseKey)

        overrides?.let {
            // 언어별 설정인 경우, overrideIdentifier(예: [typescript])를 키에 추가합니다.
            it.overrideIdentifier?.let { identifier ->
                if (scopeToLanguage == true) {
                    keyBuilder.append(".").append(identifier)
                }
            }
            // 리소스별 설정인 경우, URI의 해시코드를 키에 추가하여 고유성을 보장합니다.
            it.resource?.let { uri ->
                keyBuilder.append("@").append(uri.toString().hashCode())
            }
        }

        return keyBuilder.toString()
    }

    /**
     * 현재 IDE에서 활성화된(가장 최근에 사용된) 프로젝트를 가져옵니다.
     */
    private fun getActiveProject(): Project? {
        return ProjectManager.getInstance().openProjects.firstOrNull { it.isInitialized && !it.isDisposed }
    }

    /**
     * `PropertiesComponent`에 값의 타입에 맞춰 값을 저장합니다.
     * `PropertiesComponent`는 String, Boolean, Int 등 기본 타입에 대한 저장 메소드를 제공합니다.
     */
    private fun storeValue(properties: PropertiesComponent, key: String, value: Any?) {
        when (value) {
            null -> properties.unsetValue(key)
            is String -> properties.setValue(key, value)
            is Boolean -> properties.setValue(key, value)
            is Int -> properties.setValue(key, value, 0)
            is Float -> properties.setValue(key, value.toString())
            is Double -> properties.setValue(key, value.toString())
            is Long -> properties.setValue(key, value.toString())
            else -> {
                // 지원하지 않는 타입은 문자열로 변환하여 저장합니다. (예: JSON 객체)
                try {
                    properties.setValue(key, value.toString())
                } catch (e: Exception) {
                    logger.error("설정 값 직렬화 실패, 타입: ${value.javaClass.name}", e)
                }
            }
        }
    }

    /**
     * 리소스를 해제합니다. (Disposable 인터페이스 구현)
     */
    override fun dispose() {
        logger.info("리소스 해제: MainThreadConfiguration")
    }
}
