// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.sina.weibo.agent.plugin.DebugMode
import com.sina.weibo.agent.plugin.WecoderPluginService
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString

/**
 * 플러그인 리소스 유틸리티 클래스입니다.
 * 플러그인 내의 리소스 파일 경로를 얻는 데 사용됩니다.
 */
object PluginResourceUtil {
    private val LOG = Logger.getInstance(PluginResourceUtil::class.java)

    /**
     * 지정된 플러그인 ID와 리소스 이름에 해당하는 리소스 경로를 가져옵니다.
     *
     * @param pluginId 플러그인 ID
     * @param resourceName 리소스 이름 (예: "runtime/extension.js")
     * @return 리소스 경로 문자열, 가져오기 실패 시 null
     */
    fun getResourcePath(pluginId: String, resourceName: String): String? {
        return try {
            // 디버그 모드인 경우, 디버그 리소스 경로를 직접 사용합니다.
            if (WecoderPluginService.getDebugMode() == DebugMode.IDEA) {
                return WecoderPluginService.getDebugResource() + "/$resourceName"
            }

            val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
                ?: throw IllegalStateException("플러그인을 찾을 수 없습니다: $pluginId")

            // 개발 모드인지 프로덕션 모드인지 확인합니다.
            val isDevMode = checkDevMode(plugin)

            if (isDevMode) {
                // 개발 모드: 클래스패스 또는 프로젝트 리소스 디렉터리에서 로드합니다.
                loadDevResource(resourceName, plugin)
            } else {
                // 프로덕션 모드: 플러그인 JAR 또는 설치 디렉터리에서 로드합니다.
                loadProdResource(resourceName, plugin)
            }
        } catch (e: Exception) {
            LOG.error("플러그인 리소스 경로 가져오기 실패: $resourceName", e)
            null
        }
    }

    /**
     * 개발 모드에서 리소스를 로드합니다.
     * `debug-resources` 디렉터리에서 리소스를 찾습니다.
     */
    private fun loadDevResource(resourceName: String, plugin: IdeaPluginDescriptor): String {
        // 플러그인 경로를 기반으로 `debug-resources` 디렉터리 내의 리소스 경로를 구성합니다.
        val resourcePath = Paths.get(plugin.pluginPath.parent.parent.parent.parent.parent.pathString, "debug-resources/$resourceName")
        return resourcePath.toString()
    }

    /**
     * 프로덕션 모드에서 리소스를 로드합니다.
     * 플러그인 설치 디렉터리 내에서 리소스를 찾습니다.
     */
    private fun loadProdResource(resourceName: String, plugin: IdeaPluginDescriptor): String? {
        // 플러그인 설치 디렉터리 내에서 리소스를 찾습니다.
        val pluginDir = plugin.pluginPath.toFile()
        val resourceDir = pluginDir.resolve(resourceName)
        if (resourceDir.exists()) {
            return resourceDir.absolutePath
        }
        return null
    }

    /**
     * 현재 플러그인이 개발 모드인지 확인합니다.
     * `WecoderPluginService.getDebugMode()`를 통해 디버그 모드 설정을 확인합니다.
     */
    private fun checkDevMode(plugin: IdeaPluginDescriptor): Boolean {
        return try {
            WecoderPluginService.getDebugMode() != DebugMode.NONE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * URL에서 리소스를 읽어 임시 파일로 추출합니다.
     *
     * @param resourceUrl 리소스의 URL
     * @param filename 생성할 임시 파일의 이름
     * @return 임시 파일의 절대 경로, 또는 추출 실패 시 null
     */
    fun extractResourceToTempFile(resourceUrl: java.net.URL, filename: String): String? {
        return try {
            val tempFile = File.createTempFile("roo-cline-", "-$filename")
            tempFile.deleteOnExit() // JVM 종료 시 임시 파일 삭제

            resourceUrl.openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output) // 스트림 복사
                }
            }

            LOG.info("리소스를 임시 파일로 추출: ${tempFile.absolutePath}")
            tempFile.absolutePath
        } catch (e: Exception) {
            LOG.error("리소스를 임시 파일로 추출 실패: $filename", e)
            null
        }
    }
}
