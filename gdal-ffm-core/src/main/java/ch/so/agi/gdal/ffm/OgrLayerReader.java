package ch.so.agi.gdal.ffm;

import java.util.Iterator;

/**
 * Sequential feature reader for an OGR layer.
 * <p>
 * OGR 图层的顺序要素读取器，可 for-each 遍历；底层为流式拉取。
 * <p>
 * Always close after use (try-with-resources) / 用完请关闭：
 * <pre>{@code
 * try (OgrLayerReader reader = ds.openReader("roads", Map.of(OgrReaderOptions.LIMIT, "100"))) {
 *     for (OgrFeature feature : reader) { // ... }
 * }
 * }</pre>
 */
public interface OgrLayerReader extends AutoCloseable, Iterable<OgrFeature> {
    /**
     * Returns the feature iterator (single-use, not thread-safe).
     * <p>
     * 返回要素迭代器（一次性、非线程安全，不要并发调用）。
     *
     * @return feature iterator, never {@code null} / 要素迭代器，不会为 {@code null}
     */
    @Override
    Iterator<OgrFeature> iterator();

    /**
     * Closes the reader and releases the native cursor.
     * <p>
     * 关闭读取器并释放本地游标。
     */
    @Override
    void close();
}
