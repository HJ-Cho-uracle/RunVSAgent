// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: APACHE2.0
// SPDX-License-Identifier: Apache-2.0

// Convenient for reading variables from gradle.properties
fun properties(key: String) = providers.gradleProperty(key)

// --- 플러그인 설정 ---
// 프로젝트에 필요한 Gradle 플러그인들을 선언합니다.
plugins {
    id("java") // Java 프로젝트 지원
    // Kotlin JVM 프로젝트 지원. 버전은 settings.gradle.kts에서 동적으로 관리됩니다.
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.3" // IntelliJ 플러그인 개발을 위한 핵심 플러그인
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" // Kotlin 코드 스타일을 검사하고 교정하는 ktlint 플러그인
    id("io.gitlab.arturbosch.detekt") version "1.23.4" // Kotlin 정적 코드 분석 도구인 detekt 플러그인
}

// genPlatform.gradle 파일을 적용하여 플랫폼 관련 태스크를 포함합니다.
apply(from = "genPlatform.gradle")

// --- Java 및 Kotlin 컴파일러 설정 ---
// 프로젝트에서 사용할 Java/Kotlin 툴체인(JDK) 버전을 지정합니다.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

// ------------------------------------------------------------
// 'debugMode' 설정은 빌드 프로세스 중 플러그인 리소스가 어떻게 준비되는지 제어합니다.
// 다음 세 가지 모드를 지원합니다:
//
// 1. "idea" — 로컬 개발 모드 (VSCode 플러그인 통합 디버깅에 사용)
//    - src/main/resources/themes에서 테마 리소스를 다음 경로로 복사합니다:
//        ../debug-resources/<vscodePlugin>/src/integrations/theme/default-themes/
//    - Extension Host (Node.js 측)가 런타임에 읽는 .env 파일을 자동으로 생성합니다.
//    - VSCode 플러그인이 통합 테스트를 위해 이 디렉터리에서 리소스를 로드할 수 있도록 합니다.
//    - 일반적으로 라이브 디버깅 및 핫 리로딩을 위해 Extension Host와 함께 IntelliJ를 실행할 때 사용됩니다.
//
// 2. "release" — 프로덕션 빌드 모드 (배포 아티팩트 생성에 사용)
//    - git-lfs를 통해 가져오거나 genPlatform.gradle로 생성할 수 있는 platform.zip이 존재해야 합니다.
//    - 이 파일에는 VSCode 플러그인을 위한 전체 런타임 환경(예: node_modules, platform.txt)이 포함됩니다.
//    - zip 파일은 build/platform/으로 압축 해제되며, 그 안의 node_modules는 다른 의존성보다 우선합니다.
//    - 컴파일된 extension_host 출력(dist, package.json, node_modules) 및 플러그인 리소스를 복사합니다.
//    - 결과는 다양한 플랫폼에 배포할 준비가 된 완전히 독립적인 패키지입니다.
//
// 3. "none" (기본값) — 경량 모드 (테스트 및 CI에 사용)
//    - platform.zip에 의존하거나 VSCode 런타임 리소스를 준비하지 않습니다.
//    - 테마와 같은 플러그인의 핵심 자산만 복사합니다.
//    - 초기 단계 개발, 정적 분석, 단위 테스트 및 지속적인 통합 파이프라인에 유용합니다.
//
// 설정 방법:
//   - Gradle 인자를 통해 설정: -PdebugMode=idea / release / none
//     예시: ./gradlew prepareSandbox -PdebugMode=idea
//   - 명시적으로 설정되지 않으면 "none"이 기본값입니다.
// ------------------------------------------------------------
// --- 추가 속성 (Kotlin DSL 스타일) ---
val ext = project.extensions.extraProperties
// 'debugMode' 속성: Gradle 인자에서 읽거나 기본값 "none"을 사용합니다.
ext.set("debugMode", findProperty("debugMode") ?: "none")
// 'vscodePlugin' 속성: Gradle 인자에서 읽거나 기본값 "athena"를 사용합니다.
ext.set("vscodePlugin", findProperty("vscodePlugin") ?: "athena")

// --- 강력한 타입 프로바이더 (설정 중 문자열 기반 ext 조회 방지) ---
// Gradle 속성을 읽기 위한 프로바이더를 정의합니다. 기본값을 제공합니다.
val debugModeProp = providers.gradleProperty("debugMode").orElse("none")
val vscodePluginProp = providers.gradleProperty("vscodePlugin").orElse("athena")

// --- 프로젝트 그룹 및 버전 설정 ---
group = properties("pluginGroup").get() // gradle.properties에서 pluginGroup 읽기
version = properties("pluginVersion").get() // gradle.properties에서 pluginVersion 읽기

// --- 의존성 저장소 설정 ---
repositories {
    mavenCentral() // Maven 중앙 저장소 사용
}

// --- 프로젝트 의존성 설정 ---
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.10.0") // OkHttp 라이브러리 4.10.0 => 5.0.0
    implementation("com.google.code.gson:gson:2.10.1") // Gson 라이브러리
    testImplementation("junit:junit:4.13.2") // JUnit 4 테스트 프레임워크
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4") // Detekt 포매팅 플러그인
}

// --- IntelliJ Gradle 플러그인 설정 ---
// 자세한 내용은 다음을 참조하세요: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(properties("platformVersion")) // gradle.properties에서 플랫폼 버전 읽기
    type.set(properties("platformType")) // gradle.properties에서 플랫폼 타입 읽기
    plugins.set(listOf("com.intellij.java", "org.jetbrains.plugins.terminal")) // 플러그인이 의존하는 IntelliJ 플러그인 목록
}

// --- 사용자 정의 태스크 정의 ---
tasks {

    // Git 서브모듈 초기화 및 업데이트 태스크
    val initSubmodules by registering(Exec::class) {
        group = "build setup" // 태스크 그룹
        description = "Git 서브모듈을 초기화하고 업데이트합니다."
        workingDir = rootProject.projectDir.parentFile // 프로젝트 루트 디렉터리에서 실행
        commandLine("git", "submodule", "update", "--init", "--recursive") // 실행할 명령어
    }

    // Athena 확장의 pnpm 의존성을 설치하는 태스크
    val pnpmInstallAthena by registering(Exec::class) {
        group = "build"
        description = "Athena 확장의 pnpm 의존성을 설치합니다."
        dependsOn(initSubmodules) // 서브모듈 초기화 후 실행
        workingDir = rootProject.projectDir.resolve("../deps/athena") // Athena 서브모듈 디렉터리
        commandLine("pnpm", "install") // pnpm install 명령어 실행
    }

    // Athena 확장의 VSIX 패키지를 생성하는 태스크
    val pnpmVsixAthena by registering(Exec::class) {
        group = "build"
        description = "Athena 확장의 VSIX 패키지를 생성합니다."
        dependsOn(pnpmInstallAthena) // pnpm install 후 실행
        workingDir = rootProject.projectDir.resolve("../deps/athena")
        commandLine("pnpm", "run", "vsix") // pnpm run vsix 명령어 실행
    }

    // Athena VSIX 파일의 압축을 임시 디렉터리에 해제하는 태스크
    val unzipAthenaVsix by registering(Copy::class) {
        group = "build"
        description = "Athena VSIX 파일의 압축을 임시 디렉터리에 해제합니다."
        dependsOn(pnpmVsixAthena) // VSIX 생성 후 실행

        val athenaDir = rootProject.projectDir.resolve("../deps/athena")
        // 가장 최근에 생성된 .vsix 파일을 찾는 프로바이더
        val vsixFileProvider = project.provider {
            athenaDir.resolve("bin").listFiles { _, name -> name.endsWith(".vsix") }
                ?.maxByOrNull { it.lastModified() } // 가장 최근 수정된 파일 선택
                ?: throw GradleException("Athena .vsix 파일을 ${athenaDir.resolve("bin")}에서 찾을 수 없습니다.")
        }
        from(zipTree(vsixFileProvider)) // VSIX 파일의 압축 해제
        into(project.layout.buildDirectory.dir("unpackedVsix")) // build/unpackedVsix 디렉터리에 해제
    }

    // 압축 해제된 Athena 확장 파일을 플러그인 디렉터리로 복사하는 태스크
    val prepareAthenaPlugin by registering(Copy::class) {
        group = "build"
        description = "압축 해제된 Athena 확장 파일을 플러그인 디렉터리로 복사합니다."
        dependsOn(unzipAthenaVsix) // VSIX 압축 해제 후 실행
        from(project.layout.buildDirectory.dir("unpackedVsix/extension")) // 압축 해제된 'extension' 디렉터리
        into(project.projectDir.resolve("plugins/athena")) // jetbrains_plugin/plugins/athena 디렉터리로 복사
    }

    // Extension Host의 npm 의존성을 설치하는 태스크
    val npmInstallExtensionHost by registering(Exec::class) {
        group = "build"
        description = "Extension Host의 npm 의존성을 설치합니다."
        dependsOn(initSubmodules) // 서브모듈 초기화 후 실행
        workingDir = rootProject.projectDir.resolve("../extension_host") // Extension Host 디렉터리
        commandLine("npm", "install") // npm install 명령어 실행
    }

    // Extension Host를 빌드하는 태스크
    val npmBuildExtensionHost by registering(Exec::class) {
        group = "build"
        description = "디버깅을 위해 Extension Host를 빌드합니다."
        dependsOn(npmInstallExtensionHost) // npm install 후 실행
        workingDir = rootProject.projectDir.resolve("../extension_host")
        commandLine("npm", "run", "build") // npm run build 명령어 실행
    }

    // 플러그인 설정이 포함된 properties 파일을 생성하는 태스크
    register<DefaultTask>("generateConfigProperties") {
        description = "플러그인 설정이 포함된 properties 파일을 생성합니다."
        doLast {
            val configDir = project.projectDir.resolve("src/main/resources/com/sina/weibo/agent/plugin/config")
            configDir.mkdirs() // 디렉터리 생성
            val configFile = configDir.resolve("plugin.properties")
            configFile.writeText("debug.mode=${ext.get("debugMode")}") // debugMode 작성
            configFile.appendText("\n")
            configFile.appendText("debug.resource=${project.projectDir.resolve("../debug-resources").absolutePath}") // debugResource 작성
            println("설정 파일 생성됨: ${configFile.absolutePath}")
        }
    }

    // 문제가 있는 'buildSearchableOptions' 태스크 비활성화
    named("buildSearchableOptions") {
        enabled = false
    }

    // --- 샌드박스 준비 태스크 (가장 중요) ---
    // 'runIde' 실행 시 플러그인 실행에 필요한 모든 파일을 샌드박스 디렉터리로 복사합니다.
    prepareSandbox {
        // 이 태스크가 항상 실행되도록 강제합니다. (UP-TO-DATE 검사 무시)
        outputs.upToDateWhen { false }

        // prepareAthenaPlugin 및 npmBuildExtensionHost 태스크 완료 후 실행
        dependsOn(prepareAthenaPlugin, npmBuildExtensionHost)

        val debugMode = debugModeProp.get() // 현재 디버그 모드
        val vsCodePluginName = vscodePluginProp.get() // VSCode 플러그인 이름
        val sandboxDir = intellij.pluginName.get() // IntelliJ 샌드박스 디렉터리 이름

        duplicatesStrategy = DuplicatesStrategy.INCLUDE // 중복 파일 처리 전략: 포함 (덮어쓰기)

        val themesDir = project.projectDir.resolve("src/main/resources/themes") // 테마 리소스 디렉터리
        val vscodePluginDir = project.projectDir.resolve("plugins/${vsCodePluginName}") // VSCode 플러그인 설치 디렉터리
        val extensionHostDir = project.projectDir.resolve("../extension_host") // Extension Host 디렉터리

        // 태스크 실행 직전, 필요한 디렉터리가 존재하는지 확인합니다.
        doFirst {
            if (!themesDir.exists()) {
                throw IllegalStateException("실행 시 테마 디렉터리를 찾을 수 없습니다: ${themesDir.absolutePath}")
            }
            if (!vscodePluginDir.exists()) {
                throw IllegalStateException("실행 시 VSCode 플러그인 디렉터리를 찾을 수 없습니다: ${vscodePluginDir.absolutePath}. 'prepareAthenaPlugin' 태스크가 실패했을 수 있습니다.")
            }
        }

        // 디버그 모드에 따라 다른 복사 전략을 사용합니다.
        if (debugMode == "idea") {
            // "idea" 모드: 로컬 개발 및 디버깅을 위해 'debug-resources' 디렉터리로 복사합니다.
            val debugDir = project.projectDir.resolve("../debug-resources")
            doFirst {
                // debugDir을 정리하고 다시 생성합니다.
                if (debugDir.exists()) {
                    project.delete(debugDir)
                }
                debugDir.mkdirs()
            }
            into(debugDir) // 기본 목적지를 debug-resources로 설정
            from(vscodePluginDir) { into(vsCodePluginName) } // VSCode 플러그인 파일 복사
            from(themesDir) { into("${vsCodePluginName}/integrations/theme/default-themes") } // 테마 파일 복사
            from(themesDir) { into("themes") } // 테마 파일 복사
            from(extensionHostDir.resolve("dist")) { into("runtime") } // Extension Host dist 복사
            from(extensionHostDir.resolve("package.json")) { into("runtime") } // Extension Host package.json 복사
            from(extensionHostDir.resolve("node_modules")) { into("node_modules") } // Extension Host node_modules 복사
        } else {
            // "release" 또는 "none" 모드: 실제 배포용 샌드박스 디렉터리로 복사합니다.
            into(project.layout.buildDirectory.dir("idea-sandbox/plugins/${sandboxDir}")) // 기본 목적지를 샌드박스 플러그인 디렉터리로 설정
            from(extensionHostDir.resolve("dist")) { into("runtime") } // Extension Host dist 복사
            from(extensionHostDir.resolve("package.json")) { into("runtime") } // Extension Host package.json 복사
            from(vscodePluginDir) { into(vsCodePluginName) } // VSCode 플러그인 파일 복사
            from(themesDir) { into("${vsCodePluginName}/integrations/theme/default-themes") } // 테마 파일 복사
            from(themesDir) { into("themes") } // 테마 파일 복사
            from(extensionHostDir.resolve("node_modules")) { into("node_modules") } // Extension Host node_modules 복사

            if (debugMode == "release") {
                // "release" 모드: platform.zip 관련 로직
                val platformZip = project.file("platform.zip")
                if (!platformZip.exists() || platformZip.length() < 1024 * 1024) {
                    logger.warn("platform.zip을 찾을 수 없거나 너무 작습니다. 'genPlatform'을 태스크 의존성에 추가합니다.")
                    dependsOn("genPlatform") // platform.zip이 없으면 genPlatform 태스크 실행
                }
                // platform.zip에 대한 릴리스별 로직은 여전히 문제가 있으며, 동적 구성이 포함된 경우 별도의 태스크에서 처리해야 합니다.
                // 현재는 릴리스가 아닌 빌드가 작동하도록 하는 데 중점을 둡니다.
            }
        }

        // 태스크 완료 후 .env 파일 생성
        doLast {
            if (debugMode != "idea") {
                destinationDir.resolve("${vsCodePluginName}/.env").createNewFile()
            }
        }
    }

    // Java 컴파일 태스크 설정
    withType<JavaCompile> {
        dependsOn("generateConfigProperties") // generateConfigProperties 태스크 완료 후 실행
    }

    // Kotlin 컴파일 태스크 설정
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn("generateConfigProperties") // generateConfigProperties 태스크 완료 후 실행
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) // JVM 타겟 버전 설정
        }
    }

    // plugin.xml 패치 태스크 설정
    patchPluginXml {
        version.set(properties("pluginVersion")) // gradle.properties에서 플러그인 버전 읽기
        sinceBuild.set(properties("pluginSinceBuild")) // gradle.properties에서 최소 빌드 버전 읽기
        untilBuild.set("") // 모든 빌드 버전과 호환되도록 설정
    }

    // 플러그인 서명 태스크 설정 (환경 변수 필요)
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    // 플러그인 게시 태스크 설정 (환경 변수 필요)
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

// --- ktlint 설정 ---
ktlint {
    version.set("0.50.0") // ktlint 버전
    debug.set(false)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)
    filter {
        exclude("**/generated/**") // 생성된 파일 제외
        include("**/kotlin/**") // Kotlin 파일만 포함
    }
}

// --- detekt 설정 ---
detekt {
    toolVersion = "1.23.4" // detekt 도구 버전
    config.setFrom(file("detekt.yml")) // detekt.yml 파일에서 설정 로드
    buildUponDefaultConfig = true // 기본 설정 위에 추가
    allRules = false // 모든 규칙 활성화 (false로 설정되어 있으므로 기본 규칙만 사용)
}

// --- detekt 리포트 설정 (최신 방식) ---
// 'reports' 블록이 deprecated 되었으므로, tasks.withType을 사용하여 Detekt 태스크에 직접 리포트 설정
tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    reports {
        html.required.set(true) // HTML 리포트 생성
        xml.required.set(true) // XML 리포트 생성
        txt.required.set(true) // TXT 리포트 생성
        sarif.required.set(true) // SARIF 리포트 생성
        md.required.set(true) // Markdown 리포트 생성
    }
}
