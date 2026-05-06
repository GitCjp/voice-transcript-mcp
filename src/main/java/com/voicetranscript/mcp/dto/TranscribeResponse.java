package com.voicetranscript.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 转写结果，以 JSON 形式返回给 MCP Host。
 * 失败时 error_message 非空，success=false，text 为空字符串。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TranscribeResponse(
        boolean success,
        String text,
        String model,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("error_message") String errorMessage
) {
    /** 构建成功结果 */
    public static TranscribeResponse success(String text, String model, String requestId) {
        return new TranscribeResponse(true, text, model, requestId, null);
    }

    /** 构建失败结果 */
    public static TranscribeResponse failure(String errorMessage, String model, String requestId) {
        return new TranscribeResponse(false, "", model, requestId, errorMessage);
    }
}
