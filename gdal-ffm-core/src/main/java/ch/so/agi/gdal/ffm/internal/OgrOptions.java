package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrOpenOptions;
import ch.so.agi.gdal.ffm.OgrReaderOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Option parser for OGR open and reader options.
 * <p>
 * OGR 打开选项与读取选项的解析器，将公开 API 的原始 {@code Map} 转换为内部强类型记录。
 * <p>
 * This is an internal API, not public. Callers should use the public option keys
 * in {@code OgrOpenOptions} and {@code OgrReaderOptions} instead of calling this class directly.
 * <p>
 * 这是内部 API，不是公开 API。请使用公开选项键（{@code OgrOpenOptions} / {@code OgrReaderOptions}），不要直接调用此类。
 */
final class OgrOptions {
    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的工具类构造器。
     */
    private OgrOptions() {
    }

    /**
     * Parses raw open-option entries into {@code OpenOptions}.
     * <p>
     * 将原始打开选项映射解析为 {@code OpenOptions}。
     *
     * @param raw raw open options, must not be {@code null} / 原始打开选项，不能为 {@code null}
     * @return parsed open options, never {@code null} / 解析后的打开选项，永不为 {@code null}
     * @throws NullPointerException if {@code raw} is {@code null} / {@code raw} 为 {@code null} 时抛出
     */
    static OpenOptions parseOpenOptions(Map<String, String> raw) {
        Objects.requireNonNull(raw, "raw must not be null");

        List<String> allowedDrivers = splitCsvOrSemicolon(raw.get(OgrOpenOptions.ALLOWED_DRIVERS));
        Map<String, String> datasetOptions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || OgrOpenOptions.ALLOWED_DRIVERS.equals(key)) {
                continue;
            }
            datasetOptions.put(key.trim(), entry.getValue() == null ? "" : entry.getValue().trim());
        }
        return new OpenOptions(List.copyOf(allowedDrivers), Map.copyOf(datasetOptions));
    }

    /**
     * Parses raw reader-option entries into {@code ReaderOptions}.
     * <p>
     * 将原始读取选项映射解析为 {@code ReaderOptions}，并校验空间过滤互斥性。
     *
     * @param raw raw reader options, must not be {@code null} / 原始读取选项，不能为 {@code null}
     * @return parsed reader options, never {@code null} / 解析后的读取选项，永不为 {@code null}
     * @throws NullPointerException     if {@code raw} is {@code null} / {@code raw} 为 {@code null} 时抛出
     * @throws IllegalArgumentException if both bbox and WKT spatial filters are set, or values are malformed /
     *                                  同时设置 BBOX 与 WKT 空间过滤或数值格式非法时抛出
     */
    static ReaderOptions parseReaderOptions(Map<String, String> raw) {
        Objects.requireNonNull(raw, "raw must not be null");

        String attributeFilter = trimToNull(raw.get(OgrReaderOptions.ATTRIBUTE_FILTER));
        BoundingBox bbox = parseBoundingBox(trimToNull(raw.get(OgrReaderOptions.BBOX)));
        String spatialFilterWkt = trimToNull(raw.get(OgrReaderOptions.SPATIAL_FILTER_WKT));
        List<String> selectedFields = splitCsvOrSemicolon(raw.get(OgrReaderOptions.SELECTED_FIELDS));
        Long limit = parseLimit(trimToNull(raw.get(OgrReaderOptions.LIMIT)));

        if (bbox != null && spatialFilterWkt != null) {
            throw new IllegalArgumentException(
                    "Only one spatial filter can be set. Use either '" + OgrReaderOptions.BBOX
                            + "' or '" + OgrReaderOptions.SPATIAL_FILTER_WKT + "'."
            );
        }

        Set<String> selectedFieldsLowercase = new LinkedHashSet<>();
        for (String selectedField : selectedFields) {
            selectedFieldsLowercase.add(selectedField.toLowerCase(Locale.ROOT));
        }

        return new ReaderOptions(
                attributeFilter,
                bbox,
                spatialFilterWkt,
                List.copyOf(selectedFields),
                Set.copyOf(selectedFieldsLowercase),
                limit
        );
    }

    /**
     * Parses a bbox string. English + 解析 BBOX 字符串。
     *
     * @param raw bbox text in minX,minY,maxX,maxY form, may be {@code null} / BBOX 文本，可为 {@code null}
     * @return parsed box, or {@code null} when input is {@code null} / 解析结果；输入为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException on malformed format, numbers or min-max order / 格式、数字或大小关系非法时抛出
     */
    private static BoundingBox parseBoundingBox(String raw) {
        if (raw == null) {
            return null;
        }

        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid bbox format. Expected minX,minY,maxX,maxY");
        }

        try {
            double minX = Double.parseDouble(parts[0].trim());
            double minY = Double.parseDouble(parts[1].trim());
            double maxX = Double.parseDouble(parts[2].trim());
            double maxY = Double.parseDouble(parts[3].trim());
            if (minX > maxX || minY > maxY) {
                throw new IllegalArgumentException("Invalid bbox values. min must be <= max");
            }
            return new BoundingBox(minX, minY, maxX, maxY);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid bbox numbers: " + raw, e);
        }
    }

    /**
     * Parses a row limit. English + 解析行数限制。
     *
     * @param raw limit text, may be {@code null} / 限制文本，可为 {@code null}
     * @return parsed limit, or {@code null} when input is {@code null} / 解析结果；输入为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException if not a positive integer / 非正整数时抛出
     */
    private static Long parseLimit(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Limit must be > 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid limit: " + raw, e);
        }
    }

    /**
     * Splits a comma/semicolon separated list. English + 按逗号或分号切分列表。
     *
     * @param raw raw list text, may be {@code null} / 原始列表文本，可为 {@code null}
     * @return trimmed non-empty values, never {@code null} / 去空格后的非空值列表，永不为 {@code null}
     */
    private static List<String> splitCsvOrSemicolon(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] split = raw.split("[,;]");
        List<String> values = new ArrayList<>(split.length);
        for (String value : split) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                values.add(trimmed);
            }
        }
        return values;
    }

    /**
     * Trims text to {@code null}. English + 去空格并将空串转为 {@code null}。
     *
     * @param value input text, may be {@code null} / 输入文本，可为 {@code null}
     * @return trimmed text, or {@code null} when blank / 去空格后文本；为空时返回 {@code null}
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Parsed open options.
     * <p>
     * 解析后的打开选项（允许的驱动白名单 + 数据集打开选项）。
     *
     * @param allowedDrivers allowed driver short names / 允许的驱动短名列表
     * @param datasetOptions driver dataset open options / 驱动数据集打开选项
     */
    record OpenOptions(List<String> allowedDrivers, Map<String, String> datasetOptions) {
    }

    /**
     * Parsed layer reader options.
     * <p>
     * 解析后的图层读取选项（属性过滤、空间过滤、字段投影、行数限制）。
     *
     * @param attributeFilter         OGR attribute filter, may be {@code null} / OGR 属性过滤，可为 {@code null}
     * @param bbox                    bounding-box spatial filter, may be {@code null} / BBOX 空间过滤，可为 {@code null}
     * @param spatialFilterWkt        WKT spatial filter, may be {@code null} / WKT 空间过滤，可为 {@code null}
     * @param selectedFields          requested field names in order / 请求的字段名（保持顺序）
     * @param selectedFieldsLowercase lower-cased field names for matching / 小写字段名，用于匹配
     * @param limit                   max rows, may be {@code null} for unlimited / 最大行数，可为 {@code null} 表示不限
     */
    record ReaderOptions(
            String attributeFilter,
            BoundingBox bbox,
            String spatialFilterWkt,
            List<String> selectedFields,
            Set<String> selectedFieldsLowercase,
            Long limit
    ) {
    }

    /**
     * Bounding-box spatial filter.
     * <p>
     * 矩形空间过滤范围。
     *
     * @param minX minimum X / 最小 X
     * @param minY minimum Y / 最小 Y
     * @param maxX maximum X / 最大 X
     * @param maxY maximum Y / 最大 Y
     */
    record BoundingBox(double minX, double minY, double maxX, double maxY) {
    }
}
