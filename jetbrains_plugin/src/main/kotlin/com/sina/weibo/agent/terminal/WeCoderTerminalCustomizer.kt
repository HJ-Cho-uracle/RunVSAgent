// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WeCoder 터미널 사용자 정의기(Customizer) 클래스입니다.
 * IntelliJ의 `LocalTerminalCustomizer`를 상속받아 로컬 터미널의 동작을 사용자 정의합니다.
 * 특히 VSCode의 셸 통합 스크립트를 주입하여 터미널 기능을 향상시킵니다.
 */
class WeCoderTerminalCustomizer : LocalTerminalCustomizer() {

    private val logger = Logger.getInstance(WeCoderTerminalCustomizer::class.java)

    // 셸 통합 파일 복사 상태를 나타내는 플래그
    private val filesCopied = AtomicBoolean(false)

    // 셸 통합 파일의 기본 디렉터리 (사용자 홈 디렉터리 내)
    private val shellIntegrationBaseDir: String by lazy {
        val userHome = System.getProperty("user.home")
        Paths.get(userHome, ".run-vs-agent-shell-integrations").toString()
    }

    init {
        // 클래스 초기화 시 셸 통합 파일을 비동기적으로 복사합니다.
        copyShellIntegrationFiles()
    }

    /**
     * 셸 통합 파일을 사용자 홈 디렉터리로 비동기적으로 복사합니다.
     * 이 파일들은 터미널 시작 시 셸에 주입되어 셸 통합 기능을 활성화합니다.
     */
    private fun copyShellIntegrationFiles() {
        if (filesCopied.get()) {
            return // 이미 복사되었으면 중복 실행 방지
        }

        // IDEA의 백그라운드 스레드 풀을 사용하여 비동기적으로 실행합니다.
        ApplicationManager.getApplication().executeOnPooledThread {
            if (!filesCopied.compareAndSet(false, true)) {
                return@executeOnPooledThread // 중복 복사 방지
            }

            try {
                logger.info("🚀 셸 통합 파일 사용자 홈 디렉터리로 비동기 복사 시작...")

                // 복사할 셸 통합 설정 파일들을 정의합니다.
                val shellConfigs = mapOf(
                    "vscode-zsh" to listOf(".zshrc", ".zshenv"),
                    "vscode-bash" to listOf("bashrc"),
                    "vscode-powershell" to listOf("profile.ps1", "diagnose.ps1"),
                )

                // 각 셸 타입에 대한 통합 파일들을 복사합니다.
                shellConfigs.forEach { (shellType, files) ->
                    val sourceDir = "run-vs-agent-shell-integrations/$shellType" // 리소스 내 원본 경로
                    val targetDir = Paths.get(shellIntegrationBaseDir, shellType).toString() // 대상 경로

                    // 대상 디렉터리 생성
                    val targetDirFile = File(targetDir)
                    if (!targetDirFile.exists()) {
                        targetDirFile.mkdirs()
                        logger.info("📁 $shellType 대상 디렉터리 생성됨: $targetDir")
                    }

                    // 파일 복사
                    files.forEach { fileName ->
                        val inputStream = javaClass.classLoader.getResourceAsStream("$sourceDir/$fileName") // 리소스에서 입력 스트림 가져오기
                        if (inputStream != null) {
                            val targetFile = File("$targetDir/$fileName")
                            targetFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream) // 파일 복사
                            }
                            targetFile.setExecutable(true, true) // 실행 권한 설정
                            logger.info("✅ $shellType 파일 복사 성공: $fileName")
                        } else {
                            logger.warn("⚠️ $shellType 소스 파일을 찾을 수 없음: $fileName")
                        }
                    }
                }

                logger.info("✅ 셸 통합 파일 비동기 복사 완료")
            } catch (e: Exception) {
                logger.error("❌ 셸 통합 파일 비동기 복사 실패", e)
                filesCopied.set(false) // 복사 실패 시 상태 초기화하여 재시도 허용
            }
        }
    }

    /**
     * 터미널 명령어와 환경 변수를 사용자 정의합니다.
     * 이 메소드는 터미널 프로세스가 시작되기 전에 호출됩니다.
     *
     * @param project 현재 IntelliJ 프로젝트
     * @param workingDirectory 터미널의 현재 작업 디렉터리
     * @param command 실행될 명령어 배열
     * @param envs 환경 변수 맵
     * @return 사용자 정의된 명령어 배열
     */
    override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        command: Array<String>,
        envs: MutableMap<String, String>,
    ): Array<String> {
        logger.info("🔧 WeCodeTerminalCustomizer - 터미널 명령어 및 환경 사용자 정의")
        logger.info("📂 작업 디렉터리: $workingDirectory")
        logger.info("🔨 명령어: ${command.joinToString(" ")}")
        logger.info("🌍 환경 변수: ${envs.entries.joinToString("\n")}")

        // VSCode 셸 통합 스크립트를 주입합니다.
        return injectVSCodeScript(command, envs)
    }

    /**
     * VSCode 셸 통합 스크립트를 터미널 명령어에 주입합니다.
     * 셸 타입에 따라 다른 주입 방식을 사용합니다.
     * @param command 원본 명령어 배열
     * @param envs 환경 변수 맵
     * @return 스크립트가 주입된 새로운 명령어 배열
     */
    private fun injectVSCodeScript(command: Array<String>, envs: MutableMap<String, String>): Array<String> {
        val shellName = File(command[0]).name // 셸 실행 파일 이름 (예: "bash", "zsh")
        val scriptPath = getVSCodeScript(shellName) ?: run {
            logger.warn("🚫 셸($shellName)에 대한 통합 스크립트를 찾을 수 없습니다.")
            return command // 스크립트를 찾지 못하면 원본 명령어 반환
        }

        logger.info("🔧 셸 통합 스크립트 주입 중: $scriptPath")
        logger.info("🐚 셸 타입: $shellName")

        // 일반적인 주입 플래그 환경 변수 설정
        envs["VSCODE_INJECTION"] = "1"

        return when (shellName) {
            "bash", "sh" -> injectBashScript(command, envs, scriptPath)
            "zsh" -> injectZshScript(command, envs, scriptPath)
            "powershell", "pwsh", "powershell.exe" -> injectPowerShellScript(command, envs, scriptPath)
            else -> {
                logger.warn("⚠️ 지원되지 않는 셸 타입: $shellName")
                command
            }
        }
    }

    /**
     * Bash/Sh 셸에 VSCode 통합 스크립트를 주입합니다.
     * `--rcfile` 파라미터를 사용하여 스크립트를 로드합니다.
     */
    private fun injectBashScript(command: Array<String>, envs: MutableMap<String, String>, scriptPath: String): Array<String> {
        val rcfileIndex = command.indexOf("--rcfile")

        return if (rcfileIndex != -1 && rcfileIndex + 1 < command.size) {
            // `--rcfile` 파라미터가 이미 존재하면 원본 경로를 환경 변수에 저장합니다.
            val originalRcfile = command[rcfileIndex + 1]
            logger.info("🔧 기존 --rcfile 파라미터 감지됨: $originalRcfile")
            envs["ORIGINAL_BASH_RCFILE"] = originalRcfile

            // `--rcfile` 파라미터 값을 새 스크립트 경로로 교체합니다.
            val newCommand = command.clone()
            newCommand[rcfileIndex + 1] = scriptPath
            logger.info("🔧 --rcfile 파라미터가 '$scriptPath'(으)로 교체됨")
            newCommand
        } else {
            // `--rcfile` 파라미터가 없으면 새로 추가합니다.
            logger.info("🔧 새 --rcfile 파라미터 추가됨: $scriptPath")
            arrayOf(command[0], "--rcfile", scriptPath) + command.drop(1)
        }
    }

    /**
     * Zsh 셸에 VSCode 통합 스크립트를 주입합니다.
     * `ZDOTDIR` 환경 변수를 사용하여 스크립트를 로드합니다.
     */
    private fun injectZshScript(
        command: Array<String>,
        envs: MutableMap<String, String>,
        scriptPath: String,
    ): Array<String> {
        // 1) JetBrains의 내장 Zsh 셸 통합이 이미 적용되어 있으면 `ZDOTDIR` 재정의를 피합니다.
        val jetbrainsZshDir = envs["JETBRAINS_INTELLIJ_ZSH_DIR"] ?: System.getenv("JETBRAINS_INTELLIJ_ZSH_DIR")
        val looksLikeJbZsh = command[0].contains("/plugins/terminal/shell-integrations/zsh")

        if (jetbrainsZshDir != null || looksLikeJbZsh) {
            logger.info("🔒 JetBrains Zsh 통합 감지됨 (ZDOTDIR 재정의 건너뜀).")
            // 사용자의 원래 ZDOTDIR을 환경 변수에 유지하여 스크립트 내에서 필요할 때 사용하도록 합니다.
            val userZdotdir = envs["ZDOTDIR"] ?: System.getenv("ZDOTDIR") ?: System.getProperty("user.home")
            envs["USER_ZDOTDIR"] = userZdotdir
            return command
        }

        // 2) `scriptPath`가 유효한 `ZDOTDIR`처럼 보이는 경우에만 주입합니다. (최소한 `.zshrc`를 포함)
        val dir = File(scriptPath)
        val hasZshrc = File(dir, ".zshrc").exists()
        if (!dir.isDirectory || !hasZshrc) {
            logger.warn("🚫 Zsh 스크립트 디렉터리 '$scriptPath'가 유효하지 않음 (ZDOTDIR 재정의 건너뜀).")
            return command
        }

        // 3) `ZDOTDIR`을 설정하고 원래 값을 저장합니다.
        val userZdotdir = envs["ZDOTDIR"] ?: System.getenv("ZDOTDIR") ?: System.getProperty("user.home")
        envs["USER_ZDOTDIR"] = userZdotdir
        envs["ZDOTDIR"] = scriptPath

        logger.info("🔧 ZDOTDIR을 '$scriptPath'(으)로 설정 (원본은 USER_ZDOTDIR='$userZdotdir'에 저장됨), 셸=${File(command[0]).name}")
        return command
    }

    /**
     * PowerShell 셸에 VSCode 통합 스크립트를 주입합니다.
     * `-File` 파라미터를 사용하여 스크립트를 로드합니다.
     */
    private fun injectPowerShellScript(command: Array<String>, envs: MutableMap<String, String>, scriptPath: String): Array<String> {
        logger.info("🔧 PowerShell 스크립트 주입: $scriptPath")

        // PowerShell 셸 통합에 필요한 환경 변수 설정
        envs["VSCODE_NONCE"] = generateNonce() // 고유한 Nonce 생성
        envs["VSCODE_SHELL_ENV_REPORTING"] = "1"
        envs["VSCODE_STABLE"] = "1" // 안정 버전으로 표시

        logger.info("🔧 PowerShell 환경 변수 설정: VSCODE_NONCE=${envs["VSCODE_NONCE"]}")

        // `-File` 파라미터의 위치를 찾습니다.
        val fileIndex = command.indexOf("-File")

        return if (fileIndex != -1 && fileIndex + 1 < command.size) {
            // `-File` 파라미터가 이미 존재하면 원본 스크립트 경로를 환경 변수에 저장합니다.
            val originalScript = command[fileIndex + 1]
            logger.info("🔧 기존 -File 파라미터 감지됨: $originalScript")
            envs["ORIGINAL_POWERSHELL_SCRIPT"] = originalScript

            // `-File` 파라미터 값을 새 스크립트 경로로 교체합니다.
            val newCommand = command.clone()
            newCommand[fileIndex + 1] = scriptPath
            logger.info("🔧 -File 파라미터가 '$scriptPath'(으)로 교체됨")
            newCommand
        } else {
            // `-File` 파라미터가 없으면 IDEA 기본 형식으로 파라미터를 추가합니다.
            logger.info("🔧 새 -File 파라미터 추가됨: $scriptPath")

            val newCommand = mutableListOf<String>()
            newCommand.add(command[0]) // powershell.exe

            // `-NoExit` 파라미터가 없으면 추가
            if (!command.contains("-NoExit")) {
                newCommand.add("-NoExit")
            }

            // `-ExecutionPolicy` 파라미터가 없으면 추가
            val execPolicyIndex = command.indexOf("-ExecutionPolicy")
            if (execPolicyIndex == -1) {
                newCommand.add("-ExecutionPolicy")
                newCommand.add("Bypass")
            }

            // `-File` 파라미터와 스크립트 경로 추가
            newCommand.add("-File")
            newCommand.add(scriptPath)

            // 다른 원본 파라미터 추가 (첫 번째 실행 파일 이름은 건너뜀)
            command.drop(1).forEach { arg ->
                if (arg != "-NoExit" && arg != "-ExecutionPolicy" && arg != "Bypass") {
                    newCommand.add(arg)
                }
            }

            newCommand.toTypedArray()
        }
    }

    /**
     * 셸 통합을 위한 무작위 Nonce(Number used once)를 생성합니다.
     */
    private fun generateNonce(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    /**
     * 셸 이름에 해당하는 VSCode 셸 통합 스크립트 경로를 가져옵니다.
     */
    private fun getVSCodeScript(shellName: String): String? {
        return when (shellName) {
            "bash", "sh" -> {
                // Bash는 `--rcfile` 파라미터를 사용하므로 특정 파일을 가리켜야 합니다.
                Paths.get(shellIntegrationBaseDir, "vscode-bash", "bashrc").toString()
            }
            "zsh" -> {
                // Zsh는 `ZDOTDIR`을 사용하므로 디렉터리를 가리켜야 하며, `.zshrc`와 `.zshenv`를 자동으로 찾습니다.
                Paths.get(shellIntegrationBaseDir, "vscode-zsh").toString()
            }
            "powershell", "pwsh", "powershell.exe" -> {
                // PowerShell은 `-File` 파라미터를 사용하므로 특정 파일을 가리켜야 합니다.
                Paths.get(shellIntegrationBaseDir, "vscode-powershell", "profile.ps1").toString()
            }
            else -> null
        }
    }
}
