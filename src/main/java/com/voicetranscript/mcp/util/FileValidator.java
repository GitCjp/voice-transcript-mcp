package com.voicetranscript.mcp.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 音频文件校验工具。
 * 对本地音频文件路径进行多层校验，返回 null 表示通过，否则返回中文错误提示。
 */
public class FileValidator {

    // DashScope fun-asr 模型支持的常见音频格式
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "m4a", "mp3", "wav", "flac", "ogg", "aac",
            "wma", "opus", "amr", "caf", "aiff", "ape",
            "webm", "m4b", "mp4"
    );

    /**
     * 校验音频文件路径是否合法可读，且格式在支持列表中。
     *
     * @return null = 通过，否则返回中文错误提示
     */
    public static String validate(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            return "audio_path 参数为空";
        }

        Path path = Paths.get(audioPath);

        if (!Files.exists(path)) {
            return "音频文件不存在: " + audioPath;
        }

        if (!Files.isRegularFile(path)) {
            return "路径不是常规文件: " + audioPath;
        }

        if (!Files.isReadable(path)) {
            return "文件不可读: " + audioPath;
        }

        String ext = getExtension(audioPath);
        if (ext == null) {
            return "无法识别音频格式（无扩展名），仅支持 " + SUPPORTED_FORMATS;
        }
        if (!SUPPORTED_FORMATS.contains(ext)) {
            return "不支持的音频格式: ." + ext + "，仅支持 " + SUPPORTED_FORMATS;
        }

        return null;
    }

    /** 获取小写文件扩展名，无扩展名时返回 null */
    private static String getExtension(String path) {
        String lower = path.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) return null;
        return lower.substring(dot + 1);
    }
}
