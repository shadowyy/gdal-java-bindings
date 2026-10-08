package ch.so.agi.gdal.ffm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable GDAL configuration options (key/value pairs for {@code CPLSetConfigOption}).
 * <p>
 * 不可变 GDAL 配置项（传给 {@code CPLSetConfigOption} 的键值对），例如 {@code GDAL_HTTP_TIMEOUT}。
 * Empty instance is shared via {@link #empty()}.
 *
 * @param options config entries, must not be {@code null}; keys are trimmed and copied /
 *                配置条目，不能为 {@code null}；键会去空格并拷贝为不可变 Map
 */
public record GdalConfig(Map<String, String> options) {
    private static final GdalConfig EMPTY = new GdalConfig(Map.of());

    /**
     * Canonical constructor with normalization.
     * <p>
     * 规范构造器，校验并归一化（键去空格、值非空、拷贝为不可变）。
     *
     * @param options config entries / 配置条目
     * @throws NullPointerException     if {@code options} or any value is {@code null} / 映射或值为 {@code null} 时抛出
     * @throws IllegalArgumentException if any key is blank / 任一键为空白时抛出
     */
    public GdalConfig {
        Objects.requireNonNull(options, "options must not be null");

        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = normalize(entry.getKey(), "option key");
            String value = Objects.requireNonNull(entry.getValue(), "option value must not be null");
            normalized.put(key, value);
        }
        options = Map.copyOf(normalized);
    }

    /**
     * Returns the shared empty configuration.
     * <p>
     * 返回共享的空配置。
     *
     * @return empty config, never {@code null} / 空配置，不会为 {@code null}
     */
    public static GdalConfig empty() {
        return EMPTY;
    }

    /**
     * Checks whether this configuration is empty.
     * <p>
     * 配置是否为空。
     *
     * @return {@code true} when no options are set / 无配置项时返回 {@code true}
     */
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /**
     * Returns a new config with one additional/overridden entry.
     * <p>
     * 返回新增/覆盖单个配置项后的新配置（原对象不变）。
     *
     * @param key   option key, must not be blank / 配置键，不能为空白
     * @param value option value, must not be {@code null} / 配置值，不能为 {@code null}
     * @return new config, never {@code null} / 新配置，不会为 {@code null}
     * @throws NullPointerException     if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code key} is blank / 键为空白时抛出
     */
    public GdalConfig withConfigOption(String key, String value) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(options);
        merged.put(normalize(key, "option key"), Objects.requireNonNull(value, "value must not be null"));
        return new GdalConfig(merged);
    }

    /**
     * Returns a new config merged with additional entries.
     * <p>
     * 返回合并额外配置项后的新配置（原对象不变，同名键被覆盖）。
     *
     * @param additionalOptions entries to merge, must not be {@code null} / 待合并条目，不能为 {@code null}
     * @return new config, never {@code null} / 新配置，不会为 {@code null}
     * @throws NullPointerException     if {@code additionalOptions} or any value is {@code null} /
     *                                  参数或值为 {@code null} 时抛出
     * @throws IllegalArgumentException if any key is blank / 任一键为空白时抛出
     */
    public GdalConfig withConfig(Map<String, String> additionalOptions) {
        Objects.requireNonNull(additionalOptions, "additionalOptions must not be null");
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(options);
        for (Map.Entry<String, String> entry : additionalOptions.entrySet()) {
            merged.put(normalize(entry.getKey(), "option key"), Objects.requireNonNull(entry.getValue(), "value must not be null"));
        }
        return new GdalConfig(merged);
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
