package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FollowupFilter 单测：验证跨 delta 边界的 <FOLLOWUP> 块剔除与 hold-back 语义。
 */
class FollowupFilterTest {

    @Test
    void shouldStripFollowupBlockSplitAcrossDeltas() {
        FollowupFilter filter = new FollowupFilter();
        List<String> out = new ArrayList<>();

        out.addAll(filter.push("回答正文"));
        out.addAll(filter.push("继续<FOL"));
        out.addAll(filter.push("LOWUP>"));
        out.addAll(filter.push("追问一\n追问二"));
        out.addAll(filter.push("</FOLLOWUP>"));
        out.addAll(filter.flush());

        assertThat(String.join("", out)).isEqualTo("回答正文继续");
        assertThat(filter.suppressed()).isTrue();
    }

    @Test
    void shouldReleasePlainTextImmediately() {
        FollowupFilter filter = new FollowupFilter();

        assertThat(filter.push("普通正文")).containsExactly("普通正文");
        assertThat(filter.push("a < b 中的比较符")).isNotEmpty();
        assertThat(filter.flush()).isEmpty();
        assertThat(filter.suppressed()).isFalse();
    }

    @Test
    void shouldFlushTrailingPartialTagAsPlainText() {
        FollowupFilter filter = new FollowupFilter();
        List<String> out = new ArrayList<>();

        out.addAll(filter.push("正文结尾<FOL"));

        // 未形成完整标签的尾部被 hold-back，流结束时冲刷
        assertThat(String.join("", out)).isEqualTo("正文结尾");
        out.addAll(filter.flush());
        assertThat(String.join("", out)).isEqualTo("正文结尾<FOL");
    }

    @Test
    void shouldIgnoreDeltasAfterSuppression() {
        FollowupFilter filter = new FollowupFilter();
        filter.push("正文<FOLLOWUP>问题");

        assertThat(filter.push("更多内容")).isEmpty();
        assertThat(filter.flush()).isEmpty();
    }

    @Test
    void shouldHandleEmptyAndNullDeltas() {
        FollowupFilter filter = new FollowupFilter();

        assertThat(filter.push(null)).isEmpty();
        assertThat(filter.push("")).isEmpty();
    }
}
