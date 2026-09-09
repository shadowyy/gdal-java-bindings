package ch.so.agi.gdal.ffm;

/**
 * Key constants for {@link OgrDataSource#openReader(String, java.util.Map)} options.
 * <p>
 * {@link OgrDataSource#openReader} 读取选项的键常量。
 */
public final class OgrReaderOptions {
    /**
     * OGR attribute filter expression (SQL {@code WHERE} style).
     * <p>
     * OGR 属性过滤表达式（类 SQL {@code WHERE} 写法）。
     */
    public static final String ATTRIBUTE_FILTER = "attributeFilter";

    /**
     * Comma-separated bbox: minX,minY,maxX,maxY.
     * <p>
     * BBOX 空间范围过滤，逗号分隔：minX,minY,maxX,maxY。
     */
    public static final String BBOX = "bbox";

    /**
     * Spatial filter geometry as WKT.
     * <p>
     * WKT 表达的空间过滤几何。
     */
    public static final String SPATIAL_FILTER_WKT = "spatialFilterWkt";

    /**
     * Comma/semicolon-separated list of fields to include.
     * <p>
     * 需要返回的字段列表（逗号/分号分隔），为空表示全部字段。
     */
    public static final String SELECTED_FIELDS = "selectedFields";

    /**
     * Maximum number of features to emit.
     * <p>
     * 最多返回的要素数量。
     */
    public static final String LIMIT = "limit";

    private OgrReaderOptions() {
    }
}
