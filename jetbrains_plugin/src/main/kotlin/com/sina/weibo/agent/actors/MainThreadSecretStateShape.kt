// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.actors

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * IntelliJ 메인 스레드에서 민감한 정보(비밀)를 관리하기 위한 인터페이스입니다.
 * API 키, 토큰 등 보안이 필요한 데이터를 안전하게 저장, 조회, 삭제하는 기능을 정의합니다.
 */
interface MainThreadSecretStateShape : Disposable {
    /**
     * 지정된 키에 해당하는 비밀 값을 가져옵니다.
     * @param extensionId 이 비밀을 소유한 확장의 ID
     * @param key 비밀을 식별하는 키
     * @return 저장된 비밀 값. 존재하지 않으면 null을 반환합니다.
     */
    suspend fun getPassword(extensionId: String, key: String): String?

    /**
     * 새로운 비밀 값을 저장하거나 기존 값을 덮어씁니다.
     * @param extensionId 이 비밀을 소유한 확장의 ID
     * @param key 저장할 비밀의 키
     * @param value 저장할 비밀 값
     */
    suspend fun setPassword(extensionId: String, key: String, value: String)

    /**
     * 지정된 키에 해당하는 비밀 값을 삭제합니다.
     * @param extensionId 이 비밀을 소유한 확장의 ID
     * @param key 삭제할 비밀의 키
     */
    suspend fun deletePassword(extensionId: String, key: String)
}

/**
 * `MainThreadSecretStateShape` 인터페이스의 구현 클래스입니다.
 * 사용자의 홈 디렉터리 아래 `.roo-cline/secrets.json` 파일에 JSON 형식으로 비밀을 저장합니다.
 * 파일 접근 시 발생할 수 있는 동시성 문제를 해결하기 위해 Mutex를 사용합니다.
 */
class MainThreadSecretState : MainThreadSecretStateShape {
    private val logger = Logger.getInstance(MainThreadSecretState::class.java)
    // JSON 직렬화/역직렬화를 위한 Gson 인스턴스 (pretty printing 옵션 활성화)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    // 파일에 대한 동시 접근을 제어하기 위한 뮤텍스
    private val mutex = Mutex()
    
    // 비밀이 저장될 디렉터리 및 파일 경로
    private val secretsDir = File(System.getProperty("user.home"), ".roo-cline")
    private val secretsFile = File(secretsDir, "secrets.json")
    
    init {
        // 클래스 초기화 시, 비밀 저장 디렉터리가 존재하지 않으면 생성합니다.
        if (!secretsDir.exists()) {
            secretsDir.mkdirs()
            logger.info("비밀 저장 디렉터리 생성: ${secretsDir.absolutePath}")
        }
    }

    /**
     * JSON 파일에서 비밀 값을 읽어옵니다.
     */
    override suspend fun getPassword(extensionId: String, key: String): String? = mutex.withLock {
        try {
            if (!secretsFile.exists() || secretsFile.readText().isBlank()) {
                return null
            }
            
            val jsonContent = secretsFile.readText()
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            
            // JSON 객체에서 extensionId에 해당하는 하위 객체를 찾습니다.
            val extensionObject = jsonObject.getAsJsonObject(extensionId) ?: return null
            // 해당 객체에서 key에 해당하는 값을 찾아 문자열로 반환합니다.
            val passwordElement = extensionObject.get(key) ?: return null
            
            return passwordElement.asString
        } catch (e: Exception) {
            logger.warn("비밀 정보 조회 실패: extensionId=$extensionId, key=$key", e)
            return null
        }
    }

    /**
     * 비밀 값을 JSON 파일에 저장합니다.
     */
    override suspend fun setPassword(extensionId: String, key: String, value: String) = mutex.withLock {
        try {
            // 기존 파일이 있으면 읽어오고, 없으면 새로운 JSON 객체를 생성합니다.
            val jsonObject = if (secretsFile.exists() && secretsFile.readText().isNotBlank()) {
                JsonParser.parseString(secretsFile.readText()).asJsonObject
            } else {
                JsonObject()
            }
            
            // extensionId에 해당하는 하위 객체가 없으면 새로 생성하여 추가합니다.
            val extensionObject = jsonObject.getAsJsonObject(extensionId) ?: JsonObject().also {
                jsonObject.add(extensionId, it)
            }
            
            // 키-값 쌍을 추가하거나 덮어씁니다.
            extensionObject.addProperty(key, value)
            
            // 변경된 JSON 객체를 문자열로 변환하여 파일에 씁니다.
            val jsonString = gson.toJson(jsonObject)
            secretsFile.writeText(jsonString)
            
            logger.info("비밀 정보 설정 성공: extensionId=$extensionId, key=$key")
        } catch (e: Exception) {
            logger.error("비밀 정보 설정 실패: extensionId=$extensionId, key=$key", e)
            throw e
        }
    }

    /**
     * JSON 파일에서 비밀 값을 삭제합니다.
     */
    override suspend fun deletePassword(extensionId: String, key: String) = mutex.withLock {
        try {
            if (!secretsFile.exists() || secretsFile.readText().isBlank()) {
                return
            }
            
            val jsonContent = secretsFile.readText()
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            val extensionObject = jsonObject.getAsJsonObject(extensionId) ?: return
            
            // 해당 키를 제거합니다.
            extensionObject.remove(key)
            
            // 만약 extensionObject에 더 이상 아무 속성도 없으면, extensionObject 자체를 삭제합니다.
            if (extensionObject.size() == 0) {
                jsonObject.remove(extensionId)
            }
            
            val jsonString = gson.toJson(jsonObject)
            secretsFile.writeText(jsonString)
            
            logger.info("비밀 정보 삭제 성공: extensionId=$extensionId, key=$key")
        } catch (e: Exception) {
            logger.error("비밀 정보 삭제 실패: extensionId=$extensionId, key=$key", e)
            throw e
        }
    }

    override fun dispose() {
        logger.info("Disposing MainThreadSecretState resources")
        // JSON 파일 저장은 특별한 리소스 해제가 필요하지 않습니다.
    }
}
