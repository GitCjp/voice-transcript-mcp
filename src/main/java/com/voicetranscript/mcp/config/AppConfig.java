package com.voicetranscript.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AppConfig(
        String apiKey,
        String model,
        String endpoint
) {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String DEFAULT_MODEL = "fun-asr";
    private static final String DASHSCOPE_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1";

    public static AppConfig fromEnvironment() {
        String apiKey = System.getenv("ALIBABA_CLOUD_API_KEY");
        String model = System.getenv("ALIBABA_CLOUD_ASR_MODEL");
        String endpoint = System.getenv("ALIBABA_CLOUD_ASR_ENDPOINT");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: ALIBABA_CLOUD_API_KEY"
            );
        }

        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
            log.info("ALIBABA_CLOUD_ASR_MODEL not set, using default: {}", DEFAULT_MODEL);
        }

        if (endpoint == null || endpoint.isBlank()) {
            endpoint = DASHSCOPE_ENDPOINT;
            log.info("ALIBABA_CLOUD_ASR_ENDPOINT not set, using default endpoint");
        }

        log.info("Configuration loaded: model={}, endpoint={}", model, endpoint);
        return new AppConfig(apiKey, model, endpoint);
    }
}
