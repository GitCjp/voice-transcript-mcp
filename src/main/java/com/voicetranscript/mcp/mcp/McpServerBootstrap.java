package com.voicetranscript.mcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicetranscript.mcp.asr.AliyunAsrClient;
import com.voicetranscript.mcp.asr.AsrService;
import com.voicetranscript.mcp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * MCP stdio 传输层——基于 JSON-RPC 2.0 协议，通过 stdin/stdout 与 MCP Host 通信。
 * <p>
 * 支持的 MCP 方法：initialize, ping, tools/list, tools/call。
 * 所有日志输出到 stderr，确保 stdout 只走 JSON-RPC 消息。
 */
public class McpServerBootstrap {
    private static final Logger log = LoggerFactory.getLogger(McpServerBootstrap.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AppConfig config;
    private final TranscribeAudioTool transcribeTool;

    public McpServerBootstrap(AppConfig config) {
        this.config = config;
        AsrService asrService = new AliyunAsrClient(config);
        this.transcribeTool = new TranscribeAudioTool(asrService);
    }

    /**
     * 启动 MCP 服务主循环。
     * 从 stdin 逐行读取 JSON-RPC 请求，处理后通过 stdout 返回响应。
     * 当 stdin 关闭（EOF）时退出。
     */
    public void start() throws IOException {
        log.info("MCP stdio server started, waiting for JSON-RPC requests on stdin...");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                JsonNode message = mapper.readTree(line);
                processMessage(message, writer);
            } catch (Exception e) {
                log.error("Failed to process message: {}", e.getMessage());
            }
        }

        log.info("Stdin closed, shutting down");
    }

    /** 根据 MCP 方法名分发到不同 handler */
    private void processMessage(JsonNode message, BufferedWriter writer) throws IOException {
        String method = message.has("method") ? message.get("method").asText() : null;
        JsonNode id = message.get("id");

        // 非请求消息（如响应）直接忽略
        if (method == null) return;

        switch (method) {
            case "initialize" -> handleInitialize(id, message.get("params"), writer);
            case "notifications/initialized" -> log.info("Client initialized");
            case "ping" -> sendResponse(id, mapper.createObjectNode(), writer);
            case "tools/list" -> handleToolsList(id, writer);
            case "tools/call" -> handleToolsCall(id, message.get("params"), writer);
            default -> sendError(id, -32601, "Method not found: " + method, writer);
        }
    }

    /** 处理 MCP 初始化握手，返回服务端能力声明 */
    private void handleInitialize(JsonNode id, JsonNode params, BufferedWriter writer) throws IOException {
        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.set("capabilities", capabilities);
        result.put("serverName", "voice-transcript-mcp");
        result.put("serverVersion", "1.0.0");

        sendResponse(id, result, writer);
        log.info("MCP initialization completed");
    }

    /** 返回可用工具列表（当前仅 transcribe_audio） */
    private void handleToolsList(JsonNode id, BufferedWriter writer) throws IOException {
        ObjectNode result = mapper.createObjectNode();
        var tools = mapper.createArrayNode();
        tools.add(transcribeTool.getToolDefinition());
        result.set("tools", tools);

        sendResponse(id, result, writer);
        log.info("Tool list returned");
    }

    /** 处理工具调用：校验参数 → 执行转写 → 返回 MCP 格式结果 */
    private void handleToolsCall(JsonNode id, JsonNode params, BufferedWriter writer) throws IOException {
        String name = params != null && params.has("name") ? params.get("name").asText() : "";
        JsonNode arguments = params != null ? params.get("arguments") : null;

        if (!"transcribe_audio".equals(name)) {
            sendError(id, -32601, "Tool not found: " + name, writer);
            return;
        }

        // 提取 audio_path 参数，缺失时传空字符串由 FileValidator 处理
        String audioPath = (arguments != null && arguments.has("audio_path"))
                ? arguments.get("audio_path").asText()
                : "";

        TranscribeAudioTool.ToolResult toolResult = transcribeTool.execute(audioPath);

        // 包装为 MCP CallToolResult
        ObjectNode result = mapper.createObjectNode();
        result.set("content", toolResult.content());
        if (toolResult.isError()) {
            result.put("isError", true); // MCP 错误标记
        }

        sendResponse(id, result, writer);
        log.info("Tool call completed: transcribe_audio, success={}", !toolResult.isError());
    }

    // ==================== JSON-RPC 消息发送 ====================

    /** 发送成功响应 */
    private void sendResponse(JsonNode id, JsonNode result, BufferedWriter writer) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        writeLine(response, writer);
    }

    /** 发送错误响应 */
    private void sendError(JsonNode id, int code, String message, BufferedWriter writer) throws IOException {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("error", error);
        writeLine(response, writer);
    }

    /** 将 JSON 序列化为单行写入 stdout 并刷新 */
    private void writeLine(ObjectNode message, BufferedWriter writer) throws IOException {
        String json = mapper.writeValueAsString(message);
        writer.write(json);
        writer.newLine();
        writer.flush();
    }
}
