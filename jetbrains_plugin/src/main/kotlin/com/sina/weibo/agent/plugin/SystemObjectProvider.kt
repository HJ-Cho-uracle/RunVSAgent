// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * 시스템 객체 제공자(System Object Provider)입니다.
 * IntelliJ 플러그인 내에서 전역적으로 접근 가능한 IntelliJ 시스템 객체들을
 * (예: `ApplicationManager`) 등록하고 제공하는 역할을 합니다.
 * 싱글톤 객체로 구현되어 있습니다.
 */
object SystemObjectProvider {
    private val logger = Logger.getInstance(SystemObjectProvider::class.java)

    // 시스템 객체들을 저장하는 맵 (키 -> 객체 인스턴스)
    private val systemObjects = ConcurrentHashMap<String, Any>()

    /**
     * 시스템 객체들을 식별하기 위한 키를 정의하는 객체입니다.
     */
    object Keys {
        const val APPLICATION = "application" // IntelliJ ApplicationManager 인스턴스를 위한 키
        // 여기에 더 많은 시스템 객체 키를 추가할 수 있습니다.
    }

    /**
     * `SystemObjectProvider`를 초기화합니다.
     * 초기화 시 필요한 기본 시스템 객체들을 등록합니다.
     * @param project 현재 IntelliJ 프로젝트 (현재는 사용되지 않음)
     */
    fun initialize(project: Project) {
        logger.info("SystemObjectProvider 초기화 중 (프로젝트: ${project.name})")

        // IntelliJ ApplicationManager 인스턴스를 등록합니다.
        register(Keys.APPLICATION, ApplicationManager.getApplication())
    }

    /**
     * 시스템 객체를 등록합니다.
     * @param key 객체를 식별할 키
     * @param obj 등록할 객체 인스턴스
     */
    fun register(key: String, obj: Any) {
        systemObjects[key] = obj
        logger.debug("시스템 객체 등록됨: $key")
    }

    /**
     * 지정된 키에 해당하는 시스템 객체를 가져옵니다.
     * @param key 객체를 식별할 키
     * @return 해당 객체 인스턴스, 또는 찾지 못하면 null
     */
    @Suppress("UNCHECKED_CAST") // 타입 안전성 경고를 억제합니다.
    fun <T> get(key: String): T? {
        return systemObjects[key] as? T
    }

    /**
     * 리소스를 정리합니다.
     * 등록된 모든 시스템 객체를 맵에서 제거합니다.
     */
    fun dispose() {
        logger.info("SystemObjectProvider 해제 중")
        systemObjects.clear()
    }
}
