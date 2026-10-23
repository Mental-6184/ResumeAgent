package com.resumeagent.config;

import com.resumeagent.service.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final ResumeAgentProperties properties;
    private final RequestRateLimiter limiter;

    public RateLimitInterceptor(ResumeAgentProperties properties, RequestRateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.getRateLimit().isEnabled() || !isLlmEndpoint(request)) {
            return true;
        }
        String clientKey = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (limiter.allow(clientKey, properties.getRateLimit().getRequestsPerMinute())) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        return false;
    }

    private boolean isLlmEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("POST".equalsIgnoreCase(request.getMethod()) && "/api/chat".equals(path))
                || "/api/chat/stream".equals(path)
                || "/api/techguide/stream".equals(path)
                || path.startsWith("/api/agent/");
    }
}
