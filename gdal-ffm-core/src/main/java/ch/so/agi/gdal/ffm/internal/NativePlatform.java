package ch.so.agi.gdal.ffm.internal;

import java.util.Locale;

/**
 * Normalized operating system, architecture and bundle classifier of the current host.
 * <p>
 * 当前主机的规范化操作系统、架构与 bundle 分类串。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 *
 * @param os         normalized OS name, e.g. {@code linux} / 规范化操作系统名，例如 {@code linux}
 * @param arch       normalized architecture, e.g. {@code x86_64} / 规范化架构名，例如 {@code x86_64}
 * @param classifier platform classifier in {@code os-arch} form / {@code os-arch} 形式的平台分类串
 */
public record NativePlatform(String os, String arch, String classifier) {
    /**
     * Detects the platform of the current JVM from system properties.
     * <p>
     * 根据系统属性检测当前 JVM 所在平台。
     *
     * @return current platform, never {@code null} / 当前平台，永不为 {@code null}
     * @throws IllegalStateException if the OS or architecture is missing or unsupported / 系统或架构缺失或不受支持时抛出
     */
    public static NativePlatform current() {
        return from(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /**
     * Builds a platform from raw {@code os.name} / {@code os.arch} values.
     * <p>
     * 根据原始 {@code os.name} 与 {@code os.arch} 取值构建平台信息（便于测试）。
     *
     * @param osName raw OS name, must not be blank / 原始系统名，不能为空
     * @param osArch raw architecture name, must not be blank / 原始架构名，不能为空
     * @return normalized platform, never {@code null} / 规范化后的平台信息，永不为 {@code null}
     * @throws IllegalStateException if the OS or architecture is missing or unsupported / 系统或架构缺失或不受支持时抛出
     */
    static NativePlatform from(String osName, String osArch) {
        String normalizedOs = normalizeOs(osName);
        String normalizedArch = normalizeArch(osArch);
        return new NativePlatform(normalizedOs, normalizedArch, normalizedOs + "-" + normalizedArch);
    }

    /**
     * Normalizes a raw OS name to {@code linux}, {@code osx} or {@code windows}.
     * <p>
     * 将原始系统名规范化为 {@code linux}、{@code osx} 或 {@code windows}。
     *
     * @param osName raw OS name, must not be blank / 原始系统名，不能为空
     * @return normalized OS name, never {@code null} / 规范化系统名，永不为 {@code null}
     * @throws IllegalStateException if the OS name is missing or unsupported / 系统名缺失或不受支持时抛出
     */
    private static String normalizeOs(String osName) {
        if (osName == null || osName.isBlank()) {
            throw new IllegalStateException("System property os.name is empty");
        }
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return "osx";
        }
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        throw new IllegalStateException("Unsupported operating system for GDAL bundle: " + osName);
    }

    /**
     * Normalizes a raw architecture name to {@code x86_64} or {@code aarch64}.
     * <p>
     * 将原始架构名规范化为 {@code x86_64} 或 {@code aarch64}。
     *
     * @param osArch raw architecture name, must not be blank / 原始架构名，不能为空
     * @return normalized architecture, never {@code null} / 规范化架构名，永不为 {@code null}
     * @throws IllegalStateException if the architecture is missing or unsupported / 架构缺失或不受支持时抛出
     */
    private static String normalizeArch(String osArch) {
        if (osArch == null || osArch.isBlank()) {
            throw new IllegalStateException("System property os.arch is empty");
        }
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        throw new IllegalStateException("Unsupported architecture for GDAL bundle: " + osArch);
    }
}
