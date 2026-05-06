package com.voicetranscript.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 转写请求参数。
 *
 * @param audioPath 本地音频文件的绝对路径
 */
public record TranscribeRequest(
        @JsonProperty("audio_path") String audioPath
) {
}
