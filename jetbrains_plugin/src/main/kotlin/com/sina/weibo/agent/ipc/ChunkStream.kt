// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc

/**
 * 청크 스트림(Chunk Stream) 클래스입니다.
 * 바이너리 데이터 청크를 버퍼링하고 처리하는 데 사용됩니다.
 * VSCode의 `ChunkStream`에 해당합니다.
 */
class ChunkStream {
    // 데이터 청크들을 저장하는 리스트
    private val chunks = mutableListOf<ByteArray>()
    
    /**
     * 스트림에 있는 데이터의 총 바이트 길이를 가져옵니다.
     */
    var byteLength: Int = 0
        private set // 외부에서는 읽기만 가능
    
    /**
     * 데이터 청크를 스트림에 추가합니다.
     * @param buff 추가할 데이터 청크
     */
    fun acceptChunk(buff: ByteArray) {
        if (buff.isEmpty()) {
            return
        }
        chunks.add(buff)
        byteLength += buff.size
    }
    
    /**
     * 지정된 바이트 수만큼 데이터를 읽습니다. (스트림에서 데이터를 제거합니다.)
     * @param byteCount 읽을 바이트 수
     * @return 읽은 데이터를 담은 바이트 배열
     */
    fun read(byteCount: Int): ByteArray {
        return _read(byteCount, true)
    }
    
    /**
     * 지정된 바이트 수만큼 데이터를 미리 봅니다. (스트림에서 데이터를 제거하지 않습니다.)
     * @param byteCount 미리 볼 바이트 수
     * @return 미리 본 데이터를 담은 바이트 배열
     */
    fun peek(byteCount: Int): ByteArray {
        return _read(byteCount, false)
    }
    
    /**
     * 내부적으로 데이터를 읽는 메소드입니다.
     * @param byteCount 읽을 바이트 수
     * @param advance 데이터를 스트림에서 제거할지 여부
     * @return 읽은 데이터를 담은 바이트 배열
     * @throws IllegalArgumentException 읽으려는 바이트 수가 스트림의 총 바이트 길이보다 클 경우
     */
    private fun _read(byteCount: Int, advance: Boolean): ByteArray {
        if (byteCount == 0) {
            return ByteArray(0)
        }
        
        if (byteCount > byteLength) {
            throw IllegalArgumentException("너무 많은 바이트를 읽을 수 없습니다!")
        }
        
        // --- 최적화된 빠른 경로 ---
        // 첫 번째 청크의 크기가 정확히 읽을 바이트 수와 같을 경우
        if (chunks[0].size == byteCount) {
            val result = chunks[0]
            if (advance) {
                chunks.removeAt(0)
                byteLength -= byteCount
            }
            return result
        }
        
        // 첫 번째 청크에 읽을 데이터가 모두 포함될 경우
        if (chunks[0].size > byteCount) {
            val firstChunk = chunks[0]
            val result = ByteArray(byteCount)
            System.arraycopy(firstChunk, 0, result, 0, byteCount)
            
            if (advance) {
                val remaining = ByteArray(firstChunk.size - byteCount)
                System.arraycopy(firstChunk, byteCount, remaining, 0, remaining.size)
                chunks[0] = remaining
                byteLength -= byteCount
            }
            
            return result
        }
        
        // --- 일반 경로 (여러 청크에 걸쳐 데이터를 읽어야 할 경우) ---
        val result = ByteArray(byteCount)
        var resultOffset = 0
        var chunkIndex = 0
        var remainingBytes = byteCount
        
        while (remainingBytes > 0) {
            val chunk = chunks[chunkIndex]
            
            if (chunk.size > remainingBytes) {
                // 현재 청크가 완전히 읽히지 않을 경우
                System.arraycopy(chunk, 0, result, resultOffset, remainingBytes)
                
                if (advance) {
                    val remaining = ByteArray(chunk.size - remainingBytes)
                    System.arraycopy(chunk, remainingBytes, remaining, 0, remaining.size)
                    chunks[chunkIndex] = remaining
                    byteLength -= remainingBytes
                }
                
                resultOffset += remainingBytes
                remainingBytes = 0
            } else {
                // 현재 청크가 완전히 읽힐 경우
                System.arraycopy(chunk, 0, result, resultOffset, chunk.size)
                resultOffset += chunk.size
                remainingBytes -= chunk.size
                
                if (advance) {
                    chunks.removeAt(chunkIndex)
                    byteLength -= chunk.size
                } else {
                    chunkIndex++
                }
            }
        }
        
        return result
    }
}
