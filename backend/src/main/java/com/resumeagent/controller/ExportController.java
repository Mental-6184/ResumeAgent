package com.resumeagent.controller;

import com.resumeagent.dto.ExportInterviewRequest;
import com.resumeagent.dto.ExportResumeRequest;
import com.resumeagent.service.ExportService;
import com.resumeagent.service.ExportedFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 导出 API：简历优化版 / 面试问答集与复盘，PDF 或 Markdown。
 * 响应头 X-Export-Format 反映实际格式（PDF 中文字体缺失时自动降级为 md）。
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/resume")
    public ResponseEntity<byte[]> exportResume(@RequestBody ExportResumeRequest request) {
        try {
            return toResponse(exportService.exportResume(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/interview")
    public ResponseEntity<byte[]> exportInterview(@RequestBody ExportInterviewRequest request) {
        try {
            return toResponse(exportService.exportInterview(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private ResponseEntity<byte[]> toResponse(ExportedFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(file.filename(), StandardCharsets.UTF_8))
                .header("X-Export-Format", file.format())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }
}
