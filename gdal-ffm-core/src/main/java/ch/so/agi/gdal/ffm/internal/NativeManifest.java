package ch.so.agi.gdal.ffm.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed form of the {@code manifest.json} shipped with each native bundle.
 * <p>
 * 每个本地 bundle 自带的 {@code manifest.json} 的解析结果，描述入口库、预加载库与数据目录。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 *
 * @param bundleVersion    bundle version string / bundle 版本字符串
 * @param entryLibrary     entry library path relative to the bundle root / 相对 bundle 根目录的入口库路径
 * @param preloadLibraries preload library paths in load order / 按加载顺序排列的预加载库路径
 * @param gdalDataPath     optional GDAL data path, may be {@code null} / 可选的 GDAL 数据目录，可为 {@code null}
 * @param projDataPath     optional PROJ data path, may be {@code null} / 可选的 PROJ 数据目录，可为 {@code null}
 * @param driverPath       optional driver path, may be {@code null} / 可选的驱动目录，可为 {@code null}
 * @param caBundlePath     optional CA bundle path, may be {@code null} / 可选的 CA 证书包路径，可为 {@code null}
 * @param cacheKey         optional extraction cache key, may be {@code null} / 可选的解压缓存键，可为 {@code null}
 */
record NativeManifest(
        String bundleVersion,
        String entryLibrary,
        List<String> preloadLibraries,
        String gdalDataPath,
        String projDataPath,
        String driverPath,
        String caBundlePath,
        String cacheKey
) {
    /**
     * Parses a manifest from its JSON text.
     * <p>
     * 从 JSON 文本解析 manifest，缺失 {@code entryLibrary} 时报错，其余缺失字段使用默认值。
     *
     * @param json manifest JSON text, must not be {@code null} / manifest 的 JSON 文本，不能为 {@code null}
     * @return parsed manifest, never {@code null} / 解析后的 manifest，永不为 {@code null}
     * @throws IllegalStateException if the required {@code entryLibrary} field is missing / 缺失必需的 {@code entryLibrary} 字段时抛出
     */
    static NativeManifest parse(String json) {
        String bundleVersion = stringField(json, "bundleVersion").orElse("unknown");
        String entryLibrary = stringField(json, "entryLibrary")
                .orElseThrow(() -> new IllegalStateException("manifest.json misses required field 'entryLibrary'"));
        List<String> preload = arrayField(json, "preloadLibraries");
        String gdalDataPath = stringField(json, "gdalDataPath").orElse(null);
        String projDataPath = stringField(json, "projDataPath").orElse(null);
        String driverPath = stringField(json, "driverPath").orElse(null);
        String caBundlePath = stringField(json, "caBundlePath").orElse(null);
        String cacheKey = stringField(json, "cacheKey").orElse(null);
        return new NativeManifest(
                bundleVersion,
                entryLibrary,
                preload,
                gdalDataPath,
                projDataPath,
                driverPath,
                caBundlePath,
                cacheKey
        );
    }

    /**
     * Extracts an optional quoted string field from the manifest JSON.
     * <p>
     * 从 manifest JSON 中提取可选的字符串字段，不存在时返回空。
     *
     * @param json  manifest JSON text, must not be {@code null} / manifest 的 JSON 文本，不能为 {@code null}
     * @param field field name, must not be {@code null} / 字段名，不能为 {@code null}
     * @return field value, or empty if absent / 字段值，缺失时为空
     */
    private static Optional<String> stringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    /**
     * Extracts an optional string-array field from the manifest JSON.
     * <p>
     * 从 manifest JSON 中提取可选的字符串数组字段，不存在时返回空列表。
     *
     * @param json  manifest JSON text, must not be {@code null} / manifest 的 JSON 文本，不能为 {@code null}
     * @param field field name, must not be {@code null} / 字段名，不能为 {@code null}
     * @return field values in document order, never {@code null} / 按文档顺序排列的字段值，永不为 {@code null}
     */
    private static List<String> arrayField(String json, String field) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)]",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return List.of();
        }

        String body = matcher.group(1);
        Pattern itemPattern = Pattern.compile("\"([^\"]+)\"");
        Matcher itemMatcher = itemPattern.matcher(body);
        List<String> values = new ArrayList<>();
        while (itemMatcher.find()) {
            values.add(itemMatcher.group(1));
        }
        return List.copyOf(values);
    }
}
