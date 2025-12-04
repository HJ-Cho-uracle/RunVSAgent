// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.extensions.common.ExtensionChangeListener
import com.sina.weibo.agent.extensions.config.ExtensionProvider
import com.sina.weibo.agent.extensions.plugin.cline.ClineExtensionProvider
import com.sina.weibo.agent.extensions.plugin.costrict.CostrictExtensionProvider
import com.sina.weibo.agent.extensions.plugin.kilo.KiloCodeExtensionProvider
import com.sina.weibo.agent.extensions.plugin.roo.RooExtensionProvider
import com.sina.weibo.agent.extensions.ui.buttons.DynamicButtonManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 전역 확장 관리자 클래스입니다.
 * 플러그인에서 사용 가능한 모든 확장 제공자(ExtensionProvider)를 관리하고,
 * 현재 활성화된 확장을 추적하며, 확장의 생명주기를 제어합니다.
 */
@Service(Service.Level.PROJECT)
class ExtensionManager(private val project: Project) {
    private val LOG = Logger.getInstance(ExtensionManager::class.java)

    // 등록된 확장 제공자들을 저장하는 맵 (확장 ID -> ExtensionProvider)
    private val extensionProviders = ConcurrentHashMap<String, ExtensionProvider>()

    // 현재 활성화된 확장 제공자
    @Volatile
    private var currentProvider: ExtensionProvider? = null

    companion object {
        /**
         * `ExtensionManager`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): ExtensionManager {
            return project.getService(ExtensionManager::class.java)
                ?: error("ExtensionManager 서비스를 찾을 수 없습니다.")
        }
    }

    /**
     * 확장 관리자를 초기화합니다.
     * @param configuredExtensionId 설정 파일에서 읽어온 확장 ID. null이면 기본 제공자를 설정하지 않습니다.
     */
    fun initialize(configuredExtensionId: String? = null) {
        LOG.info("확장 관리자 초기화 중, 설정된 확장: $configuredExtensionId")

        // 모든 사용 가능한 확장 제공자를 등록합니다.
        registerExtensionProviders()

        if (configuredExtensionId != null) {
            // 특정 확장이 설정되어 있으면 해당 제공자를 직접 설정합니다.
            val provider = extensionProviders[configuredExtensionId]
            if (provider != null && provider.isAvailable(project)) {
                currentProvider = provider
                LOG.info("설정된 확장 제공자 설정 완료: $configuredExtensionId")
            } else {
                LOG.warn("설정된 확장 제공자를 사용할 수 없습니다: $configuredExtensionId")
                currentProvider = null // 기본 제공자를 설정하지 않고 초기화되지 않은 상태로 둡니다.
            }
        } else {
            LOG.info("설정된 확장이 없으므로 기본 제공자 설정 건너뜀")
            // 설정이 없는 경우 기본 제공자를 자동으로 설정하는 로직은 주석 처리되어 있습니다.
        }

        LOG.info("확장 관리자 초기화 완료")
    }

    /**
     * 확장 관리자를 기본 동작으로 초기화합니다. (하위 호환성을 위해)
     */
    fun initialize() {
        initialize(null)
    }

    /**
     * 현재 확장 관리자의 설정이 유효한지 확인합니다.
     */
    fun isConfigurationValid(): Boolean {
        return currentProvider != null && currentProvider!!.isAvailable(project)
    }

    /**
     * 설정 유효성 검사 실패 시 오류 메시지를 반환합니다.
     */
    fun getConfigurationError(): String? {
        return if (currentProvider == null) {
            "확장 제공자가 설정되지 않았습니다."
        } else if (!currentProvider!!.isAvailable(project)) {
            "확장 제공자 '${currentProvider!!.getExtensionId()}'를 사용할 수 없습니다."
        } else {
            null
        }
    }

    /**
     * 확장 관리자가 유효한 제공자로 제대로 초기화되었는지 확인합니다.
     */
    fun isProperlyInitialized(): Boolean {
        return currentProvider != null && currentProvider!!.isAvailable(project)
    }

    /**
     * 모든 사용 가능한 확장 제공자 목록을 반환합니다.
     * (현재는 하드코딩된 목록을 반환합니다.)
     */
    fun getAllExtensions(): List<ExtensionProvider> {
        return ArrayList<ExtensionProvider>().apply {
            add(RooExtensionProvider())
            add(ClineExtensionProvider())
            add(KiloCodeExtensionProvider())
            add(CostrictExtensionProvider())
        }
    }

    /**
     * 모든 확장 제공자를 등록합니다.
     */
    private fun registerExtensionProviders() {
        getAllExtensions().forEach { registerExtensionProvider(it) }
    }

    /**
     * 단일 확장 제공자를 등록합니다.
     */
    fun registerExtensionProvider(provider: ExtensionProvider) {
        extensionProviders[provider.getExtensionId()] = provider
        LOG.info("확장 제공자 등록됨: ${provider.getExtensionId()}")
    }

    /**
     * (현재 사용되지 않음) 기본 확장 제공자를 설정합니다.
     */
    private fun setDefaultExtensionProvider() {
        // ... (기본 제공자를 설정하는 로직, 현재는 주석 처리됨)
    }

    /**
     * 현재 활성화된 확장 제공자를 가져옵니다.
     */
    fun getCurrentProvider(): ExtensionProvider? {
        return currentProvider
    }

    /**
     * 현재 활성화된 확장 제공자를 설정합니다.
     * 이 메소드는 설정 및 UI 상태만 업데이트하며, Extension Host 프로세스를 재시작하지는 않습니다.
     * @param extensionId 설정할 확장의 ID
     * @param forceRestart 강제로 재시작할지 여부 (현재는 사용되지 않음)
     * @return 설정 성공 여부
     */
    fun setCurrentProvider(extensionId: String, forceRestart: Boolean? = false): Boolean {
        val provider = extensionProviders[extensionId]
        if (provider != null && provider.isAvailable(project)) {
            val oldProvider = currentProvider
            if (forceRestart == false) {
                currentProvider = provider
            }

            // 새 제공자를 초기화합니다. (프로세스 재시작 없이)
            provider.initialize(project)

            // 설정 관리자를 업데이트합니다.
            try {
                val configManager = ExtensionConfigurationManager.getInstance(project)
                configManager.setCurrentExtensionId(extensionId)
            } catch (e: Exception) {
                LOG.warn("설정 관리자 업데이트 실패", e)
            }

            // 동적 버튼 및 컨텍스트 메뉴 설정을 업데이트합니다.
            try {
                if (forceRestart == false) {
                    val buttonManager = DynamicButtonManager.getInstance(project)
                    buttonManager.setCurrentExtension(extensionId)
                }
            } catch (e: Exception) {
                LOG.warn("버튼 설정 업데이트 실패", e)
            }
            try {
                if (forceRestart == false) {
                    val contextMenuManager = com.sina.weibo.agent.extensions.ui.contextmenu.DynamicContextMenuManager.getInstance(project)
                    contextMenuManager.setCurrentExtension(extensionId)
                }
            } catch (e: Exception) {
                LOG.warn("컨텍스트 메뉴 설정 업데이트 실패", e)
            }

            // 확장 변경 리스너들에게 알립니다.
            try {
                project.messageBus.syncPublisher(ExtensionChangeListener.EXTENSION_CHANGE_TOPIC)
                    .onExtensionChanged(extensionId)
            } catch (e: Exception) {
                LOG.warn("확장 변경 리스너 알림 실패", e)
            }

            LOG.info("확장 제공자로 설정 업데이트 완료: $extensionId (이전: ${oldProvider?.getExtensionId()}) - 다음 시작 시 적용됩니다.")
            return true
        } else {
            LOG.warn("확장 제공자를 찾을 수 없거나 사용할 수 없습니다: $extensionId")
            return false
        }
    }

    /**
     * 확장을 재시작과 함께 전환합니다.
     * `ExtensionSwitcher`를 사용하여 실제 전환 로직을 수행합니다.
     */
    fun switchExtensionProvider(extensionId: String, forceRestart: Boolean = false): CompletableFuture<Boolean> {
        val extensionSwitcher = ExtensionSwitcher.getInstance(project)
        return extensionSwitcher.switchExtension(extensionId, forceRestart)
    }

    /**
     * 사용 가능한 모든 확장 제공자 목록을 가져옵니다.
     */
    fun getAvailableProviders(): List<ExtensionProvider> {
        return extensionProviders.values.filter { it.isAvailable(project) }
    }

    /**
     * 등록된 모든 확장 제공자 목록을 가져옵니다.
     */
    fun getAllProviders(): List<ExtensionProvider> {
        return extensionProviders.values.toList()
    }

    /**
     * ID를 사용하여 확장 제공자를 가져옵니다.
     */
    fun getProvider(extensionId: String): ExtensionProvider? {
        return extensionProviders[extensionId]
    }

    /**
     * 현재 활성화된 확장 제공자를 초기화합니다.
     */
    fun initializeCurrentProvider() {
        currentProvider?.initialize(project)
    }

    /**
     * 모든 확장 제공자의 리소스를 해제합니다.
     */
    fun dispose() {
        LOG.info("확장 관리자 해제 중")
        extensionProviders.values.forEach { it.dispose() }
        extensionProviders.clear()
        currentProvider = null
    }
}
