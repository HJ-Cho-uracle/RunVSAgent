// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sina.weibo.agent.commands.CommandRegistry
import com.sina.weibo.agent.commands.ICommand
import com.sina.weibo.agent.editor.registerOpenEditorAPICommands
import com.sina.weibo.agent.terminal.registerTerminalAPICommands
import com.sina.weibo.agent.util.doInvokeMethod
import kotlin.reflect.full.functions

/**
 * IntelliJ의 메인 스레드에서 커맨드(명령) 관련 작업을 처리하기 위한 인터페이스입니다.
 * VSCode Extension Host의 `MainThreadCommandsShape`에 해당하며,
 * RPC를 통해 커맨드를 등록, 해제, 실행하는 기능을 제공합니다.
 */
interface MainThreadCommandsShape : Disposable {
    /**
     * Extension Host에 커맨드가 존재함을 알리고 등록합니다.
     * 실제 커맨드 로직은 IntelliJ 플러그인 내부에 구현되어 있습니다.
     * @param id 등록할 커맨드의 고유 식별자
     */
    fun registerCommand(id: String)
    
    /**
     * 등록된 커맨드를 해제합니다.
     * @param id 해제할 커맨드의 고유 식별자
     */
    fun unregisterCommand(id: String)
    
    /**
     * 커맨드 활성화 이벤트를 발생시킵니다. (현재는 로깅만 수행)
     * @param id 활성화할 커맨드의 고유 식별자
     */
    fun fireCommandActivationEvent(id: String)
    
    /**
     * Extension Host의 요청에 따라 특정 커맨드를 실행합니다.
     * @param id 실행할 커맨드의 고유 식별자
     * @param args 커맨드 실행에 필요한 인자 목록
     * @return 커맨드 실행 결과 (비동기적으로 반환될 수 있음)
     */
    suspend fun executeCommand(id: String, args: List<Any?>): Any?
    
    /**
     * 현재 등록된 모든 커맨드의 ID 목록을 가져옵니다.
     * @return 커맨드 ID 문자열의 리스트
     */
    fun getCommands(): List<String>
}

/**
 * `MainThreadCommandsShape` 인터페이스의 구현 클래스입니다.
 * 커맨드 레지스트리(CommandRegistry)를 사용하여 커맨드를 관리하고,
 * 요청에 따라 실제 커맨드 핸들러를 찾아 실행합니다.
 *
 * @property project 현재 IntelliJ 프로젝트 컨텍스트
 */
class MainThreadCommands(val project: Project) : MainThreadCommandsShape {
    // 커맨드를 등록하고 관리하는 레지스트리 객체
    private val registry = CommandRegistry(project)
    private val logger = Logger.getInstance(MainThreadCommandsShape::class.java)
    
    /**
     * 클래스 초기화 시, 플러그인에서 기본적으로 제공하는 커맨드들을 등록합니다.
     */
    init {
        // 에디터 API 관련 커맨드 등록
        registerOpenEditorAPICommands(project, registry)
        // 터미널 API 관련 커맨드 등록
        registerTerminalAPICommands(project, registry)
        // TODO: 추후 다른 내장 커맨드들을 여기에 추가할 수 있습니다.
    }

    /**
     * 커맨드를 등록합니다. (현재 구현에서는 로깅만 수행)
     * 실제 커맨드 객체는 `init` 블록에서 `registry`에 직접 등록됩니다.
     */
    override fun registerCommand(id: String) {
        logger.info("커맨드 등록 요청: $id")
    }

    /**
     * 커맨드를 해제합니다. (현재 구현에서는 로깅만 수행)
     */
    override fun unregisterCommand(id: String) {
        logger.info("커맨드 해제 요청: $id")
    }

    /**
     * 커맨드 활성화 이벤트를 발생시킵니다. (현재 구현에서는 로깅만 수행)
     */
    override fun fireCommandActivationEvent(id: String) {
        logger.info("커맨드 활성화 이벤트: $id")
    }

    /**
     * ID에 해당하는 커맨드를 찾아 실행합니다.
     */
    override suspend fun executeCommand(id: String, args: List<Any?>): Any? {
        logger.info("커맨드 실행 요청: $id")
        // 레지스트리에서 ID로 커맨드를 찾습니다.
        registry.getCommand(id)?.let { cmd ->
            // 커맨드를 찾으면 runCmd를 통해 실행합니다.
            runCmd(cmd, args)
        } ?: run {
            // 커맨드를 찾지 못하면 경고를 기록합니다.
            logger.warn("커맨드를 찾을 수 없음: $id")
        }
        return Unit // 현재는 특별한 반환값이 없습니다.
    }

    /**
     * 등록된 모든 커맨드의 ID 목록을 반환합니다.
     */
    override fun getCommands(): List<String> {
        logger.info("등록된 모든 커맨드 목록 요청")
        return registry.getCommands().keys.toList()
    }

    /**
     * 리소스를 해제합니다. (Disposable 인터페이스 구현)
     */
    override fun dispose() {
        logger.info("리소스 해제: MainThreadCommands")
    }

    /**
     * 실제 커맨드 객체를 실행하는 내부 함수입니다.
     * Kotlin Reflection을 사용하여 커맨드 객체 내에서 실행할 메소드를 찾고, 인자를 전달하여 호출합니다.
     *
     * @param cmd 실행할 ICommand 객체
     * @param args 전달할 인자 목록
     */
    private suspend fun runCmd(cmd: ICommand, args: List<Any?>) {
        // 커맨드에 연결된 핸들러(로직을 담고 있는 객체)를 가져옵니다.
        val handler = cmd.handler()
        val method = try {
            // Kotlin Reflection을 사용하여 핸들러 클래스에서 메소드 이름으로 실제 KFunction을 찾습니다.
            handler::class.functions.first { it.name == cmd.getMethod() }
        } catch (e: Exception) {
            logger.error("커맨드 메소드를 찾을 수 없음: ${cmd.getMethod()}", e)
            return
        }
        // 찾은 메소드를 인자와 함께 동적으로 호출합니다.
        doInvokeMethod(method, args, handler)
    }

}
