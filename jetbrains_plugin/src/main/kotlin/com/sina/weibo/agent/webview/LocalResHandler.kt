// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.webview

import com.intellij.openapi.diagnostic.Logger
import io.ktor.http.*
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.File


/**
 * 로컬 리소스 요청을 처리하는 `CefResourceRequestHandlerAdapter` 구현체입니다.
 * WebView에서 로컬 파일 시스템의 리소스(HTML, CSS, JS 등)를 요청할 때 이를 가로채어
 * `LocalCefResHandle`을 통해 실제 파일 내용을 제공합니다.
 *
 * @param resourcePath 리소스 파일의 기본 경로
 * @param request 원본 `CefRequest` 객체
 */
class LocalResHandler(val resourcePath: String, val request: CefRequest?) : CefResourceRequestHandlerAdapter() {

    /**
     * `CefResourceHandler`를 반환하여 실제 리소스 처리 로직을 제공합니다.
     */
    override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): CefResourceHandler {
        return LocalCefResHandle(resourcePath, request)
    }

}

/**
 * 로컬 리소스 요청을 처리하는 `CefResourceHandler` 구현체입니다.
 * WebView에서 요청한 로컬 파일의 내용을 읽어 응답으로 제공합니다.
 *
 * @param resourceBasePath 리소스 파일의 기본 경로 (예: 확장자의 `webview-ui/build` 디렉터리)
 * @param request 원본 `CefRequest` 객체
 */
class LocalCefResHandle(val resourceBasePath: String, val request: CefRequest?) : CefResourceHandler {
    private val logger = Logger.getInstance(LocalCefResHandle::class.java)

    private var file: File? = null // 요청된 파일 객체
    private var fileContent: ByteArray? = null // 파일 내용 (바이트 배열)
    private var offset = 0 // 현재 읽은 오프셋

    init {
        logger.info("=== LocalCefResHandle 초기화 시작 ===")
        logger.info("리소스 기본 경로: $resourceBasePath")
        logger.info("요청 URL: ${request?.url}")
        
        // 요청 URL에서 호스트와 쿼리 파라미터를 제거하고 경로만 추출합니다.
        // 예: "http://localhost:63342/index.html?param=value" -> "index.html"
        val requestPath = request?.url?.decodeURLPart()?.replace("http://localhost:", "")?.substringAfter("/")?.substringBefore("?")
        logger.info("추출된 요청 경로: $requestPath")
        
        requestPath?.let {
            // 요청 경로를 기반으로 실제 파일 경로를 구성합니다.
            val filePath = if (requestPath.isEmpty()) {
                "$resourceBasePath/index.html" // 경로가 비어있으면 기본적으로 index.html을 요청한 것으로 간주
            } else {
                "$resourceBasePath/$requestPath"
            }
            logger.info("구성된 파일 경로: $filePath")
            
            file = File(filePath) // 파일 객체 생성
            logger.info("파일 객체 생성됨: $file")

            // 파일이 존재하고 일반 파일인 경우 내용을 읽어옵니다.
            if (file!!.exists() && file!!.isFile) {
                try {
                    fileContent = file!!.readBytes() // 파일 내용을 바이트 배열로 읽기
                    logger.info("파일 내용 로드 성공, 크기: ${fileContent?.size} 바이트")
                } catch (e: Exception) {
                    logger.warn("파일 내용을 가져올 수 없음, 오류: $e")
                    file = null
                    fileContent = null
                }
            } else {
                logger.warn("파일이 존재하지 않거나 파일이 아님: exists=${file?.exists()}, isFile=${file?.isFile}")
                file = null
                fileContent = null
            }
            logger.info("최종 상태: file=$file, exists=${file?.exists()}, content size=${fileContent?.size}")
        }
        logger.info("=== LocalCefResHandle 초기화 종료 ===")
    }

    /**
     * 요청을 처리합니다. (현재는 `callback.Continue()`만 호출)
     */
    override fun processRequest(p0: CefRequest?, callback: CefCallback?): Boolean {
        callback?.Continue() // 요청 처리를 계속 진행하도록 알립니다.
        return true
    }

    /**
     * 파일 경로에 따라 MIME 타입을 결정합니다.
     * @param filePath 파일 경로
     * @return 해당 파일의 MIME 타입 문자열
     */
    fun getMimeTypeForFile(filePath: String): String {
        return when {
            filePath.endsWith(".html", true) -> "text/html"
            filePath.endsWith(".css", true) -> "text/css"
            filePath.endsWith(".js", true) -> "application/javascript"
            filePath.endsWith(".json", true) -> "application/json"
            filePath.endsWith(".png", true) -> "image/png"
            filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
            filePath.endsWith(".gif", true) -> "image/gif"
            filePath.endsWith(".svg", true) -> "image/svg+xml"
            filePath.endsWith(".woff", true) -> "font/woff"
            filePath.endsWith(".woff2", true) -> "font/woff2"
            filePath.endsWith(".ttf", true) -> "font/ttf"
            filePath.endsWith(".eot", true) -> "application/vnd.ms-fontobject"
            filePath.endsWith(".otf", true) -> "font/otf"
            else -> "application/octet-stream" // 기본값
        }
    }

    /**
     * 응답 헤더를 설정합니다.
     * 파일 내용이 있으면 200 OK와 함께 MIME 타입 및 Content-Length를 설정합니다.
     */
    override fun getResponseHeaders(resp: CefResponse?, p1: IntRef?, p2: StringRef?) {
        if (fileContent == null) {
            resp?.status = 404
            resp?.statusText = "Not Found"
            return
        }

        resp?.status = 200
        resp?.statusText = "OK"
        resp?.mimeType = getMimeTypeForFile(file?.name ?: "index.html")
        resp?.setHeaderByName("Content-Length", fileContent!!.size.toString(), true)
    }

    /**
     * 응답 본문 데이터를 읽습니다.
     * 요청된 바이트 수만큼 파일 내용을 `dataOut` 버퍼에 복사합니다.
     */
    override fun readResponse(dataOut: ByteArray?, bytesToRead: Int, bytesRead: IntRef?, callback: CefCallback?): Boolean {
        if (fileContent == null || dataOut == null || bytesRead == null) {
            return false
        }

        val remaining = fileContent!!.size - offset // 남은 데이터 크기
        if (remaining <= 0) {
            return false // 더 이상 읽을 데이터가 없음
        }

        val readSize = minOf(bytesToRead, remaining) // 실제로 읽을 크기
        System.arraycopy(fileContent, offset, dataOut, 0, readSize) // 데이터 복사
        offset += readSize // 오프셋 업데이트
        bytesRead.set(readSize) // 읽은 바이트 수 설정

        return offset <= fileContent!!.size // 아직 읽을 데이터가 남아 있으면 true 반환
    }

    /**
     * 리소스 처리가 취소되었을 때 호출됩니다.
     * 내부 상태를 초기화합니다.
     */
    override fun cancel() {
        file = null
        fileContent = null
        offset = 0
    }

}
