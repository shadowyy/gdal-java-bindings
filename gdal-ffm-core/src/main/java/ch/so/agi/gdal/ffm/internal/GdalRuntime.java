package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.DatasetRef;
import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.GdalException;
import ch.so.agi.gdal.ffm.ProgressCallback;
import ch.so.agi.gdal.ffm.RasterDriverInfo;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal FFM runtime behind the public {@code Gdal} facade.
 * <p>
 * 公开 {@code Gdal} 门面背后的内部 FFM 运行时，封装本地 GDAL 初始化与算法调用。
 * <p>
 * This is an internal API, not public. External callers should use {@code ch.so.agi.gdal.ffm.Gdal}.
 * <p>
 * 这是内部 API，不是公开 API。对外请使用 {@code ch.so.agi.gdal.ffm.Gdal}。
 */
public final class GdalRuntime {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    private static final int GDAL_OF_RASTER = 0x02;
    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;
    private static final String MD_DCAP_RASTER = "DCAP_RASTER";
    private static final String MD_DCAP_CREATE = "DCAP_CREATE";
    private static final String MD_DCAP_CREATECOPY = "DCAP_CREATECOPY";
    private static final String MD_DMD_EXTENSIONS = "DMD_EXTENSIONS";
    private static final String MD_DMD_EXTENSION = "DMD_EXTENSION";
    private static final String MD_DMD_CREATIONOPTIONLIST = "DMD_CREATIONOPTIONLIST";

    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的工具类构造器。
     */
    private GdalRuntime() {
    }

    /**
     * Runs GDALVectorTranslate from local paths with optional progress.
     * <p>
     * 基于本地路径执行矢量转换（GDALVectorTranslate），支持可选进度回调。
     *
     * @param dest     output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param src      input dataset path, must not be {@code null} / 输入数据集路径，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra GDALVectorTranslate CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if option creation or translation fails / 选项创建或转换失败时抛出
     */
    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        vectorTranslate(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    /**
     * Runs GDALVectorTranslate from dataset references with scoped config options and
     * optional progress. Config options are applied thread-scoped and restored afterwards.
     * <p>
     * 基于数据集引用执行矢量转换（GDALVectorTranslate）：配置项以线程级作用域生效并在调用结束后自动恢复，
     * 支持可选进度回调。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra GDALVectorTranslate CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if option creation or translation fails / 选项创建或转换失败时抛出
     */
    public static void vectorTranslate(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();

        MemorySegment options = MemorySegment.NULL;
        MemorySegment sourceDataset = MemorySegment.NULL;
        MemorySegment resultDataset = MemorySegment.NULL;

        GdalGenerated.CPLErrorReset();
        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(config);
             Arena arena = Arena.ofConfined();
             ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            MemorySegment argv = CArgv.toCStringArray(args, arena);
            options = GdalGenerated.GDALVectorTranslateOptionsNew(argv, MemorySegment.NULL);
            if (CStrings.isNull(options)) {
                throw GdalErrors.lastError("Failed to create GDALVectorTranslate options");
            }

            if (!CStrings.isNull(progressHandle.callbackFn())) {
                GdalGenerated.GDALVectorTranslateOptionsSetProgress(options, progressHandle.callbackFn(), progressHandle.userData());
            }

            sourceDataset = openDataset(src, GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR, arena);

            MemorySegment sources = arena.allocate(ValueLayout.ADDRESS);
            sources.set(ValueLayout.ADDRESS, 0, sourceDataset);

            MemorySegment usageError = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment destination = arena.allocateFrom(dest.toGdalIdentifier());

            resultDataset = GdalGenerated.GDALVectorTranslate(
                    destination,
                    MemorySegment.NULL,
                    1,
                    sources,
                    options,
                    usageError
            );

            throwIfCallbackFailed(progressHandle);

            if (usageError.get(ValueLayout.JAVA_INT, 0) != 0 || CStrings.isNull(resultDataset)) {
                throw GdalErrors.lastError("GDALVectorTranslate failed");
            }
        } finally {
            freeQuietly(options, GdalGenerated::GDALVectorTranslateOptionsFree);
            closeDatasetQuietly(resultDataset);
            closeDatasetQuietly(sourceDataset);
        }
    }

    /**
     * Returns {@code gdal raster info} output for a dataset.
     * <p>
     * 返回数据集的 {@code gdal raster info} 文本输出。
     *
     * @param src    dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param config GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param args   extra CLI arguments / 透传的额外命令行参数
     * @return info output text, never {@code null} / 信息文本输出，永不为 {@code null}
     * @throws NullPointerException if {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static String rasterInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        return GdalAlgorithmRunner.runForStringOutput(
                List.of("raster", "info"),
                config,
                null,
                withInputArg(src, args)
        );
    }

    /**
     * Returns {@code gdal vector info} output for a dataset.
     * <p>
     * 返回数据集的 {@code gdal vector info} 文本输出。
     *
     * @param src    dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param config GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param args   extra CLI arguments / 透传的额外命令行参数
     * @return info output text, never {@code null} / 信息文本输出，永不为 {@code null}
     * @throws NullPointerException if {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static String vectorInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        return GdalAlgorithmRunner.runForStringOutput(
                List.of("vector", "info"),
                config,
                null,
                withInputArg(src, args)
        );
    }

    /**
     * Clips a raster via {@code gdal raster clip}.
     * <p>
     * 通过 {@code gdal raster clip} 裁剪栅格。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void rasterClip(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "clip"), config, progress, withInputOutputArgs(src, dest, args));
    }

    /**
     * Converts a raster via {@code gdal raster convert}.
     * <p>
     * 通过 {@code gdal raster convert} 转换栅格。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void rasterConvert(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "convert"), config, progress, withInputOutputArgs(src, dest, args));
    }

    /**
     * Reprojects a raster via {@code gdal raster reproject}.
     * <p>
     * 通过 {@code gdal raster reproject} 重投影栅格。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void rasterReproject(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(
                List.of("raster", "reproject"),
                config,
                progress,
                withInputOutputArgs(src, dest, args)
        );
    }

    /**
     * Resizes a raster via {@code gdal raster resize}.
     * <p>
     * 通过 {@code gdal raster resize} 调整栅格尺寸。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void rasterResize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "resize"), config, progress, withInputOutputArgs(src, dest, args));
    }

    /**
     * Mosaics multiple rasters via {@code gdal raster mosaic}.
     * <p>
     * 通过 {@code gdal raster mosaic} 镶嵌多个栅格。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param sources  input dataset references, must not be {@code null} or empty / 输入数据集引用列表，不能为 {@code null} 或空
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException     if {@code dest}, {@code sources} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code sources} is empty / {@code sources} 为空时抛出
     * @throws GdalException            if the native call fails / 本地调用失败时抛出
     */
    public static void rasterMosaic(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }

        initialize();
        GdalAlgorithmRunner.run(
                List.of("raster", "mosaic"),
                config,
                progress,
                withInputOutputArgs(sources, dest, args)
        );
    }

    /**
     * Computes zonal statistics via {@code gdal raster zonal-stats}.
     * <p>
     * 通过 {@code gdal raster zonal-stats} 计算分区统计。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input raster reference, must not be {@code null} / 输入栅格引用，不能为 {@code null}
     * @param zones    zone dataset reference, must not be {@code null} / 分区数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if any required argument is {@code null} / 任一必要参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void rasterZonalStats(
            DatasetRef dest,
            DatasetRef src,
            DatasetRef zones,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(zones, "zones must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(
                List.of("raster", "zonal-stats"),
                config,
                progress,
                withInputZonesOutputArgs(src, zones, dest, args)
        );
    }

    /**
     * Rasterizes a vector via {@code gdal vector rasterize}.
     * <p>
     * 通过 {@code gdal vector rasterize} 将矢量栅格化。
     *
     * @param dest     output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src      input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config   GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args     extra CLI arguments / 透传的额外命令行参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException        if the native call fails / 本地调用失败时抛出
     */
    public static void vectorRasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        GdalAlgorithmRunner.run(List.of("vector", "rasterize"), config, progress, withInputOutputArgs(src, dest, args));
    }

    /**
     * Lists raster drivers supporting create or create-copy.
     * <p>
     * 列出支持创建或复制创建的栅格驱动。
     *
     * @return sorted immutable driver list, never {@code null} / 排序后的不可变驱动列表，永不为 {@code null}
     * @throws GdalException if driver metadata cannot be read / 驱动元数据读取失败时抛出
     */
    public static List<RasterDriverInfo> listWritableRasterDrivers() {
        initialize();

        int driverCount = GdalGenerated.GDALGetDriverCount();
        if (driverCount <= 0) {
            return List.of();
        }

        List<RasterDriverInfo> writableDrivers = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0; i < driverCount; i++) {
                MemorySegment driver = GdalGenerated.GDALGetDriver(i);
                if (CStrings.isNull(driver) || !isMetadataTrue(driver, MD_DCAP_RASTER, arena)) {
                    continue;
                }

                boolean canCreate = isMetadataTrue(driver, MD_DCAP_CREATE, arena);
                boolean canCreateCopy = isMetadataTrue(driver, MD_DCAP_CREATECOPY, arena);
                if (!canCreate && !canCreateCopy) {
                    continue;
                }

                String shortName = CStrings.fromCString(GdalGenerated.GDALGetDriverShortName(driver)).trim();
                if (shortName.isEmpty()) {
                    continue;
                }
                String longName = CStrings.fromCString(GdalGenerated.GDALGetDriverLongName(driver)).trim();
                if (longName.isEmpty()) {
                    longName = shortName;
                }

                String extensionMetadata = readMetadataItem(driver, MD_DMD_EXTENSIONS, arena);
                if (extensionMetadata.isBlank()) {
                    extensionMetadata = readMetadataItem(driver, MD_DMD_EXTENSION, arena);
                }
                List<String> extensions = parseExtensions(extensionMetadata);

                writableDrivers.add(
                        new RasterDriverInfo(shortName, longName, extensions, canCreate, canCreateCopy)
                );
            }
        }

        writableDrivers.sort((left, right) -> left.shortName().compareToIgnoreCase(right.shortName()));
        return List.copyOf(writableDrivers);
    }

    /**
     * Returns raw creation-option-list XML for a raster driver.
     * <p>
     * 返回栅格驱动的创建选项列表原始 XML。
     *
     * @param driverShortName driver short name, must not be {@code null} or blank / 驱动短名，不能为 {@code null} 或空
     * @return XML text, possibly empty / XML 文本，可能为空
     * @throws NullPointerException     if {@code driverShortName} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if blank or driver not found / 为空或驱动不存在时抛出
     */
    public static String driverCreationOptionListXml(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        initialize();
        String normalized = driverShortName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("driverShortName must not be blank");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment driver = resolveRasterDriver(normalized, arena);
            return readMetadataItem(driver, MD_DMD_CREATIONOPTIONLIST, arena);
        }
    }

    /**
     * Lists allowed enum values for a driver creation option.
     * <p>
     * 列出某驱动创建选项的枚举可选值。
     *
     * @param driverShortName driver short name, must not be {@code null} / 驱动短名，不能为 {@code null}
     * @param optionName      option name, must not be {@code null} / 选项名，不能为 {@code null}
     * @return allowed values, possibly empty / 可选值列表，可能为空
     * @throws NullPointerException     if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver is unknown / 驱动未知时抛出
     */
    public static List<String> listCreationOptionEnumValues(String driverShortName, String optionName) {
        Objects.requireNonNull(optionName, "optionName must not be null");
        return CreationOptionListParser.enumValues(driverCreationOptionListXml(driverShortName), optionName);
    }

    /**
     * Lists COMPRESS values supported by a driver.
     * <p>
     * 列出某驱动支持的 COMPRESS 压缩选项值。
     *
     * @param driverShortName driver short name, must not be {@code null} / 驱动短名，不能为 {@code null}
     * @return allowed COMPRESS values, possibly empty / 支持的压缩值，可能为空
     * @throws NullPointerException     if {@code driverShortName} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver is unknown / 驱动未知时抛出
     */
    public static List<String> listCompressionOptions(String driverShortName) {
        return listCreationOptionEnumValues(driverShortName, "COMPRESS");
    }

    /**
     * Initializes native GDAL once in a thread-safe way.
     * <p>
     * 以线程安全方式一次性初始化本地 GDAL。
     *
     * @throws GdalException if native loading or registration fails / 本地加载或注册失败时抛出
     */
    public static void initialize() {
        if (INITIALIZED.get()) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (INITIALIZED.get()) {
                return;
            }

            NativeAccess.ensureEnabled();
            NativeBundleInfo bundleInfo = NativeLoader.load();
            applyConfig(bundleInfo);
            GdalGenerated.GDALAllRegister();
            INITIALIZED.set(true);
        }
    }

    /**
     * Opens a GDAL dataset handle with the given flags.
     * <p>
     * 按指定标志打开 GDAL 数据集句柄。
     *
     * @param src   dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param flags GDAL open flags / GDAL 打开标志
     * @param arena arena for the path string / 用于路径字符串的 Arena
     * @return native dataset handle, never null / 本地数据集句柄，永不为空
     * @throws GdalException if the dataset cannot be opened / 数据集无法打开时抛出
     */
    static MemorySegment openDataset(DatasetRef src, int flags, Arena arena) {
        MemorySegment sourcePath = arena.allocateFrom(src.toGdalIdentifier());
        MemorySegment dataset = GdalGenerated.GDALOpenEx(
                sourcePath,
                flags,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL
        );
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open source dataset: " + src.identifier());
        }
        return dataset;
    }

    /**
     * Resolves a writable raster driver. English + 解析可写栅格驱动。
     *
     * @param driverShortName driver short name / 驱动短名
     * @param arena           arena for transient strings / 临时字符串的 Arena
     * @return native driver handle / 本地驱动句柄
     * @throws IllegalArgumentException if not found or not writable / 不存在或不可写时抛出
     */
    private static MemorySegment resolveRasterDriver(String driverShortName, Arena arena) {
        MemorySegment driverName = arena.allocateFrom(driverShortName);
        MemorySegment driver = GdalGenerated.GDALGetDriverByName(driverName);
        if (!CStrings.isNull(driver)) {
            return driver;
        }
        List<String> available = listWritableRasterDrivers().stream().map(RasterDriverInfo::shortName).toList();
        throw new IllegalArgumentException(
                "Raster driver not found or not writable: '" + driverShortName + "'. Available drivers: " + available
        );
    }

    /**
     * Reads a metadata item. English + 读取元数据项。
     */
    private static String readMetadataItem(MemorySegment majorObject, String key, Arena arena) {
        MemorySegment keyCString = arena.allocateFrom(key);
        return CStrings.fromCString(GdalGenerated.GDALGetMetadataItem(majorObject, keyCString, MemorySegment.NULL)).trim();
    }

    /**
     * Checks a YES/TRUE/1 metadata flag. English + 判断元数据开关是否为真。
     */
    private static boolean isMetadataTrue(MemorySegment majorObject, String key, Arena arena) {
        String value = readMetadataItem(majorObject, key, arena);
        return "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Parses extension metadata. English + 解析扩展名元数据。
     */
    private static List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        String[] split = raw.split("[,;\\s]+");
        for (String extension : split) {
            if (extension == null) {
                continue;
            }
            String trimmed = extension.trim();
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed);
            }
        }
        return List.copyOf(extensions);
    }

    /**
     * Applies bundled global config options. English + 应用内置全局配置项。
     */
    private static void applyConfig(NativeBundleInfo bundleInfo) {
        try (Arena arena = Arena.ofConfined()) {
            for (Map.Entry<String, Path> entry : NativeBundleRuntimeConfig.globalConfigOptions(bundleInfo).entrySet()) {
                setConfigOption(arena, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Sets one config option. English + 设置单个配置项。
     */
    private static void setConfigOption(Arena arena, String key, Path value) {
        if (value == null) {
            return;
        }

        MemorySegment keyString = arena.allocateFrom(key);
        MemorySegment valueString = arena.allocateFrom(value.toAbsolutePath().toString());
        GdalGenerated.CPLSetConfigOption(keyString, valueString);
        System.setProperty(key, value.toAbsolutePath().toString());
    }

    /**
     * Native free callback. English + 本地资源释放回调。
     */
    @FunctionalInterface
    private interface NativeFree {
        /**
         * Frees a native segment. English + 释放本地内存段。
         *
         * @param segment native handle, may be null / 本地句柄，可为空
         */
        void free(MemorySegment segment);
    }

    /**
     * Frees best-effort. English + 尽力释放，忽略释放异常。
     */
    private static void freeQuietly(MemorySegment segment, NativeFree free) {
        if (CStrings.isNull(segment)) {
            return;
        }
        try {
            free.free(segment);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause from the utility call.
        }
    }

    /**
     * Builds CLI args with input. English + 构建带输入的命令行参数。
     */
    private static List<String> withInputArg(DatasetRef input, String... args) {
        Objects.requireNonNull(input, "input must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>();
        if (args != null) {
            commandLineArgs.addAll(List.of(args));
        }
        commandLineArgs.add("-i");
        commandLineArgs.add(input.toGdalIdentifier());
        return commandLineArgs;
    }

    /**
     * Builds CLI args with input and output. English + 构建带输入与输出的命令行参数。
     */
    private static List<String> withInputOutputArgs(DatasetRef input, DatasetRef output, String... args) {
        Objects.requireNonNull(output, "output must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>(withInputArg(input, args));
        commandLineArgs.add("-o");
        commandLineArgs.add(output.toGdalIdentifier());
        return commandLineArgs;
    }

    /**
     * Builds CLI args with multiple inputs and output. English + 构建多输入单输出的命令行参数。
     */
    private static List<String> withInputOutputArgs(List<DatasetRef> inputs, DatasetRef output, String... args) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(output, "output must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>();
        if (args != null) {
            commandLineArgs.addAll(List.of(args));
        }
        for (DatasetRef input : inputs) {
            commandLineArgs.add("-i");
            commandLineArgs.add(input.toGdalIdentifier());
        }
        commandLineArgs.add("-o");
        commandLineArgs.add(output.toGdalIdentifier());
        return commandLineArgs;
    }

    /**
     * Builds CLI args with input, zones and output. English + 构建带输入、分区与输出的命令行参数。
     */
    private static List<String> withInputZonesOutputArgs(
            DatasetRef input,
            DatasetRef zones,
            DatasetRef output,
            String... args
    ) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(zones, "zones must not be null");
        Objects.requireNonNull(output, "output must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>();
        if (args != null) {
            commandLineArgs.addAll(List.of(args));
        }
        commandLineArgs.add("-i");
        commandLineArgs.add(input.toGdalIdentifier());
        commandLineArgs.add("--zones");
        commandLineArgs.add(zones.toGdalIdentifier());
        commandLineArgs.add("-o");
        commandLineArgs.add(output.toGdalIdentifier());
        return commandLineArgs;
    }

    /**
     * Closes a dataset best-effort. English + 尽力关闭数据集。
     */
    private static void closeDatasetQuietly(MemorySegment dataset) {
        if (CStrings.isNull(dataset)) {
            return;
        }
        try {
            GdalGenerated.GDALClose(dataset);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause from the utility call.
        }
    }

    /**
     * Rethrows progress-callback failure. English + 重新抛出进度回调中的失败。
     */
    private static void throwIfCallbackFailed(ProgressBridge.ProgressHandle progressHandle) {
        RuntimeException callbackFailure = progressHandle.callbackFailure();
        if (callbackFailure != null) {
            throw callbackFailure;
        }
    }
}
