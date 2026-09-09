package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * Writable raster driver metadata.
 * <p>
 * 可写栅格驱动元信息。
 *
 * @param shortName driver short name, e.g. {@code "GTiff"}, must not be {@code null} /
 *                  驱动短名称，例如 {@code "GTiff"}，不能为 {@code null}
 * @param longName driver long name, must not be {@code null} / 驱动长名称，不能为 {@code null}
 * @param extensions known file extensions, must not be {@code null} (defensively copied) /
 *                   已知文件扩展名，不能为 {@code null}（内部拷贝）
 * @param canCreate whether the driver supports {@code Create} / 是否支持 {@code Create} 直接创建
 * @param canCreateCopy whether the driver supports {@code CreateCopy} / 是否支持 {@code CreateCopy} 拷贝创建
 */
public record RasterDriverInfo(
        String shortName,
        String longName,
        List<String> extensions,
        boolean canCreate,
        boolean canCreateCopy
) {
    /**
     * Canonical constructor with null checks.
     * <p>
     * 规范构造器，校验非空并拷贝扩展名列表。
     *
     * @param shortName driver short name / 驱动短名称
     * @param longName driver long name / 驱动长名称
     * @param extensions file extensions / 文件扩展名
     * @param canCreate supports {@code Create} / 是否支持直接创建
     * @param canCreateCopy supports {@code CreateCopy} / 是否支持拷贝创建
     * @throws NullPointerException if any of {@code shortName}/{@code longName}/{@code extensions} is {@code null} /
     *                              任一参数为 {@code null} 时抛出
     */
    public RasterDriverInfo {
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(longName, "longName must not be null");
        Objects.requireNonNull(extensions, "extensions must not be null");
        extensions = List.copyOf(extensions);
    }
}
