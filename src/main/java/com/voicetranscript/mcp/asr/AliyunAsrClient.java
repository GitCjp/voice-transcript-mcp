package com.voicetranscript.mcp.asr;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionTaskResult;
import com.alibaba.dashscope.common.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicetranscript.mcp.config.AppConfig;
import com.voicetranscript.mcp.dto.TranscribeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 阿里云 DashScope ASR 客户端实现。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>将本地音频文件上传至 DashScope 文件 API（OSS 预签名 URL）</li>
 *   <li>提交 fun-asr 异步转录任务</li>
 *   <li>轮询等待任务完成</li>
 *   <li>从转录结果 URL 获取文本</li>
 * </ol>
 */
public class AliyunAsrClient implements AsrService {
    private static final Logger log = LoggerFactory.getLogger(AliyunAsrClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String FILE_UPLOAD_URL = "https://dashscope.aliyuncs.com/api/v1/files";

    private final AppConfig config;

    public AliyunAsrClient(AppConfig config) {
        this.config = config;
    }

    @Override
    public TranscribeResponse transcribe(String audioPath) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting transcription: file={}, requestId={}", audioPath, requestId);

            // ① 上传本地文件到 DashScope，获取 OSS 预签名 URL
            String fileUrl = uploadFile(audioPath);
            log.info("File uploaded, url obtained");

            // ② 提交异步转录任务
            TranscriptionParam param = TranscriptionParam.builder()
                    .apiKey(config.apiKey())
                    .model(config.model())
                    .parameter("language_hints", new String[]{"zh", "en"})
                    .fileUrls(List.of(fileUrl))
                    .build();

            Transcription transcription = new Transcription();
            TranscriptionResult asyncResult = transcription.asyncCall(param);
            String taskId = asyncResult.getTaskId();
            log.info("Task submitted: taskId={}", taskId);

            // ③ 阻塞等待任务完成（SDK 内部自动轮询）
            TranscriptionQueryParam queryParam = TranscriptionQueryParam
                    .FromTranscriptionParam(param, taskId);
            TranscriptionResult finalResult = transcription.wait(queryParam);

            long duration = System.currentTimeMillis() - startTime;

            // 任务失败处理
            if (finalResult.getTaskStatus() != TaskStatus.SUCCEEDED) {
                log.error("Task failed: taskId={}, status={}", taskId, finalResult.getTaskStatus());
                return TranscribeResponse.failure(
                        "转录任务失败: " + finalResult.getTaskStatus(),
                        config.model(), requestId);
            }

            // ④ 从转录结果中提取文本
            String text = extractText(finalResult);

            log.info("Transcription completed: file={}, duration={}ms, textLength={}",
                    audioPath, duration, text != null ? text.length() : 0);

            if (text == null || text.isBlank()) {
                return TranscribeResponse.failure("识别结果为空", config.model(), requestId);
            }

            return TranscribeResponse.success(text, config.model(), requestId);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Transcription failed after {}ms: {}", duration, e.getMessage());
            return TranscribeResponse.failure(
                    "语音识别失败: " + toUserMessage(e), config.model(), requestId);
        }
    }

    // ==================== 文件上传 ====================

    /**
     * 两阶段获取可用 URL：
     * ① multipart 上传到 DashScope → 得到 file_id
     * ② GET 文件列表 → 根据 file_id 找到 OSS 预签名 URL
     */
    private String uploadFile(String audioPath) throws Exception {
        Path path = Path.of(audioPath);
        String filename = path.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(path);

        // ① 上传
        String boundary = "----DashScopeUpload" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, filename, fileBytes);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(FILE_UPLOAD_URL))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> uploadResp = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        if (uploadResp.statusCode() != 200) {
            throw new RuntimeException("文件上传失败: HTTP " + uploadResp.statusCode());
        }

        JsonNode uploadJson = mapper.readTree(uploadResp.body());
        JsonNode uploadedFiles = uploadJson.path("data").path("uploaded_files");
        if (!uploadedFiles.isArray() || uploadedFiles.size() == 0) {
            throw new RuntimeException("文件上传返回异常: " + uploadResp.body());
        }
        String fileId = uploadedFiles.get(0).path("file_id").asText();
        log.debug("Uploaded file: name={}, fileId={}", filename, fileId);

        // ② 查文件列表，获取 OSS 预签名 URL
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(FILE_UPLOAD_URL))
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> listResp = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
        if (listResp.statusCode() != 200) {
            throw new RuntimeException("获取文件列表失败: HTTP " + listResp.statusCode());
        }

        JsonNode listJson = mapper.readTree(listResp.body());
        JsonNode files = listJson.path("data").path("files");
        if (files.isArray()) {
            for (JsonNode f : files) {
                if (fileId.equals(f.path("file_id").asText(""))) {
                    String url = f.path("url").asText();
                    if (url != null && !url.isBlank()) {
                        log.info("Got presigned OSS URL for file_id={}", fileId);
                        return url;
                    }
                }
            }
        }

        throw new RuntimeException("无法获取上传文件的访问URL");
    }

    /** 构建 RFC 7578 multipart/form-data 请求体 */
    private byte[] buildMultipartBody(String boundary, String filename, byte[] fileBytes) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            bos.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            bos.write(fileBytes);
            String footer = "\r\n--" + boundary + "--\r\n";
            bos.write(footer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("构建 multipart 请求失败", e);
        }
    }

    // ==================== 结果提取 ====================

    /** 从转录任务结果中提取文本——先取 transcriptionUrl，再 HTTP GET 获取实际内容 */
    private String extractText(TranscriptionResult result) {
        List<TranscriptionTaskResult> taskResults = result.getResults();
        if (taskResults == null || taskResults.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (TranscriptionTaskResult taskResult : taskResults) {
            String transcriptionUrl = taskResult.getTranscriptionUrl();
            if (transcriptionUrl != null && !transcriptionUrl.isBlank()) {
                String text = fetchTranscriptionText(transcriptionUrl);
                if (text != null && !text.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
            }
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    /**
     * 从转录结果 URL 获取文本内容。
     * 返回的 JSON 可能包含 transcripts 数组、text 字段或原始文本。
     */
    private String fetchTranscriptionText(String url) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                try {
                    // 尝试从 JSON 结构中提取 transcripts[].text
                    JsonNode root = mapper.readTree(body);
                    if (root.has("transcripts") && root.get("transcripts").isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode t : root.get("transcripts")) {
                            String text = t.has("text") ? t.get("text").asText() : t.asText();
                            if (!text.isBlank()) {
                                if (!sb.isEmpty()) sb.append(" ");
                                sb.append(text);
                            }
                        }
                        return !sb.isEmpty() ? sb.toString() : body;
                    }
                    if (root.has("text")) {
                        return root.get("text").asText();
                    }
                    return body;
                } catch (Exception e) {
                    // 不是 JSON，直接返回原始文本
                    return body;
                }
            }
            log.warn("Failed to fetch transcription text: HTTP {} from {}", response.statusCode(), url);
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch transcription URL: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 错误处理 ====================

    /** 将原始异常转换为用户可读的中文错误提示 */
    private String toUserMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误，请查看日志";
        String lower = msg.toLowerCase();

        if (lower.contains("apikey") || lower.contains("鉴权") || lower.contains("unauthorized")) {
            return "API 鉴权失败，请检查 ALIBABA_CLOUD_API_KEY";
        }
        if (lower.contains("timeout")) {
            return "请求超时，请稍后重试";
        }
        if (lower.contains("model") && (lower.contains("not") || lower.contains("not found"))) {
            return "模型不存在或未开通: " + config.model();
        }
        if (lower.contains("unsupported") || lower.contains("format")) {
            return "音频格式不支持，请检查文件是否为有效的音频文件";
        }
        if (lower.contains("accessdenied")) {
            return "API 权限不足，请检查 API Key 是否开通语音识别服务";
        }
        return msg;
    }
}
