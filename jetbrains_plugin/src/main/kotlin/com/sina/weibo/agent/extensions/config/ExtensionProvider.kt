// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.config

import com.intellij.openapi.project.Project

/**
 * 확장(Extension) 제공자 인터페이스입니다.
 * 플러그인에서 지원하는 모든 확장 구현체는 이 인터페이스를 구현해야 합니다.
 * 이 인터페이스는 확장의 기본 정보와 생명주기 메소드를 정의합니다.
 */
interface ExtensionProvider {
    /**
     * 확장의 고유 식별자(ID)를 가져옵니다.
     * @return 고유한 확장 식별자 문자열
     */
    fun getExtensionId(): String
    
    /**
     * 확장의 표시 이름(Display Name)을 가져옵니다.
     * @return 사람이 읽을 수 있는 확장 이름
     */
    fun getDisplayName(): String
    
    /**
     * 확장에 대한 설명을 가져옵니다.
     * @return 확장의 기능이나 목적에 대한 설명
     */
    fun getDescription(): String
    
    /**
     * 확장을 초기화합니다.
     * @param project 현재 IntelliJ 프로젝트
     */
    fun initialize(project: Project)
    
    /**
     * 확장이 현재 사용 가능한 상태인지 확인합니다.
     * (예: 필요한 파일이 존재하는지, 설정이 올바른지 등)
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장이 사용 가능하면 true, 그렇지 않으면 false
     */
    fun isAvailable(project: Project): Boolean
    
    /**
     * 확장의 설정 정보를 가져옵니다.
     * @param project 현재 IntelliJ 프로젝트
     * @return 확장의 메타데이터를 담은 `ExtensionMetadata` 객체
     */
    fun getConfiguration(project: Project): ExtensionMetadata
    
    /**
     * 확장 리소스를 해제합니다.
     */
    fun dispose()
}

/**
 * 확장의 설정 메타데이터를 정의하는 인터페이스입니다.
 * `package.json` 파일의 내용과 유사한 정보를 제공합니다.
 */
interface ExtensionMetadata {
    /**
     * 확장 파일이 위치한 디렉터리 이름을 가져옵니다.
     * @return 확장 파일이 있는 디렉터리 이름
     */
    fun getCodeDir(): String
    
    /**
     * 확장의 게시자(Publisher) 이름을 가져옵니다.
     * @return 확장의 게시자 이름
     */
    fun getPublisher(): String
    
    /**
     * 확장의 버전을 가져옵니다.
     * @return 확장 버전 문자열
     */
    fun getVersion(): String
    
    /**
     * 확장의 메인 진입점(Entry File) 파일 경로를 가져옵니다.
     * @return 메인 JavaScript 파일 경로
     */
    fun getMainFile(): String
    
    /**
     * 확장의 활성화 이벤트 목록을 가져옵니다.
     * @return 활성화 이벤트 문자열 리스트 (예: "onStartupFinished")
     */
    fun getActivationEvents(): List<String>
    
    /**
     * 확장의 엔진 요구사항을 가져옵니다.
     * @return 엔진 요구사항을 담은 Map (예: "vscode" 버전)
     */
    fun getEngines(): Map<String, String>
    
    /**
     * 확장의 기능(Capabilities) 목록을 가져옵니다.
     * @return 확장의 기능을 담은 Map
     */
    fun getCapabilities(): Map<String, Any>
    
    /**
     * 확장의 의존성 목록을 가져옵니다.
     * @return 확장 의존성 문자열 리스트
     */
    fun getExtensionDependencies(): List<String>
}
