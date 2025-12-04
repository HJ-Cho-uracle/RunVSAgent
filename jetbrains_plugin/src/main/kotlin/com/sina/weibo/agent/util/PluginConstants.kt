// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

/**
 * 플러그인에서 사용되는 상수들을 정의하는 객체입니다.
 */
object PluginConstants {
    // 플러그인의 고유 ID
    const val PLUGIN_ID = "RunVSAgent"

    // Node.js 모듈 경로
    const val NODE_MODULES_PATH = "node_modules"

    // Extension Host의 진입점 파일 이름
    const val EXTENSION_ENTRY_FILE = "extension.js"

    // 런타임 디렉터리 이름
    const val RUNTIME_DIR = "runtime"

    /**
     * 설정 파일 관련 상수들을 정의하는 객체입니다.
     */
    object ConfigFiles {
        /**
         * 메인 설정 파일 이름
         */
        const val MAIN_CONFIG_FILE = ".vscode-agent"

        /**
         * 확장별 설정 파일 이름의 접두사
         */
        const val EXTENSION_CONFIG_PREFIX = ".vscode-agent."

        /**
         * 확장의 타입을 지정하는 설정 키
         */
        const val EXTENSION_TYPE_KEY = "extension.type"

        /**
         * 디버그 모드를 지정하는 설정 키
         */
        const val DEBUG_MODE_KEY = "debug.mode"

        /**
         * 디버그 리소스 경로를 지정하는 설정 키
         */
        const val DEBUG_RESOURCE_KEY = "debug.resource"

        /**
         * 사용자 설정 파일이 저장될 디렉터리 경로를 가져옵니다.
         * (예: `~/.run-vs-agent`)
         */
        fun getUserConfigDir(): String {
            return System.getProperty("user.home") + "/.run-vs-agent"
        }

        /**
         * 메인 설정 파일의 전체 경로를 가져옵니다.
         */
        fun getMainConfigPath(): String {
            return getUserConfigDir() + "/" + MAIN_CONFIG_FILE
        }

        /**
         * 특정 확장의 설정 파일 전체 경로를 가져옵니다.
         * @param extensionId 확장의 ID
         */
        fun getExtensionConfigPath(extensionId: String): String {
            return getUserConfigDir() + "/" + EXTENSION_CONFIG_PREFIX + extensionId
        }

        /**
         * 확장 설정 파일 이름으로부터 확장 ID를 추출합니다.
         * (예: ".vscode-agent.roo-code" -> "roo-code")
         * @param filename 확장 설정 파일 이름
         * @return 추출된 확장 ID, 또는 해당 파일이 확장 설정 파일이 아니면 null
         */
        fun getExtensionIdFromFilename(filename: String): String? {
            return if (filename.startsWith(EXTENSION_CONFIG_PREFIX)) {
                filename.substring(EXTENSION_CONFIG_PREFIX.length)
            } else {
                null
            }
        }

        /**
         * 파일 이름이 확장 설정 파일 형식인지 확인합니다.
         * @param filename 확인할 파일 이름
         * @return 확장 설정 파일이면 true
         */
        fun isExtensionConfigFile(filename: String): Boolean {
            return filename.startsWith(EXTENSION_CONFIG_PREFIX) && filename != MAIN_CONFIG_FILE
        }
    }
}
