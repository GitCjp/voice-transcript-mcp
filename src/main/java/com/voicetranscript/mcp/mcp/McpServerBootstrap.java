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

    private void processMessage(JsonNode message, BufferedWriter writer) throws IOException {
        String method = message.has("method") ? message.get("method").asText() : null;
        JsonNode id = message.get("id");

        if (method == null) {
            return;
        }

        switch (method) {
            case "initialize" -> handleInitialize(id, message.get("params"), writer);
            case "notifications/initialized" -> log.info("Client initialized");
            case "ping" -> sendResponse(id, mapper.createObjectNode(), writer);
            case "tools/list" -> handleToolsList(id, writer);
            case "tools/call" -> handleToolsCall(id, message.get("params"), writer);
            default -> sendError(id, -32601, "Method not found: " + method, writer);
        }
    }

    private void handleInitialize(JsonNode id, JsonNode params, BufferedWriter writer) throws IOException {
        int clientVersion = 0;
        if (params != null && params.has("protocolVersion")) {
            try {
                String ver = params.get("protocolVersion").asText();
                clientVersion = Integer.parseInt(ver.replace("-", "").replace(".", "").substring(0, 2));
            } catch (Exception ignored) {
            }
        }

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
        log.info("MCP initialization completed (protocolVersion=2024-11-05)");
    }

    private void handleToolsList(JsonNode id, BufferedWriter writer) throws IOException {
        ObjectNode result = mapper.createObjectNode();
        var tools = mapper.createArrayNode();
        tools.add(transcribeTool.getToolDefinition());
        result.set("tools", tools);

        sendResponse(id, result, writer);
        log.info("Tool list requested and returned");
    }

    private void handleToolsCall(JsonNode id, JsonNode params, BufferedWriter writer) throws IOException {
        String name = params != null && params.has("name") ? params.get("name").asText() : "";
        JsonNode arguments = params != null ? params.get("arguments") : null;

        if (!"transcribe_audio".equals(name)) {
            sendError(id, -32601, "Tool not found: " + name, writer);
            return;
        }

        String audioPath = (arguments != null && arguments.has("audio_path"))
                ? arguments.get("audio_path").asText()
                : "";

        TranscribeAudioTool.ToolResult toolResult = transcribeTool.execute(audioPath);

        ObjectNode result = mapper.createObjectNode();
        result.set("content", toolResult.content());
        if (toolResult.isError()) {
            result.put("isError", true);
        }

        sendResponse(id, result, writer);
        log.info("Tool call completed: transcribe_audio, success={}", !toolResult.isError());
    }

    private void sendResponse(JsonNode id, JsonNode result, BufferedWriter writer) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        writeLine(response, writer);
    }

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

    private void writeLine(ObjectNode message, BufferedWriter writer) throws IOException {
        String json = mapper.writeValueAsString(message);
        writer.write(json);
        writer.newLine();
        writer.flush();
    }
}
