// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 부하 추정기(Load Estimator) 인터페이스입니다.
 * 시스템이 현재 높은 부하 상태에 있는지 여부를 추정하는 기능을 정의합니다.
 * VSCode의 `ILoadEstimator`에 해당합니다.
 */
interface ILoadEstimator {
    /**
     * 현재 시스템이 높은 부하 상태에 있는지 확인합니다.
     * @return 높은 부하 상태이면 true, 그렇지 않으면 false
     */
    fun hasHighLoad(): Boolean
}
