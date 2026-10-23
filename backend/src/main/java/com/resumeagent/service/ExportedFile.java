package com.resumeagent.service;

/**
 * 导出产物：文件名、媒体类型、字节内容。
 * format 反映实际输出格式（PDF 字体缺失时自动降级为 Markdown）。
 */
public record ExportedFile(String filename, String mediaType, String format, byte[] content) {
}
