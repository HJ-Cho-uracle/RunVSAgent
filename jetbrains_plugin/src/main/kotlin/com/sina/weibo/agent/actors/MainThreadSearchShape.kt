// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.net.URI

/**
 * IntelliJ 메인 스레드에서 검색 관련 서비스를 처리하기 위한 인터페이스입니다.
 * 파일 검색, 텍스트 검색 등의 제공자를 등록하고 검색 결과를 처리하는 기능을 정의합니다.
 * VSCode Extension Host의 `MainThreadSearchShape`에 해당합니다.
 */
interface MainThreadSearchShape : Disposable {
    /**
     * 파일 검색 제공자를 등록합니다.
     * @param handle 제공자의 고유 식별자
     * @param scheme 이 제공자가 처리할 URI 스키마
     */
    fun registerFileSearchProvider(handle: Int, scheme: String)
    
    /**
     * AI 기반 텍스트 검색 제공자를 등록합니다.
     * @param handle 제공자의 고유 식별자
     * @param scheme 이 제공자가 처리할 URI 스키마
     */
    fun registerAITextSearchProvider(handle: Int, scheme: String)
    
    /**
     * 일반 텍스트 검색 제공자를 등록합니다.
     * @param handle 제공자의 고유 식별자
     * @param scheme 이 제공자가 처리할 URI 스키마
     */
    fun registerTextSearchProvider(handle: Int, scheme: String)
    
    /**
     * 등록된 제공자를 해제합니다.
     * @param handle 해제할 제공자의 고유 식별자
     */
    fun unregisterProvider(handle: Int)
    
    /**
     * 파일 검색 결과를 처리합니다.
     * @param handle 결과를 보낸 제공자의 핸들
     * @param session 검색 작업을 식별하는 세션 ID
     * @param data 검색된 파일들의 URI 정보를 담은 리스트
     */
    fun handleFileMatch(handle: Int, session: Int, data: List<Map<String, Any?>>)
    
    /**
     * 텍스트 검색 결과를 처리합니다.
     * @param handle 결과를 보낸 제공자의 핸들
     * @param session 검색 작업을 식별하는 세션 ID
     * @param data 검색된 텍스트와 관련 정보를 담은 리스트
     */
    fun handleTextMatch(handle: Int, session: Int, data: List<Map<String, Any?>>)
    
    /**
     * 검색 관련 원격 측정(Telemetry) 데이터를 처리합니다.
     * @param eventName 이벤트 이름
     * @param data 원격 측정 데이터
     */
    fun handleTelemetry(eventName: String, data: Any?)
}

/**
 * `MainThreadSearchShape` 인터페이스의 구현 클래스입니다.
 * 검색 제공자를 관리하고, 검색 결과를 세션별로 저장하는 기능을 제공합니다.
 */
class MainThreadSearch : MainThreadSearchShape {
    private val logger = Logger.getInstance(MainThreadSearch::class.java)
    // 등록된 검색 제공자들을 저장하는 맵 (핸들 -> "타입:스키마")
    private val searchProviders = mutableMapOf<Int, String>()
    // 파일 검색 결과를 세션별로 저장하는 맵 (세션 ID -> URI 리스트)
    private val fileSessions = mutableMapOf<Int, MutableList<URI>>()
    // 텍스트 검색 결과를 세션별로 저장하는 맵 (세션 ID -> 결과 데이터 리스트)
    private val textSessions = mutableMapOf<Int, MutableList<Map<String, Any?>>>()

    override fun registerFileSearchProvider(handle: Int, scheme: String) {
        try {
            logger.info("파일 검색 제공자 등록: handle=$handle, scheme=$scheme")
            searchProviders[handle] = "file:$scheme"
        } catch (e: Exception) {
            logger.error("파일 검색 제공자 등록 실패", e)
        }
    }

    override fun registerAITextSearchProvider(handle: Int, scheme: String) {
        try {
            logger.info("AI 텍스트 검색 제공자 등록: handle=$handle, scheme=$scheme")
            searchProviders[handle] = "aitext:$scheme"
        } catch (e: Exception) {
            logger.error("AI 텍스트 검색 제공자 등록 실패", e)
        }
    }

    override fun registerTextSearchProvider(handle: Int, scheme: String) {
        try {
            logger.info("텍스트 검색 제공자 등록: handle=$handle, scheme=$scheme")
            searchProviders[handle] = "text:$scheme"
        } catch (e: Exception) {
            logger.error("텍스트 검색 제공자 등록 실패", e)
        }
    }

    override fun unregisterProvider(handle: Int) {
        try {
            logger.info("제공자 등록 해제: handle=$handle")
            searchProviders.remove(handle)
        } catch (e: Exception) {
            logger.error("검색 제공자 등록 해제 실패", e)
        }
    }

    override fun handleFileMatch(handle: Int, session: Int, data: List<Map<String, Any?>>) {
        try {
            logger.info("파일 검색 결과 처리: handle=$handle, session=$session, 찾은 개수=${data.size}")
            
            // Map 형태의 URI 구성요소를 실제 URI 객체로 변환합니다.
            val uris = data.mapNotNull { uriComponents ->
                try {
                    val scheme = uriComponents["scheme"] as? String ?: return@mapNotNull null
                    val authority = uriComponents["authority"] as? String ?: ""
                    val path = uriComponents["path"] as? String ?: return@mapNotNull null
                    val query = uriComponents["query"] as? String ?: ""
                    val fragment = uriComponents["fragment"] as? String ?: ""
                    
                    URI(scheme, authority, path, query, fragment)
                } catch (e: Exception) {
                    logger.warn("URI 구성요소 변환 실패: $uriComponents", e)
                    null
                }
            }
            
            // 검색 결과를 세션 ID에 따라 저장합니다.
            fileSessions.getOrPut(session) { mutableListOf() }.addAll(uris)
            
            // TODO: 실제 구현에서는 이 결과를 IntelliJ의 검색 결과 패널에 표시해야 합니다.
        } catch (e: Exception) {
            logger.error("파일 검색 결과 처리 실패", e)
        }
    }

    override fun handleTextMatch(handle: Int, session: Int, data: List<Map<String, Any?>>) {
        try {
            logger.info("텍스트 검색 결과 처리: handle=$handle, session=$session, 찾은 개수=${data.size}")
            
            // 검색 결과를 세션 ID에 따라 저장합니다.
            textSessions.getOrPut(session) { mutableListOf() }.addAll(data)
            
            // TODO: 실제 구현에서는 이 결과를 IntelliJ의 검색 결과 패널에 표시하고, 일치하는 텍스트를 하이라이트해야 합니다.
        } catch (e: Exception) {
            logger.error("텍스트 검색 결과 처리 실패", e)
        }
    }

    override fun handleTelemetry(eventName: String, data: Any?) {
        try {
            logger.info("원격 측정 데이터 처리: event=$eventName, data=$data")
        } catch (e: Exception) {
            logger.error("원격 측정 데이터 처리 실패", e)
        }
    }

    override fun dispose() {
        logger.info("Disposing MainThreadSearch")
        searchProviders.clear()
        fileSessions.clear()
        textSessions.clear()
    }
}
