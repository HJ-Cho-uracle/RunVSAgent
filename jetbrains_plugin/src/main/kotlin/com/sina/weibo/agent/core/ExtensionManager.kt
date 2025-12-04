// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol
import com.sina.weibo.agent.util.URI
import com.sina.weibo.agent.util.toCompletableFuture
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import com.sina.weibo.agent.extensions.config.ExtensionConfig as RooExtensionConfig
import com.sina.weibo.agent.extensions.config.ExtensionMetadata as ExtensionConfigurationInterface

/**
 * 확장(Extension) 관리자 클래스입니다.
 * 확장의 등록 및 활성화를 책임집니다.
 */
class ExtensionManager : Disposable {
    companion object {
        val LOG = Logger.getInstance(ExtensionManager::class.java)
    }

    // 등록된 확장들을 저장하는 맵 (확장 ID -> 확장 설명 객체)
    private val extensions = ConcurrentHashMap<String, ExtensionDescription>()

    private val gson = Gson()

    /**
     * 확장 경로와 설정 정보를 바탕으로 `package.json`을 읽어 `ExtensionDescription` 객체를 생성합니다.
     * @param extensionPath 확장이 위치한 경로
     * @param extensionConfig 확장에 대한 메타데이터 설정
     * @return 파싱된 `ExtensionDescription` 객체
     */
    private fun parseExtensionDescription(extensionPath: String, extensionConfig: RooExtensionConfig): ExtensionDescription {
        LOG.info("확장 파싱 중: $extensionPath")

        val packageJsonPath = Paths.get(extensionPath, "package.json").toString()
        val packageJsonContent = File(packageJsonPath).readText()
        val packageJson = gson.fromJson(packageJsonContent, PackageJson::class.java)

        // 'publisher.name' 형식의 고유 ID를 생성합니다.
        val name = "${extensionConfig.publisher}.${packageJson.name}"
        val extensionIdentifier = ExtensionIdentifier(name)

        // `package.json`과 설정 파일의 정보를 조합하여 최종 설명 객체를 만듭니다.
        return ExtensionDescription(
            id = name,
            identifier = extensionIdentifier,
            name = name,
            displayName = "${extensionConfig.displayName}: ${packageJson.displayName}",
            description = "${extensionConfig.description}: ${packageJson.description}",
            version = packageJson.version ?: extensionConfig.version,
            publisher = extensionConfig.publisher,
            main = packageJson.main ?: extensionConfig.mainFile,
            activationEvents = packageJson.activationEvents ?: extensionConfig.activationEvents,
            extensionLocation = URI.file(extensionPath),
            targetPlatform = "universal",
            isBuiltin = false,
            isUserBuiltin = false,
            isUnderDevelopment = false,
            engines = packageJson.engines?.let { mapOf("vscode" to (it.vscode ?: "^1.0.0")) } ?: extensionConfig.engines,
            preRelease = false,
            capabilities = extensionConfig.capabilities,
            extensionDependencies = packageJson.extensionDependencies ?: extensionConfig.extensionDependencies,
        )
    }

    /**
     * 새로운 설정 인터페이스(`ExtensionConfigurationInterface`)를 사용하여 `ExtensionDescription`을 생성합니다.
     */
    private fun parseExtensionDescriptionFromNewConfig(extensionPath: String, extensionConfig: ExtensionConfigurationInterface): ExtensionDescription {
        LOG.info("확장 파싱 중: $extensionPath")

        val packageJsonPath = Paths.get(extensionPath, "package.json").toString()
        val packageJsonContent = File(packageJsonPath).readText()
        val packageJson = gson.fromJson(packageJsonContent, PackageJson::class.java)

        val name = "${extensionConfig.getPublisher()}.${packageJson.name}"
        val extensionIdentifier = ExtensionIdentifier(name)

        return ExtensionDescription(
            id = name,
            identifier = extensionIdentifier,
            name = name,
            displayName = "${extensionConfig.getCodeDir()}: ${packageJson.displayName}",
            description = "${extensionConfig.getCodeDir()}: ${packageJson.description}",
            version = packageJson.version ?: extensionConfig.getVersion(),
            publisher = extensionConfig.getPublisher(),
            main = packageJson.main ?: extensionConfig.getMainFile(),
            activationEvents = packageJson.activationEvents ?: extensionConfig.getActivationEvents(),
            extensionLocation = URI.file(extensionPath),
            targetPlatform = "universal",
            isBuiltin = false,
            isUserBuiltin = false,
            isUnderDevelopment = false,
            engines = packageJson.engines?.let { mapOf("vscode" to (it.vscode ?: "^1.0.0")) } ?: extensionConfig.getEngines(),
            preRelease = false,
            capabilities = extensionConfig.getCapabilities(),
            extensionDependencies = packageJson.extensionDependencies ?: extensionConfig.getExtensionDependencies(),
        )
    }

    /**
     * 파싱된 모든 확장의 설명 객체 목록을 가져옵니다.
     */
    fun getAllExtensionDescriptions(): List<ExtensionDescription> {
        return extensions.values.toList()
    }

    /**
     * 지정된 ID의 확장 설명 정보를 가져옵니다.
     */
    fun getExtensionDescription(extensionId: String): ExtensionDescription? {
        return extensions[extensionId]
    }

    /**
     * 확장을 등록합니다.
     */
    fun registerExtension(extensionPath: String, extensionConfig: RooExtensionConfig): ExtensionDescription {
        val extensionDescription = parseExtensionDescription(extensionPath, extensionConfig)
        extensions[extensionDescription.name] = extensionDescription
        LOG.info("확장 등록됨: ${extensionDescription.name}")
        return extensionDescription
    }

    /**
     * 새로운 설정 인터페이스를 사용하여 확장을 등록합니다.
     */
    fun registerExtension(extensionPath: String, extensionConfig: ExtensionConfigurationInterface): ExtensionDescription {
        val extensionDescription = parseExtensionDescriptionFromNewConfig(extensionPath, extensionConfig)
        extensions[extensionDescription.name] = extensionDescription
        LOG.info("확장 등록됨: ${extensionDescription.name}")
        return extensionDescription
    }

    /**
     * 확장을 활성화합니다.
     * RPC를 통해 Extension Host의 `ExtHostExtensionService`에 활성화를 요청합니다.
     * @param extensionId 활성화할 확장의 ID
     * @param rpcProtocol RPC 통신 프로토콜
     * @return 활성화 결과를 담은 `CompletableFuture`
     */
    fun activateExtension(extensionId: String, rpcProtocol: IRPCProtocol): CompletableFuture<Boolean> {
        LOG.info("확장 활성화 중: $extensionId")

        try {
            val extension = extensions[extensionId]
            if (extension == null) {
                LOG.error("확장을 찾을 수 없음: $extensionId")
                return CompletableFuture.failedFuture(IllegalArgumentException("확장을 찾을 수 없음: $extensionId"))
            }

            // 활성화에 필요한 파라미터를 구성합니다.
            val activationParams = mapOf(
                "startup" to true,
                "extensionId" to extension.identifier,
                "activationEvent" to "api",
            )

            // RPC를 통해 원격 서비스의 프록시 객체를 가져옵니다.
            val extHostService = rpcProtocol.getProxy(ServiceProxyRegistry.ExtHostContext.ExtHostExtensionService)

            try {
                // 원격 서비스의 `activate` 메소드를 호출하고, 그 결과를 `CompletableFuture`로 변환합니다.
                val lazyPromise = extHostService.activate(extension.identifier.value, activationParams)

                return lazyPromise.toCompletableFuture<Any?>().thenApply { result ->
                    val boolResult = result as? Boolean ?: false
                    LOG.info("확장 활성화 ${if (boolResult) "성공" else "실패"}: $extensionId")
                    boolResult
                }.exceptionally { throwable ->
                    LOG.error("확장 활성화 실패: $extensionId", throwable)
                    false
                }
            } catch (e: Exception) {
                LOG.error("activate 메소드 호출 실패: $extensionId", e)
                return CompletableFuture.failedFuture(e)
            }
        } catch (e: Exception) {
            LOG.error("확장 활성화 실패: $extensionId", e)
            return CompletableFuture.failedFuture(e)
        }
    }

    /**
     * 리소스를 해제합니다.
     */
    override fun dispose() {
        LOG.info("ExtensionManager 리소스 해제")
        extensions.clear()
    }
}

/**
 * 확장의 `package.json` 파일 내용을 파싱하기 위한 데이터 클래스입니다.
 */
data class PackageJson(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val version: String? = null,
    val engines: Engines? = null,
    val activationEvents: List<String>? = null,
    val main: String? = null,
    val extensionDependencies: List<String>? = null,
)

/**
 * `package.json`의 `engines` 필드를 파싱하기 위한 데이터 클래스입니다.
 */
data class Engines(
    val vscode: String? = null,
    val node: String? = null,
)

/**
 * 확장에 대한 모든 메타데이터를 담고 있는 데이터 클래스입니다.
 * VSCode의 `IExtensionDescription`에 해당합니다.
 */
data class ExtensionDescription(
    val id: String? = null,
    val identifier: ExtensionIdentifier,
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val version: String,
    val publisher: String,
    val main: String? = null,
    val activationEvents: List<String>? = null,
    val extensionLocation: URI,
    val targetPlatform: String = "universal",
    val isBuiltin: Boolean = false,
    val isUserBuiltin: Boolean = false,
    val isUnderDevelopment: Boolean = false,
    val engines: Map<String, String>,
    val preRelease: Boolean = false,
    val capabilities: Map<String, Any> = emptyMap(),
    val extensionDependencies: List<String> = emptyList(),
)

/**
 * `ExtensionDescription` 객체를 RPC 통신에 사용하기 쉬운 `Map` 형태로 변환하는 확장 함수입니다.
 */
fun ExtensionDescription.toMap(): Map<String, Any?> {
    return mapOf(
        "identifier" to this.identifier.value,
        "name" to this.name,
        // ... (나머지 속성들을 Map에 추가)
    )
}
