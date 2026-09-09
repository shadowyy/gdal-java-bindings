package ch.so.agi.gdal.ffm.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Derives GDAL runtime configuration options from a loaded native bundle.
 * <p>
 * 根据已加载的本地 bundle 推导 GDAL 运行时配置项（数据目录、证书包等）。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 */
final class NativeBundleRuntimeConfig {
    /**
     * GDAL data directory option name.
     * <p>
     * GDAL 数据目录配置项名。
     */
    static final String GDAL_DATA = "GDAL_DATA";
    /**
     * PROJ data directory option name.
     * <p>
     * PROJ 数据目录配置项名。
     */
    static final String PROJ_LIB = "PROJ_LIB";
    /**
     * GDAL driver search path option name.
     * <p>
     * GDAL 驱动搜索路径配置项名。
     */
    static final String GDAL_DRIVER_PATH = "GDAL_DRIVER_PATH";
    /**
     * CA bundle option name used by curl-based drivers.
     * <p>
     * 基于 curl 的驱动所用的 CA 证书包配置项名。
     */
    static final String CURL_CA_BUNDLE = "CURL_CA_BUNDLE";
    /**
     * CA bundle option name used by SSL-based drivers.
     * <p>
     * 基于 SSL 的驱动所用的 CA 证书包配置项名。
     */
    static final String SSL_CERT_FILE = "SSL_CERT_FILE";

    /**
     * Prevents instantiation; all members are static.
     * <p>
     * 禁止实例化，所有成员均为静态。
     */
    private NativeBundleRuntimeConfig() {
    }

    /**
     * Builds process-wide config options pointing at the bundled data directories.
     * <p>
     * 构建指向 bundle 自带数据目录的进程级配置项。
     *
     * @param bundleInfo loaded bundle info, must not be {@code null} / 已加载的 bundle 信息，不能为 {@code null}
     * @return unmodifiable option-to-path map, never {@code null} / 不可修改的配置项到路径映射，永不为 {@code null}
     * @throws NullPointerException if {@code bundleInfo} is {@code null} / {@code bundleInfo} 为 {@code null} 时抛出
     */
    static Map<String, Path> globalConfigOptions(NativeBundleInfo bundleInfo) {
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");

        LinkedHashMap<String, Path> options = new LinkedHashMap<>();
        putIfPresent(options, GDAL_DATA, bundleInfo.gdalData());
        putIfPresent(options, PROJ_LIB, bundleInfo.projData());
        putIfPresent(options, GDAL_DRIVER_PATH, bundleInfo.driverPath());
        return Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * Builds scoped (thread-local) config options from the current environment.
     * <p>
     * 根据当前环境变量与系统属性构建作用域级（线程局部）配置项。
     *
     * @param bundleInfo loaded bundle info, must not be {@code null} / 已加载的 bundle 信息，不能为 {@code null}
     * @return unmodifiable option map, never {@code null} / 不可修改的配置项映射，永不为 {@code null}
     * @throws NullPointerException if {@code bundleInfo} is {@code null} / {@code bundleInfo} 为 {@code null} 时抛出
     */
    static Map<String, String> scopedConfigOptions(NativeBundleInfo bundleInfo) {
        return scopedConfigOptions(bundleInfo, System.getenv(), System.getProperties());
    }

    /**
     * Builds scoped (thread-local) config options from explicit environment inputs.
     * <p>
     * 根据显式传入的环境变量与系统属性构建作用域级（线程局部）配置项（便于测试）。
     *
     * @param bundleInfo loaded bundle info, must not be {@code null} / 已加载的 bundle 信息，不能为 {@code null}
     * @param environment environment variables, must not be {@code null} / 环境变量，不能为 {@code null}
     * @param systemProperties system properties, must not be {@code null} / 系统属性，不能为 {@code null}
     * @return unmodifiable option map, never {@code null} / 不可修改的配置项映射，永不为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    static Map<String, String> scopedConfigOptions(
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        Path bundledCa = bundledCaBundle(bundleInfo, environment, systemProperties);
        if (bundledCa != null) {
            String bundledCaPath = bundledCa.toAbsolutePath().toString();
            options.put(CURL_CA_BUNDLE, bundledCaPath);
            options.put(SSL_CERT_FILE, bundledCaPath);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * Resolves the bundled CA bundle to use, honoring user overrides.
     * <p>
     * 解析应使用的 bundle 自带 CA 证书包；非 Unix 平台、用户已自定义或证书包缺失时返回 {@code null}。
     *
     * @param bundleInfo loaded bundle info, must not be {@code null} / 已加载的 bundle 信息，不能为 {@code null}
     * @param environment environment variables, must not be {@code null} / 环境变量，不能为 {@code null}
     * @param systemProperties system properties, must not be {@code null} / 系统属性，不能为 {@code null}
     * @return CA bundle path, or {@code null} if none applies / CA 证书包路径，不适用时为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    static Path bundledCaBundle(
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        if (!isUnixClassifier(bundleInfo.classifier()) || hasUserDefinedCaOption(environment, systemProperties)) {
            return null;
        }

        Path caBundle = bundleInfo.caBundle();
        if (caBundle == null || !Files.isRegularFile(caBundle)) {
            return null;
        }
        return caBundle.toAbsolutePath();
    }

    /**
     * Checks whether the classifier denotes a Unix-like platform.
     * <p>
     * 判断分类串是否为类 Unix 平台（linux 或 osx 开头）。
     *
     * @param classifier platform classifier, must not be {@code null} / 平台分类串，不能为 {@code null}
     * @return {@code true} for Linux/macOS classifiers / Linux/macOS 分类串返回 {@code true}
     */
    private static boolean isUnixClassifier(String classifier) {
        return classifier.startsWith("linux-") || classifier.startsWith("osx-");
    }

    /**
     * Checks whether the user already defined a CA bundle option.
     * <p>
     * 检查用户是否已在环境变量或系统属性中自定义 CA 证书包配置项。
     *
     * @param environment environment variables, must not be {@code null} / 环境变量，不能为 {@code null}
     * @param systemProperties system properties, must not be {@code null} / 系统属性，不能为 {@code null}
     * @return {@code true} if any CA option is set to a non-blank value / 任一 CA 配置项为非空值时返回 {@code true}
     */
    private static boolean hasUserDefinedCaOption(Map<String, String> environment, Properties systemProperties) {
        return hasValue(environment.get(CURL_CA_BUNDLE))
                || hasValue(environment.get(SSL_CERT_FILE))
                || hasValue(systemProperties.getProperty(CURL_CA_BUNDLE))
                || hasValue(systemProperties.getProperty(SSL_CERT_FILE));
    }

    /**
     * Checks whether a config value counts as set.
     * <p>
     * 判断配置值是否视为已设置（非 {@code null} 且非空白）。
     *
     * @param value value to check, may be {@code null} / 待检查的值，可为 {@code null}
     * @return {@code true} if the value is non-null and non-blank / 值非空且非空白时返回 {@code true}
     */
    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Puts the entry if the path is present.
     * <p>
     * 路径非空时才放入映射（绝对路径形式）。
     *
     * @param options target map, must not be {@code null} / 目标映射，不能为 {@code null}
     * @param key option name, must not be {@code null} / 配置项名，不能为 {@code null}
     * @param value path value, may be {@code null} / 路径值，可为 {@code null}
     */
    private static void putIfPresent(Map<String, Path> options, String key, Path value) {
        if (value != null) {
            options.put(key, value.toAbsolutePath());
        }
    }
}
