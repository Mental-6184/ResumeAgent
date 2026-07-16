package com.resumeagent.service;

import com.resumeagent.dto.IngestRequest;
import com.resumeagent.dto.IngestResponse;
import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.dto.KnowledgeSearchResponse;
import com.resumeagent.model.KnowledgeChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private final EmbeddingService embeddingService;
    private final List<StoredChunk> chunks = new CopyOnWriteArrayList<>();

    public KnowledgeBaseService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public IngestResponse ingest(IngestRequest request) {
        List<String> splitChunks = splitContent(request.content());
        for (String chunk : splitChunks) {
            String searchableText = request.sourceName() + " " + request.sourceType() + " " + chunk;
            chunks.add(new StoredChunk(
                    new KnowledgeChunk(request.sourceName(), request.sourceType(), chunk),
                    embeddingService.embed(searchableText)
            ));
        }
        return new IngestResponse(true, "已写入知识库", splitChunks.size());
    }

    public KnowledgeSearchResponse search(String query) {
        double[] queryEmbedding = embeddingService.embed(query);
        String normalizedQuery = normalize(query);
        boolean isListingQuery = isListingQuery(normalizedQuery);

        List<KnowledgeSearchItem> items = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk.chunk(), score(normalizedQuery, queryEmbedding, chunk, isListingQuery)))
                .filter(scoredChunk -> scoredChunk.score() > 0.1)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(isListingQuery ? 15 : 5)
                .map(scoredChunk -> new KnowledgeSearchItem(
                        scoredChunk.chunk().sourceName(),
                        scoredChunk.chunk().sourceType(),
                        scoredChunk.chunk().content(),
                        Math.round(scoredChunk.score() * 100.0) / 100.0))
                .toList();
        return new KnowledgeSearchResponse(items);
    }

    private boolean isListingQuery(String normalizedQuery) {
        return (normalizedQuery.contains("项目") || normalizedQuery.contains("经历") || normalizedQuery.contains("作品"))
                && (normalizedQuery.contains("哪些") || normalizedQuery.contains("什么") || normalizedQuery.contains("列举")
                    || normalizedQuery.contains("做过") || normalizedQuery.contains("有") || normalizedQuery.contains("所有"));
    }

    private List<String> splitContent(String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        for (String paragraph : paragraphs) {
            result.addAll(splitParagraph(paragraph.trim()));
        }
        if (result.isEmpty() && !content.isBlank()) {
            result.add(content.trim());
        }
        return result;
    }

    private List<String> splitParagraph(String paragraph) {
        if (paragraph.isBlank()) {
            return List.of();
        }
        if (paragraph.length() <= 260) {
            return List.of(paragraph);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int maxLength = 260;
        int overlap = 40;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + maxLength);
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            if (end >= paragraph.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private double score(String query, double[] queryEmbedding, StoredChunk chunk, boolean isListingQuery) {
        double cosineScore = embeddingService.cosineSimilarity(queryEmbedding, chunk.embedding());
        String searchableText = normalize(chunk.chunk().sourceName() + " " + chunk.chunk().sourceType() + " " + chunk.chunk().content());
        double lexicalBoost = 0.0;
        if (!query.isBlank() && searchableText.contains(query)) {
            lexicalBoost += 0.45;
        }
        String[] tokens = query.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (token.length() == 1) {
                // 单字符权重降低，减少噪音
                if (searchableText.contains(token)) {
                    lexicalBoost += 0.02;
                }
            } else {
                if (searchableText.contains(token)) {
                    lexicalBoost += 0.08;
                }
            }
        }

        // 项目名匹配加权
        String sourceName = normalize(chunk.chunk().sourceName());
        for (String token : tokens) {
            if (!token.isBlank() && token.length() > 1 && sourceName.contains(token)) {
                lexicalBoost += 0.15;
            }
        }

        // 列举类查询：给 project 类型的 chunk 加权
        if (isListingQuery && "project".equals(chunk.chunk().sourceType())) {
            lexicalBoost += 0.3;
        }

        return cosineScore + lexicalBoost;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase();
    }

    private record StoredChunk(KnowledgeChunk chunk, double[] embedding) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
