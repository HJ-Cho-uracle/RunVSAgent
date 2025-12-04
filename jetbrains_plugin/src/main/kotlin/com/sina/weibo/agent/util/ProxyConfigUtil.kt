// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.HttpConfigurable
import java.net.URI
import java.net.URISyntaxException

/**
 * 프록시 설정 유틸리티 클래스입니다.
 * IDE 설정 또는 환경 변수로부터 프록시 설정을 가져오는 역할을 합니다.
 */
object ProxyConfigUtil {
    private val logger = Logger.getInstance(ProxyConfigUtil::class.java)

    /**
     * 프록시 설정 데이터를 담는 데이터 클래스입니다.
     * @property proxyUrl HTTP/HTTPS 프록시 URL
     * @property proxyExceptions 프록시를 사용하지 않을 예외 호스트 목록
     * @property pacUrl PAC(Proxy Auto-Configuration) 파일 URL
     * @property source 프록시 설정의 출처 (예: "ide-http", "env")
     */
    data class ProxyConfig(
        val proxyUrl: String?,
        val proxyExceptions: String?,
        val pacUrl: String?,
        val source: String,
    ) {
        /** 프록시 설정이 유효한지 여부 */
        val hasProxy: Boolean
            get() = !proxyUrl.isNullOrEmpty() || !pacUrl.isNullOrEmpty()
    }

    /**
     * 시스템의 프록시 설정을 가져옵니다.
     * 우선순위: IntelliJ IDE 설정 > 환경 변수
     * @return `ProxyConfig` 객체
     */
    fun getProxyConfig(): ProxyConfig {
        // 1. IntelliJ IDE 프록시 설정 확인
        val ideProxyConfig = getIDEProxyConfig()
        if (ideProxyConfig.hasProxy) {
            logger.info("IDE 프록시 설정 사용: ${ideProxyConfig.proxyUrl ?: ideProxyConfig.pacUrl}")
            return ideProxyConfig
        }

        // 2. 환경 변수 프록시 설정 확인
        val envProxyConfig = getEnvironmentProxyConfig()
        if (envProxyConfig.hasProxy) {
            logger.info("환경 변수 프록시 설정 사용: ${envProxyConfig.proxyUrl}")
            return envProxyConfig
        }

        // 3. 프록시 설정 없음
        logger.info("프록시 설정을 찾을 수 없습니다.")
        return ProxyConfig(null, null, null, "none")
    }

    /**
     * IntelliJ IDE의 프록시 설정을 가져옵니다.
     */
    private fun getIDEProxyConfig(): ProxyConfig {
        return try {
            val proxyConfig = HttpConfigurable.getInstance()

            // PAC 프록시 설정 확인
            if (proxyConfig.USE_PROXY_PAC) {
                val pacUrl = proxyConfig.PAC_URL
                if (!pacUrl.isNullOrEmpty()) {
                    return ProxyConfig(null, null, pacUrl, "ide-pac")
                }
            }

            // HTTP 프록시 설정 확인
            if (proxyConfig.USE_HTTP_PROXY) {
                val proxyHost = proxyConfig.PROXY_HOST
                val proxyPort = proxyConfig.PROXY_PORT

                if (!proxyHost.isNullOrEmpty() && proxyPort > 0) {
                    val proxyUrl = "http://$proxyHost:$proxyPort"
                    val proxyExceptions = proxyConfig.PROXY_EXCEPTIONS
                    return ProxyConfig(proxyUrl, proxyExceptions, null, "ide-http")
                }
            }

            ProxyConfig(null, null, null, "ide-none")
        } catch (e: Exception) {
            logger.warn("IDE 프록시 설정 가져오기 실패", e)
            ProxyConfig(null, null, null, "ide-error")
        }
    }

    /**
     * 환경 변수로부터 프록시 설정을 가져옵니다.
     * `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` 환경 변수를 확인합니다.
     */
    private fun getEnvironmentProxyConfig(): ProxyConfig {
        return try {
            val httpProxy = System.getenv("HTTP_PROXY") ?: System.getenv("http_proxy")
            val httpsProxy = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
            val noProxy = System.getenv("NO_PROXY") ?: System.getenv("no_proxy")

            // HTTPS_PROXY를 우선하고, 없으면 HTTP_PROXY를 사용합니다.
            val proxyUrl = when {
                !httpsProxy.isNullOrEmpty() -> normalizeProxyUrl(httpsProxy)
                !httpProxy.isNullOrEmpty() -> normalizeProxyUrl(httpProxy)
                else -> null
            }

            ProxyConfig(proxyUrl, noProxy, null, "env")
        } catch (e: Exception) {
            logger.warn("환경 변수 프록시 설정 가져오기 실패", e)
            ProxyConfig(null, null, null, "env-error")
        }
    }

    /**
     * 프록시 URL을 정규화합니다.
     * 스키마가 없으면 "http://"를 추가하고, 유효하지 않은 URL은 "http://"를 가정합니다.
     */
    private fun normalizeProxyUrl(url: String): String {
        return try {
            val uri = URI(url)
            when {
                uri.scheme.isNullOrEmpty() -> "http://$url" // 스키마가 없으면 http:// 추가
                uri.scheme == "http" || uri.scheme == "https" -> url // http 또는 https 스키마는 그대로 사용
                else -> "http://$url" // 다른 스키마는 http://를 가정
            }
        } catch (e: URISyntaxException) {
            // URL 파싱 실패 시, http:// 프로토콜을 가정합니다.
            "http://$url"
        }
    }

    /**
     * `initializeConfiguration` 메소드를 위한 HTTP 프록시 설정을 Map 형태로 가져옵니다.
     * PAC 프록시인 경우 `proxy` 키에 PAC URL을 설정합니다.
     */
    fun getHttpProxyConfigForInitialization(): Map<String, Any>? {
        val proxyConfig = getProxyConfig()
        if (!proxyConfig.hasProxy) {
            return null
        }

        val configMap = mutableMapOf<String, Any>()

        if (!proxyConfig.pacUrl.isNullOrEmpty()) {
            configMap["proxy"] = proxyConfig.pacUrl
            configMap["proxySupport"] = "on"
        } else if (!proxyConfig.proxyUrl.isNullOrEmpty()) {
            configMap["proxy"] = proxyConfig.proxyUrl
            configMap["proxySupport"] = "on"
        }

        // `noProxy` 설정 추가
        if (!proxyConfig.proxyExceptions.isNullOrEmpty()) {
            val noProxyList = proxyConfig.proxyExceptions
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (noProxyList.isNotEmpty()) {
                configMap["noProxy"] = noProxyList
            }
        }

        return if (configMap.isNotEmpty()) configMap else null
    }

    /**
     * 프로세스 시작을 위한 프록시 환경 변수를 가져옵니다.
     * `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` 환경 변수를 설정합니다.
     */
    fun getProxyEnvVarsForProcessStart(): Map<String, String> {
        val proxyConfig = getProxyConfig()
        val envVars = mutableMapOf<String, String>()

        if (!proxyConfig.hasProxy) {
            return emptyMap()
        }

        if (!proxyConfig.pacUrl.isNullOrEmpty()) {
            // PAC 프록시인 경우 `PROXY_PAC_URL` 환경 변수를 설정합니다.
            envVars["PROXY_PAC_URL"] = proxyConfig.pacUrl
        } else if (!proxyConfig.proxyUrl.isNullOrEmpty()) {
            // HTTP 프록시인 경우 `HTTP_PROXY` 및 `HTTPS_PROXY` 환경 변수를 설정합니다.
            envVars["HTTP_PROXY"] = proxyConfig.proxyUrl
            envVars["HTTPS_PROXY"] = proxyConfig.proxyUrl
        }

        // `NO_PROXY` 환경 변수 설정
        if (!proxyConfig.proxyExceptions.isNullOrEmpty()) {
            envVars["NO_PROXY"] = proxyConfig.proxyExceptions
        }

        return envVars
    }
}
