package com.voicetranscript.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TranscribeResponse(
        boolean success,
        String text,
        String model,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("error_message") String errorMessage
) {
    public static TranscribeResponse success(String text, String model, String requestId) {
        return new TranscribeResponse(true, text, model, requestId, null);
    }

    public static TranscribeResponse failure(String errorMessage, String model, String requestId) {
        return new TranscribeResponse(false, "", model, requestId, errorMessage);
    }
}
