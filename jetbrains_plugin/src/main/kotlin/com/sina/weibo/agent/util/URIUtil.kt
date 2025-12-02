// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.util

import java.nio.file.Path
import java.nio.file.Paths

/**
 * URI 구성 요소 인터페이스입니다.
 * URI의 기본 구성 요소들을 정의합니다.
 */
interface URIComponents {
    val scheme: String      // 스키마 (예: "file", "http")
    val authority: String?  // 권한 (예: "localhost:8080")
    val path: String        // 경로 (예: "/path/to/file.txt")
    val query: String?      // 쿼리 문자열 (예: "key=value")
    val fragment: String?   // 프래그먼트 (예: "section1")
}

/**
 * Uniform Resource Identifier (URI) 클래스입니다.
 * VSCode의 URI 구현을 기반으로 하며, URI를 파싱, 생성, 조작하는 기능을 제공합니다.
 */
class URI private constructor(
    override val scheme: String,
    override val authority: String?,
    override val path: String,
    override val query: String?,
    override val fragment: String?
) : URIComponents {
    
    companion object {
        private val isWindows = System.getProperty("os.name").lowercase().contains("windows") // 현재 OS가 Windows인지 여부
        private const val SLASH = "/"
        private val EMPTY = ""
        
        // 스키마 패턴 정규식
        private val schemePattern = Regex("^\\w[\\w\\d+.-]*$")
        // 단일 슬래시로 시작하는지 확인하는 정규식
        private val singleSlashStart = Regex("^/")
        // 이중 슬래시로 시작하는지 확인하는 정규식
        private val doubleSlashStart = Regex("^//")
        
        // URI 문자열을 파싱하기 위한 정규식 (VSCode의 _regexp에 해당)
        private val uriRegex = Regex("^(([^:/?#]+?):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?")
        
        /**
         * 문자열로부터 URI를 파싱합니다.
         * @param value URI 문자열
         * @param strict 엄격 모드 (유효성 검사 수행)
         * @return 파싱된 `URI` 객체
         */
        fun parse(value: String, strict: Boolean = false): URI {
            val match = uriRegex.find(value) ?: return URI(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
            
            return URI(
                scheme = match.groups[2]?.value ?: EMPTY,
                authority = percentDecode(match.groups[4]?.value ?: EMPTY),
                path = percentDecode(match.groups[5]?.value ?: EMPTY),
                query = percentDecode(match.groups[7]?.value ?: EMPTY),
                fragment = percentDecode(match.groups[9]?.value ?: EMPTY),
                strict = strict
            )
        }
        
        /**
         * 파일 경로로부터 URI를 생성합니다.
         * @param path 파일 시스템 경로
         * @return `URI` 객체
         */
        fun file(path: String): URI {
            var normalizedPath = path
            var authority = EMPTY
            
            // Windows에서는 역슬래시를 슬래시로 변환합니다.
            if (isWindows) {
                normalizedPath = normalizedPath.replace('\\', '/')
            }
            
            // UNC 공유 경로 (예: //server/share) 처리
            if (normalizedPath.startsWith("//")) {
                val idx = normalizedPath.indexOf('/', 2)
                if (idx == -1) {
                    authority = normalizedPath.substring(2)
                    normalizedPath = SLASH
                } else {
                    authority = normalizedPath.substring(2, idx)
                    normalizedPath = normalizedPath.substring(idx) ?: SLASH
                }
            }
            
            return URI("file", authority, normalizedPath, EMPTY, EMPTY)
        }
        
        /**
         * `Path` 객체로부터 URI를 생성합니다.
         * @param path `Path` 객체
         * @return `URI` 객체
         */
        fun file(path: Path): URI {
            return file(path.toString())
        }
        
        /**
         * URI 구성 요소로부터 URI를 생성합니다.
         * @param components URI 구성 요소
         * @param strict 엄격 모드
         * @return `URI` 객체
         */
        fun from(components: URIComponents, strict: Boolean = false): URI {
            return URI(
                components.scheme,
                components.authority,
                components.path,
                components.query,
                components.fragment,
                strict
            )
        }
        
        /**
         * URI 경로와 경로 조각들을 결합하여 새로운 URI를 생성합니다.
         * @param uri 입력 URI
         * @param pathFragments 결합할 경로 조각들
         * @return 결과 `URI` 객체
         */
        fun joinPath(uri: URI, vararg pathFragments: String): URI {
            if (uri.path.isEmpty()) {
                throw IllegalArgumentException("[UriError]: 경로가 없는 URI에는 joinPath를 호출할 수 없습니다.")
            }
            
            val newPath: String = if (isWindows && uri.scheme == "file") {
                val fsPath = uriToFsPath(uri, true)
                val joinedPath = Paths.get(fsPath, *pathFragments).toString()
                file(joinedPath).path
            } else {
                // POSIX 스타일 경로 결합 사용
                val fragments = listOf(uri.path) + pathFragments
                fragments.joinToString("/").replace(Regex("/+"), "/")
            }
            
            return uri.with(path = newPath)
        }
        
        /**
         * 퍼센트 인코딩된 문자열을 디코딩합니다.
         * @param str 디코딩할 문자열
         * @return 디코딩된 문자열
         */
        private fun percentDecode(str: String): String {
            val encodedAsHex = Regex("(%[0-9A-Za-z][0-9A-Za-z])+")
            
            if (!encodedAsHex.containsMatchIn(str)) {
                return str
            }
            
            return encodedAsHex.replace(str) { match ->
                try {
                    java.net.URLDecoder.decode(match.value, "UTF-8")
                } catch (e: Exception) {
                     // 디코딩 실패 시 원본 문자열 유지
                    match.value
                }
            }
        }
    }
    
    /**
     * 주 생성자 (내부 사용).
     * URI 구성 요소를 정규화하고 유효성을 검사합니다.
     */
    private constructor(
        scheme: String,
        authority: String?,
        path: String,
        query: String?,
        fragment: String?,
        strict: Boolean
    ) : this(
        scheme = schemeFix(scheme, strict),
        authority = authority,
        path = referenceResolution(schemeFix(scheme, strict), path),
        query = query,
        fragment = fragment
    ) {
        if (strict) {
            validate() // 엄격 모드에서 유효성 검사
        }
    }
    
    /**
     * URI의 유효성을 검사합니다.
     * @throws IllegalArgumentException 유효하지 않은 URI 구성 요소가 있을 경우
     */
    private fun validate() {
        // 스키마 확인
        if (scheme.isEmpty()) {
            throw IllegalArgumentException(
                "[UriError]: 스키마가 누락되었습니다: {scheme: \"\", authority: \"$authority\", path: \"$path\", query: \"$query\", fragment: \"$fragment\"}"
            )
        }
        
        // 스키마 형식 확인
        if (!schemePattern.matches(scheme)) {
            throw IllegalArgumentException("[UriError]: 스키마에 허용되지 않는 문자가 포함되어 있습니다.")
        }
        
        // 경로 형식 확인
        if (path.isNotEmpty()) {
            if (authority?.isNotEmpty() == true) {
                if (!singleSlashStart.containsMatchIn(path)) {
                    throw IllegalArgumentException(
                        "[UriError]: URI에 권한 구성 요소가 포함된 경우, 경로 구성 요소는 비어 있거나 슬래시(\"/\") 문자로 시작해야 합니다."
                    )
                }
            } else {
                if (doubleSlashStart.containsMatchIn(path)) {
                    throw IllegalArgumentException(
                        "[UriError]: URI에 권한 구성 요소가 포함되지 않은 경우, 경로는 이중 슬래시(\"//\") 문자로 시작할 수 없습니다."
                    )
                }
            }
        }
    }
    
    /**
     * 파일 시스템 경로를 가져옵니다.
     * @return 파일 시스템 경로 문자열
     */
    val fsPath: String
        get() = uriToFsPath(this, false)
    
    /**
     * URI의 구성 요소를 수정하여 새로운 URI를 생성합니다.
     * @param scheme 새로운 스키마 (null이면 현재 스키마 유지)
     * @param authority 새로운 권한 (null이면 현재 권한 유지)
     * @param path 새로운 경로 (null이면 현재 경로 유지)
     * @param query 새로운 쿼리 (null이면 현재 쿼리 유지)
     * @param fragment 새로운 프래그먼트 (null이면 현재 프래그먼트 유지)
     * @return 새로운 `URI` 객체
     */
    fun with(
        scheme: String? = null,
        authority: String? = null,
        path: String? = null,
        query: String? = null,
        fragment: String? = null
    ): URI {
        val newScheme = scheme ?: this.scheme
        val newAuthority = authority ?: this.authority
        val newPath = path ?: this.path
        val newQuery = query ?: this.query
        val newFragment = fragment ?: this.fragment
        
        // 변경 사항이 없으면 현재 객체를 반환합니다.
        if (newScheme == this.scheme &&
            newAuthority == this.authority &&
            newPath == this.path &&
            newQuery == this.query &&
            newFragment == this.fragment
        ) {
            return this
        }
        
        return URI(newScheme, newAuthority, newPath, newQuery, newFragment)
    }
    
    /**
     * URI를 문자열로 변환합니다.
     * @return URI의 문자열 표현
     */
    override fun toString(): String {
        return asFormatted(false)
    }
    
    /**
     * URI를 형식화된 문자열로 변환합니다.
     * @param skipEncoding 인코딩을 건너뛸지 여부
     * @return 형식화된 문자열
     */
    fun toString(skipEncoding: Boolean): String {
        return asFormatted(skipEncoding)
    }
    
    /**
     * URI를 형식화된 문자열로 변환하는 내부 헬퍼 함수입니다.
     * @param skipEncoding 인코딩을 건너뛸지 여부
     * @return 형식화된 문자열
     */
    private fun asFormatted(skipEncoding: Boolean): String {
        // 인코더 함수 선택
        val encoderFn: (String, Boolean, Boolean) -> String = 
            if (!skipEncoding) ::encodeURIComponentFast else ::encodeURIComponentMinimal
        
        var res = ""
        
        // 스키마 추가
        if (scheme.isNotEmpty()) {
            res += scheme
            res += ":"
        }
        
        // 권한 또는 "file" 스키마인 경우 이중 슬래시 추가
        if (authority?.isNotEmpty() == true || scheme == "file") {
            res += SLASH
            res += SLASH
        }
        
        // 권한 추가
        if (authority?.isNotEmpty() == true) {
            // ... (권한 파싱 및 인코딩 로직)
        }
        
        // 경로 추가
        if (path.isNotEmpty()) {
            // Windows 드라이브 경로 처리
            var normalizedPath = path
            if (normalizedPath.length >= 3 && normalizedPath[0] == '/' && normalizedPath[2] == ':') {
                val code = normalizedPath[1].code
                if (code in 65..90) { // A-Z
                    normalizedPath = "/${normalizedPath[1].lowercaseChar()}:${normalizedPath.substring(3)}"
                }
            } else if (normalizedPath.length >= 2 && normalizedPath[1] == ':') {
                val code = normalizedPath[0].code
                if (code in 65..90) { // A-Z
                    normalizedPath = "${normalizedPath[0].lowercaseChar()}:${normalizedPath.substring(2)}"
                }
            }
            
            res += encoderFn(normalizedPath, true, false)
        }
        
        // 쿼리 추가
        if (query?.isNotEmpty() == true) {
            res += "?"
            res += encoderFn(query, false, false)
        }
        
        // 프래그먼트 추가
        if (fragment?.isNotEmpty() == true) {
            res += "#"
            res += encoderFn(fragment, false, false)
        }
        
        return res
    }
    
    /**
     * `equals` 메소드 오버라이드.
     * `scheme`, `path`가 같고, `authority`, `query`, `fragment`가 null 또는 빈 문자열인 경우 동일하게 처리합니다.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is URI) return false
        
        if (scheme != other.scheme) return false
        if (path != other.path) return false
        
        // authority: null과 빈 문자열을 동일하게 처리
        if ((authority == null || authority.isEmpty()) &&
            (other.authority == null || other.authority.isEmpty())) {
             // 둘 다 null 또는 빈 문자열이면 동일
        } else if (authority != other.authority) {
            return false
        }
        
        // query: null과 빈 문자열을 동일하게 처리
        if ((query == null || query.isEmpty()) &&
            (other.query == null || other.query.isEmpty())) {
             // 둘 다 null 또는 빈 문자열이면 동일
        } else if (query != other.query) {
            return false
        }
        
        // fragment: null과 빈 문자열을 동일하게 처리
        if ((fragment == null || fragment.isEmpty()) &&
            (other.fragment == null || other.fragment.isEmpty())) {
             // 둘 다 null 또는 빈 문자열이면 동일
        } else if (fragment != other.fragment) {
            return false
        }
        
        return true
    }
    
    /**
     * `hashCode` 메소드 오버라이드.
     * `equals` 메소드와 일관성을 유지하도록 해시 코드를 계산합니다.
     */
    override fun hashCode(): Int {
        var result = scheme.hashCode()
        if(authority != null && authority != ""){
            result = 31 * result + authority.hashCode()
        }
        result = 31 * result + path.hashCode()
        if (query != null && query != ""){
            result = 31 * result + query.hashCode()
        }
        if (fragment != null && fragment != ""){
            result = 31 * result + fragment.hashCode()
        }
        return result
    }
}

/**
 * URI의 파일 시스템 경로를 계산합니다.
 * @param uri `URI` 객체
 * @param keepDriveLetterCasing 드라이브 문자 대소문자를 유지할지 여부 (Windows 전용)
 * @return 파일 시스템 경로 문자열
 */
private fun uriToFsPath(uri: URI, keepDriveLetterCasing: Boolean): String {
    val value: String
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    
    if (uri.authority?.isNotEmpty() == true && uri.path.length > 1 && uri.scheme == "file") {
        // UNC 경로: file://shares/c$/far/boo
        value = "//${uri.authority}${uri.path}"
    } else if (
        uri.path.isNotEmpty() &&
        uri.path[0] == '/' &&
        uri.path.length >= 3 &&
        ((uri.path[1] in 'A'..'Z') || (uri.path[1] in 'a'..'z')) &&
        uri.path[2] == ':'
    ) {
        // Windows 드라이브 경로: file:///c:/far/boo
        if (!keepDriveLetterCasing) {
            value = uri.path[1].lowercaseChar() + uri.path.substring(2)
        } else {
            value = uri.path.substring(1)
        }
    } else {
        // 기타 경로
        value = uri.path
    }
    
    return if (isWindows) {
        value.replace('/', '\\') // Windows에서는 슬래시를 역슬래시로 변환
    } else {
        value
    }
}

/**
 * 스키마를 수정합니다.
 * @param scheme 원본 스키마
 * @param strict 엄격 모드
 * @return 수정된 스키마
 */
private fun schemeFix(scheme: String, strict: Boolean): String {
    return if (scheme.isEmpty() && !strict) {
        "file" // 엄격 모드가 아니면 빈 스키마를 "file"로 간주
    } else {
        scheme
    }
}

/**
 * 참조 경로를 처리합니다.
 * @param scheme 프로토콜 스키마
 * @param path 경로
 * @return 처리된 경로
 */
private fun referenceResolution(scheme: String, path: String): String {
    var result = path
    when (scheme) {
        "https", "http", "file" -> {
            if (result.isEmpty()) {
                result = "/"
            } else if (result[0] != '/') {
                result = "/$result"
            }
        }
    }
    return result
}

/**
 * URI 구성 요소를 빠르게 인코딩합니다.
 * @param uriComponent 인코딩할 URI 구성 요소
 * @param isPath 경로 구성 요소인지 여부
 * @param isAuthority 권한 구성 요소인지 여부
 * @return 인코딩된 문자열
 */
private fun encodeURIComponentFast(uriComponent: String, isPath: Boolean, isAuthority: Boolean): String {
    var result: String? = null
    var nativeEncodePos = -1
    
    for (pos in uriComponent.indices) {
        val code = uriComponent[pos].code
        
        // 인코딩이 필요 없는 문자들
        if ((code in 97..122) || // a-z
            (code in 65..90) ||  // A-Z
            (code in 48..57) ||  // 0-9
            code == 45 ||        // -
            code == 46 ||        // .
            code == 95 ||        // _
            code == 126 ||       // ~
            (isPath && code == 47) || // / (경로인 경우)
            (isAuthority && code == 91) || // [ (권한인 경우)
            (isAuthority && code == 93) || // ] (권한인 경우)
            (isAuthority && code == 58)    // : (권한인 경우)
        ) {
            // ... (인코딩 로직)
        } else {
            // 인코딩이 필요한 경우
            // ... (인코딩 로직)
        }
    }
    
    // ... (최종 인코딩 처리)
    return result ?: uriComponent
}

/**
 * URI 구성 요소를 최소한으로 인코딩합니다.
 * @param path 경로
 * @param isPath 경로 여부 (무시됨)
 * @param isAuthority 권한 여부 (무시됨)
 * @return 인코딩된 문자열
 */
private fun encodeURIComponentMinimal(path: String, isPath: Boolean = false, isAuthority: Boolean = false): String {
    var result: String? = null
    
    for (pos in path.indices) {
        val code = path[pos].code
        
        if (code == 35 || code == 63) { // # 또는 ?
            if (result == null) {
                result = path.substring(0, pos)
            }
            result += when (code) {
                35 -> "%23" // #
                63 -> "%3F" // ?
                else -> throw IllegalStateException("예상치 못한 코드: $code")
            }
        } else {
            if (result != null) {
                result += path[pos]
            }
        }
    }
    
    return result ?: path
}
