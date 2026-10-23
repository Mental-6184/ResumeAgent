package com.resumeagent.controller;

import com.resumeagent.dto.IngestRequest;
import com.resumeagent.dto.IngestResponse;
import com.resumeagent.dto.KnowledgeDocumentInfo;
import com.resumeagent.dto.KnowledgeSaveRequest;
import com.resumeagent.dto.KnowledgeSearchResponse;
import com.resumeagent.service.KnowledgeBaseService;
import com.resumeagent.service.KnowledgeDocumentService;
import com.resumeagent.service.PdfTextExtractor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/api/rag")
public class RagController {

    private static final int MAX_DOC_CHARS = 20000;

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentService documentService;
    private final PdfTextExtractor pdfTextExtractor;

    public RagController(KnowledgeBaseService knowledgeBaseService,
                         KnowledgeDocumentService documentService,
                         PdfTextExtractor pdfTextExtractor) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    /** 入库并落盘持久化（重启后由 KnowledgeDocumentService 自动重新加载） */
    @PostMapping("/ingest")
    public IngestResponse ingest(@Valid @RequestBody IngestRequest request) {
        documentService.save(request.sourceType(), request.sourceName(), request.content());
        return new IngestResponse(true, "已写入知识库并持久化",
                knowledgeBaseService.searchBySourceName(request.sourceName()).size());
    }

    /** 粘贴文本保存文档（入库 + 落盘） */
    @PostMapping("/documents")
    public IngestResponse saveDocument(@Valid @RequestBody KnowledgeSaveRequest request) {
        documentService.save(request.sourceType(), request.sourceName(), request.content());
        return new IngestResponse(true, "已保存文档",
                knowledgeBaseService.searchBySourceName(request.sourceName()).size());
    }

    @GetMapping("/documents")
    public List<KnowledgeDocumentInfo> documents() {
        return documentService.list();
    }

    @DeleteMapping("/documents/{sourceName}")
    public Map<String, Object> deleteDocument(@PathVariable String sourceName) {
        if (documentService.isBuiltin(sourceName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内置文档不可删除");
        }
        if (!documentService.delete(sourceName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return Map.of("success", true);
    }

    /**
     * 上传文档文件（PDF / TXT / MD）：解析文本后入库并落盘。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResponse upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "sourceType", defaultValue = "project") String sourceType) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件为空");
        }
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String sourceName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String lower = fileName.toLowerCase();
        try {
            String text;
            if (lower.endsWith(".pdf")) {
                text = pdfTextExtractor.extract(file.getBytes());
                if (text.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "未能从 PDF 中提取到文本（可能是扫描图片版），请直接粘贴文本");
                }
            } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".text")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该文件格式，请上传 PDF / TXT / MD 文件");
            }
            if (text.length() > MAX_DOC_CHARS) {
                text = text.substring(0, MAX_DOC_CHARS);
            }
            documentService.save(sourceType, sourceName, text);
            return new IngestResponse(true, "已上传并入库：" + fileName,
                    knowledgeBaseService.searchBySourceName(sourceName).size());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件解析失败：" + e.getMessage());
        }
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(@RequestParam @NotBlank @Size(max = 500) String query) {
        return knowledgeBaseService.search(query);
    }
}
