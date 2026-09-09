package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * Writable OGR driver metadata.
 * <p>
 * 可写 OGR 驱动元信息。
 *
 * @param shortName driver short name, e.g. {@code "GPKG"}, must not be {@code null} /
 *                  驱动短名称，例如 {@code "GPKG"}，不能为 {@code null}
 * @param longName driver long name, must not be {@code null} / 驱动长名称，不能为 {@code null}
 * @param extensions known file extensions, must not be {@code null} (defensively copied) /
 *                   已知文件扩展名，不能为 {@code null}（内部拷贝）
 * @param canCreate whether the driver supports dataset creation / 是否支持数据集创建
 * @param isVector whether the driver exposes vector capabilities / 是否具备矢量能力
 */
public record OgrDriverInfo(
        String shortName,
        String longName,
        List<String> extensions,
        boolean canCreate,
        boolean isVector
) {
    /**
     * Canonical constructor with null checks.
     * <p>
     * 规范构造器，校验非空并拷贝扩展名列表。
     *
     * @param shortName driver short name / 驱动短名称
     * @param longName driver long name / 驱动长名称
     * @param extensions file extensions / 文件扩展名
     * @param canCreate supports creation / 是否支持创建
     * @param isVector is vector driver / 是否为矢量驱动
     * @throws NullPointerException if any of {@code shortName}/{@code longName}/{@code extensions} is {@code null} /
     *                              任一参数为 {@code null} 时抛出
     */
    public OgrDriverInfo {
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(longName, "longName must not be null");
        Objects.requireNonNull(extensions, "extensions must not be null");
        extensions = List.copyOf(extensions);
    }
}
