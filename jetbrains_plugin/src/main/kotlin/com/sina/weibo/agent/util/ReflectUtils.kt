// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

/**
 * Kotlin 리플렉션을 사용하여 메소드를 동적으로 호출하는 함수입니다.
 * 특히 직렬화 과정에서 발생할 수 있는 타입 불일치(예: Int가 Double로 직렬화되는 경우)를 처리하고,
 * 널러블(nullable) 파라미터 문제를 해결합니다.
 *
 * @param method 호출할 `KFunction` 객체
 * @param args 메소드에 전달할 인자 목록
 * @param actor 메소드를 호출할 대상 객체 (리시버)
 * @return 메소드 실행 결과
 */
suspend fun doInvokeMethod(
    method: KFunction<*>,
    args: List<Any?>,
    actor: Any
): Any? {
    val parameterTypes = method.parameters // 메소드의 파라미터 목록
    val processedArgs = ArrayList<Any?>(parameterTypes.size) // 처리된 인자 목록
    // 실제 인자 목록 (첫 번째는 리시버인 actor)
    val realArgs = listOf(actor, *args.toTypedArray())

    // 파라미터 타입 불일치 및 널러블 파라미터 문제 처리
    for (i in parameterTypes.indices) {
        if (i < realArgs.size) {
            // 인자가 제공된 경우, 타입 변환 처리
            val arg = realArgs[i]
            val paramType = parameterTypes[i]

            // 직렬화로 인해 발생할 수 있는 타입 불일치 처리 (예: Int가 Double로 직렬화되는 경우)
            val convertedArg = when {
                arg is Double && paramType.type.isSubtypeOf(typeOf<Int>()) ->
                    arg.toInt() // Double을 Int로 변환

                arg is Double && paramType.type.isSubtypeOf(typeOf<Long>()) ->
                    arg.toLong() // Double을 Long으로 변환

                arg is Double && paramType.type.isSubtypeOf(typeOf<Float>()) ->
                    arg.toFloat() // Double을 Float으로 변환

                arg is Double && paramType.type.isSubtypeOf(typeOf<Short>()) ->
                    arg.toInt().toShort() // Double을 Short으로 변환

                arg is Double && paramType.type.isSubtypeOf(typeOf<Byte>()) ->
                    arg.toInt().toByte() // Double을 Byte로 변환

                arg is Double && paramType.type.isSubtypeOf(typeOf<Boolean>()) ->
                    arg != 0.0 // Double을 Boolean으로 변환 (0.0이 아니면 true)

                else -> arg // 그 외의 경우는 인자 그대로 사용
            }

            processedArgs.add(convertedArg)
        } else {
            // 인자가 누락된 경우, 파라미터가 원시 타입이면 적절한 기본값을 설정합니다.
            val paramType = parameterTypes[i]

            // String 타입에 대한 특별 처리: null 대신 빈 문자열을 설정합니다.
            if (paramType.type.isSubtypeOf(typeOf<String>())) {
                processedArgs.add("")
            } else {
                processedArgs.add(null) // 그 외의 경우는 null
            }
        }
    }

    // 메소드를 호출합니다. `callSuspend`를 사용하여 suspend 함수도 호출할 수 있습니다.
    return method.callSuspend(*processedArgs.toTypedArray())
}
