package io.github.kennethfan.mpes.index;

/**
 * reindex 执行报告（同步等待完成后的计数）。
 *
 * @param total            源索引命中文档总数
 * @param created          目标索引新建文档数
 * @param updated          目标索引更新文档数（conflicts=proceed 时覆盖旧文档）
 * @param versionConflicts 版本冲突跳过数
 */
public record ReindexReport(long total, long created, long updated, long versionConflicts) {
}
