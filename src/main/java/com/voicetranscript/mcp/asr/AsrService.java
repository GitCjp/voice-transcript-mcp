package com.voicetranscript.mcp.asr;

import com.voicetranscript.mcp.dto.TranscribeResponse;

/**
 * ASR 语音识别服务接口。
 * 提供统一的转写方法，具体实现可对接不同厂商的语音识别 API。
 */
public interface AsrService {

    /**
     * 对本地音频文件执行语音识别，返回结构化的转写结果。
     *
     * @param audioPath 本地音频文件的绝对路径
     * @return 包含 success/text/model/request_id/error_message 的结果对象
     */
    TranscribeResponse transcribe(String audioPath);
}
