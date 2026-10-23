package com.resumeagent.dto;

/** 模板版本历史条目（时间戳即版本 id） */
public record ProfileVersionItem(
        long timestamp,
        String name,
        String summary
) {
}
