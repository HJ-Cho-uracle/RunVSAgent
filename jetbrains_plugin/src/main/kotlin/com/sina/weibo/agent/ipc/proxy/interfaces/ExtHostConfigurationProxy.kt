// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

/**
 * Extension Host 설정 서비스 프록시 인터페이스입니다.
 * VSCode의 `ExtHostConfiguration`에 해당하며, Extension Host가 IntelliJ 플러그인의
 * 설정 값을 초기화, 업데이트, 조회하기 위해 사용됩니다.
 */
interface ExtHostConfigurationProxy {
    /**
     * 설정 모델을 초기화합니다.
     * Extension Host가 시작될 때 IntelliJ 플러그인으로부터 초기 설정 정보를 받습니다.
     * @param configModel 설정 정보를 담은 Map 형태의 설정 모델
     */
    fun initializeConfiguration(configModel: Map<String, Any?>)

    /**
     * 설정 값을 업데이트합니다.
     * @param configModel 업데이트할 설정 정보를 담은 Map 형태의 설정 모델
     */
    fun updateConfiguration(configModel: Map<String, Any?>)

    /**
     * 특정 설정 키에 해당하는 값을 가져옵니다.
     * @param key 조회할 설정 키 (예: "editor.fontSize")
     * @param section 설정 섹션 (선택 사항)
     * @param scopeToLanguage 언어별 설정으로 범위를 지정할지 여부
     * @return 조회된 설정 값, 또는 해당 키가 없으면 null
     */
    fun getConfiguration(key: String, section: String?, scopeToLanguage: Boolean): Any?
}
