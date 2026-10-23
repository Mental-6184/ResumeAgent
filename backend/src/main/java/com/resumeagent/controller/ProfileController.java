package com.resumeagent.controller;

import com.resumeagent.dto.ProfileListResponse;
import com.resumeagent.dto.ProfileSaveRequest;
import com.resumeagent.dto.ProfileTemplateResponse;
import com.resumeagent.dto.ProfileUploadResponse;
import com.resumeagent.dto.ProfileVersionItem;
import com.resumeagent.model.ProfileTemplate;
import com.resumeagent.service.PdfTextExtractor;
import com.resumeagent.service.ProfileService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户档案模板 API：网申信息填写、简历文件上传解析、多模板管理与切换。
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final int MAX_RESUME_CHARS = 20000;

    private final ProfileService profileService;
    private final PdfTextExtractor pdfTextExtractor;

    public ProfileController(ProfileService profileService, PdfTextExtractor pdfTextExtractor) {
        this.profileService = profileService;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    @GetMapping("/templates")
    public ProfileListResponse list() {
        return profileService.list();
    }

    @PostMapping("/templates")
    public ProfileTemplateResponse create(@Valid @RequestBody ProfileSaveRequest request) {
        ProfileTemplate template = profileService.create(request);
        return toResponse(template, false);
    }

    @PutMapping("/templates/{id}")
    public ProfileTemplateResponse update(@PathVariable String id, @Valid @RequestBody ProfileSaveRequest request) {
        ProfileTemplate template = profileService.update(id, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        return toResponse(template, isActive(template));
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        boolean removed = profileService.delete(id);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
        }
        return Map.of("success", true, "activeId", currentActiveId());
    }

    @PostMapping("/templates/{id}/activate")
    public ProfileListResponse activate(@PathVariable String id) {
        profileService.activate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        return profileService.list();
    }

    // ===== 版本历史 =====

    @GetMapping("/templates/{id}/versions")
    public java.util.List<ProfileVersionItem> versions(@PathVariable String id) {
        return profileService.listVersions(id);
    }

    @GetMapping("/templates/{id}/versions/{timestamp}")
    public ProfileTemplateResponse version(@PathVariable String id, @PathVariable long timestamp) {
        ProfileTemplate template = profileService.getVersion(id, timestamp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        return toResponse(template, false);
    }

    @PostMapping("/templates/{id}/versions/{timestamp}/rollback")
    public ProfileTemplateResponse rollback(@PathVariable String id, @PathVariable long timestamp) {
        ProfileTemplate template = profileService.rollback(id, timestamp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        return toResponse(template, isActive(template));
    }

    @GetMapping("/active")
    public Map<String, Object> active() {
        return Map.of(
                "activeId", currentActiveId(),
                "template", profileService.active().map(t -> toResponse(t, true)).orElse(null));
    }

    /**
     * 上传简历文件（PDF / TXT / MD），解析为文本供前端预览编辑，确认后随模板保存。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileUploadResponse upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件为空");
        }
        String fileName = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String lower = fileName.toLowerCase();
        try {
            String text;
            if (lower.endsWith(".pdf")) {
                text = pdfTextExtractor.extract(file.getBytes());
                if (text.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "未能从 PDF 中提取到文本（可能是扫描图片版），请直接粘贴简历文本");
                }
            } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".text")) {
                text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "暂不支持该文件格式，请上传 PDF / TXT / MD 文件");
            }
            if (text.length() > MAX_RESUME_CHARS) {
                text = text.substring(0, MAX_RESUME_CHARS);
            }
            return new ProfileUploadResponse(fileName, text, text.length());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件解析失败：" + e.getMessage());
        }
    }

    private ProfileTemplateResponse toResponse(ProfileTemplate template, boolean active) {
        return new ProfileTemplateResponse(
                template.id(), template.name(), template.form(), template.resumeText(),
                template.resumeFileName(), template.createdAt(), template.updatedAt(), active);
    }

    private boolean isActive(ProfileTemplate template) {
        return template.id().equals(currentActiveId());
    }

    private String currentActiveId() {
        String activeId = profileService.list().activeId();
        return activeId == null ? "" : activeId;
    }
}
