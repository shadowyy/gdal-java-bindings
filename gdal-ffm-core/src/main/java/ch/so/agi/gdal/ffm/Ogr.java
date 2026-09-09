package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.OgrRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OGR vector streaming entry points (open / create / inspect drivers).
 * <p>
 * OGR 矢量流式读写入口：打开已有矢量、创建新的矢量数据集、查询可写驱动。
 * <p>
 * Typical usage / 典型用法：
 * <pre>{@code
 * try (OgrDataSource ds = Ogr.open(Path.of("input.gpkg"))) {
 *     for (OgrLayerDefinition layer : ds.listLayers()) {
 *         try (OgrLayerReader reader = ds.openReader(layer.name(), Map.of())) {
 *             for (OgrFeature feature : reader) {
 *                 // ... consume feature / 消费要素
 *             }
 *         }
 *     }
 * }
 * }</pre>
 */
public final class Ogr {
    private Ogr() {
    }

    /**
     * Opens a vector dataset from a local path in read-only mode.
     * <p>
     * 以只读方式打开本地矢量数据集。
     *
     * @param path dataset path, must not be {@code null} / 数据集路径，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if {@code path} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     * @see OgrOpenOptions for open-option keys / 打开选项键请见 {@link OgrOpenOptions}
     */
    public static OgrDataSource open(Path path) {
        return open(path, Map.of());
    }

    /**
     * Opens a vector dataset from a local path with open options.
     * <p>
     * 以指定打开选项打开本地矢量数据集。
     *
     * @param path dataset path, must not be {@code null} / 数据集路径，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     * @see OgrOpenOptions
     */
    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        return OgrRuntime.open(DatasetRef.local(path), openOptions, GdalConfig.empty());
    }

    /**
     * Opens a vector dataset referenced by {@link DatasetRef} in read-only mode.
     * <p>
     * 以只读方式打开任意数据集引用（本地路径 / HTTP / VSI）。
     *
     * @param datasetRef dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if {@code datasetRef} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     */
    public static OgrDataSource open(DatasetRef datasetRef) {
        return open(datasetRef, Map.of(), GdalConfig.empty());
    }

    /**
     * Opens a vector dataset referenced by {@link DatasetRef} with open options.
     * <p>
     * 以指定打开选项打开数据集引用。
     *
     * @param datasetRef dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     */
    public static OgrDataSource open(DatasetRef datasetRef, Map<String, String> openOptions) {
        return open(datasetRef, openOptions, GdalConfig.empty());
    }

    /**
     * Full open overload with dataset reference, open options and GDAL config.
     * <p>
     * 最完整的打开重载，支持数据集引用、打开选项与 GDAL 配置。
     *
     * @param datasetRef dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     */
    public static OgrDataSource open(
            DatasetRef datasetRef,
            Map<String, String> openOptions,
            GdalConfig config
    ) {
        Objects.requireNonNull(datasetRef, "datasetRef must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return OgrRuntime.open(datasetRef, openOptions, config);
    }

    /**
     * Creates a new vector dataset at a local path.
     * <p>
     * 在本地路径创建新的矢量数据集；已存在时默认抛异常。
     *
     * @param path output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param driverShortName OGR driver short name, e.g. {@code "GPKG"}, {@code "FlatGeobuf"} /
     *                        OGR 驱动短名称，例如 {@code "GPKG"}，不能为 {@code null}
     * @param writeMode behavior when the target exists, must not be {@code null} /
     *                  目标已存在时的处理策略，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the target exists and mode is {@code FAIL_IF_EXISTS} /
     *                                  目标已存在且策略为存在即失败时抛出
     * @throws GdalException if creation fails / 创建失败时抛出
     * @see OgrWriteMode
     */
    public static OgrDataSource create(Path path, String driverShortName, OgrWriteMode writeMode) {
        return create(path, driverShortName, writeMode, Map.of());
    }

    /**
     * Creates a new vector dataset at a local path with dataset creation options.
     * <p>
     * 在本地路径创建矢量数据集，并透传数据集创建选项（如压缩、编码）。
     *
     * @param path output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param driverShortName OGR driver short name, must not be {@code null} / OGR 驱动短名称，不能为 {@code null}
     * @param writeMode behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @param datasetCreationOptions driver dataset-creation options, must not be {@code null} /
     *                               驱动数据集创建选项，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if creation fails / 创建失败时抛出
     */
    public static OgrDataSource create(
            Path path,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        Objects.requireNonNull(path, "path must not be null");
        return create(DatasetRef.local(path), driverShortName, writeMode, datasetCreationOptions, GdalConfig.empty());
    }

    /**
     * Creates a new vector dataset referenced by {@link DatasetRef}.
     * <p>
     * 创建任意数据集引用指向的矢量数据集（本地路径 / VSI 等）。
     *
     * @param datasetRef output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param driverShortName OGR driver short name, must not be {@code null} / OGR 驱动短名称，不能为 {@code null}
     * @param writeMode behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if creation fails / 创建失败时抛出
     */
    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode
    ) {
        return create(datasetRef, driverShortName, writeMode, Map.of(), GdalConfig.empty());
    }

    /**
     * Creates a new vector dataset with dataset creation options.
     * <p>
     * 创建矢量数据集并透传数据集创建选项。
     *
     * @param datasetRef output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param driverShortName OGR driver short name, must not be {@code null} / OGR 驱动短名称，不能为 {@code null}
     * @param writeMode behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @param datasetCreationOptions driver dataset-creation options, must not be {@code null} /
     *                               驱动数据集创建选项，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if creation fails / 创建失败时抛出
     */
    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        return create(datasetRef, driverShortName, writeMode, datasetCreationOptions, GdalConfig.empty());
    }

    /**
     * Full create overload with dataset reference, creation options and GDAL config.
     * <p>
     * 最完整的创建重载，支持数据集引用、创建选项与 GDAL 配置。
     *
     * @param datasetRef output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param driverShortName OGR driver short name, must not be {@code null} / OGR 驱动短名称，不能为 {@code null}
     * @param writeMode behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @param datasetCreationOptions driver dataset-creation options, must not be {@code null} /
     *                               驱动数据集创建选项，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver name is blank / 驱动名为空时抛出
     * @throws GdalException if creation fails / 创建失败时抛出
     */
    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions,
            GdalConfig config
    ) {
        Objects.requireNonNull(datasetRef, "datasetRef must not be null");
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return OgrRuntime.create(datasetRef, driverShortName, writeMode, datasetCreationOptions, config);
    }

    /**
     * Lists vector drivers that support creation.
     * <p>
     * 列出支持创建的矢量驱动（如 GPKG、FlatGeobuf、GeoJSON 等），按短名称排序。
     *
     * @return immutable list of writable vector drivers, never {@code null} /
     *         可写矢量驱动的不可变列表，不会为 {@code null}
     * @throws GdalException if driver enumeration fails / 枚举驱动失败时抛出
     */
    public static List<OgrDriverInfo> listWritableVectorDrivers() {
        return OgrRuntime.listWritableVectorDrivers();
    }
}
