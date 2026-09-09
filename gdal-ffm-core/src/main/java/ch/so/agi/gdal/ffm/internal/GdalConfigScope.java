package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Applies thread-scoped GDAL config options with automatic restoration.
 * <p>
 * 应用线程作用域 GDAL 配置项并在关闭时自动恢复旧值。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 */
public final class GdalConfigScope {
    /**
     * Prevents instantiation; all members are static.
     * <p>
     * 禁止实例化，所有成员均为静态。
     */
    private GdalConfigScope() {
    }

    /**
     * Applies the config plus bundled defaults as thread-local options.
     * <p>
     * 将给定配置与 bundle 默认值合并后应用为线程局部配置项，返回的句柄关闭时恢复旧值。
     *
     * @param config user config, must not be {@code null} / 用户配置，不能为 {@code null}
     * @return handle restoring previous values on close, never {@code null} / 关闭时恢复旧值的句柄，永不为 {@code null}
     * @throws NullPointerException if {@code config} is {@code null} / {@code config} 为 {@code null} 时抛出
     */
    public static ScopedConfigHandle applyScoped(GdalConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.initialize();
        Map<String, String> effectiveOptions =
                effectiveConfigOptions(config, NativeLoader.load(), System.getenv(), System.getProperties());
        if (effectiveOptions.isEmpty()) {
            return ScopedConfigHandle.NOOP;
        }

        LinkedHashMap<String, String> previousValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : effectiveOptions.entrySet()) {
            previousValues.put(entry.getKey(), getThreadLocalConfigOption(entry.getKey()));
            setThreadLocalConfigOption(entry.getKey(), entry.getValue());
        }
        return new ScopedConfigHandle(previousValues);
    }

    /**
     * Merges bundled scoped defaults with user options; user options win.
     * <p>
     * 合并 bundle 作用域默认值与用户配置，用户配置优先；便于测试的显式参数版本。
     *
     * @param config user config, must not be {@code null} / 用户配置，不能为 {@code null}
     * @param bundleInfo loaded bundle info, must not be {@code null} / 已加载的 bundle 信息，不能为 {@code null}
     * @param environment environment variables, must not be {@code null} / 环境变量，不能为 {@code null}
     * @param systemProperties system properties, must not be {@code null} / 系统属性，不能为 {@code null}
     * @return unmodifiable effective options, never {@code null} / 不可修改的生效配置项，永不为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    static Map<String, String> effectiveConfigOptions(
            GdalConfig config,
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        LinkedHashMap<String, String> effectiveOptions = new LinkedHashMap<>();
        effectiveOptions.putAll(NativeBundleRuntimeConfig.scopedConfigOptions(bundleInfo, environment, systemProperties));
        effectiveOptions.putAll(config.options());
        return Collections.unmodifiableMap(new LinkedHashMap<>(effectiveOptions));
    }

    /**
     * Sets one thread-local GDAL config option via the native API.
     * <p>
     * 通过本地 API 设置单个线程局部 GDAL 配置项，值为 {@code null} 时清除该项。
     *
     * @param key option name, must not be {@code null} / 配置项名，不能为 {@code null}
     * @param value option value, may be {@code null} to clear / 配置项值，可为 {@code null} 表示清除
     */
    private static void setThreadLocalConfigOption(String key, String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyString = arena.allocateFrom(key);
            MemorySegment valueString = value == null ? MemorySegment.NULL : arena.allocateFrom(value);
            GdalGenerated.CPLSetThreadLocalConfigOption(keyString, valueString);
        }
    }

    /**
     * Reads one thread-local GDAL config option via the native API.
     * <p>
     * 通过本地 API 读取单个线程局部 GDAL 配置项。
     *
     * @param key option name, must not be {@code null} / 配置项名，不能为 {@code null}
     * @return current value, or {@code null} if unset / 当前值，未设置时为 {@code null}
     */
    static String getThreadLocalConfigOption(String key) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyString = arena.allocateFrom(key);
            MemorySegment result = GdalGenerated.CPLGetThreadLocalConfigOption(keyString, MemorySegment.NULL);
            return CStrings.isNull(result) ? null : CStrings.fromCString(result);
        }
    }

    /**
     * Restores previous thread-local config values when closed.
     * <p>
     * 关闭时恢复之前线程局部配置旧值的句柄，支持 try-with-resources；重复关闭无副作用。
     */
    public static final class ScopedConfigHandle implements AutoCloseable {
        /**
         * Shared handle for the empty-options case doing nothing on close.
         * <p>
         * 无生效配置项时共享的空操作句柄，关闭时不做任何事。
         */
        private static final ScopedConfigHandle NOOP = new ScopedConfigHandle(Map.of());

        /**
         * Previous option values to restore.
         * <p>
         * 待恢复的配置项旧值。
         */
        private final Map<String, String> previousValues;
        /**
         * Whether this handle has been closed.
         * <p>
         * 句柄是否已关闭。
         */
        private boolean closed;

        /**
         * Creates a handle restoring the given previous values.
         * <p>
         * 创建关闭时恢复给定旧值的句柄。
         *
         * @param previousValues previous option values, must not be {@code null} / 配置项旧值，不能为 {@code null}
         */
        private ScopedConfigHandle(Map<String, String> previousValues) {
            this.previousValues = previousValues;
        }

        /**
         * Restores the previous thread-local config values.
         * <p>
         * 恢复之前的线程局部配置值；重复调用仅第一次生效。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Map.Entry<String, String> entry : previousValues.entrySet()) {
                setThreadLocalConfigOption(entry.getKey(), entry.getValue());
            }
        }
    }
}
