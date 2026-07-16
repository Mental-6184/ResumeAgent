package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.SafetyVerdict;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SafetyService {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // English: explicit instruction override attempts
            Pattern.compile("(?i)ignore\\s+(?:all\\s+)?(?:previous|prior|above|earlier)\\s+(?:instructions|prompts|rules|directives)"),
            Pattern.compile("(?i)disregard\\s+(?:all\\s+)?(?:previous|prior|above)\\s+(?:instructions|prompts|rules)"),
            Pattern.compile("(?i)reveal\\s+(?:the\\s+)?(?:system|hidden|secret)\\s+prompt"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(?:a|an|the|DAN|unrestricted)"),
            Pattern.compile("(?i)\\bjailbreak\\b"),
            // Chinese: explicit injection phrases (require full phrase, not substrings)
            Pattern.compile("忽略(?:之前|上面|以前|前面)(?:的|所有)?(?:指令|提示词|规则|指示)"),
            Pattern.compile("无视(?:之前|上面|以前|前面)(?:的|所有)?(?:指令|提示词|规则|指示)"),
            Pattern.compile("泄露(?:你的|系统的|AI的)?(?:提示词|系统提示|system\\s*prompt)"),
            Pattern.compile("重新定义(?:你的|AI的)?角色"),
            Pattern.compile("你现在是(?:一个)?(?:全新的|新的)?(?:AI|模型|助手|角色)")
    );

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("\\b1\\d{10}\\b"),
            Pattern.compile("\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b"),
            Pattern.compile("\\b\\d{17}[\\dXx]\\b")
    );

    private final ResumeAgentProperties properties;

    public SafetyService(ResumeAgentProperties properties) {
        this.properties = properties;
    }

    public SafetyVerdict inspect(String input) {
        String text = input == null ? "" : input;
        boolean injectionDetected = INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
        boolean sensitiveDetected = SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
        String sanitizedText = properties.getSecurity().isRedactSensitive() ? redactSensitive(text) : text;

        if (injectionDetected && properties.getSecurity().isBlockOnInjection()) {
            return new SafetyVerdict(false, true, sensitiveDetected, sanitizedText, "检测到潜在 Prompt Injection，已拒绝处理。");
        }
        if (sensitiveDetected) {
            return new SafetyVerdict(true, injectionDetected, true, sanitizedText, "已检测到敏感信息，已执行脱敏。");
        }
        return new SafetyVerdict(true, injectionDetected, false, sanitizedText, "通过安全检查。");
    }

    private String redactSensitive(String text) {
        String redacted = text;
        redacted = redacted.replaceAll("\\b1\\d{10}\\b", "[手机号已脱敏]");
        redacted = redacted.replaceAll("\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "[邮箱已脱敏]");
        redacted = redacted.replaceAll("\\b\\d{17}[\\dXx]\\b", "[身份证号已脱敏]");
        return redacted;
    }
}

