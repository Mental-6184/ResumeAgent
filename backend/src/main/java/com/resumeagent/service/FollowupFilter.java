package com.resumeagent.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式输出的 {@code <FOLLOWUP>} 块过滤器。
 *
 * TechGuide 的 LLM 回复末尾会带一个 {@code <FOLLOWUP>...</FOLLOWUP>} 追问建议块，
 * 该块需要从正文流中剔除、改以独立 SSE 事件下发。真流式场景下标签可能被增量
 * delta 切断，因此采用 hold-back 策略：
 * 1. 命中完整标签：只发出标签之前的正文，其后内容全部丢弃（含标签本身）；
 * 2. 尾部出现疑似未完整标签前缀（以 '<' 开头且是完整标签的前缀）：从 '<' 起保留待续；
 * 3. 其余情况立即发出，输出延迟最多一个字符。
 */
public class FollowupFilter {

    static final String TAG = "<FOLLOWUP>";

    private final StringBuilder pending = new StringBuilder();
    private boolean suppressed = false;

    /**
     * 推入一个增量 delta，返回当前可以安全发出的正文片段（可能为空列表）。
     */
    public List<String> push(String delta) {
        List<String> output = new ArrayList<>();
        if (delta == null || delta.isEmpty() || suppressed) {
            return output;
        }
        pending.append(delta);

        int tagIndex = pending.indexOf(TAG);
        if (tagIndex >= 0) {
            addIfPresent(output, pending.substring(0, tagIndex));
            pending.setLength(0);
            suppressed = true;
            return output;
        }

        // 尾部可能是被切断的标签前缀：从最后一个 '<' 起 hold-back
        int releaseUpTo = pending.length();
        int lastLt = pending.lastIndexOf("<");
        if (lastLt >= 0 && TAG.startsWith(pending.substring(lastLt))) {
            releaseUpTo = lastLt;
        }
        if (releaseUpTo > 0) {
            addIfPresent(output, pending.substring(0, releaseUpTo));
            pending.delete(0, releaseUpTo);
        }
        return output;
    }

    /**
     * 流结束时冲刷残留文本（未形成完整标签的尾部原样发出）。
     */
    public List<String> flush() {
        List<String> output = new ArrayList<>();
        if (!suppressed && pending.length() > 0) {
            addIfPresent(output, pending.toString());
        }
        pending.setLength(0);
        return output;
    }

    /** 是否已进入标签抑制状态（后续 delta 不再输出） */
    public boolean suppressed() {
        return suppressed;
    }

    private void addIfPresent(List<String> output, String text) {
        if (!text.isEmpty()) {
            output.add(text);
        }
    }
}
