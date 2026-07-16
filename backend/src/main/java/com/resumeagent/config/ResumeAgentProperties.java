package com.resumeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resumeagent")
public class ResumeAgentProperties {

    private final OpenAi openai = new OpenAi();
    private final Security security = new Security();
    private final Audit audit = new Audit();

    public OpenAi getOpenai() {
        return openai;
    }

    public Security getSecurity() {
        return security;
    }

    public Audit getAudit() {
        return audit;
    }

    public static class OpenAi {
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModel = "gpt-4o-mini";
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

