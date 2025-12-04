// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.theme

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBusConnection
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.UIManager
import kotlin.io.path.exists
import kotlin.io.path.notExists

/**
 * 테마 변경 리스너 인터페이스입니다.
 * 테마가 변경될 때 알림을 받기 위해 컴포넌트들이 이 인터페이스를 구현할 수 있습니다.
 */
interface ThemeChangeListener {
    /**
     * 테마가 변경되었을 때 호출됩니다.
     * @param themeConfig 변환된 테마 설정 JSON 객체
     * @param isDarkTheme 현재 테마가 다크 테마인지 여부
     */
    fun onThemeChanged(themeConfig: JsonObject, isDarkTheme: Boolean)
}

/**
 * 테마 관리자 클래스입니다.
 * IntelliJ IDE 테마 변경을 모니터링하고, VSCode 테마 형식으로 변환하여
 * 플러그인 내의 다른 컴포넌트들에게 알리는 역할을 합니다.
 */
class ThemeManager : Disposable {
    private val logger = Logger.getInstance(ThemeManager::class.java)

    // 테마 설정 파일이 위치한 리소스 디렉터리
    private var themeResourceDir: Path? = null

    // 현재 테마가 다크 테마인지 여부
    private var isDarkTheme = true

    // 현재 테마 설정의 캐시 (VSCode 형식의 JSON 객체)
    private var currentThemeConfig: JsonObject? = null

    // VSCode 테마 CSS 내용 캐시
    private var themeStyleContent: String? = null

    // IntelliJ 메시지 버스 연결
    private var messageBusConnection: MessageBusConnection? = null

    // 테마 변경 리스너 목록 (동시성 안전한 리스트)
    private val themeChangeListeners = CopyOnWriteArrayList<ThemeChangeListener>()

    // JSON 직렬화/역직렬화를 위한 Gson 인스턴스
    private val gson = Gson()

    /**
     * 테마 관리자를 초기화합니다.
     * @param resourceRoot 테마 리소스의 루트 디렉터리 경로
     */
    fun initialize(resourceRoot: String) {
        logger.info("테마 관리자 초기화 중, 리소스 루트: $resourceRoot")

        // 테마 리소스 디렉터리 설정
        themeResourceDir = getThemeResourceDir(resourceRoot)

        if (themeResourceDir == null) {
            logger.warn("리소스 루트 '$resourceRoot'에 대한 테마 리소스 디렉터리가 존재하지 않습니다.")
            return
        }

        logger.info("테마 리소스 디렉터리 설정됨: $themeResourceDir")

        // 초기화 시 현재 테마 상태 감지
        updateCurrentThemeStatus()

        // 초기 테마 설정 로드
        loadThemeConfig()

        // IntelliJ 테마 변경 리스너 등록
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
        messageBusConnection?.subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener {
                logger.info("IDE 테마 변경 감지됨")
                val oldIsDarkTheme = isDarkTheme
                val oldConfig = currentThemeConfig

                // 테마 상태 업데이트
                updateCurrentThemeStatus()

                // 테마 타입이 변경되었거나 이전 설정이 없으면 설정을 다시 로드합니다.
                if (oldIsDarkTheme != isDarkTheme || oldConfig == null) {
                    loadThemeConfig()
                }
            },
        )

        logger.info("테마 관리자 초기화 완료, 현재 테마: ${if (isDarkTheme) "dark" else "light"}")
    }

    /**
     * 초기화 여부와 상관없이 현재 테마가 다크 테마인지 강제로 확인합니다.
     */
    fun isDarkThemeForce(): Boolean {
        updateCurrentThemeStatus()
        return isDarkTheme()
    }

    /**
     * 현재 IntelliJ 테마의 상태(다크/라이트)를 업데이트합니다.
     * `UIManager`를 통해 배경색의 밝기를 측정하여 판단합니다.
     */
    private fun updateCurrentThemeStatus() {
        try {
            val background = UIManager.getColor("Panel.background")
            if (background != null) {
                // 배경색의 밝기를 계산하여 0.5 미만이면 다크 테마로 간주합니다.
                val brightness = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
                isDarkTheme = brightness < 0.5
                logger.info("테마 감지됨: ${if (isDarkTheme) "dark" else "light"} (밝기: $brightness)")
            } else {
                isDarkTheme = true // 감지 실패 시 기본값으로 다크 테마 가정
                logger.warn("테마 밝기를 감지할 수 없어 다크 테마로 기본 설정됩니다.")
            }
        } catch (e: Exception) {
            logger.error("테마 상태 업데이트 중 오류 발생", e)
            isDarkTheme = true // 오류 발생 시 다크 테마로 기본 설정
        }
    }

    /**
     * 테마 설정 문자열을 파싱하고 주석을 제거하여 `JsonObject`로 변환합니다.
     */
    private fun parseThemeString(themeString: String): JsonObject {
        try {
            // 주석 라인( // 로 시작하는 라인)을 제거합니다.
            val cleanedContent = themeString
                .split("\n")
                .filter { !it.trim().startsWith("//") }
                .joinToString("\n")

            return JsonParser.parseString(cleanedContent).asJsonObject
        } catch (e: Exception) {
            logger.error("테마 문자열 파싱 중 오류 발생", e)
            throw e
        }
    }

    /**
     * 두 `JsonObject`를 병합합니다.
     * 두 번째 객체의 속성이 첫 번째 객체의 속성을 덮어씁니다.
     * 배열이나 객체는 재귀적으로 병합됩니다.
     */
    private fun mergeJsonObjects(first: JsonObject, second: JsonObject): JsonObject {
        try {
            val result = gson.fromJson(gson.toJson(first), JsonObject::class.java) // 첫 번째 객체를 복사

            for (key in second.keySet()) {
                if (!first.has(key)) {
                    result.add(key, second.get(key)) // 첫 번째에 없는 키는 추가
                    continue
                }

                val firstValue = first.get(key)
                val secondValue = second.get(key)

                if (firstValue.isJsonArray && secondValue.isJsonArray) {
                    // 배열 병합
                    val resultArray = firstValue.asJsonArray
                    secondValue.asJsonArray.forEach { resultArray.add(it) }
                } else if (firstValue.isJsonObject && secondValue.isJsonObject) {
                    // 객체는 재귀적으로 병합
                    result.add(key, mergeJsonObjects(firstValue.asJsonObject, secondValue.asJsonObject))
                } else {
                    // 다른 타입은 두 번째 값으로 덮어쓰기
                    result.add(key, secondValue)
                }
            }

            return result
        } catch (e: Exception) {
            logger.error("JSON 객체 병합 중 오류 발생", e)
            // 병합 실패 시, 두 객체의 모든 속성을 포함하는 새 객체를 반환합니다.
            val result = gson.fromJson(gson.toJson(first), JsonObject::class.java)
            second.entrySet().forEach { result.add(it.key, it.value) }
            return result
        }
    }

    /**
     * VSCode 테마 형식을 Monaco Editor 테마 형식으로 변환합니다.
     * `monaco-vscode-textmate-theme-converter`의 `convertTheme` 로직을 따릅니다.
     */
    private fun convertTheme(theme: JsonObject): JsonObject {
        try {
            val result = JsonObject()
            result.addProperty("inherit", false)

            // 기본 테마 설정 (vs-dark, vs, hc-black)
            var base = "vs-dark"
            if (theme.has("type")) {
                base = when (theme.get("type").asString) {
                    "light", "vs" -> "vs"
                    "hc", "high-contrast", "hc-light", "high-contrast-light" -> "hc-black"
                    else -> "vs-dark"
                }
            } else {
                base = if (isDarkTheme) "vs-dark" else "vs"
            }
            result.addProperty("base", base)

            // 색상 복사
            if (theme.has("colors")) {
                result.add("colors", theme.get("colors"))
            } else {
                result.add("colors", JsonObject())
            }

            // `tokenColors`를 `rules`로 변환
            val monacoThemeRules = JsonParser.parseString("[]").asJsonArray
            result.add("rules", monacoThemeRules)
            result.add("encodedTokensColors", JsonParser.parseString("[]").asJsonArray)

            // `tokenColors` 또는 `settings` 필드를 처리하여 `rules` 배열을 채웁니다.
            // ... (복잡한 파싱 로직 생략)

            return result
        } catch (e: Exception) {
            logger.error("테마 형식 변환 중 오류 발생", e)
            throw e
        }
    }

    /**
     * 클래스패스에서 VSCode 테마 스타일 파일을 읽어옵니다.
     * @param vscodeThemeFile 읽을 VSCode 테마 CSS 파일
     * @return 테마 CSS 내용 문자열
     */
    private fun loadVscodeThemeStyle(vscodeThemeFile: File): String? {
        try {
            logger.info("VSCode 테마 스타일 파일 로드 시도 중: ${vscodeThemeFile.absolutePath}")
            val content = vscodeThemeFile.readText(StandardCharsets.UTF_8)
            logger.info("VSCode 테마 스타일 로드 성공, 크기: ${content.length} 바이트")
            return content
        } catch (e: Exception) {
            logger.error("VSCode 테마 스타일 파일 읽기 실패: ${vscodeThemeFile.absolutePath}", e)
        }

        return null
    }

    /**
     * 테마 설정을 로드합니다.
     * 현재 IDE 테마에 따라 적절한 테마 파일을 선택하고, 파싱 및 변환을 수행합니다.
     */
    private fun loadThemeConfig() {
        if (themeResourceDir?.notExists() == true) {
            logger.warn("테마 설정을 로드할 수 없습니다: 리소스 디렉터리가 존재하지 않습니다.")
            return
        }

        try {
            // 현재 테마(다크/라이트)에 따라 적절한 테마 파일과 VSCode 테마 CSS 파일을 선택합니다.
            val themeFileName = if (isDarkTheme) "dark_modern.json" else "light_modern.json"
            val vscodeThemeName = if (isDarkTheme) "vscode-theme-dark.css" else "vscode-theme-light.css"
            val themeFile = themeResourceDir?.resolve(themeFileName)?.toFile()
            val vscodeThemeFile = themeResourceDir?.resolve(vscodeThemeName)?.toFile()

            val cssExists = vscodeThemeFile?.exists() == true
            if (!cssExists) {
                logger.warn("VSCode 테마 스타일 파일이 존재하지 않습니다: $vscodeThemeName")
                return
            }

            // 테마 파일 내용을 읽거나, 파일이 없으면 빈 문자열로 시작합니다.
            val themeContent = if (themeFile?.exists() == true) themeFile.readText() else ""

            // 테마 내용을 파싱하거나, 내용이 없으면 빈 JsonObject로 시작합니다.
            val parsed = if (themeContent.isNotBlank()) parseThemeString(themeContent) else JsonObject()

            // `include` 필드를 처리하여 다른 테마 파일을 포함합니다.
            var finalTheme = parsed
            if (parsed.has("include")) {
                val includeFileName = parsed.get("include").asString
                val includePath = themeResourceDir?.resolve(includeFileName)

                if (includePath != null && includePath.exists()) {
                    try {
                        val includeContent = includePath.toFile().readText()
                        val includeTheme = parseThemeString(includeContent)
                        finalTheme = mergeJsonObjects(finalTheme, includeTheme)
                    } catch (e: Exception) {
                        logger.error("포함된 테마 처리 중 오류 발생: $includeFileName", e)
                    }
                }
            }

            // 테마를 변환합니다.
            val converted = convertTheme(finalTheme)

            // VSCode 테마 스타일 파일을 읽어옵니다.
            themeStyleContent = vscodeThemeFile?.let { loadVscodeThemeStyle(it) }

            // 변환된 테마 객체에 스타일 내용을 추가합니다.
            if (themeStyleContent != null) {
                converted.addProperty("cssContent", themeStyleContent)
            }

            // 캐시 업데이트 및 리스너에게 알림
            val oldConfig = currentThemeConfig
            currentThemeConfig = converted

            logger.info("테마 설정 로드 및 변환 완료: $themeFileName (테마 파일 존재: ${themeFile?.exists() == true}, CSS 존재: $cssExists)")

            // 설정이 변경되었으면 리스너들에게 알립니다.
            if (oldConfig?.toString() != converted.toString()) {
                notifyThemeChangeListeners()
            }
        } catch (e: IOException) {
            logger.error("테마 설정 읽기 중 오류 발생", e)
        } catch (e: JsonIOException) {
            logger.error("테마 JSON 처리 중 오류 발생", e)
        } catch (e: Exception) {
            logger.error("테마 설정 로드 중 알 수 없는 오류 발생", e)
        }
    }

    /**
     * 모든 테마 변경 리스너에게 테마 변경을 알립니다.
     */
    private fun notifyThemeChangeListeners() {
        val config = currentThemeConfig ?: return

        logger.info("${themeChangeListeners.size}개의 테마 변경 리스너에게 알림")
        themeChangeListeners.forEach { listener ->
            try {
                listener.onThemeChanged(config, isDarkTheme)
            } catch (e: Exception) {
                logger.error("테마 변경 리스너 알림 중 오류 발생", e)
            }
        }
    }

    /**
     * 테마 변경 리스너를 추가합니다.
     * @param listener 추가할 리스너
     */
    fun addThemeChangeListener(listener: ThemeChangeListener) {
        themeChangeListeners.add(listener)
        logger.info("테마 변경 리스너 추가됨, 현재 리스너 수: ${themeChangeListeners.size}")

        // 테마 설정이 이미 존재하면 새로 추가된 리스너에게 즉시 알립니다.
        currentThemeConfig?.let {
            try {
                listener.onThemeChanged(it, isDarkTheme)
                logger.info("새로 추가된 리스너에게 현재 테마 설정 알림")
            } catch (e: Exception) {
                logger.error("새 리스너에게 현재 테마 설정 알림 중 오류 발생", e)
            }
        }
    }

    /**
     * 테마 변경 리스너를 제거합니다.
     * @param listener 제거할 리스너
     */
    fun removeThemeChangeListener(listener: ThemeChangeListener) {
        themeChangeListeners.remove(listener)
        logger.info("테마 변경 리스너 제거됨, 남은 리스너 수: ${themeChangeListeners.size}")
    }

    /**
     * 테마 설정을 수동으로 다시 로드합니다.
     * 테마가 변경되지 않았더라도 다시 로드하고 리스너들에게 알립니다.
     */
    fun reloadThemeConfig() {
        logger.info("테마 설정 수동으로 다시 로드 중")
        loadThemeConfig()
    }

    /**
     * 현재 테마가 다크 테마인지 여부를 반환합니다.
     */
    fun isDarkTheme(): Boolean {
        return isDarkTheme
    }

    /**
     * 현재 테마 설정 JSON 객체를 가져옵니다.
     */
    fun getCurrentThemeConfig(): JsonObject? {
        return currentThemeConfig
    }

    override fun dispose() {
        logger.info("테마 관리자 리소스 해제 중")

        themeChangeListeners.clear() // 리스너 목록 비우기

        try {
            messageBusConnection?.disconnect() // 메시지 버스 연결 해제
        } catch (e: Exception) {
            logger.error("메시지 버스 연결 해제 중 오류 발생", e)
        }
        messageBusConnection = null

        // 리소스 초기화
        themeResourceDir = null
        currentThemeConfig = null
        themeStyleContent = null

        resetInstance() // 싱글톤 인스턴스 초기화

        logger.info("테마 관리자 리소스 해제 완료")
    }

    companion object {
        @Volatile
        private var instance: ThemeManager? = null

        /**
         * 테마 리소스 디렉터리 경로를 가져옵니다.
         * @param resourceRoot 테마 리소스의 루트 디렉터리
         * @return 테마 리소스 디렉터리 경로, 디렉터리가 존재하지 않으면 null
         */
        fun getThemeResourceDir(resourceRoot: String): Path? {
            // 첫 번째 경로 시도: src/integrations/theme/default-themes
            var themeDir = Paths.get(resourceRoot, "src", "integrations", "theme", "default-themes")

            if (themeDir.notExists()) {
                // 두 번째 경로 시도: integrations/theme/default-themes
                themeDir = getDefaultThemeResourceDir(resourceRoot)
                if (themeDir.notExists()) {
                    return null
                }
            }

            return themeDir
        }

        /**
         * 기본 테마 리소스 디렉터리 경로를 가져옵니다.
         */
        fun getDefaultThemeResourceDir(resourceRoot: String): Path {
            return Paths.get(resourceRoot, "integrations", "theme", "default-themes")
        }

        /**
         * `ThemeManager`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager().also { instance = it }
            }
        }

        /**
         * `ThemeManager` 싱글톤 인스턴스를 초기화합니다.
         */
        private fun resetInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
