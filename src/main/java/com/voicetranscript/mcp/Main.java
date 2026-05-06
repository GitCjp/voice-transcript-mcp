package com.voicetranscript.mcp;

import com.voicetranscript.mcp.config.AppConfig;
import com.voicetranscript.mcp.mcp.McpServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting voice-transcript-mcp server...");

        try {
            AppConfig config = AppConfig.fromEnvironment();
            McpServerBootstrap bootstrap = new McpServerBootstrap(config);
            bootstrap.start();
        } catch (Exception e) {
            log.error("Failed to start server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
