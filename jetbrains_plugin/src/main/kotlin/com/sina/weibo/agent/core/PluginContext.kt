// Copyright 2009-2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.ipc.proxy.IRPCProtocol

/**
 * 플러그인의 전역 컨텍스트를 제공하는 서비스 클래스입니다.
 * 프로젝트 레벨 서비스로 등록되어, 프로젝트 내에서 공유되어야 하는 핵심 리소스(예: RPC 프로토콜)를 관리합니다.
 * `@Service(Service.Level.PROJECT)` 어노테이션을 통해 IntelliJ에 프로젝트 서비스로 등록됩니다.
 */
@Service(Service.Level.PROJECT)
class PluginContext {
    private val logger = Logger.getInstance(PluginContext::class.java)

    // Extension Host와의 통신을 위한 RPC 프로토콜 인스턴스
    @Volatile // 여러 스레드에서 접근할 수 있으므로 가시성을 보장합니다.
    private var rpcProtocol: IRPCProtocol? = null

    /**
     * RPC 프로토콜 인스턴스를 설정합니다.
     * Extension Host와의 연결이 수립된 후 호출됩니다.
     * @param protocol 설정할 RPC 프로토콜 인스턴스
     */
    fun setRPCProtocol(protocol: IRPCProtocol) {
        logger.info("RPC 프로토콜 인스턴스 설정 중")
        rpcProtocol = protocol
    }

    /**
     * 현재 설정된 RPC 프로토콜 인스턴스를 가져옵니다.
     * @return RPC 프로토콜 인스턴스, 설정되지 않았으면 null
     */
    fun getRPCProtocol(): IRPCProtocol? {
        return rpcProtocol
    }

    /**
     * `PluginContext`가 관리하는 모든 리소스를 해제합니다.
     * 프로젝트가 닫히거나 플러그인이 언로드될 때 호출될 수 있습니다.
     */
    fun clear() {
        logger.info("PluginContext의 리소스 해제 중")
        rpcProtocol = null
    }

    companion object {
        /**
         * `PluginContext`의 싱글톤 인스턴스를 가져옵니다.
         * IntelliJ의 서비스 메커니즘을 통해 프로젝트별 인스턴스를 제공합니다.
         * @param project 현재 IntelliJ 프로젝트
         * @return `PluginContext` 인스턴스
         */
        fun getInstance(project: Project): PluginContext {
            return project.getService(PluginContext::class.java)
        }
    }
}
