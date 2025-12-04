// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.terminal

import com.pty4j.PtyProcess
import com.pty4j.WinSize

/**
 * `ProxyPtyProcess`의 콜백 인터페이스입니다.
 * 원시 데이터 콜백만 제공하는 간소화된 버전입니다.
 */
interface ProxyPtyProcessCallback {
    /**
     * 원시 데이터 콜백 메소드입니다.
     * @param data 원시 문자열 데이터
     * @param streamType 스트림 타입 (STDOUT: 표준 출력, STDERR: 표준 에러)
     */
    fun onRawData(data: String, streamType: String)
}

/**
 * `ProxyPtyProcess` 구현체입니다.
 * `PtyProcess`의 입출력 스트림 작업을 가로채고 원시 데이터 콜백을 제공합니다.
 * 이를 통해 터미널 프로세스의 출력을 모니터링하거나 수정할 수 있습니다.
 */
class ProxyPtyProcess(
    private val originalProcess: PtyProcess, // 프록시할 원본 `PtyProcess`
    private val callback: ProxyPtyProcessCallback? = null, // 데이터 수신 시 호출될 콜백
) : PtyProcess() { // `PtyProcess`를 상속받아 기존 메소드를 오버라이드합니다.

    // 프록시 입력 스트림 (프로세스의 표준 출력)을 생성합니다.
    private val proxyInputStream: ProxyInputStream = ProxyInputStream(
        originalProcess.inputStream, // 원본 프로세스의 표준 출력 스트림
        "STDOUT", // 스트림 타입
        callback, // 콜백
    )

    // 프록시 에러 스트림 (프로세스의 표준 에러 출력)을 생성합니다.
    private val proxyErrorStream: ProxyInputStream = ProxyInputStream(
        originalProcess.errorStream, // 원본 프로세스의 표준 에러 스트림
        "STDERR", // 스트림 타입
        callback, // 콜백
    )

    // --- `PtyProcess`의 메소드 오버라이드 ---
    // 표준 출력과 에러 스트림은 프록시 스트림을 반환하여 데이터를 가로챕니다.
    override fun getInputStream(): java.io.InputStream = proxyInputStream
    override fun getErrorStream(): java.io.InputStream = proxyErrorStream

    // 표준 입력 스트림은 원본 프로세스의 것을 그대로 사용합니다.
    override fun getOutputStream(): java.io.OutputStream = originalProcess.outputStream

    // --- 나머지 `PtyProcess` 메소드들은 원본 프로세스에 위임합니다. ---
    override fun isAlive(): Boolean = originalProcess.isAlive()
    override fun pid(): Long = originalProcess.pid()
    override fun exitValue(): Int = originalProcess.exitValue()
    override fun waitFor(): Int = originalProcess.waitFor()
    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean =
        originalProcess.waitFor(timeout, unit)
    override fun destroy() = originalProcess.destroy()
    override fun destroyForcibly(): Process = originalProcess.destroyForcibly()
    override fun info(): ProcessHandle.Info = originalProcess.info()
    override fun children(): java.util.stream.Stream<ProcessHandle> = originalProcess.children()
    override fun descendants(): java.util.stream.Stream<ProcessHandle> = originalProcess.descendants()
    override fun setWinSize(winSize: WinSize) = originalProcess.setWinSize(winSize)
    override fun toHandle(): ProcessHandle = originalProcess.toHandle()
    override fun onExit(): java.util.concurrent.CompletableFuture<Process> = originalProcess.onExit()

    // --- `PtyProcess` 고유 메소드 ---
    override fun getWinSize(): WinSize = originalProcess.winSize
    override fun isConsoleMode(): Boolean = originalProcess.isConsoleMode
}

/**
 * 프록시 입력 스트림(Proxy InputStream) 구현체입니다.
 * 원본 입력 스트림의 `read` 작업을 가로채고, 읽은 데이터를 콜백을 통해 전달합니다.
 */
class ProxyInputStream(
    private val originalStream: java.io.InputStream, // 래핑할 원본 입력 스트림
    private val streamType: String, // 스트림 타입 (STDOUT 또는 STDERR)
    private val callback: ProxyPtyProcessCallback?, // 데이터 수신 시 호출될 콜백
) : java.io.InputStream() { // `java.io.InputStream`을 상속받습니다.

    /**
     * 단일 바이트를 읽습니다.
     * @return 읽은 바이트 (0-255) 또는 스트림의 끝(-1)
     */
    override fun read(): Int {
        val result = originalStream.read()
        if (result != -1 && callback != null) {
            // 읽은 단일 바이트를 문자열로 변환하여 콜백을 호출합니다.
            val dataString = String(byteArrayOf(result.toByte()), Charsets.UTF_8)
            callback.onRawData(dataString, streamType)
        }
        return result
    }

    /**
     * 바이트 배열로 데이터를 읽습니다.
     * @param b 데이터를 저장할 바이트 배열
     * @return 읽은 바이트 수 또는 스트림의 끝(-1)
     */
    override fun read(b: ByteArray): Int {
        val result = originalStream.read(b)
        if (result > 0 && callback != null) {
            // 읽은 바이트 배열을 문자열로 변환하여 콜백을 호출합니다.
            val dataString = String(b, 0, result, Charsets.UTF_8)
            callback.onRawData(dataString, streamType)
        }
        return result
    }

    /**
     * 바이트 배열의 특정 오프셋부터 지정된 길이만큼 데이터를 읽습니다.
     * @param b 데이터를 저장할 바이트 배열
     * @param off 배열 내에서 데이터를 저장할 시작 오프셋
     * @param len 읽을 최대 바이트 수
     * @return 읽은 바이트 수 또는 스트림의 끝(-1)
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val result = originalStream.read(b, off, len)
        if (result > 0 && callback != null) {
            // 읽은 바이트 배열의 특정 부분을 문자열로 변환하여 콜백을 호출합니다.
            val dataString = String(b, off, result, Charsets.UTF_8)
            callback.onRawData(dataString, streamType)
        }
        return result
    }

    // --- 나머지 `InputStream` 메소드들은 원본 스트림에 위임합니다. ---
    override fun available(): Int = originalStream.available()
    override fun close() = originalStream.close()
    override fun mark(readlimit: Int) = originalStream.mark(readlimit)
    override fun reset() = originalStream.reset()
    override fun markSupported(): Boolean = originalStream.markSupported()
}
