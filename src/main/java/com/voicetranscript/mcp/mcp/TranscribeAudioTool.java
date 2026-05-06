package com.voicetranscript.mcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicetranscript.mcp.asr.AsrService;
import com.voicetranscript.mcp.dto.TranscribeResponse;
import com.voicetranscript.mcp.util.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranscribeAudioTool {
    private static final Logger log = LoggerFactory.getLogger(TranscribeAudioTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AsrService asrService;

    public TranscribeAudioTool(AsrService asrService) {
        this.asrService = asrService;
    }

    public ObjectNode getToolDefinition() {
        ObjectNode toolNode = mapper.createObjectNode();
        toolNode.put("name", "transcribe_audio");
        toolNode.put("description", "将本地 m4a 音频文件转写为文本");

        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode audioPathProp = mapper.createObjectNode();
        audioPathProp.put("type", "string");
        audioPathProp.put("description", "本地 m4a 音频文件的绝对路径");
        properties.set("audio_path", audioPathProp);
        inputSchema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("audio_path");
        inputSchema.set("required", required);

        toolNode.set("inputSchema", inputSchema);
        return toolNode;
    }

    public ToolResult execute(String audioPath) {
        log.info("Tool called: transcribe_audio, audio_path={}", audioPath);

        String validationError = FileValidator.validate(audioPath);
        if (validationError != null) {
            log.warn("Validation failed: {}", validationError);
            return ToolResult.error(buildContent(TranscribeResponse.failure(validationError, "Fun-ASR", "")));
        }

        TranscribeResponse response = asrService.transcribe(audioPath);
        if (response.success()) {
            return ToolResult.success(buildContent(response));
        } else {
            return ToolResult.error(buildContent(response));
        }
    }

    private String buildContent(TranscribeResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to serialize response", e);
            return "{\"success\":false,\"error_message\":\"序列化结果失败\"}";
        }
    }

    public record ToolResult(ArrayNode content, boolean isError) {
        public static ToolResult success(String text) {
            return new ToolResult(buildContentArray(text), false);
        }

        public static ToolResult error(String text) {
            return new ToolResult(buildContentArray(text), true);
        }

        private static ArrayNode buildContentArray(String text) {
            ObjectNode contentItem = mapper.createObjectNode();
            contentItem.put("type", "text");
            contentItem.put("text", text);
            ArrayNode content = mapper.createArrayNode();
            content.add(contentItem);
            return content;
        }
    }
}
