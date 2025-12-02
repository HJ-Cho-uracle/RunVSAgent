// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors

/**
 * 부하 추정기(Load Estimator) 클래스입니다.
 * 시스템의 부하 상태를 추정하여 높은 부하 상태인지 여부를 판단합니다.
 * VSCode의 `LoadEstimator`에 해당합니다.
 */
class LoadEstimator private constructor() : ILoadEstimator {
    // 최근 실행 시간 기록을 위한 배열
    private val lastRuns = LongArray(HISTORY_LENGTH)
    // 주기적인 작업을 위한 스케줄러
    private val scheduler: ScheduledExecutorService
    
    init {
        val now = System.currentTimeMillis()
        
        // `lastRuns` 배열을 초기화하여 과거의 가상 실행 시간을 설정합니다.
        for (i in 0 until HISTORY_LENGTH) {
            lastRuns[i] = now - 1000L * i
        }
        
        // 단일 스레드 스케줄러를 생성하고 데몬 스레드로 설정합니다.
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "LoadEstimator").apply { isDaemon = true }
        }
        
        // 1초마다 `lastRuns` 배열을 업데이트하는 작업을 스케줄링합니다.
        scheduler.scheduleAtFixedRate({
            // 배열의 값을 한 칸씩 뒤로 밀고, 가장 최근 시간을 맨 앞에 기록합니다.
            for (i in HISTORY_LENGTH - 1 downTo 1) {
                lastRuns[i] = lastRuns[i - 1]
            }
            lastRuns[0] = System.currentTimeMillis()
        }, 0, 1000, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 현재 부하를 추정하여 0(낮은 부하)에서 1(높은 부하) 사이의 값을 반환합니다.
     * @return 부하 추정 값
     */
    private fun load(): Double {
        val now = System.currentTimeMillis()
        val historyLimit = (1 + HISTORY_LENGTH) * 1000L // 기록 유지 시간 (HISTORY_LENGTH + 1초)
        var score = 0 // 유효한 기록의 개수
        
        // `lastRuns` 배열을 순회하며 유효한 기록의 개수를 계산합니다.
        for (i in 0 until HISTORY_LENGTH) {
            if (now - lastRuns[i] <= historyLimit) {
                score++
            }
        }
        
        // 점수를 기반으로 부하 추정 값을 계산합니다.
        return 1.0 - score.toDouble() / HISTORY_LENGTH
    }
    
    /**
     * 현재 시스템이 높은 부하 상태인지 여부를 판단합니다.
     * 부하 추정 값이 0.5 이상이면 높은 부하로 간주합니다.
     * @return 높은 부하 상태이면 true
     */
    override fun hasHighLoad(): Boolean {
        return load() >= 0.5
    }
    
    companion object {
        private const val HISTORY_LENGTH = 10 // 부하 추정에 사용할 기록 길이 (초 단위)
        private val INSTANCE = AtomicReference<LoadEstimator>() // 싱글톤 인스턴스를 위한 AtomicReference
        
        /**
         * `LoadEstimator`의 싱글톤 인스턴스를 가져옵니다.
         * @return `LoadEstimator` 인스턴스
         */
        @JvmStatic
        fun getInstance(): LoadEstimator {
            var instance = INSTANCE.get()
            if (instance == null) {
                instance = LoadEstimator()
                // CAS (Compare-And-Swap) 연산을 사용하여 스레드 안전하게 인스턴스를 설정합니다.
                if (!INSTANCE.compareAndSet(null, instance)) {
                    // 다른 스레드가 이미 인스턴스를 설정한 경우, 그 인스턴스를 사용합니다.
                    instance = INSTANCE.get()
                }
            }
            return instance
        }
    }
}
