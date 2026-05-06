package com.voicetranscript.mcp.asr;

import com.voicetranscript.mcp.dto.TranscribeResponse;

public interface AsrService {
    TranscribeResponse transcribe(String audioPath);
}
