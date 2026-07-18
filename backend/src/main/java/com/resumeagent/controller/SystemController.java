package com.resumeagent.controller;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.service.LlmService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ResumeAgentProperties properties;
    private final LlmService llmService;

    public SystemController(ResumeAgentProperties properties, LlmService llmService) {
        this.properties = properties;
        this.llmService = llmService;
    }

    @GetMapping("/llm-config")
    public Map<String, Object> llmConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        ResumeAgentProperties.OpenAi openai = properties.getOpenai();

        String baseUrl = openai.getBaseUrl();
        config.put("baseUrl", baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "未配置");
        config.put("model", openai.getChatModel());
        config.put("temperature", openai.getTemperature());
        config.put("maxTokens", openai.getMaxTokens());
        config.put("llmAvailable", llmService.isAvailable());

        // 掩码 api-key，只显示前 6 位和后 4 位
        String apiKey = openai.getApiKey();
        if (apiKey != null && apiKey.length() > 10) {
            config.put("apiKey", apiKey.substring(0, 6) + "..." + apiKey.substring(apiKey.length() - 4));
        } else if (apiKey != null && !apiKey.isEmpty()) {
            config.put("apiKey", "***");
        } else {
            config.put("apiKey", "未配置");
        }

        return config;
    }
}
