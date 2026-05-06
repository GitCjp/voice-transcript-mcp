package com.voicetranscript.mcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicetranscript.mcp.asr.AsrService;
import com.voicetranscript.mcp.dto.TranscribeResponse;
import com.voicetranscript.mcp.util.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP 工具定义与调用处理。
 * 向 MCP Host 暴露 transcribe_audio 工具，接收本地音频文件路径，返回识别文本。
 */
public class TranscribeAudioTool {
    private static final Logger log = LoggerFactory.getLogger(TranscribeAudioTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AsrService asrService;

    public TranscribeAudioTool(AsrService asrService) {
        this.asrService = asrService;
    }

    /** 返回 MCP 协议的工具定义（名称、描述、入参 JSON Schema） */
    public ObjectNode getToolDefinition() {
        ObjectNode toolNode = mapper.createObjectNode();
        toolNode.put("name", "transcribe_audio");
        toolNode.put("description",
                "将本地音频文件（m4a/mp3/wav/flac/ogg/aac 等格式）转写为文本。调用阿里云 DashScope fun-asr 模型。");

        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode audioPathProp = mapper.createObjectNode();
        audioPathProp.put("type", "string");
        audioPathProp.put("description", "本地音频文件的绝对路径，支持 m4a/mp3/wav/flac/ogg/aac/wma 等常见格式");
        properties.set("audio_path", audioPathProp);
        inputSchema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("audio_path");
        inputSchema.set("required", required);

        toolNode.set("inputSchema", inputSchema);
        return toolNode;
    }

    /**
     * 执行转写工具调用。
     * 先校验文件合法性，再委托 ASR 服务转写，最后包装为 MCP 响应格式。
     */
    public ToolResult execute(String audioPath) {
        log.info("Tool called: transcribe_audio, audio_path={}", audioPath);

        // 文件校验
        String validationError = FileValidator.validate(audioPath);
        if (validationError != null) {
            log.warn("Validation failed: {}", validationError);
            return ToolResult.error(serialize(TranscribeResponse.failure(validationError, "fun-asr", "")));
        }

        // 调用 ASR 服务
        TranscribeResponse response = asrService.transcribe(audioPath);
        if (response.success()) {
            return ToolResult.success(serialize(response));
        } else {
            return ToolResult.error(serialize(response));
        }
    }

    /** 将 TranscribeResponse 序列化为 JSON 字符串 */
    private String serialize(TranscribeResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to serialize response", e);
            return "{\"success\":false,\"error_message\":\"序列化结果失败\"}";
        }
    }

    /**
     * MCP 工具调用结果。
     * @param content MCP content 数组（包含 type/text 项）
     * @param isError 是否为错误结果
     */
    public record ToolResult(ArrayNode content, boolean isError) {
        public static ToolResult success(String text) {
            return new ToolResult(buildContentArray(text), false);
        }

        public static ToolResult error(String text) {
            return new ToolResult(buildContentArray(text), true);
        }

        /** 构建 MCP 协议的 content 数组：[{"type": "text", "text": "..."}] */
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
