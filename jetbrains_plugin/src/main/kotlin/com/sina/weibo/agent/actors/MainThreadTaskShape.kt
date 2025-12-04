// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ 메인 스레드에서 백그라운드 태스크(Task) 관련 서비스를 처리하기 위한 인터페이스입니다.
 * VSCode의 `tasks.json`과 유사한 기능을 제공하며, 태스크를 등록, 실행, 종료하는 등의 기능을 정의합니다.
 * VSCode Extension Host의 `MainThreadTaskShape`에 해당합니다.
 */
interface MainThreadTaskShape : Disposable {
    /**
     * 주어진 태스크 정보에 대한 고유 ID를 생성합니다.
     * @param task 태스크 정보를 담은 DTO(Map)
     * @return 생성된 태스크 ID
     */
    fun createTaskId(task: Map<String, Any?>): String

    /**
     * 특정 타입의 태스크를 제공하는 `TaskProvider`를 등록합니다.
     * @param handle 제공자의 고유 식별자
     * @param type 이 제공자가 처리할 태스크 타입 (예: "shell", "process")
     */
    fun registerTaskProvider(handle: Int, type: String)

    /**
     * 등록된 태스크 제공자를 해제합니다.
     * @param handle 해제할 제공자의 고유 식별자
     */
    fun unregisterTaskProvider(handle: Int)

    /**
     * 현재 실행 가능한 태스크 목록을 가져옵니다.
     * @param filter 특정 조건에 맞는 태스크만 필터링하기 위한 정보
     * @return 태스크 정보 DTO의 리스트
     */
    fun fetchTasks(filter: Map<String, Any?>?): List<Map<String, Any?>>

    /**
     * 특정 태스크의 실행 인스턴스 정보를 가져옵니다.
     * @param value 태스크 핸들 또는 태스크 DTO
     * @return 태스크 실행 정보를 담은 DTO
     */
    fun getTaskExecution(value: Map<String, Any?>): Map<String, Any?>

    /**
     * 지정된 태스크를 실행합니다.
     * @param task 실행할 태스크 핸들 또는 태스크 DTO
     * @return 태스크 실행 정보를 담은 DTO
     */
    fun executeTask(task: Map<String, Any?>): Map<String, Any?>

    /**
     * 실행 중인 태스크를 종료합니다.
     * @param id 종료할 태스크의 ID
     */
    fun terminateTask(id: String)

    /**
     * 새로운 태스크 시스템을 등록합니다.
     * @param scheme 태스크 시스템이 사용하는 스키마
     * @param info 태스크 시스템에 대한 정보
     */
    fun registerTaskSystem(scheme: String, info: Map<String, Any?>)

    /**
     * 사용자 정의 실행(Custom Execution)이 완료되었음을 알립니다.
     * @param id 완료된 태스크의 ID
     * @param result 실행 결과 코드
     */
    fun customExecutionComplete(id: String, result: Int?)

    /**
     * 이 플러그인이 지원하는 태스크 실행 유형을 등록합니다.
     * @param custom 사용자 정의 실행 지원 여부
     * @param shell 셸 스크립트 실행 지원 여부
     * @param process 외부 프로세스 실행 지원 여부
     */
    fun registerSupportedExecutions(custom: Boolean?, shell: Boolean?, process: Boolean?)
}

/**
 * `MainThreadTaskShape` 인터페이스의 구현 클래스입니다.
 * 현재는 각 메소드 호출 시 정보를 로깅하고, 태스크 실행 상태를 내부 맵에서 간단히 관리하는 역할만 수행합니다.
 * 향후 IntelliJ의 `TaskManager`와 연동하여 실제 백그라운드 태스크를 실행하고 관리하는 로직이 추가될 수 있습니다.
 */
class MainThreadTask : MainThreadTaskShape {
    private val logger = Logger.getInstance(MainThreadTask::class.java)

    // 등록된 태스크 제공자들을 저장하는 맵 (핸들 -> 타입)
    private val taskProviders = mutableMapOf<Int, String>()

    // 실행 중인 태스크 정보를 저장하는 맵 (태스크 ID -> 실행 정보 DTO)
    private val taskExecutions = mutableMapOf<String, Map<String, Any?>>()

    override fun createTaskId(task: Map<String, Any?>): String {
        try {
            logger.info("태스크 ID 생성 중: $task")
            val id = "task-${System.currentTimeMillis()}-${task.hashCode()}"
            logger.debug("생성된 태스크 ID: $id")
            return id
        } catch (e: Exception) {
            logger.error("태스크 ID 생성 실패", e)
            throw e
        }
    }

    override fun registerTaskProvider(handle: Int, type: String) {
        try {
            logger.info("태스크 제공자 등록: handle=$handle, type=$type")
            taskProviders[handle] = type
        } catch (e: Exception) {
            logger.error("태스크 제공자 등록 실패", e)
        }
    }

    override fun unregisterTaskProvider(handle: Int) {
        try {
            logger.info("태스크 제공자 등록 해제: handle=$handle")
            taskProviders.remove(handle)
        } catch (e: Exception) {
            logger.error("태스크 제공자 등록 해제 실패", e)
        }
    }

    override fun fetchTasks(filter: Map<String, Any?>?): List<Map<String, Any?>> {
        try {
            logger.info("태스크 목록 가져오기, 필터: $filter")
            // TODO: 실제 구현에서는 IntelliJ의 태스크 시스템을 조회해야 합니다.
            return emptyList()
        } catch (e: Exception) {
            logger.error("태스크 목록 가져오기 실패", e)
            throw e
        }
    }

    override fun getTaskExecution(value: Map<String, Any?>): Map<String, Any?> {
        try {
            val taskId = value["id"] as? String ?: value["taskId"] as? String
            logger.info("태스크 실행 정보 가져오기: $taskId")

            // 간단한 태스크 실행 DTO를 생성하여 반환합니다.
            return mapOf(
                "id" to (taskId ?: "unknown-task"),
                "task" to value,
                "active" to false,
            )
        } catch (e: Exception) {
            logger.error("태스크 실행 정보 가져오기 실패", e)
            throw e
        }
    }

    override fun executeTask(task: Map<String, Any?>): Map<String, Any?> {
        try {
            val taskId = task["id"] as? String ?: task["taskId"] as? String ?: "unknown-task"
            logger.info("태스크 실행: $taskId")

            // '실행 중' 상태의 태스크 실행 DTO를 생성합니다.
            val execution = mapOf(
                "id" to taskId,
                "task" to task,
                "active" to true,
            )

            // 태스크 실행 정보를 맵에 저장합니다.
            taskExecutions[taskId] = execution
            return execution
        } catch (e: Exception) {
            logger.error("태스크 실행 실패", e)
            throw e
        }
    }

    override fun terminateTask(id: String) {
        try {
            logger.info("태스크 종료: $id")
            taskExecutions.remove(id)
        } catch (e: Exception) {
            logger.error("태스크 종료 실패", e)
        }
    }

    override fun registerTaskSystem(scheme: String, info: Map<String, Any?>) {
        try {
            logger.info("태스크 시스템 등록: scheme=$scheme, info=$info")
        } catch (e: Exception) {
            logger.error("태스크 시스템 등록 실패", e)
        }
    }

    override fun customExecutionComplete(id: String, result: Int?) {
        try {
            logger.info("사용자 정의 실행 완료: task=$id, result=$result")
            // 태스크 실행 상태를 '비활성'으로 업데이트합니다.
            taskExecutions[id]?.let { execution ->
                taskExecutions[id] = execution + ("active" to false)
            }
        } catch (e: Exception) {
            logger.error("사용자 정의 실행 완료 상태 업데이트 실패", e)
        }
    }

    override fun registerSupportedExecutions(custom: Boolean?, shell: Boolean?, process: Boolean?) {
        try {
            logger.info("지원되는 실행 유형 등록: custom=$custom, shell=$shell, process=$process")
        } catch (e: Exception) {
            logger.error("지원되는 실행 유형 등록 실패", e)
        }
    }

    override fun dispose() {
        logger.info("Disposing MainThreadTask")
        taskProviders.clear()
        taskExecutions.clear()
    }
}
