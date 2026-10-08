package ch.so.agi.gdal.ffm.internal;

import java.nio.file.Path;

/**
 * Resolved locations of a loaded native bundle.
 * <p>
 * 已加载本地 bundle 解析后的位置信息，包括解压根目录与可选数据目录。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 *
 * @param classifier     platform classifier the bundle was loaded for / 加载该 bundle 所用的平台分类串
 * @param bundleVersion  bundle version string / bundle 版本字符串
 * @param extractionRoot root directory of the extracted (or source) bundle / 解压后（或源码）bundle 的根目录
 * @param gdalData       resolved GDAL data directory, may be {@code null} / 解析后的 GDAL 数据目录，可为 {@code null}
 * @param projData       resolved PROJ data directory, may be {@code null} / 解析后的 PROJ 数据目录，可为 {@code null}
 * @param driverPath     resolved driver directory, may be {@code null} / 解析后的驱动目录，可为 {@code null}
 * @param caBundle       resolved CA bundle file, may be {@code null} / 解析后的 CA 证书包文件，可为 {@code null}
 */
record NativeBundleInfo(
        String classifier,
        String bundleVersion,
        Path extractionRoot,
        Path gdalData,
        Path projData,
        Path driverPath,
        Path caBundle
) {
}
