package ch.so.agi.gdal.ffm;

/**
 * Feature writer for an OGR layer.
 * <p>
 * OGR 图层的要素写入器，逐条写入 {@link OgrFeature}。
 * <pre>{@code
 * OgrLayerWriteSpec spec = new OgrLayerWriteSpec("points", null, fields);
 * try (OgrLayerWriter writer = ds.openWriter(spec)) {
 *     writer.write(new OgrFeature(0, Map.of("name", "A"), geom));
 * }
 * }</pre>
 */
public interface OgrLayerWriter extends AutoCloseable {
    /**
     * Writes a single feature to the layer.
     * <p>
     * 向图层写入单条要素。
     *
     * @param feature feature to write, must not be {@code null} / 待写入要素，不能为 {@code null}
     * @throws NullPointerException if {@code feature} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native write fails / 本地写入失败时抛出
     */
    void write(OgrFeature feature);

    /**
     * Flushes pending writes and releases the native handle.
     * <p>
     * 刷盘并释放本地句柄；关闭后写入即提交（视驱动而定）。
     */
    @Override
    void close();
}
