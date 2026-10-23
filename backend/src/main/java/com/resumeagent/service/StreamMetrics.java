package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

@Service
public class StreamMetrics {

    private final Semaphore slots;
    private final int capacity;

    public StreamMetrics(ResumeAgentProperties properties) {
        this.capacity = Math.max(1, properties.getStream().getMaxConcurrent());
        this.slots = new Semaphore(capacity);
    }

    public boolean tryAcquire() {
        return slots.tryAcquire();
    }

    public void release() {
        if (slots.availablePermits() < capacity) {
            slots.release();
        }
    }

    public int activeCount() {
        return capacity - slots.availablePermits();
    }
}
