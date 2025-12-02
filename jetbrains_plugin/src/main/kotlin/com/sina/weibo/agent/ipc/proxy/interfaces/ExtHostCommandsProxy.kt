// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

import com.sina.weibo.agent.ipc.proxy.LazyPromise

/**
 * Extension Host가 IntelliJ 플러그인의 커맨드(명령)를 실행하기 위한 프록시 인터페이스입니다.
 * TypeScript의 `ExtHostCommandsShape`에 해당합니다.
 * 이 인터페이스를 통해 Extension Host는 IntelliJ 플러그인에 등록된 커맨드를 원격으로 호출할 수 있습니다.
 */
interface ExtHostCommandsProxy {
    /**
     * 기여된(contributed) 커맨드를 인자와 함께 실행합니다.
     * @param id 실행할 커맨드의 고유 ID
     * @param args 커맨드 실행에 필요한 인자 목록
     * @return 비동기 작업의 결과를 담는 `LazyPromise`
     */
    fun executeContributedCommand(id: String, args: List<Any?>): LazyPromise

    /**
     * 기여된 커맨드를 가변 인자(vararg)와 함께 실행합니다.
     * @param id 실행할 커맨드의 고유 ID
     * @param args 커맨드 실행에 필요한 가변 인자
     * @return 비동기 작업의 결과를 담는 `LazyPromise`
     */
    fun executeContributedCommand(id: String, vararg args: Any?): LazyPromise

    /**
     * 기여된 커맨드를 인자 없이 실행합니다.
     * @param id 실행할 커맨드의 고유 ID
     * @return 비동기 작업의 결과를 담는 `LazyPromise`
     */
    fun executeContributedCommand(id: String): LazyPromise

    /**
     * 기여된 커맨드들의 메타데이터를 가져옵니다.
     * @return 비동기 작업의 결과를 담는 `LazyPromise` (커맨드 메타데이터 맵을 포함)
     */
    fun getContributedCommandMetadata(): LazyPromise
}
