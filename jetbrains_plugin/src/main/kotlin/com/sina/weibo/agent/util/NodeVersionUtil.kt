// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import com.intellij.openapi.diagnostic.Logger

/**
 * Node.js 버전 정보를 나타내는 데이터 클래스입니다.
 */
data class NodeVersion(
    val major: Int, // 주 버전 (Major version)
    val minor: Int, // 부 버전 (Minor version)
    val patch: Int, // 패치 버전 (Patch version)
    val original: String, // 원본 버전 문자열 (예: "v20.19.2")
) {
    /**
     * 두 Node.js 버전 번호를 비교합니다.
     * @param other 비교할 다른 버전
     * @return 현재 버전이 작으면 음수, 같으면 0, 크면 양수
     */
    fun compareTo(other: NodeVersion): Int {
        return when {
            major != other.major -> major - other.major
            minor != other.minor -> minor - other.minor
            else -> patch - other.patch
        }
    }

    /**
     * 지정된 버전보다 낮은지 여부를 확인합니다.
     */
    fun isLowerThan(other: NodeVersion): Boolean {
        return compareTo(other) < 0
    }

    /**
     * 지정된 버전보다 크거나 같은지 여부를 확인합니다.
     */
    fun isGreaterOrEqualTo(other: NodeVersion): Boolean {
        return compareTo(other) >= 0
    }

    /**
     * `NodeVersion` 객체를 문자열로 표현할 때 원본 버전 문자열을 반환합니다.
     */
    override fun toString(): String = original
}

/**
 * Node.js 버전 유틸리티 클래스입니다.
 * Node.js 실행 파일로부터 버전을 가져오고, 파싱하며, 버전 비교를 수행합니다.
 */
object NodeVersionUtil {
    private val LOG = Logger.getInstance(NodeVersionUtil::class.java)

    /**
     * Node.js 실행 파일 경로로부터 버전 정보를 가져옵니다.
     * @param nodePath Node.js 실행 파일의 경로
     * @return `NodeVersion` 객체, 또는 가져오기 실패 시 null
     */
    fun getNodeVersion(nodePath: String): NodeVersion? {
        return try {
            val process = ProcessBuilder(nodePath, "--version").start() // `node --version` 실행
            val output = process.inputStream.bufferedReader().readText().trim() // 출력 읽기
            process.waitFor() // 프로세스 종료 대기

            parseNodeVersion(output) // 출력 파싱
        } catch (e: Exception) {
            LOG.warn("Node.js 버전 가져오기 실패", e)
            null
        }
    }

    /**
     * Node.js 버전 문자열을 파싱하여 `NodeVersion` 객체로 변환합니다.
     * @param versionOutput `node --version` 명령어의 출력 문자열 (예: "v20.19.2")
     * @return 파싱된 `NodeVersion` 객체, 또는 파싱 실패 시 null
     */
    private fun parseNodeVersion(versionOutput: String): NodeVersion? {
        return try {
            // Node.js 버전 형식은 일반적으로 vX.Y.Z 이므로 정규식을 사용하여 파싱합니다.
            val versionRegex = Regex("v(\\d+)\\.(\\d+)\\.(\\d+)")
            val matchResult = versionRegex.find(versionOutput.trim())

            if (matchResult != null) {
                val major = matchResult.groupValues[1].toInt()
                val minor = matchResult.groupValues[2].toInt()
                val patch = matchResult.groupValues[3].toInt()
                val nodeVersion = NodeVersion(major, minor, patch, versionOutput.trim())

                LOG.info("Node.js 버전: $versionOutput, 파싱 결과: $major.$minor.$patch")
                nodeVersion
            } else {
                LOG.warn("출력 '$versionOutput'에서 Node.js 버전을 파싱 실패")
                null
            }
        } catch (e: Exception) {
            LOG.warn("Node.js 버전 파싱 실패", e)
            null
        }
    }

    /**
     * 현재 Node.js 버전이 최소 요구 버전을 충족하는지 확인합니다.
     * @param nodeVersion 현재 Node.js 버전
     * @param minRequiredVersion 최소 요구 버전
     * @return 요구 사항을 충족하면 true
     */
    fun isVersionSupported(nodeVersion: NodeVersion?, minRequiredVersion: NodeVersion): Boolean {
        return nodeVersion?.isGreaterOrEqualTo(minRequiredVersion) == true
    }
}
