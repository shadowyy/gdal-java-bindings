package ch.so.agi.gdal.ffm;

/**
 * Type of a {@link DatasetRef}.
 * <p>
 * {@link DatasetRef} 的引用类型。
 */
public enum DatasetRefType {
    /**
     * Local filesystem path. / 本地文件系统路径。
     */
    LOCAL_PATH,
    /**
     * HTTP(S) URL, mapped to {@code /vsicurl/} for GDAL. / HTTP(S) 地址，底层映射为 {@code /vsicurl/}。
     */
    HTTP_URL,
    /**
     * Explicit GDAL/VSI path (must start with {@code /vsi}). / 显式 GDAL/VSI 路径（须以 {@code /vsi} 开头）。
     */
    GDAL_VSI,
    /**
     * Pass-through GDAL dataset identifier, used verbatim by GDAL (e.g. a driver connection
     * string such as {@code PG:"host=... dbname=..."}). No format validation is applied.
     * <p>
     * 直通 GDAL 数据集标识，原样交给 GDAL（如驱动连接串 {@code PG:"host=... dbname=..."}），不做格式校验。
     */
    RAW
}
