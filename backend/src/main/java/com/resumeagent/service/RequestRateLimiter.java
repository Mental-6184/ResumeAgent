package com.resumeagent.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RequestRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        long now = Instant.now().toEpochMilli();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= 60_000L) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void removeExpiredWindows() {
        long cutoff = Instant.now().toEpochMilli() - 120_000L;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt < cutoff);
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
