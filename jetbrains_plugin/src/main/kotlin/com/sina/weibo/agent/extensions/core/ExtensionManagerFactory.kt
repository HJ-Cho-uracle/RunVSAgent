package com.sina.weibo.agent.extensions.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.core.ExtensionManager
import com.sina.weibo.agent.extensions.common.ExtensionType
import com.sina.weibo.agent.extensions.config.ExtensionConfig
import com.sina.weibo.agent.extensions.config.ExtensionConfiguration
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.PluginResourceUtil
import java.io.File

/**
 * 확장 관리자 팩토리 클래스입니다.
 * Roo Code 플러그인을 위한 확장 관리자를 생성하고 관리합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class ExtensionManagerFactory(private val project: Project) {
    private val LOG = Logger.getInstance(ExtensionManagerFactory::class.java)

    // 확장 타입별 ExtensionManager 인스턴스를 캐시하는 맵
    private val extensionManagers = mutableMapOf<ExtensionType, ExtensionManager>()

    companion object {
        /**
         * `ExtensionManagerFactory`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): ExtensionManagerFactory {
            return project.getService(ExtensionManagerFactory::class.java)
                ?: error("ExtensionManagerFactory 서비스를 찾을 수 없습니다.")
        }
    }

    /**
     * 확장 관리자 팩토리를 초기화합니다.
     * 모든 지원되는 확장 타입에 대해 `ExtensionManager`를 생성합니다.
     */
    fun initialize() {
        LOG.info("확장 관리자 팩토리 초기화 중")

        // 확장 설정 관리자 인스턴스를 가져옵니다.
        val extensionConfig = ExtensionConfiguration.getInstance(project)

        // 모든 지원되는 확장 타입에 대해 확장 관리자를 생성합니다.
        ExtensionType.getAllTypes().forEach { extensionType ->
            createExtensionManager(extensionType, extensionConfig.getConfig(extensionType))
        }

        LOG.info("확장 관리자 팩토리 초기화 완료")
    }

    /**
     * 현재 활성화된 확장 타입에 대한 `ExtensionManager`를 가져옵니다.
     */
    fun getCurrentExtensionManager(): ExtensionManager {
        val extensionConfig = ExtensionConfiguration.getInstance(project)
        val currentType = extensionConfig.getCurrentExtensionType()
        return getExtensionManager(currentType)
    }

    /**
     * 특정 확장 타입에 대한 `ExtensionManager`를 가져옵니다.
     * @param extensionType 확장 관리자를 가져올 확장 타입
     * @return 해당 확장 타입의 `ExtensionManager` 인스턴스
     * @throws IllegalStateException 해당 타입의 확장 관리자를 찾을 수 없을 경우
     */
    fun getExtensionManager(extensionType: ExtensionType): ExtensionManager {
        return extensionManagers[extensionType]
            ?: throw IllegalStateException("타입 '${extensionType.code}'에 대한 확장 관리자를 찾을 수 없습니다.")
    }

    /**
     * 특정 확장 타입에 대한 `ExtensionManager`를 생성합니다.
     * @param extensionType 확장 관리자를 생성할 확장 타입
     * @param config 해당 확장의 설정 정보
     */
    private fun createExtensionManager(extensionType: ExtensionType, config: ExtensionConfig) {
        LOG.info("타입 '${extensionType.code}'에 대한 확장 관리자 생성 중")

        val extensionManager = ExtensionManager()

        // 확장이 존재하면 등록을 시도합니다.
        val extensionPath = getExtensionPath(config)
        if (extensionPath != null && File(extensionPath).exists()) {
            try {
                extensionManager.registerExtension(extensionPath, config)
                LOG.info("타입 '${extensionType.code}'에 대한 확장 등록됨")
                extensionManagers[extensionType] = extensionManager
            } catch (e: Exception) {
                LOG.warn("타입 '${extensionType.code}'에 대한 확장 등록 실패", e)
            }
        } else {
            LOG.info("타입 '${extensionType.code}' (${config.codeDir})에 대한 확장 경로를 찾을 수 없습니다: $extensionPath")
        }
    }

    /**
     * 설정 정보를 바탕으로 확장의 실제 파일 경로를 가져옵니다.
     * `PluginResourceUtil`을 사용하여 플러그인 리소스 내에서 경로를 찾습니다.
     * @param config 확장의 설정 정보
     * @return 확장의 절대 경로, 또는 찾지 못하면 null
     */
    private fun getExtensionPath(config: ExtensionConfig): String? {
        try {
            val extensionPath = PluginResourceUtil.getResourcePath(
                PluginConstants.PLUGIN_ID,
                config.codeDir
            )
            if (extensionPath != null && File(extensionPath).exists()) {
                LOG.info("PluginResourceUtil을 통해 확장 경로 찾음: $extensionPath")
                return extensionPath
            }
        } catch (e: Exception) {
            LOG.warn("PluginResourceUtil을 통해 확장 경로 가져오기 실패: ${config.codeDir}", e)
        }

        LOG.warn("타입 '${config.extensionType.code}' (${config.codeDir})에 대한 확장 경로를 찾을 수 없습니다.")
        return null
    }

    /**
     * 다른 확장 타입으로 전환합니다.
     * @param extensionType 전환할 확장 타입
     */
    fun switchExtensionType(extensionType: ExtensionType) {
        LOG.info("확장 타입 전환 중: ${extensionType.code}")

        val extensionConfig = ExtensionConfiguration.getInstance(project)
        extensionConfig.setCurrentExtensionType(extensionType)

        // 새로운 설정으로 다시 초기화합니다.
        val config = extensionConfig.getConfig(extensionType)
        createExtensionManager(extensionType, config)

        LOG.info("확장 타입 전환 완료: ${extensionType.code}")
    }

    /**
     * 사용 가능한 모든 확장 타입의 목록을 가져옵니다.
     */
    fun getAvailableExtensionTypes(): List<ExtensionType> {
        return extensionManagers.keys.toList()
    }

    /**
     * 모든 확장 관리자를 해제하고 리소스를 정리합니다.
     */
    fun dispose() {
        LOG.info("확장 관리자 팩토리 해제 중")
        extensionManagers.values.forEach { it.dispose() }
        extensionManagers.clear()
    }
}
