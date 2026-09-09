package ch.so.agi.gdal.ffm;

/**
 * Write behavior when dataset/layer already exists.
 * <p>
 * 数据集/图层已存在时的写策略。
 */
public enum OgrWriteMode {
    /**
     * Fail when the target already exists (default, safest). / 目标已存在时抛异常（默认，最安全）。
     */
    FAIL_IF_EXISTS,
    /**
     * Delete and recreate the target. / 删除后重建目标。
     */
    OVERWRITE,
    /**
     * Open the existing target and append features. / 打开已存在目标并追加要素。
     */
    APPEND;

    /**
     * Parses a raw string leniently (case-insensitive, blank -&gt; default).
     * <p>
     * 宽松解析字符串（大小写不敏感，空值回退默认）。
     *
     * @param raw raw value, may be {@code null} or blank / 原始值，可为 {@code null} 或空白
     * @return parsed mode, {@link #FAIL_IF_EXISTS} for {@code null}/blank / 解析后的模式，空值返回默认
     * @throws IllegalArgumentException if the value is unknown / 值未知时抛出
     */
    public static OgrWriteMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAIL_IF_EXISTS;
        }
        return OgrWriteMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
