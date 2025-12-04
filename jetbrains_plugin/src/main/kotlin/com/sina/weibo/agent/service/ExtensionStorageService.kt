// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.service

import com.google.gson.Gson
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 확장(Extension) 데이터를 영속적으로 저장하고 관리하는 서비스입니다.
 * IntelliJ의 `PersistentStateComponent`를 구현하여 플러그인 설정 파일에 데이터를 저장합니다.
 * `@Service` 어노테이션을 통해 IntelliJ에 애플리케이션 서비스로 등록됩니다.
 * `@State` 어노테이션은 이 서비스의 상태가 저장될 파일의 이름과 위치를 정의합니다.
 */
@Service
@State(
    name = "com.sina.weibo.agent.service.ExtensionStorageService", // 상태 컴포넌트의 고유 이름
    storages = [Storage("roo-cline-extension-storage.xml")], // 상태가 저장될 XML 파일 이름
)
class ExtensionStorageService : PersistentStateComponent<ExtensionStorageService> {
    private val gson = Gson() // 객체를 JSON 문자열로 변환하기 위한 Gson 인스턴스
    var storageMap: MutableMap<String, String> = mutableMapOf() // 실제 데이터를 저장하는 맵

    /**
     * 현재 서비스 인스턴스를 상태로 반환합니다.
     * IntelliJ는 이 객체의 필드들을 직렬화하여 저장합니다.
     */
    override fun getState(): ExtensionStorageService = this

    /**
     * 저장된 상태를 현재 서비스 인스턴스에 로드합니다.
     * IntelliJ가 설정 파일을 읽어와 이 메소드를 호출합니다.
     * @param state 로드할 상태를 담은 `ExtensionStorageService` 객체
     */
    override fun loadState(state: ExtensionStorageService) {
        XmlSerializerUtil.copyBean(state, this) // 로드된 상태를 현재 인스턴스에 복사
    }

    /**
     * 지정된 키에 값을 저장합니다.
     * 값이 `String`이 아니면 JSON 문자열로 변환하여 저장합니다.
     * @param key 저장할 데이터의 키
     * @param value 저장할 데이터 객체
     */
    fun setValue(key: String, value: Any) {
        storageMap[key] = when (value) {
            is String -> value
            else -> gson.toJson(value) // String이 아니면 JSON으로 직렬화하여 저장
        }
    }

    /**
     * 지정된 키에 해당하는 값을 가져옵니다.
     * @param key 가져올 데이터의 키
     * @return 저장된 값 (문자열), 또는 해당 키가 없으면 null
     */
    fun getValue(key: String): String? {
        return storageMap[key]
    }

    /**
     * 지정된 키에 해당하는 값을 저장소에서 제거합니다.
     * @param key 제거할 데이터의 키
     */
    fun removeValue(key: String) {
        storageMap.remove(key)
    }

    /**
     * 저장소의 모든 데이터를 지웁니다.
     */
    fun clear() {
        storageMap.clear()
    }
}
