package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Map;

/**
 * Open OGR datasource handle (read layers, open readers/writers).
 * <p>
 * 已打开的 OGR 数据源句柄：查询图层列表、打开要素读取器/写入器。
 * <p>
 * Instances are {@link AutoCloseable}; always use try-with-resources /
 * 实例为 {@link AutoCloseable}，请使用 try-with-resources 及时关闭：
 * <pre>{@code
 * try (OgrDataSource ds = Ogr.open(path);
 *      OgrLayerReader reader = ds.openReader("my_layer", Map.of())) {
 *     for (OgrFeature f : reader) { // ... }
 * }
 * }</pre>
 */
public interface OgrDataSource extends AutoCloseable {
    /**
     * Lists layers contained in this datasource.
     * <p>
     * 列出该数据源包含的所有图层元信息。
     *
     * @return immutable layer definitions, never {@code null} / 图层元信息不可变列表，不会为 {@code null}
     * @throws GdalException if layer enumeration fails / 枚举图层失败时抛出
     */
    List<OgrLayerDefinition> listLayers();

    /**
     * Opens a sequential reader for the given layer.
     * <p>
     * 打开指定图层的顺序读取器，可附带过滤选项。
     *
     * @param layerName layer name, must not be {@code null} / 图层名，不能为 {@code null}
     * @param options reader options, must not be {@code null} / 读取选项，不能为 {@code null}
     * @return open reader, must be closed by the caller / 已打开的读取器，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the layer does not exist / 图层不存在时抛出
     * @throws GdalException if the reader cannot be opened / 读取器无法打开时抛出
     * @see OgrReaderOptions for option keys / 选项键请见 {@link OgrReaderOptions}
     */
    OgrLayerReader openReader(String layerName, Map<String, String> options);

    /**
     * Opens a writer for the given layer write specification.
     * <p>
     * 按写入规格打开图层写入器（推荐方式，可指定几何类型、字段、写模式）。
     *
     * @param spec layer write specification, must not be {@code null} / 图层写入规格，不能为 {@code null}
     * @return open writer, must be closed by the caller / 已打开的写入器，调用方负责关闭
     * @throws NullPointerException if {@code spec} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the writer cannot be opened / 写入器无法打开时抛出
     * @see OgrLayerWriteSpec
     */
    OgrLayerWriter openWriter(OgrLayerWriteSpec spec);

    /**
     * Legacy writer signature. Prefer {@link #openWriter(OgrLayerWriteSpec)}.
     * <p>
     * 遗留写入方法，建议改用 {@link #openWriter(OgrLayerWriteSpec)}。
     *
     * @param layerName layer name / 图层名
     * @param options legacy writer options (see {@link OgrWriterOptions}) / 遗留写入选项
     * @return open writer, must be closed by the caller / 已打开的写入器，调用方负责关闭
     * @deprecated use {@link #openWriter(OgrLayerWriteSpec)} instead / 请改用新方法
     */
    @Deprecated
    default OgrLayerWriter openWriter(String layerName, Map<String, String> options) {
        OgrWriteMode writeMode = OgrWriteMode.FAIL_IF_EXISTS;
        if (options != null) {
            writeMode = OgrWriteMode.fromString(options.get(OgrWriterOptions.WRITE_MODE));
        }
        return openWriter(new OgrLayerWriteSpec(layerName, null, List.of()).withWriteMode(writeMode));
    }

    /**
     * Closes the datasource and releases the native handle.
     * <p>
     * 关闭数据源并释放本地句柄；多次调用安全。
     */
    @Override
    void close();
}
