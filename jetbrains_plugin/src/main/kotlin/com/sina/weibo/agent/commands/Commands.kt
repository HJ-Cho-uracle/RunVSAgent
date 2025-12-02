// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.commands

import com.intellij.openapi.project.Project

/**
 * 시스템 내의 단일 커맨드(명령)를 나타내는 인터페이스입니다.
 * 커맨드는 등록하고 호출할 수 있는 실행 가능한 액션을 정의합니다.
 */
interface ICommand {
   /**
    * 이 커맨드의 고유 식별자(ID)를 가져옵니다.
    * @return 커맨드 ID 문자열
    */
   fun getId(): String
   
   /**
    * 이 커맨드가 실행될 때 호출되어야 하는 메소드의 이름을 가져옵니다.
    * @return 메소드 이름 문자열
    */
   fun getMethod(): String
   
   /**
    * 호출될 메소드를 포함하고 있는 핸들러(handler) 객체를 가져옵니다.
    * 실제 로직은 이 핸들러 객체 안에 구현되어 있습니다.
    * @return 핸들러 객체
    */
   fun handler(): Any
   
   /**
    * 커맨드의 반환 타입을 가져옵니다. (선택 사항)
    * @return 반환 타입 문자열. 반환 값이 없으면 null.
    */
   fun returns(): String?
}

/**
 * 커맨드를 관리하는 레지스트리(등록소)를 위한 인터페이스입니다.
 * 시스템 내의 커맨드를 등록, 조회, 관리하는 기능을 제공합니다.
 */
interface ICommandRegistry {
    /**
     * 커맨드가 등록되었을 때 호출됩니다.
     * @param id 등록된 커맨드의 ID
     */
    fun onDidRegisterCommand(id: String)
    
    /**
     * 레지스트리에 커맨드를 등록합니다.
     * @param command 등록할 커맨드 객체
     */
    fun registerCommand(command: ICommand)
    
    /**
     * 기존 커맨드에 대한 별칭(alias)을 등록합니다.
     * @param oldId 기존 커맨드의 ID
     * @param newId 새로운 별칭 ID
     */
    fun registerCommandAlias(oldId: String, newId: String)
    
    /**
     * ID를 사용하여 커맨드를 가져옵니다.
     * @param id 조회할 커맨드의 ID
     * @return 커맨드 객체. 찾지 못하면 null.
     */
    fun getCommand(id: String): ICommand?
    
    /**
     * 등록된 모든 커맨드를 가져옵니다.
     * @return 커맨드 ID를 키로, 커맨드 객체를 값으로 하는 Map
     */
    fun getCommands(): Map<String, ICommand>
}

/**
 * `ICommandRegistry` 인터페이스의 구현 클래스입니다.
 * 특정 프로젝트에 대한 커맨드를 관리합니다.
 *
 * @property project 이 커맨드 레지스트리의 컨텍스트가 되는 프로젝트
 */
class CommandRegistry(val project: Project) : ICommandRegistry {

    /**
     * 커맨드 ID를 키로, 커맨드 객체의 리스트를 값으로 하는 맵입니다.
     * 리스트를 사용하여 향후 동일한 ID에 여러 커맨드를 연결(오버로딩)할 가능성을 열어둡니다.
     */
    private val commands = mutableMapOf<String, MutableList<ICommand>>()

    /**
     * 커맨드가 등록되었을 때 호출됩니다. (현재는 구현되지 않음)
     */
    override fun onDidRegisterCommand(id: String) {
        TODO("Not yet implemented")
    }

    /**
     * 커맨드를 레지스트리에 등록합니다.
     */
    override fun registerCommand(command: ICommand) {
        commands[command.getId()] = mutableListOf(command)
    }

    /**
     * 기존 커맨드에 대한 별칭을 등록합니다.
     * 원본 커맨드가 존재하면, 새로운 ID로 동일한 커맨드 객체를 가리키는 새 항목을 생성합니다.
     */
    override fun registerCommandAlias(oldId: String, newId: String) {
        getCommand(oldId)?.let {
            commands[newId] = mutableListOf(it)
        }
    }

    /**
     * ID로 커맨드를 가져옵니다.
     * 해당 ID로 등록된 첫 번째 커맨드를 반환합니다.
     */
    override fun getCommand(id: String): ICommand? {
        return commands[id]?.firstOrNull()
    }

    /**
     * 등록된 모든 커맨드를 가져옵니다.
     * 각 ID에 대해 첫 번째로 등록된 커맨드만 맵으로 만들어 반환합니다.
     */
    override fun getCommands(): Map<String, ICommand> {
        return commands.mapValues { it.value.first() }
    }
}
