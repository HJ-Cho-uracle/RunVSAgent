package com.sina.weibo.agent.extensions.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Properties
import com.sina.weibo.agent.util.PluginConstants
import com.sina.weibo.agent.util.ConfigFileUtils

/**
 * 확장(Extension) 설정 관리자입니다.
 * 다양한 확장에 대한 설정을 관리하고, 설정 값을 영속화(파일에 저장)합니다.
 */
@Service(Service.Level.PROJECT)
class ExtensionConfigurationManager(private val project: Project) {

    private val logger = Logger.getInstance(ExtensionConfigurationManager::class.java)

    // 메인 설정 파일의 경로
    private val configFile: File
        get() = File(PluginConstants.ConfigFiles.getMainConfigPath())

    // 현재 활성화된 확장의 ID
    @Volatile
    private var currentExtensionId: String? = null
    
    // 설정의 유효성 상태
    @Volatile
    private var isConfigurationValid = false
    
    // 설정 로딩 완료 여부
    @Volatile
    private var isConfigurationLoaded = false
    
    // 설정 로딩 시간
    private var configurationLoadTime: Long? = null

    companion object {
        /**
         * `ExtensionConfigurationManager`의 싱글톤 인스턴스를 가져옵니다.
         */
        fun getInstance(project: Project): ExtensionConfigurationManager {
            return project.getService(ExtensionConfigurationManager::class.java)
                ?: error("ExtensionConfigurationManager 서비스를 찾을 수 없습니다.")
        }
    }

    /**
     * 설정 관리자를 초기화합니다.
     * 설정 파일을 로드하고 초기 상태를 설정합니다.
     */
    fun initialize() {
        logger.info("확장 설정 관리자 초기화 중")
        loadConfiguration()
    }
    
    /**
     * 현재 설정이 유효하고 사용 준비가 되었는지 확인합니다.
     */
    fun isConfigurationValid(): Boolean {
        return isConfigurationValid
    }
    
    /**
     * 설정이 파일로부터 로드되었는지 확인합니다.
     */
    fun isConfigurationLoaded(): Boolean {
        return isConfigurationLoaded
    }
    
    /**
     * 설정이 로드된 시간을 가져옵니다.
     */
    fun getConfigurationLoadTime(): Long? {
        return configurationLoadTime
    }
    
    /**
     * 설정 유효성 검사 실패 시 오류 메시지를 반환합니다.
     */
    fun getConfigurationError(): String? {
        return if (isConfigurationLoaded && !isConfigurationValid) {
            val extensionId = currentExtensionId // 동시성 문제 방지를 위해 로컬 변수 사용
            when {
                extensionId == null -> "확장 타입이 설정되지 않았습니다. ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일에 '${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}'를 설정해주세요."
                extensionId.isBlank() -> "확장 타입이 비어 있습니다. ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일에 '${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}'에 유효한 값을 설정해주세요."
                else -> "유효하지 않은 확장 타입: '$extensionId'. ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일의 '${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}' 값을 확인해주세요."
            }
        } else null
    }
    
    /**
     * 상세한 설정 상태 정보를 문자열로 반환합니다.
     */
    fun getConfigurationStatus(): String {
        return buildString {
            append("설정 상태: ")
            if (isConfigurationLoaded) {
                if (isConfigurationValid) {
                    val extensionId = currentExtensionId
                    append("유효함 ($extensionId)")
                } else {
                    append("유효하지 않음 - ${getConfigurationError()}")
                }
            } else {
                append("로딩 중")
            }
            append(" | 파일: ${getConfigurationFilePath()}")
        }
    }
    
    /**
     * 문제 해결을 위한 상세 디버그 정보를 문자열로 반환합니다.
     */
    fun getDebugInfo(): String {
        return buildString {
            append("=== 설정 디버그 정보 ===\n")
            append("설정 로드됨: $isConfigurationLoaded\n")
            append("설정 유효함: $isConfigurationValid\n")
            append("현재 확장 ID: $currentExtensionId\n")
            append("설정 파일 경로: ${getConfigurationFilePath()}\n")
            append("설정 파일 존재 여부: ${configFile.exists()}\n")
            
            if (configFile.exists()) {
                append("설정 파일 크기: ${configFile.length()} 바이트\n")
                append("설정 파일 최종 수정일: ${java.util.Date(configFile.lastModified())}\n")
                
                try {
                    val properties = Properties()
                    properties.load(configFile.inputStream())
                    append("설정 파일 내용:\n")
                    properties.stringPropertyNames().forEach { key ->
                        append("  $key = ${properties.getProperty(key)}\n")
                    }
                } catch (e: Exception) {
                    append("설정 파일 읽기 실패: ${e.message}\n")
                }
            }
            
            append("프로젝트 기본 경로: ${project.basePath}\n")
            append("================================")
        }
    }
    
    /**
     * 유효하지 않은 설정에 대한 복구 제안 목록을 반환합니다.
     */
    fun getRecoverySuggestions(): List<String> {
        return if (isConfigurationLoaded && !isConfigurationValid) {
            listOf(
                "1. 프로젝트 루트에 ${PluginConstants.ConfigFiles.MAIN_CONFIG_FILE} 파일이 존재하는지 확인하세요.",
                "2. '${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}' 속성이 유효한 확장 ID로 설정되어 있는지 확인하세요.",
                "3. 유효한 확장 타입: roo-code, cline, custom",
                "4. 'createDefaultConfiguration()'을 실행하여 템플릿을 생성해보세요.",
                "5. 파일 권한을 확인하고 파일이 읽기 가능한지 확인하세요."
            )
        } else {
            emptyList()
        }
    }

    /**
     * 설정 파일로부터 설정을 로드합니다.
     */
    private fun loadConfiguration() {
        try {
            isConfigurationLoaded = false
            isConfigurationValid = false
            configurationLoadTime = System.currentTimeMillis()
            
            if (ConfigFileUtils.mainConfigExists()) {
                val properties = ConfigFileUtils.loadMainConfig()
                currentExtensionId = properties.getProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY)
                
                isConfigurationValid = validateConfiguration(currentExtensionId)
                
                if (isConfigurationValid) {
                    logger.info("유효한 설정 로드 완료: 현재 확장 = $currentExtensionId")
                } else {
                    logger.warn("설정 로드되었으나 유효하지 않음: 확장 타입이 null이거나 비어 있습니다.")
                }
            } else {
                logger.warn("설정 파일을 찾을 수 없음: ${configFile.absolutePath}")
                currentExtensionId = null
                isConfigurationValid = false
            }
            
            isConfigurationLoaded = true
        } catch (e: Exception) {
            logger.error("설정 로드 실패", e)
            currentExtensionId = null
            isConfigurationValid = false
            isConfigurationLoaded = true
        }
    }
    
    /**
     * 확장 ID의 유효성을 검사합니다.
     */
    private fun validateConfiguration(extensionId: String?): Boolean {
        return !extensionId.isNullOrBlank()
    }

    /**
     * 현재 설정을 파일에 저장합니다.
     */
    private fun saveConfiguration() {
        try {
            val properties = Properties()
            currentExtensionId?.let { properties.setProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY, it) }

            logger.info("설정 파일에 저장 중: ${configFile.absolutePath}")
            logger.info("설정 내용: ${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}=$currentExtensionId")
            
            ConfigFileUtils.saveMainConfig(properties)
            
            // 파일이 제대로 생성되고 내용이 저장되었는지 확인합니다.
            if (configFile.exists()) {
                val savedProperties = Properties()
                savedProperties.load(configFile.inputStream())
                val savedExtensionId = savedProperties.getProperty(PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY)
                logger.info("설정 저장 성공. 파일 내용: ${PluginConstants.ConfigFiles.EXTENSION_TYPE_KEY}=$savedExtensionId")
            } else {
                logger.error("저장 작업 후 설정 파일이 생성되지 않았습니다.")
            }
        } catch (e: Exception) {
            logger.error("설정 저장 실패", e)
            throw e // 호출자에게 실패를 알리기 위해 예외를 다시 던집니다.
        }
    }

    /**
     * 설정 파일을 다시 로드합니다.
     */
    fun reloadConfiguration() {
        logger.info("설정 다시 로드 중")
        loadConfiguration()
    }
    
    /**
     * 설정 파일 변경을 확인하고, 변경되었으면 다시 로드합니다.
     */
    fun checkConfigurationChange() {
        try {
            if (configFile.exists()) {
                val lastModified = configFile.lastModified()
                // 간단한 변경 감지 - 파일 워처(watcher)로 개선될 수 있습니다.
                if (lastModified > (System.currentTimeMillis() - 5000)) { // 최근 5초 내에 수정되었는지 확인
                    logger.info("설정 파일 변경 감지, 다시 로드 중...")
                    reloadConfiguration()
                }
            }
        } catch (e: Exception) {
            logger.warn("설정 변경 확인 중 오류 발생", e)
        }
    }
    
    /**
     * 설정 파일의 절대 경로를 반환합니다.
     */
    fun getConfigurationFilePath(): String {
        return configFile.absolutePath
    }
    
    /**
     * 현재 활성화된 확장의 ID를 반환합니다.
     */
    fun getCurrentExtensionId(): String? {
        return currentExtensionId
    }

    /**
     * 현재 활성화될 확장의 ID를 설정하고, 설정 파일에 저장한 후 다시 로드합니다.
     * @param extensionId 새로 설정할 확장의 ID
     */
    fun setCurrentExtensionId(extensionId: String) {
        logger.info("현재 확장 ID를 다음으로 설정: $extensionId")
        currentExtensionId = extensionId
        
        saveConfiguration() // 설정 파일에 저장
        reloadConfiguration() // 저장된 설정을 다시 로드하여 유효성 검사
        
        logger.info("확장 ID 설정 및 설정 다시 로드 완료: $extensionId, 유효함: $isConfigurationValid")
    }

    /**
     * 특정 확장에 대한 설정 맵을 가져옵니다.
     * @param extensionId 설정을 가져올 확장의 ID
     * @return 확장 설정을 담은 Map
     */
    fun getExtensionConfiguration(extensionId: String): Map<String, String> {
        return try {
            if (ConfigFileUtils.extensionConfigExists(extensionId)) {
                val properties = ConfigFileUtils.loadExtensionConfig(extensionId)
                properties.stringPropertyNames().associateWith { properties.getProperty(it) }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            logger.warn("확장 설정 로드 실패: $extensionId", e)
            emptyMap()
        }
    }

    /**
     * 특정 확장에 대한 설정을 파일에 저장합니다.
     * @param extensionId 설정을 저장할 확장의 ID
     * @param config 저장할 설정 맵
     */
    fun setExtensionConfiguration(extensionId: String, config: Map<String, String>) {
        try {
            val properties = Properties()
            config.forEach { (key, value) ->
                properties.setProperty(key, value)
            }

            ConfigFileUtils.saveExtensionConfig(extensionId, properties)
            logger.info("확장 설정 저장 완료: $extensionId")
        } catch (e: Exception) {
            logger.warn("확장 설정 저장 실패: $extensionId", e)
        }
    }

    /**
     * 사용 가능한 모든 확장의 설정을 가져옵니다.
     * @return 확장 ID를 키로, 확장 설정을 값으로 하는 Map
     */
    fun getAllExtensionConfigurations(): Map<String, Map<String, String>> {
        val configs = mutableMapOf<String, Map<String, String>>()

        try {
            val extensionIds = ConfigFileUtils.listExtensionConfigFiles()
            extensionIds.forEach { extensionId ->
                val config = getExtensionConfiguration(extensionId)
                if (config.isNotEmpty()) {
                    configs[extensionId] = config
                }
            }
        } catch (e: Exception) {
            logger.warn("모든 확장 설정 가져오기 실패", e)
        }

        return configs
    }

    /**
     * 기본 설정 파일을 생성합니다. (파일이 존재하지 않을 경우)
     */
    fun createDefaultConfiguration() {
        try {
            if (!ConfigFileUtils.mainConfigExists()) {
                ConfigFileUtils.createDefaultMainConfig()
                logger.info("기본 설정 파일 생성 완료: ${configFile.absolutePath}")
                reloadConfiguration() // 생성 후 설정 다시 로드
            }
        } catch (e: Exception) {
            logger.error("기본 설정 생성 실패", e)
        }
    }

    /**
     * 설정 관리자를 해제하고 리소스를 정리합니다.
     */
    fun dispose() {
        logger.info("확장 설정 관리자 해제 중")
        saveConfiguration() // 종료 시 현재 설정을 저장합니다.
    }
}
