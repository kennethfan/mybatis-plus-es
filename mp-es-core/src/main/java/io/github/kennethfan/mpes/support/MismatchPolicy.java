package io.github.kennethfan.mpes.support;

/**
 * 索引 mapping 与实体定义不一致时的处理策略（Q18：可配置）。
 */
public enum MismatchPolicy {

    /** 仅记录告警，不阻断启动（默认，顺应 ES 动态 mapping 的渐进演化惯例） */
    WARN,

    /** 启动失败，快速暴露索引结构漂移 */
    FAIL
}
