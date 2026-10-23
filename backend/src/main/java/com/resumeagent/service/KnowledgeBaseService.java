package com.resumeagent.service;

import com.resumeagent.dto.IngestRequest;
import com.resumeagent.dto.IngestResponse;
import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.dto.KnowledgeSearchResponse;
import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.KnowledgeChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 内存向量知识库。
 *
 * 性能设计：
 * 1. ingest 时预计算归一化文本与 token 集合，检索打分不再重复构建字符串；
 * 2. 批量写入（整体替换不可变快照），避免 CopyOnWriteArrayList 逐条 add 的 O(n²) 拷贝；
 * 3. 倒排索引粗排 + 向量/词汇精排的两阶段检索，避免每次 search 全量扫描。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 用户档案（激活模板）在知识库中的固定 sourceName */
    public static final String PROFILE_SOURCE_NAME = "我的档案";

    private static final double MIN_SCORE = 0.1;
    private static final int DEFAULT_LIMIT = 5;
    private static final int LISTING_LIMIT = 15;
    /** 两阶段检索：倒排索引粗排的候选上限 */
    private static final int MAX_CANDIDATES = 200;
    /** IDF 关键词抽取：出现在超过该比例 chunk 中的 token 视为高频停用词 */
    private static final double STOPWORD_DOC_RATIO = 0.3;

    private final EmbeddingService embeddingService;
    private final ResumeAgentProperties properties;

    private final Object writeLock = new Object();
    /** 不可变快照，读路径无锁遍历；写路径在 writeLock 内整体替换 */
    private volatile List<StoredChunk> chunks = List.of();
    /** 倒排索引：token → chunk 下标数组，与 chunks 快照同步整体替换 */
    private volatile Map<String, int[]> postings = Map.of();

    @Autowired
    public KnowledgeBaseService(EmbeddingService embeddingService, ResumeAgentProperties properties) {
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public KnowledgeBaseService(EmbeddingService embeddingService) {
        this(embeddingService, new ResumeAgentProperties());
    }

    public IngestResponse ingest(IngestRequest request) {
        List<StoredChunk> added = buildStoredChunks(List.of(request));
        publishAdded(added);
        return new IngestResponse(true, "已写入知识库", added.size());
    }

    public IngestResponse ingestAll(List<IngestRequest> requests) {
        List<StoredChunk> added = buildStoredChunks(requests);
        if (!added.isEmpty()) {
            publishAdded(added);
        }
        return new IngestResponse(true, "已批量写入知识库", added.size());
    }

    private List<StoredChunk> buildStoredChunks(List<IngestRequest> requests) {
        ArrayList<StoredChunk> added = new ArrayList<>();
        for (IngestRequest request : requests) {
            List<String> splitChunks = splitContent(request.content());
            added.ensureCapacity(added.size() + splitChunks.size());
            for (String chunk : splitChunks) {
                String searchableText = request.sourceName() + " " + request.sourceType() + " " + chunk;
                String normalizedText = normalize(searchableText);
                added.add(new StoredChunk(
                        new KnowledgeChunk(request.sourceName(), request.sourceType(), chunk),
                        embeddingService.embed(searchableText),
                        normalizedText,
                        new LinkedHashSet<>(tokensOf(normalizedText))
                ));
            }
        }
        return added;
    }

    private void publishAdded(List<StoredChunk> added) {
        if (added.isEmpty()) {
            return;
        }
        while (true) {
            List<StoredChunk> base = chunks;
            List<StoredChunk> merged = new ArrayList<>(base.size() + added.size());
            merged.addAll(base);
            merged.addAll(added);
            Map<String, int[]> rebuiltPostings = buildPostings(merged);
            synchronized (writeLock) {
                if (chunks != base) {
                    continue;
                }
                chunks = List.copyOf(merged);
                postings = rebuiltPostings;
                logIndexSize();
                return;
            }
        }
    }

    private void logIndexSize() {
        int count = chunks.size();
        if (count >= Math.max(1, properties.getIndex().getWarnChunkCount())) {
            LOGGER.warn("知识库 chunk 数量达到性能告警阈值: count={}", count);
        }
    }

    public KnowledgeSearchResponse search(String query) {
        double[] queryEmbedding = embeddingService.embed(query);
        String normalizedQuery = normalize(query);
        boolean isListingQuery = isListingQuery(normalizedQuery);

        List<KnowledgeSearchItem> items = candidates(query).stream()
                .map(chunk -> new ScoredChunk(chunk.chunk(), score(normalizedQuery, queryEmbedding, chunk, isListingQuery)))
                .filter(scoredChunk -> scoredChunk.score() > MIN_SCORE)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(isListingQuery ? LISTING_LIMIT : DEFAULT_LIMIT)
                .map(scoredChunk -> new KnowledgeSearchItem(
                        scoredChunk.chunk().sourceName(),
                        scoredChunk.chunk().sourceType(),
                        scoredChunk.chunk().content(),
                        Math.round(scoredChunk.score() * 100.0) / 100.0))
                .toList();
        return new KnowledgeSearchResponse(items);
    }

    public int chunkCount() {
        return chunks.size();
    }

    /**
     * 按 sourceName 精确删除知识块（用于档案模板切换时整体替换）。
     */
    public void removeBySourceName(String sourceName) {
        while (true) {
            List<StoredChunk> base = chunks;
            List<StoredChunk> remaining = new ArrayList<>();
            for (StoredChunk chunk : base) {
                if (!chunk.chunk().sourceName().equals(sourceName)) {
                    remaining.add(chunk);
                }
            }
            if (remaining.size() == base.size()) {
                return;
            }
            Map<String, int[]> rebuiltPostings = buildPostings(remaining);
            synchronized (writeLock) {
                if (chunks != base) {
                    continue;
                }
                chunks = List.copyOf(remaining);
                postings = rebuiltPostings;
                logIndexSize();
                return;
            }
        }
    }

    /**
     * 按 sourceName 模糊匹配查找知识块。
     * 用于精确查找特定文件（如某项目文档）的内容。
     */
    public List<KnowledgeSearchItem> searchBySourceName(String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return List.of();
        }
        List<KnowledgeSearchItem> result = new ArrayList<>();
        for (StoredChunk chunk : chunks) {
            if (normalize(chunk.chunk().sourceName()).contains(normalizedKeyword)) {
                result.add(new KnowledgeSearchItem(
                        chunk.chunk().sourceName(),
                        chunk.chunk().sourceType(),
                        chunk.chunk().content(),
                        1.0));
            }
        }
        return result;
    }

    /**
     * 返回知识库中所有 distinct sourceName。
     * 供上层做动态检索路由（如项目名匹配），避免把项目名硬编码进检索逻辑。
     */
    public List<String> sourceNames() {
        Set<String> names = new LinkedHashSet<>();
        for (StoredChunk chunk : chunks) {
            names.add(chunk.chunk().sourceName());
        }
        return List.copyOf(names);
    }

    /**
     * 按 sourceType 检索，每个 sourceName 最多返回 maxPerSource 个块。
     * 用于获取某类资料的整体概览（如所有项目文档的开头段落）。
     */
    public List<KnowledgeSearchItem> searchBySourceType(String sourceType, int maxPerSource) {
        Map<String, Integer> perSourceCount = new LinkedHashMap<>();
        List<KnowledgeSearchItem> items = new ArrayList<>();
        for (StoredChunk chunk : chunks) {
            if (!sourceType.equals(chunk.chunk().sourceType())) {
                continue;
            }
            String name = chunk.chunk().sourceName();
            int used = perSourceCount.getOrDefault(name, 0);
            if (used >= maxPerSource) {
                continue;
            }
            perSourceCount.put(name, used + 1);
            items.add(new KnowledgeSearchItem(name, chunk.chunk().sourceType(), chunk.chunk().content(), 1.0));
        }
        return items;
    }

    /**
     * 基于知识库词频（IDF）从文本中抽取高信息量关键词。
     * 完全由知识库数据驱动：出现在越多 chunk 中的词权重越低，替代硬编码关键词表。
     */
    public List<String> extractKeywords(String text, int limit) {
        if (text == null || text.isBlank() || limit <= 0) {
            return List.of();
        }
        List<StoredChunk> snapshot = chunks;
        Map<String, int[]> index = postings;
        if (snapshot.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> termFreq = new LinkedHashMap<>();
        for (String token : tokensOf(normalize(text))) {
            termFreq.merge(token, 1, Integer::sum);
        }

        record ScoredTerm(String term, double score) {
        }
        List<ScoredTerm> scored = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int[] docs = index.get(term);
            if (docs == null || docs.length == 0) {
                continue;
            }
            double docRatio = (double) docs.length / snapshot.size();
            if (docRatio > STOPWORD_DOC_RATIO) {
                continue;
            }
            double idf = 1.0 - docRatio;
            scored.add(new ScoredTerm(term, entry.getValue() * idf));
        }
        scored.sort(Comparator.comparingDouble(ScoredTerm::score).reversed().thenComparing(ScoredTerm::term));
        return scored.stream().limit(limit).map(ScoredTerm::term).toList();
    }

    /**
     * 两阶段检索第一阶段：倒排索引粗排。
     * 用 query 的 token（含中文 2-gram）命中的 chunk 作为候选，按命中 token 数排序；
     * 无任何词元重叠时返回空——宁可不召回，也不把余弦噪声放进上下文（防幻觉优先）。
     */
    private List<StoredChunk> candidates(String query) {
        List<StoredChunk> snapshot = chunks;
        Map<String, int[]> index = postings;
        List<String> queryTokens = tokensOf(normalize(query));
        if (queryTokens.isEmpty() || index.isEmpty()) {
            return List.of();
        }

        Map<Integer, Integer> overlapCount = new HashMap<>();
        for (String token : queryTokens) {
            int[] chunkIndexes = index.get(token);
            if (chunkIndexes == null) {
                continue;
            }
            for (int chunkIndex : chunkIndexes) {
                overlapCount.merge(chunkIndex, 1, Integer::sum);
            }
        }
        if (overlapCount.isEmpty()) {
            return List.of();
        }

        return overlapCount.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparingInt(Map.Entry::getKey))
                .limit(MAX_CANDIDATES)
                .map(entry -> snapshot.get(entry.getKey()))
                .toList();
    }

    private Map<String, int[]> buildPostings(List<StoredChunk> allChunks) {
        Map<String, List<Integer>> mutable = new LinkedHashMap<>();
        for (int chunkIndex = 0; chunkIndex < allChunks.size(); chunkIndex++) {
            for (String token : allChunks.get(chunkIndex).tokens()) {
                mutable.computeIfAbsent(token, key -> new ArrayList<>()).add(chunkIndex);
            }
        }
        Map<String, int[]> result = new HashMap<>(mutable.size() * 2);
        for (Map.Entry<String, List<Integer>> entry : mutable.entrySet()) {
            int[] indexes = new int[entry.getValue().size()];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = entry.getValue().get(i);
            }
            result.put(entry.getKey(), indexes);
        }
        return result;
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

    /** 打分公式（保持稳定）：向量余弦 + 词汇增强 + 项目名匹配加权 + 列举类加权 */
    private double score(String query, double[] queryEmbedding, StoredChunk chunk, boolean isListingQuery) {
        double cosineScore = embeddingService.cosineSimilarity(queryEmbedding, chunk.embedding());
        String searchableText = chunk.normalizedText();
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

    /**
     * 提取倒排索引 token：ASCII/数字词元（≥2 字符）+ 中文连续片段及其 2-gram。
     * 与 EmbeddingService 的 CJK n-gram 思路一致，保证中文查询能命中索引。
     */
    static List<String> tokensOf(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (isCjk(character)) {
                flushAscii(ascii, tokens);
                cjk.append(character);
            } else if (Character.isLetterOrDigit(character)) {
                flushCjk(cjk, tokens);
                ascii.append(character);
            } else {
                flushAscii(ascii, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushAscii(ascii, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushAscii(StringBuilder builder, List<String> tokens) {
        if (builder.length() >= 2) {
            tokens.add(builder.toString());
        }
        builder.setLength(0);
    }

    private static void flushCjk(StringBuilder builder, List<String> tokens) {
        if (builder.length() == 0) {
            return;
        }
        String run = builder.toString();
        tokens.add(run);
        for (int i = 0; i + 1 < run.length(); i++) {
            tokens.add(run.substring(i, i + 2));
        }
        builder.setLength(0);
    }

    private static boolean isCjk(char character) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(character);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase();
    }

    /** normalizedText 的去重 token 集合（保证倒排索引 df 按文档计数） */
    private record StoredChunk(KnowledgeChunk chunk, double[] embedding, String normalizedText, Set<String> tokens) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
