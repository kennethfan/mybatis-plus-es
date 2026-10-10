package io.github.kennethfan.mpes.index;

/**
 * rebuild 执行结果。
 *
 * @param freshIndex    重建后的新物理索引名
 * @param previousIndex 重建前的旧物理索引名（空索引起步为 null）
 * @param reindexed     搬迁文档数
 */
public record RebuildResult(String freshIndex, String previousIndex, long reindexed) {
}
