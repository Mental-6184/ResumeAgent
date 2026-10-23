package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumeagent.config.ResumeAgentProperties;
import org.junit.jupiter.api.Test;

class ConversationStoreTest {

    @Test
    void keepsOnlyConfiguredHistoryWindow() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        properties.getSession().setMaxHistoryMessages(2);
        ConversationStore store = new ConversationStore(properties);

        store.append("chat", "session-1", "user", "one");
        store.append("chat", "session-1", "assistant", "two");
        store.append("chat", "session-1", "user", "three");

        assertThat(store.history("chat", "session-1"))
                .extracting(message -> message.content())
                .containsExactly("two", "three");
    }

    @Test
    void evictsOldestSessionWhenCapacityIsReached() throws InterruptedException {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        properties.getSession().setMaxConversations(1);
        ConversationStore store = new ConversationStore(properties);

        store.append("chat", "first", "user", "one");
        Thread.sleep(5);
        store.append("chat", "second", "user", "two");

        assertThat(store.activeCount()).isEqualTo(1);
        assertThat(store.history("chat", "second")).isNotEmpty();
    }
}
