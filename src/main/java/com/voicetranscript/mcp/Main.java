package com.voicetranscript.mcp;

import com.voicetranscript.mcp.config.AppConfig;
import com.voicetranscript.mcp.mcp.McpServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序入口。
 * 读取环境变量配置，启动 MCP stdio 服务并阻塞等待 JSON-RPC 请求。
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting voice-transcript-mcp server...");

        try {
            AppConfig config = AppConfig.fromEnvironment();
            McpServerBootstrap bootstrap = new McpServerBootstrap(config);
            bootstrap.start(); // 阻塞，监听 stdin，直到 EOF 才退出
        } catch (Exception e) {
            log.error("Failed to start server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
