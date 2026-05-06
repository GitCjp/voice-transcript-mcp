package com.voicetranscript.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TranscribeRequest(
        @JsonProperty("audio_path") String audioPath
) {
}
