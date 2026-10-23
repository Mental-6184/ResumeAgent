package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.ConversationMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 有界、带空闲过期的内存会话存储。 */
@Service
public class ConversationStore {

    private final ResumeAgentProperties properties;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public ConversationStore(ResumeAgentProperties properties) {
        this.properties = properties;
    }

    public List<ConversationMessage> history(String namespace, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        Session session = sessions.get(key(namespace, conversationId));
        if (session == null) {
            return Collections.emptyList();
        }
        synchronized (session) {
            session.touch();
            return List.copyOf(session.messages);
        }
    }

    public void append(String namespace, String conversationId, String role, String content) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        Session session = sessions.computeIfAbsent(key(namespace, conversationId), key -> {
            evictIfNecessary();
            return new Session();
        });
        synchronized (session) {
            int maxMessages = Math.max(1, properties.getSession().getMaxHistoryMessages());
            session.messages.addLast(new ConversationMessage(role, content));
            while (session.messages.size() > maxMessages) {
                session.messages.removeFirst();
            }
            session.touch();
        }
    }

    public void clear(String namespace, String conversationId) {
        if (conversationId != null) {
            sessions.remove(key(namespace, conversationId));
        }
    }

    public int activeCount() {
        return sessions.size();
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void removeExpired() {
        long idleMillis = Duration.ofMinutes(Math.max(1, properties.getSession().getIdleTimeoutMinutes())).toMillis();
        long cutoff = System.currentTimeMillis() - idleMillis;
        sessions.entrySet().removeIf(entry -> entry.getValue().lastAccess < cutoff);
    }

    private void evictIfNecessary() {
        int maxSessions = Math.max(1, properties.getSession().getMaxConversations());
        if (sessions.size() < maxSessions) {
            return;
        }
        String oldestId = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().lastAccess < oldest) {
                oldest = entry.getValue().lastAccess;
                oldestId = entry.getKey();
            }
        }
        if (oldestId != null) {
            sessions.remove(oldestId);
        }
    }

    private String key(String namespace, String conversationId) {
        return namespace + ":" + conversationId;
    }

    private static final class Session {
        private final ArrayDeque<ConversationMessage> messages = new ArrayDeque<>();
        private volatile long lastAccess = Instant.now().toEpochMilli();

        private void touch() {
            lastAccess = System.currentTimeMillis();
        }
    }
}
