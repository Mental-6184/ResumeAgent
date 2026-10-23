package com.resumeagent.config;

import com.resumeagent.service.RequestRateLimiter;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ResumeAgentProperties properties;
    private final RequestRateLimiter rateLimiter;

    public WebConfig(ResumeAgentProperties properties, RequestRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(Arrays.stream(properties.getAllowedOrigins().split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public org.springframework.web.servlet.config.annotation.InterceptorRegistry addInterceptors(
            org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(properties, rateLimiter));
        return registry;
    }
}

