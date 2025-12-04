// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.sina.weibo.agent.service.ExtensionStorageService

/**
 * IntelliJ 메인 스레드에서 데이터 저장소 관련 작업을 처리하기 위한 인터페이스입니다.
 * Extension Host가 생성한 데이터를 IntelliJ의 영구 저장소에 저장하고 관리하는 기능을 정의합니다.
 */
interface MainThreadStorageShape : Disposable {
    /**
     * 특정 확장을 위한 저장소를 초기화하고, 저장된 데이터를 반환합니다.
     * @param shared 여러 작업 공간에서 공유되는 전역 저장소인지 여부
     * @param extensionId 저장소를 사용할 확장의 ID
     * @return 저장소에 저장된 데이터. 없으면 null일 수 있습니다.
     */
    fun initializeExtensionStorage(shared: Boolean, extensionId: String): Any?

    /**
     * 지정된 확장의 저장소에 값을 저장합니다.
     * @param shared 전역 저장소에 저장할지 여부
     * @param extensionId 값을 저장할 확장의 ID
     * @param value 저장할 데이터 객체
     */
    fun setValue(shared: Boolean, extensionId: String, value: Any)

    /**
     * 동기화할 저장소 키 목록을 등록합니다.
     * (예: 설정 동기화 기능을 위해 어떤 키들을 동기화할지 지정)
     * @param extension 확장 정보 (ID와 버전 포함)
     * @param keys 동기화할 키 목록
     */
    fun registerExtensionStorageKeysToSync(extension: Any, keys: List<String>)
}

/**
 * `MainThreadStorageShape` 인터페이스의 구현 클래스입니다.
 * IntelliJ의 서비스인 `ExtensionStorageService`를 사용하여 실제 데이터 저장 및 조회를 처리합니다.
 */
class MainThreadStorage : MainThreadStorageShape {
    private val logger = Logger.getInstance(MainThreadStorage::class.java)

    /**
     * 확장 저장소를 초기화하고 저장된 값을 반환합니다.
     * `ExtensionStorageService`에서 해당 확장의 데이터를 조회합니다.
     */
    override fun initializeExtensionStorage(shared: Boolean, extensionId: String): Any? {
        logger.info("확장 저장소 초기화: shared=$shared, extensionId=$extensionId")
        val storage = service<ExtensionStorageService>()
        return storage.getValue(extensionId)
    }

    /**
     * `ExtensionStorageService`에 값을 저장하도록 위임합니다.
     */
    override fun setValue(shared: Boolean, extensionId: String, value: Any) {
        // 로깅은 데이터가 클 수 있으므로 주석 처리되었습니다.
//        logger.info("값 설정: shared=$shared, extensionId=$extensionId, value=$value")
        val storage = service<ExtensionStorageService>()
        storage.setValue(extensionId, value)
    }

    /**
     * 동기화할 키를 등록합니다. (현재는 로깅만 수행)
     */
    override fun registerExtensionStorageKeysToSync(extension: Any, keys: List<String>) {
        val extensionId = if (extension is Map<*, *>) {
            "${extension["id"]}_${extension["version"]}"
        } else {
            "$extension"
        }
        logger.info("동기화를 위한 확장 저장소 키 등록: extension=$extensionId, keys=$keys")
    }

    override fun dispose() {
        logger.info("Dispose MainThreadStorage")
    }
}
