// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.sina.weibo.agent.core.ExtensionUnixDomainSocketServer
import com.sina.weibo.agent.core.ISocketServer
import com.sina.weibo.agent.extensions.common.ExtensionChangeListener
import com.sina.weibo.agent.extensions.ui.buttons.DynamicButtonManager
import com.sina.weibo.agent.plugin.WecoderPluginService
import com.sina.weibo.agent.util.ExtensionUtils
import com.sina.weibo.agent.webview.WebViewManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * 확장 전환 서비스입니다.
 * 활성화된 확장 제공자를 다른 확장 제공자로 전환하는 작업을 처리합니다.
 * 현재는 전환 시 설정만 저장하고, 실제 Extension Host 프로세스 재시작은 다음 IDE 시작 시 적용됩니다.
 */
@Service(Service.Level.PROJECT)
class ExtensionSwitcher(private val project: Project) {
    private val LOG = Logger.getInstance(ExtensionSwitcher::class.java)

    // 현재 전환 작업이 진행 중인지 여부
    @Volatile
    private var isSwitching = false

    // 전환 작업의 완료를 나타내는 Future
    private var switchingFuture: CompletableFuture<Boolean>? = null

    // 전환 작업을 위한 코루틴 스코프
    private val switchingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        /**
         * `ExtensionSwitcher`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): ExtensionSwitcher {
            return project.getService(ExtensionSwitcher::class.java)
                ?: error("ExtensionSwitcher 서비스를 찾을 수 없습니다.")
        }
    }

    /**
     * 확장을 전환하기 전에 필요한 서비스들이 사용 가능한지 확인합니다.
     */
    private fun checkServicesAvailability(): Boolean {
        return try {
            val pluginService = project.getService(WecoderPluginService::class.java)
            if (pluginService == null) {
                LOG.error("WecoderPluginService를 사용할 수 없습니다.")
                return false
            }

            // 프로세스 관리자 및 소켓 서버의 가용성을 확인합니다.
            pluginService.getProcessManager()
            pluginService.getSocketServer()

            true
        } catch (e: Exception) {
            LOG.error("서비스 가용성 확인 중 오류 발생", e)
            false
        }
    }

    /**
     * 다른 확장 제공자로 전환합니다.
     * @param extensionId 대상 확장의 ID
     * @param forceRestart Extension Host 프로세스를 강제로 재시작할지 여부 (현재는 새 모드에서 무시됨)
     * @return 전환 작업의 완료를 나타내는 `CompletableFuture`
     */
    fun switchExtension(extensionId: String, forceRestart: Boolean = false): CompletableFuture<Boolean> {
        if (isSwitching) {
            LOG.warn("확장 전환 작업이 이미 진행 중입니다.")
            return CompletableFuture.completedFuture(false)
        }

        val extensionManager = ExtensionManager.getInstance(project)
        val targetProvider = extensionManager.getProvider(extensionId)

        if (targetProvider == null) {
            LOG.error("확장 제공자를 찾을 수 없습니다: $extensionId")
            return CompletableFuture.completedFuture(false)
        }

        if (!targetProvider.isAvailable(project)) {
            LOG.error("확장 제공자를 사용할 수 없습니다: $extensionId")
            return CompletableFuture.completedFuture(false)
        }

        val currentProvider = extensionManager.getCurrentProvider()
        if (currentProvider?.getExtensionId() == extensionId) {
            LOG.info("이미 확장 제공자 '$extensionId'를 사용 중입니다.")
            return CompletableFuture.completedFuture(true)
        }

        LOG.info("확장 전환 시작: ${currentProvider?.getExtensionId()} -> $extensionId (다음 시작 시 적용)")

        if (!checkServicesAvailability()) {
            LOG.error("필수 서비스를 사용할 수 없어 확장 전환을 수행할 수 없습니다.")
            return CompletableFuture.completedFuture(false)
        }

        isSwitching = true
        switchingFuture = CompletableFuture()

        // 백그라운드에서 전환 작업을 수행합니다.
        switchingScope.launch {
            try {
                val success = performExtensionSwitch(extensionId, forceRestart)
                switchingFuture?.complete(success)
            } catch (e: Exception) {
                LOG.error("확장 전환 중 오류 발생", e)
                switchingFuture?.completeExceptionally(e)
            } finally {
                isSwitching = false
            }
        }

        return switchingFuture!!
    }

    /**
     * 실제 확장 전환 작업을 수행합니다.
     * @param extensionId 대상 확장의 ID
     * @param forceRestart 강제 재시작 여부
     * @return 전환 성공 여부
     */
    private suspend fun performExtensionSwitch(extensionId: String, forceRestart: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1단계: 확장 관리자를 업데이트합니다. (이 과정에서 설정이 저장됩니다.)
                updateExtensionManager(extensionId, forceRestart)

                if (forceRestart) {
                    // 2단계: 버튼 설정을 업데이트합니다.
                    updateButtonConfiguration(extensionId)

                    // 3단계: UI 컴포넌트들에게 변경을 알립니다.
                    notifyExtensionChanged(extensionId)
                }

                LOG.info("확장 전환 설정이 성공적으로 저장되었습니다: $extensionId (다음 시작 시 적용)")
                true
            } catch (e: Exception) {
                LOG.error("확장 전환 실패: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 새로운 제공자로 확장 관리자를 업데이트합니다.
     * 이 과정에서 설정이 저장되지만, 프로세스는 재시작되지 않습니다.
     */
    private suspend fun updateExtensionManager(extensionId: String, forceRestart: Boolean) {
        withContext(Dispatchers.Main) {
            val extensionManager = ExtensionManager.getInstance(project)

            // 새 확장 제공자를 설정합니다. (이 과정에서 설정이 저장됩니다.)
            val success = extensionManager.setCurrentProvider(extensionId, forceRestart)
            if (!success) {
                throw IllegalStateException("확장 제공자 설정 실패: $extensionId")
            }

            LOG.info("확장 관리자가 새 제공자로 업데이트됨: $extensionId (설정 저장됨)")
        }
    }

    /**
     * 새로운 확장에 맞춰 버튼 설정을 업데이트합니다.
     */
    private suspend fun updateButtonConfiguration(extensionId: String) {
        withContext(Dispatchers.Main) {
            try {
                val buttonManager = DynamicButtonManager.getInstance(project)
                buttonManager.setCurrentExtension(extensionId)
                LOG.info("버튼 설정 업데이트됨: $extensionId")
            } catch (e: Exception) {
                LOG.warn("버튼 설정 업데이트 실패", e)
            }
        }
    }

    /**
     * 확장 변경에 대해 UI 컴포넌트들에게 알립니다.
     */
    private suspend fun notifyExtensionChanged(extensionId: String) {
        withContext(Dispatchers.Main) {
            // WebView 관리자에게 알립니다. (현재는 로깅만)
            try {
                val webViewManager = project.getService(WebViewManager::class.java)
                if (webViewManager != null) {
                    LOG.debug("WebViewManager는 사용 가능하지만 확장 변경 알림은 아직 구현되지 않았습니다.")
                }
            } catch (e: Exception) {
                LOG.debug("WebViewManager를 사용할 수 없거나 확장 변경을 지원하지 않습니다: ${e.message}")
            }

            // 다른 컴포넌트들에게 메시지 버스를 통해 알립니다.
            project.messageBus.syncPublisher(ExtensionChangeListener.EXTENSION_CHANGE_TOPIC)
                .onExtensionChanged(extensionId)
        }
    }

    /**
     * 전환 작업이 진행 중인지 확인합니다.
     */
    fun isSwitching(): Boolean = isSwitching

    /**
     * 현재 진행 중인 전환 작업이 완료될 때까지 기다립니다.
     */
    fun waitForSwitching(): CompletableFuture<Boolean>? = switchingFuture

    /**
     * 현재 진행 중인 전환 작업을 취소합니다.
     */
    fun cancelSwitching() {
        if (isSwitching) {
            switchingScope.cancel("확장 전환이 취소되었습니다.")
            isSwitching = false
            switchingFuture?.cancel(true)
            LOG.info("확장 전환이 취소되었습니다.")
        }
    }

    /**
     * 리소스를 해제합니다.
     */
    fun dispose() {
        switchingScope.cancel()
        switchingFuture?.cancel(true)
    }
}
