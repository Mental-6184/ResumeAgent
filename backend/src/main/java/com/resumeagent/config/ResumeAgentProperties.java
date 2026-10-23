package com.resumeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resumeagent")
public class ResumeAgentProperties {

    private final OpenAi openai = new OpenAi();
    private final Security security = new Security();
    private final Audit audit = new Audit();
    private final Stream stream = new Stream();
    private final Session session = new Session();
    private final RateLimit rateLimit = new RateLimit();
    private final Index index = new Index();
    private String adminToken = "";
    private String allowedOrigins = "http://localhost:5173";

    public OpenAi getOpenai() {
        return openai;
    }

    public Security getSecurity() {
        return security;
    }

    public Audit getAudit() {
        return audit;
    }

    public Stream getStream() {
        return stream;
    }

    public Session getSession() {
        return session;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Index getIndex() {
        return index;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public static class OpenAi {
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "";
        private double temperature = 0.2;
        private int maxTokens = 2048;
        private double topP = 0.9;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }
    }

    public static class Security {
        private boolean blockOnInjection = true;
        private boolean redactSensitive = true;

        public boolean isBlockOnInjection() {
            return blockOnInjection;
        }

        public void setBlockOnInjection(boolean blockOnInjection) {
            this.blockOnInjection = blockOnInjection;
        }

        public boolean isRedactSensitive() {
            return redactSensitive;
        }

        public void setRedactSensitive(boolean redactSensitive) {
            this.redactSensitive = redactSensitive;
        }
    }

    public static class Audit {
        private boolean enabled = true;
        private int maxEntries = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    public static class Stream {
        private long timeoutMs = 300_000L;
        private int maxConcurrent = 16;

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }

    public static class Session {
        private int maxConversations = 1000;
        private int idleTimeoutMinutes = 30;
        private int maxHistoryMessages = 20;

        public int getMaxConversations() {
            return maxConversations;
        }

        public void setMaxConversations(int maxConversations) {
            this.maxConversations = maxConversations;
        }

        public int getIdleTimeoutMinutes() {
            return idleTimeoutMinutes;
        }

        public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
        }

        public int getMaxHistoryMessages() {
            return maxHistoryMessages;
        }

        public void setMaxHistoryMessages(int maxHistoryMessages) {
            this.maxHistoryMessages = maxHistoryMessages;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }

    public static class Index {
        private boolean batchLoad = true;
        private int warnChunkCount = 10000;

        public boolean isBatchLoad() {
            return batchLoad;
        }

        public void setBatchLoad(boolean batchLoad) {
            this.batchLoad = batchLoad;
        }

        public int getWarnChunkCount() {
            return warnChunkCount;
        }

        public void setWarnChunkCount(int warnChunkCount) {
            this.warnChunkCount = warnChunkCount;
        }
    }
}

