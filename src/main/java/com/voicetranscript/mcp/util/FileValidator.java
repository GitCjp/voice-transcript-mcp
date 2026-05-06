package com.voicetranscript.mcp.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileValidator {

    public static String validate(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return "audio_path 参数为空";
        }

        Path path = Paths.get(audioPath);

        if (!Files.exists(path)) {
            return "音频文件不存在: " + audioPath;
        }

        if (!Files.isRegularFile(path)) {
            return "路径不是文件: " + audioPath;
        }

        if (!audioPath.toLowerCase().endsWith(".m4a")) {
            return "仅支持 .m4a 格式音频文件";
        }

        if (!Files.isReadable(path)) {
            return "文件不可读: " + audioPath;
        }

        return null;
    }
}
