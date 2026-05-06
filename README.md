# voice-transcript-mcp

MCP stdio 服务，将本地音频文件转写为文本。

基于 Java 21，调用阿里云 DashScope `fun-asr` 模型，自动完成文件上传 → 语音识别 → 返回文本。

支持格式：**m4a / mp3 / wav / flac / ogg / aac / wma / opus / amr / caf / aiff / ape / webm / m4b / mp4**

## 环境要求

- Java 21+
- Maven 3.8+
- [阿里云 DashScope API Key](https://bailian.console.aliyun.com/)（开通语音识别服务）

## 快速开始

### 1. 编译

```bash
git clone https://github.com/GitCjp/voice-transcript-mcp.git
cd voice-transcript-mcp
mvn clean package
```

### 2. 接入 MCP Host

#### Claude Code

项目目录下创建 `.mcp.json`（参考 `.mcp.json.example`）：

```json
{
  "mcpServers": {
    "voice-transcript": {
      "command": "java",
      "args": ["-jar", "/absolute/path/voice-transcript-mcp/target/voice-transcript-mcp-1.0.0.jar"],
      "env": {
        "ALIBABA_CLOUD_API_KEY": "你的DashScope API Key"
      }
    }
  }
}
```

`~/.claude/settings.json` 中确保开启：

```json
{
  "enableAllProjectMcpServers": true
}
```

重启 Claude Code，在对话中说：

> 帮我把 /Users/xxx/录音.m4a 转成文字

#### Codex

`~/.codex/config.toml` 中添加：

```toml
[mcp_servers.voice-transcript]
type = "stdio"
command = "java"
args = ["-jar", "/absolute/path/voice-transcript-mcp/target/voice-transcript-mcp-1.0.0.jar"]

[mcp_servers.voice-transcript.env]
ALIBABA_CLOUD_API_KEY = "你的DashScope API Key"
```

重启 Codex，在对话中明确指定工具：

> 使用 voice-transcript 的 transcribe_audio 工具，转写 /Users/xxx/录音.mp3

### 3. 效果

```
用户: 帮我把 /Users/me/Downloads/面试录音.m4a 转成文字
→ 上传文件
→ fun-asr 模型转录（约 30 秒）
→ 返回完整文本
```

## 环境变量

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `ALIBABA_CLOUD_API_KEY` | 是 | - | DashScope API Key |
| `ALIBABA_CLOUD_ASR_MODEL` | 否 | `fun-asr` | ASR 模型名 |
| `ALIBABA_CLOUD_ASR_ENDPOINT` | 否 | `https://dashscope.aliyuncs.com/api/v1` | API 端点 |

## 工具说明

### `transcribe_audio`

输入：

```json
{
  "audio_path": "/absolute/path/to/recording.mp3"
}
```

成功返回：

```json
{
  "success": true,
  "text": "这是音频识别出来的文本内容。",
  "model": "fun-asr",
  "request_id": "req-xxx"
}
```

失败返回：

```json
{
  "success": false,
  "error_message": "音频文件不存在",
  "model": "fun-asr",
  "request_id": ""
}
```

## 工作流程

1. 校验本地音频文件
2. 上传到 DashScope 文件 API
3. 提交 `fun-asr` 异步转录任务
4. 轮询等待任务完成
5. 获取转录文本并返回

## 项目结构

```
src/main/java/com/voicetranscript/mcp/
├── Main.java                    # 入口
├── config/
│   └── AppConfig.java           # 环境变量读取
├── mcp/
│   ├── McpServerBootstrap.java  # MCP stdio JSON-RPC 服务
│   └── TranscribeAudioTool.java # 工具定义与调用
├── asr/
│   ├── AsrService.java          # ASR 服务接口
│   └── AliyunAsrClient.java     # DashScope SDK 实现
├── dto/
│   ├── TranscribeRequest.java
│   └── TranscribeResponse.java
└── util/
    └── FileValidator.java       # 文件校验
```

## 注意事项

- 日志输出到 stderr，不干扰 stdout 上的 MCP 通信
- API Key 仅通过环境变量注入，不写入日志
- 支持 m4a / mp3 / wav / flac / ogg / aac 等 15 种音频格式
- 对音频时长无硬限制（10 分钟约 30 秒完成）
- 不需要手动启动 jar 进程，MCP Host 自动管理生命周期
