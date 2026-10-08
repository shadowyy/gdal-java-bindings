package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.DatasetRef;
import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrDriverInfo;
import ch.so.agi.gdal.ffm.OgrFeature;
import ch.so.agi.gdal.ffm.OgrFieldDefinition;
import ch.so.agi.gdal.ffm.OgrFieldType;
import ch.so.agi.gdal.ffm.OgrGeometry;
import ch.so.agi.gdal.ffm.OgrLayerDefinition;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriteSpec;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import ch.so.agi.gdal.ffm.OgrOpenOptions;
import ch.so.agi.gdal.ffm.OgrWriteMode;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal FFM runtime behind the public {@code Ogr} facade.
 * <p>
 * 公开 {@code Ogr} 门面背后的内部 FFM 运行时，封装矢量数据的打开、创建、读写与驱动查询。
 * <p>
 * This is an internal API, not public. External callers should use {@code ch.so.agi.gdal.ffm.Ogr}.
 * <p>
 * 这是内部 API，不是公开 API。对外请使用 {@code ch.so.agi.gdal.ffm.Ogr}。
 */
public final class OgrRuntime {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    private static final int GDAL_OF_UPDATE = 0x01;
    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;

    private static final int OGRERR_NONE = 0;
    private static final int WKB_BYTE_ORDER_NDR = 1;
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int WKB_HEADER_SIZE = 5;
    private static final int EWKB_SRID_SIZE = 4;

    private static final String DRIVER_CAPABILITY_CREATE_DATA_SOURCE = "CreateDataSource";
    private static final String DRIVER_CAPABILITY_DELETE_DATA_SOURCE = "DeleteDataSource";

    private static final String MD_DCAP_VECTOR = "DCAP_VECTOR";
    private static final String MD_DCAP_CREATE = "DCAP_CREATE";
    private static final String MD_DMD_EXTENSIONS = "DMD_EXTENSIONS";

    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的工具类构造器。
     */
    private OgrRuntime() {
    }

    /**
     * Opens a local vector dataset in read-only mode.
     * <p>
     * 以只读方式打开本地矢量数据集。
     *
     * @param path        local dataset path, must not be {@code null} / 本地数据集路径，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        return open(DatasetRef.local(path), openOptions, GdalConfig.empty());
    }

    /**
     * Opens a referenced dataset with open options and GDAL config.
     * <p>
     * 按数据集引用、打开选项与 GDAL 配置打开矢量数据源。
     *
     * @param datasetRef  dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @param config      GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    public static OgrDataSource open(DatasetRef datasetRef, Map<String, String> openOptions, GdalConfig config) {
        return open(datasetRef, openOptions, config, false);
    }

    /**
     * Creates a vector dataset at a local path.
     * <p>
     * 在本地路径创建新的矢量数据集。
     *
     * @param path                   local dataset path, must not be {@code null} / 本地数据集路径，不能为 {@code null}
     * @param driverShortName        driver short name, must not be {@code null} or blank / 驱动短名，不能为 {@code null} 或空
     * @param writeMode              behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @param datasetCreationOptions driver creation options, must not be {@code null} / 驱动创建选项，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException     if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver name is blank, unknown, or the target handling fails /
     *                                  驱动名为空、未知或目标处理失败时抛出
     */
    public static OgrDataSource create(
            Path path,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        return create(DatasetRef.local(path), driverShortName, writeMode, datasetCreationOptions, GdalConfig.empty());
    }

    /**
     * Creates a vector dataset for a dataset reference with GDAL config.
     * <p>
     * 按数据集引用与 GDAL 配置创建新的矢量数据集。
     *
     * @param datasetRef             dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param driverShortName        driver short name, must not be {@code null} or blank / 驱动短名，不能为 {@code null} 或空
     * @param writeMode              behavior when the target exists, must not be {@code null} / 目标已存在时的处理策略，不能为 {@code null}
     * @param datasetCreationOptions driver creation options, must not be {@code null} / 驱动创建选项，不能为 {@code null}
     * @param config                 GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @return open writable datasource, must be closed by the caller / 已打开的可写数据源，调用方负责关闭
     * @throws NullPointerException     if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver name is blank, unknown, or the target handling fails /
     *                                  驱动名为空、未知或目标处理失败时抛出
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
        ensureInitialized();

        String normalizedDriverShortName = driverShortName.trim();
        if (normalizedDriverShortName.isEmpty()) {
            throw new IllegalArgumentException("driverShortName must not be blank");
        }

        String datasetIdentifier = datasetRef.toGdalIdentifier();
        Path absolutePath = datasetRef.isLocalPath() ? datasetRef.localPath() : null;
        boolean datasetExists = absolutePath != null && Files.exists(absolutePath);

        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(config)) {
            if (datasetExists) {
                switch (writeMode) {
                    case FAIL_IF_EXISTS -> throw new IllegalArgumentException(
                            "Target dataset already exists: " + absolutePath
                    );
                    case OVERWRITE -> {
                        MemorySegment driver = resolveDriverByName(normalizedDriverShortName);
                        deleteDataSource(driver, datasetIdentifier);
                    }
                    case APPEND -> {
                        return open(
                                datasetRef,
                                Map.of(OgrOpenOptions.ALLOWED_DRIVERS, normalizedDriverShortName),
                                config,
                                true
                        );
                    }
                    default -> throw new IllegalStateException("Unhandled write mode: " + writeMode);
                }
            }

            if (!datasetExists && writeMode == OgrWriteMode.APPEND && !datasetRef.isLocalPath()) {
                try {
                    return open(
                            datasetRef,
                            Map.of(OgrOpenOptions.ALLOWED_DRIVERS, normalizedDriverShortName),
                            config,
                            true
                    );
                } catch (RuntimeException ignoredOpenFailure) {
                    // Fall through to create when remote/non-file append target does not already exist.
                }
            }

            MemorySegment driver = resolveDriverByName(normalizedDriverShortName);
            MemorySegment dataset = createDataSource(driver, datasetIdentifier, datasetCreationOptions);
            return new NativeOgrDataSource(datasetIdentifier, dataset, true);
        }
    }

    /**
     * Lists vector drivers supporting data-source creation.
     * <p>
     * 列出支持创建数据源的矢量驱动。
     *
     * @return sorted immutable driver list, never {@code null} / 排序后的不可变驱动列表，永不为 {@code null}
     */
    public static List<OgrDriverInfo> listWritableVectorDrivers() {
        ensureInitialized();

        int driverCount = GdalGenerated.OGRGetDriverCount();
        if (driverCount <= 0) {
            return List.of();
        }

        List<OgrDriverInfo> writableDrivers = new ArrayList<>();
        for (int i = 0; i < driverCount; i++) {
            MemorySegment ogrDriver = GdalGenerated.OGRGetDriver(i);
            if (CStrings.isNull(ogrDriver)) {
                continue;
            }

            String shortName = CStrings.fromCString(GdalGenerated.OGR_Dr_GetName(ogrDriver)).trim();
            if (shortName.isEmpty()) {
                continue;
            }

            boolean canCreate = testDriverCapability(ogrDriver, DRIVER_CAPABILITY_CREATE_DATA_SOURCE);
            if (!canCreate) {
                continue;
            }

            String longName = shortName;
            List<String> extensions = List.of();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment shortNameCString = arena.allocateFrom(shortName);
                MemorySegment gdalDriver = GdalGenerated.GDALGetDriverByName(shortNameCString);
                if (!CStrings.isNull(gdalDriver)) {
                    if (!isMetadataTrue(gdalDriver, MD_DCAP_VECTOR, arena)) {
                        continue;
                    }
                    String candidateLongName = CStrings.fromCString(
                            GdalGenerated.GDALGetDriverLongName(gdalDriver)
                    ).trim();
                    if (!candidateLongName.isEmpty()) {
                        longName = candidateLongName;
                    }
                    extensions = parseExtensions(readMetadataItem(gdalDriver, MD_DMD_EXTENSIONS, arena));
                }
            }

            writableDrivers.add(new OgrDriverInfo(shortName, longName, extensions, true, true));
        }

        writableDrivers.sort((a, b) -> a.shortName().compareToIgnoreCase(b.shortName()));
        return List.copyOf(writableDrivers);
    }

    /**
     * Opens a dataset with explicit writable flag.
     * <p>
     * 按可写标志打开数据集的内部实现。
     *
     * @param datasetRef  dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param openOptions driver open options, must not be {@code null} / 驱动打开选项，不能为 {@code null}
     * @param config      GDAL config, must not be {@code null} / GDAL 配置，不能为 {@code null}
     * @param writable    {@code true} to request update access / 是否请求更新权限
     * @return open datasource, must be closed by the caller / 已打开的数据源，调用方负责关闭
     * @throws NullPointerException if any required argument is {@code null} / 任一必要参数为 {@code null} 时抛出
     */
    private static OgrDataSource open(
            DatasetRef datasetRef,
            Map<String, String> openOptions,
            GdalConfig config,
            boolean writable
    ) {
        Objects.requireNonNull(datasetRef, "datasetRef must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        Objects.requireNonNull(config, "config must not be null");
        ensureInitialized();

        OgrOptions.OpenOptions parsedOpenOptions = OgrOptions.parseOpenOptions(openOptions);
        String[] allowedDrivers = parsedOpenOptions.allowedDrivers().toArray(String[]::new);
        String[] datasetOptions = parsedOpenOptions.datasetOptions().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        int openFlags = GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR;
        if (writable) {
            openFlags |= GDAL_OF_UPDATE;
        }

        GdalGenerated.CPLErrorReset();
        MemorySegment dataset;
        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(config);
             Arena arena = Arena.ofConfined()) {
            MemorySegment sourcePath = arena.allocateFrom(datasetRef.toGdalIdentifier());
            MemorySegment allowedDriversArgv = allowedDrivers.length == 0
                    ? MemorySegment.NULL
                    : CArgv.toCStringArray(allowedDrivers, arena);
            MemorySegment openOptionsArgv = datasetOptions.length == 0
                    ? MemorySegment.NULL
                    : CArgv.toCStringArray(datasetOptions, arena);

            dataset = GdalGenerated.GDALOpenEx(
                    sourcePath,
                    openFlags,
                    allowedDriversArgv,
                    openOptionsArgv,
                    MemorySegment.NULL
            );
        }
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open OGR datasource: " + datasetRef.identifier());
        }
        return new NativeOgrDataSource(datasetRef.toGdalIdentifier(), dataset, writable);
    }

    /**
     * Resolves an OGR driver. English + 解析 OGR 驱动。
     *
     * @param driverShortName driver short name / 驱动短名
     * @return native driver handle / 本地驱动句柄
     * @throws IllegalArgumentException if not found or not writable / 不存在或不可写时抛出
     */
    private static MemorySegment resolveDriverByName(String driverShortName) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment driverName = arena.allocateFrom(driverShortName);
            MemorySegment driver = GdalGenerated.OGRGetDriverByName(driverName);
            if (!CStrings.isNull(driver)) {
                return driver;
            }
        }

        List<String> available = listWritableVectorDrivers().stream().map(OgrDriverInfo::shortName).toList();
        throw new IllegalArgumentException(
                "OGR driver not found or not writable: '" + driverShortName + "'. Available drivers: " + available
        );
    }

    /**
     * Creates a data source. English + 创建数据源。
     *
     * @param driver                 native driver handle / 本地驱动句柄
     * @param datasetIdentifier      GDAL dataset identifier / GDAL 数据集标识
     * @param datasetCreationOptions creation options / 创建选项
     * @return native dataset handle / 本地数据集句柄
     */
    private static MemorySegment createDataSource(
            MemorySegment driver,
            String datasetIdentifier,
            Map<String, String> datasetCreationOptions
    ) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathCString = arena.allocateFrom(datasetIdentifier);
            String[] options = toKeyValueArray(datasetCreationOptions);
            MemorySegment optionsArgv = options.length == 0 ? MemorySegment.NULL : CArgv.toCStringArray(options, arena);

            MemorySegment dataset = GdalGenerated.OGR_Dr_CreateDataSource(driver, pathCString, optionsArgv);
            if (CStrings.isNull(dataset)) {
                throw GdalErrors.lastError("Failed to create OGR datasource: " + datasetIdentifier);
            }
            return dataset;
        }
    }

    /**
     * Deletes a data source. English + 删除数据源。
     *
     * @param driver            native driver handle / 本地驱动句柄
     * @param datasetIdentifier GDAL dataset identifier / GDAL 数据集标识
     * @throws IllegalArgumentException if the driver lacks delete capability / 驱动不支持删除时抛出
     */
    private static void deleteDataSource(MemorySegment driver, String datasetIdentifier) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathCString = arena.allocateFrom(datasetIdentifier);
            if (!testDriverCapability(driver, DRIVER_CAPABILITY_DELETE_DATA_SOURCE)) {
                throw new IllegalArgumentException(
                        "Driver does not support overwrite/delete for existing dataset: " + datasetIdentifier
                );
            }
            int errorCode = GdalGenerated.OGR_Dr_DeleteDataSource(driver, pathCString);
            throwIfOgrError(errorCode, "Failed to overwrite existing OGR datasource: " + datasetIdentifier);
        }
    }

    /**
     * Tests a driver capability. English + 检测驱动能力。
     */
    private static boolean testDriverCapability(MemorySegment driver, String capability) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capabilityCString = arena.allocateFrom(capability);
            return GdalGenerated.OGR_Dr_TestCapability(driver, capabilityCString) != 0;
        }
    }

    /**
     * Reads a driver metadata item. English + 读取驱动元数据项。
     */
    private static String readMetadataItem(MemorySegment driver, String key, Arena arena) {
        MemorySegment keyCString = arena.allocateFrom(key);
        return CStrings.fromCString(GdalGenerated.GDALGetMetadataItem(driver, keyCString, MemorySegment.NULL)).trim();
    }

    /**
     * Checks a YES/TRUE/1 metadata flag. English + 判断元数据开关是否为真。
     */
    private static boolean isMetadataTrue(MemorySegment driver, String key, Arena arena) {
        String value = readMetadataItem(driver, key, arena);
        return "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Parses extension metadata. English + 解析扩展名元数据。
     */
    private static List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String[] split = raw.split("[,;\\s]+");
        LinkedHashSet<String> extensions = new LinkedHashSet<>();
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
     * Converts options to KEY=VALUE array. English + 转换为 KEY=VALUE 数组。
     */
    private static String[] toKeyValueArray(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return new String[0];
        }
        List<String> keyValues = new ArrayList<>(options.size());
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            keyValues.add(key.trim() + "=" + value);
        }
        return keyValues.toArray(String[]::new);
    }

    /**
     * Native {@code OgrDataSource} implementation.
     * <p>
     * 基于本地数据集句柄的 {@code OgrDataSource} 实现。
     */
    private static final class NativeOgrDataSource implements OgrDataSource {
        private final String sourcePath;
        private final MemorySegment dataset;
        private final boolean writable;
        private volatile boolean closed;

        /**
         * Creates a wrapper. English + 创建数据源包装器。
         *
         * @param sourcePath source identifier for diagnostics / 用于诊断的来源标识
         * @param dataset    native dataset handle / 本地数据集句柄
         * @param writable   whether writing is allowed / 是否允许写入
         */
        private NativeOgrDataSource(String sourcePath, MemorySegment dataset, boolean writable) {
            this.sourcePath = sourcePath;
            this.dataset = dataset;
            this.writable = writable;
        }

        /**
         * Lists layer definitions.
         * <p>
         * 列出所有图层定义。
         *
         * @return immutable layer definitions, never {@code null} / 不可变图层定义列表，永不为 {@code null}
         * @throws IllegalStateException if the datasource is closed / 数据源已关闭时抛出
         */
        @Override
        public synchronized List<OgrLayerDefinition> listLayers() {
            ensureOpen();

            int layerCount = GdalGenerated.GDALDatasetGetLayerCount(dataset);
            if (layerCount <= 0) {
                return List.of();
            }

            List<OgrLayerDefinition> definitions = new ArrayList<>(layerCount);
            for (int i = 0; i < layerCount; i++) {
                MemorySegment layer = GdalGenerated.GDALDatasetGetLayer(dataset, i);
                if (CStrings.isNull(layer)) {
                    continue;
                }
                definitions.add(describeLayer(layer));
            }
            return List.copyOf(definitions);
        }

        /**
         * Opens a layer reader with filters.
         * <p>
         * 按选项打开图层读取器（含过滤与字段投影）。
         *
         * @param layerName layer name, blank means the first layer / 图层名，为空表示首个图层
         * @param options   reader options, may be {@code null} / 读取选项，可为 {@code null}
         * @return open reader, must be closed by the caller / 已打开的读取器，调用方负责关闭
         * @throws IllegalStateException    if the datasource is closed / 数据源已关闭时抛出
         * @throws IllegalArgumentException if the layer or selected fields are missing / 图层或字段不存在时抛出
         */
        @Override
        public synchronized OgrLayerReader openReader(String layerName, Map<String, String> options) {
            ensureOpen();

            Map<String, String> safeOptions = options == null ? Map.of() : options;
            OgrOptions.ReaderOptions parsedOptions = OgrOptions.parseReaderOptions(safeOptions);

            MemorySegment layer = resolveLayer(layerName);
            if (CStrings.isNull(layer)) {
                throw new IllegalArgumentException(
                        "Layer '" + layerName + "' was not found in datasource '" + sourcePath + "'."
                );
            }

            OgrLayerDefinition layerDefinition = describeLayer(layer);
            int[] projectedFieldIndices = resolveProjectedFieldIndices(layerDefinition, parsedOptions);

            configureLayer(layer, layerDefinition, parsedOptions);
            GdalGenerated.OGR_L_ResetReading(layer);

            Long limit = parsedOptions.limit();
            long rowLimit = limit == null ? Long.MAX_VALUE : limit;
            return new NativeOgrLayerReader(this, layer, layerDefinition, projectedFieldIndices, rowLimit);
        }

        /**
         * Opens a layer writer for create/overwrite/append.
         * <p>
         * 按写入规格打开图层写入器（新建 / 覆盖 / 追加）。
         *
         * @param spec write specification, must not be {@code null} / 写入规格，不能为 {@code null}
         * @return open writer, must be closed by the caller / 已打开的写入器，调用方负责关闭
         * @throws IllegalStateException    if opened read-only or datasource is closed / 只读打开或已关闭时抛出
         * @throws NullPointerException     if {@code spec} is {@code null} / {@code spec} 为 {@code null} 时抛出
         * @throws IllegalArgumentException if the layer state or schema is incompatible / 图层状态或模式不兼容时抛出
         */
        @Override
        public synchronized OgrLayerWriter openWriter(OgrLayerWriteSpec spec) {
            ensureOpen();
            if (!writable) {
                throw new IllegalStateException(
                        "Datasource was opened read-only. Use Ogr.create(...) or open in write mode for writing."
                );
            }
            Objects.requireNonNull(spec, "spec must not be null");

            Map<String, String> effectiveLayerCreationOptions = withOptionalIdFieldNames(
                    spec.layerCreationOptions(),
                    spec.fidFieldName(),
                    spec.geometryFieldName()
            );

            MemorySegment layer = resolveLayerOrNull(spec.layerName());
            switch (spec.writeMode()) {
                case FAIL_IF_EXISTS -> {
                    if (!CStrings.isNull(layer)) {
                        throw new IllegalArgumentException(
                                "Layer already exists in target datasource: " + spec.layerName()
                        );
                    }
                }
                case OVERWRITE -> {
                    if (!CStrings.isNull(layer)) {
                        deleteLayer(spec.layerName());
                        layer = MemorySegment.NULL;
                    }
                }
                case APPEND -> {
                    // Keep existing layer if present.
                }
                default -> throw new IllegalStateException("Unhandled write mode: " + spec.writeMode());
            }

            Map<String, Integer> boundFieldIndexesByRequestedName = null;
            if (CStrings.isNull(layer)) {
                layer = createLayer(spec.layerName(), spec.geometryTypeCode(), effectiveLayerCreationOptions, spec.fields());
                OgrLayerDefinition layerDefinition = describeLayer(layer);
                boundFieldIndexesByRequestedName =
                        bindCreatedLayerFields(spec.layerName(), spec.fields(), layerDefinition);
                int geometryFieldIndex = resolveGeometryFieldIndex(layer, spec.geometryFieldName());
                return new NativeOgrLayerWriter(
                        this,
                        layer,
                        layerDefinition,
                        geometryFieldIndex,
                        boundFieldIndexesByRequestedName
                );
            }

            validateExistingLayerSchema(layer, spec.fields());
            OgrLayerDefinition layerDefinition = describeLayer(layer);
            int geometryFieldIndex = resolveGeometryFieldIndex(layer, spec.geometryFieldName());
            return new NativeOgrLayerWriter(this, layer, layerDefinition, geometryFieldIndex, null);
        }

        /**
         * Closes the native dataset idempotently.
         * <p>
         * 幂等地关闭本地数据集。
         */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeDatasetQuietly(dataset);
        }

        /**
         * Ensures the datasource is open. English + 确保数据源仍处于打开状态。
         *
         * @throws IllegalStateException if closed / 已关闭时抛出
         */
        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Datasource is closed: " + sourcePath);
            }
        }

        /**
         * Resolves a layer, defaulting to the first one. English + 解析图层，为空时取首个。
         *
         * @param layerName layer name, may be {@code null} or blank / 图层名，可为 {@code null} 或空
         * @return native layer handle / 本地图层句柄
         * @throws IllegalArgumentException if no readable layer exists or lookup fails / 无可读图层或查找失败时抛出
         */
        private MemorySegment resolveLayer(String layerName) {
            if (layerName == null || layerName.isBlank()) {
                MemorySegment firstLayer = GdalGenerated.GDALDatasetGetLayer(dataset, 0);
                if (CStrings.isNull(firstLayer)) {
                    throw new IllegalArgumentException("Datasource contains no readable OGR layers: " + sourcePath);
                }
                return firstLayer;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                return GdalGenerated.GDALDatasetGetLayerByName(dataset, layerNameCString);
            }
        }

        /**
         * Resolves a layer or NULL. English + 解析图层，不存在时返回 NULL。
         */
        private MemorySegment resolveLayerOrNull(String layerName) {
            if (layerName == null || layerName.isBlank()) {
                return MemorySegment.NULL;
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                return GdalGenerated.GDALDatasetGetLayerByName(dataset, layerNameCString);
            }
        }

        /**
         * Deletes a layer by name. English + 按名称删除图层。
         */
        private void deleteLayer(String layerName) {
            int index = findLayerIndex(layerName);
            if (index < 0) {
                return;
            }
            GdalGenerated.CPLErrorReset();
            int errorCode = GdalGenerated.OGR_DS_DeleteLayer(dataset, index);
            throwIfOgrError(errorCode, "Failed to delete existing layer '" + layerName + "'");
        }

        /**
         * Finds a layer index. English + 查找图层索引。
         *
         * @param layerName layer name / 图层名
         * @return index, or -1 when missing / 索引；不存在时返回 -1
         */
        private int findLayerIndex(String layerName) {
            int layerCount = GdalGenerated.GDALDatasetGetLayerCount(dataset);
            for (int i = 0; i < layerCount; i++) {
                MemorySegment layer = GdalGenerated.GDALDatasetGetLayer(dataset, i);
                if (CStrings.isNull(layer)) {
                    continue;
                }
                String currentName = CStrings.fromCString(GdalGenerated.OGR_L_GetName(layer));
                if (layerName.equals(currentName)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Creates a layer with fields.
         * <p>
         * 创建带字段定义的图层。
         *
         * @param layerName            layer name / 图层名
         * @param geometryTypeCode     geometry type code, must not be {@code null} / 几何类型码，不能为 {@code null}
         * @param layerCreationOptions creation options / 创建选项
         * @param fields               field definitions / 字段定义
         * @return native layer handle / 本地图层句柄
         * @throws IllegalArgumentException if the geometry type is missing / 缺少几何类型时抛出
         */
        private MemorySegment createLayer(
                String layerName,
                Integer geometryTypeCode,
                Map<String, String> layerCreationOptions,
                List<OgrFieldDefinition> fields
        ) {
            int effectiveGeometryTypeCode = geometryTypeCode == null ? 0 : geometryTypeCode;
            if (geometryTypeCode == null) {
                throw new IllegalArgumentException(
                        "geometryTypeCode is required when creating a new layer: " + layerName
                );
            }

            GdalGenerated.CPLErrorReset();
            MemorySegment layer;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                String[] options = toKeyValueArray(layerCreationOptions);
                MemorySegment layerOptionsArgv = options.length == 0
                        ? MemorySegment.NULL
                        : CArgv.toCStringArray(options, arena);

                layer = GdalGenerated.OGR_DS_CreateLayer(
                        dataset,
                        layerNameCString,
                        MemorySegment.NULL,
                        effectiveGeometryTypeCode,
                        layerOptionsArgv
                );
            }
            if (CStrings.isNull(layer)) {
                throw GdalErrors.lastError("Failed to create OGR layer: " + layerName);
            }

            addFields(layer, fields);
            return layer;
        }

        /**
         * Adds fields to a layer. English + 为图层添加字段。
         *
         * @param layer  native layer handle / 本地图层句柄
         * @param fields field definitions, may be {@code null} / 字段定义，可为 {@code null}
         * @throws IllegalArgumentException on blank names or UNKNOWN type / 名称为空或类型为 UNKNOWN 时抛出
         */
        private void addFields(MemorySegment layer, List<OgrFieldDefinition> fields) {
            if (fields == null || fields.isEmpty()) {
                return;
            }

            for (OgrFieldDefinition field : fields) {
                String fieldName = field.name() == null ? "" : field.name().trim();
                if (fieldName.isEmpty()) {
                    throw new IllegalArgumentException("Field name must not be blank");
                }

                OgrFieldType fieldType = field.type();
                if (fieldType == OgrFieldType.UNKNOWN) {
                    throw new IllegalArgumentException(
                            "Field type UNKNOWN is not writable for field '" + fieldName + "'"
                    );
                }

                GdalGenerated.CPLErrorReset();
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment fieldNameCString = arena.allocateFrom(fieldName);
                    MemorySegment fieldDefn = GdalGenerated.OGR_Fld_Create(fieldNameCString, fieldType.nativeCode());
                    if (CStrings.isNull(fieldDefn)) {
                        throw GdalErrors.lastError("Failed to allocate field definition for: " + fieldName);
                    }
                    try {
                        int errorCode = GdalGenerated.OGR_L_CreateField(layer, fieldDefn, 1);
                        throwIfOgrError(errorCode, "Failed to create field: " + fieldName);
                    } finally {
                        GdalGenerated.OGR_Fld_Destroy(fieldDefn);
                    }
                }
            }
        }

        /**
         * Validates append schema. English + 校验追加写入的模式兼容性。
         */
        private void validateExistingLayerSchema(MemorySegment layer, List<OgrFieldDefinition> requestedFields) {
            if (requestedFields == null || requestedFields.isEmpty()) {
                return;
            }

            OgrLayerDefinition existing = describeLayer(layer);
            Map<String, OgrFieldType> byLowercaseName = new LinkedHashMap<>();
            for (OgrFieldDefinition existingField : existing.fields()) {
                byLowercaseName.put(existingField.name().toLowerCase(Locale.ROOT), existingField.type());
            }
            String existingFieldNames = formatFieldNames(existing.fields());

            for (OgrFieldDefinition requestedField : requestedFields) {
                String lower = requestedField.name().toLowerCase(Locale.ROOT);
                OgrFieldType existingType = byLowercaseName.get(lower);
                if (existingType == null) {
                    throw new IllegalArgumentException(
                            "Append target layer is missing field '" + requestedField.name()
                                    + "'. Existing target field names: " + existingFieldNames
                                    + ". APPEND expects the actual persisted target field names."
                    );
                }
                if (requestedField.type() != OgrFieldType.UNKNOWN && existingType != requestedField.type()) {
                    throw new IllegalArgumentException(
                            "Append field type mismatch for '" + requestedField.name() + "': expected "
                                    + requestedField.type() + " but layer has " + existingType
                    );
                }
            }
        }

        /**
         * Binds requested fields to created indexes. English + 绑定请求字段到创建后的索引。
         */
        private Map<String, Integer> bindCreatedLayerFields(
                String requestedLayerName,
                List<OgrFieldDefinition> requestedFields,
                OgrLayerDefinition createdLayerDefinition
        ) {
            if (requestedFields == null || requestedFields.isEmpty()) {
                return Map.of();
            }

            List<OgrFieldDefinition> actualFields = createdLayerDefinition.fields();
            if (requestedFields.size() != actualFields.size()) {
                throw new IllegalStateException(
                        "Created target layer schema differs from requested schema for layer '"
                                + requestedLayerName + "'"
                                + " (actual layer name: '" + createdLayerDefinition.name() + "'). Requested fields ("
                                + requestedFields.size() + "): " + formatFieldNames(requestedFields)
                                + ". Actual fields (" + actualFields.size() + "): " + formatFieldNames(actualFields)
                );
            }

            Map<String, Integer> bindings = new LinkedHashMap<>();
            for (int i = 0; i < requestedFields.size(); i++) {
                String requestedFieldName = requestedFields.get(i).name();
                String lower = requestedFieldName.toLowerCase(Locale.ROOT);
                Integer previous = bindings.put(lower, i);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate requested field name while binding created layer schema: " + requestedFieldName
                    );
                }
            }
            return Map.copyOf(bindings);
        }

        /**
         * Resolves a geometry field index. English + 解析几何字段索引。
         *
         * @param layer             native layer handle / 本地图层句柄
         * @param geometryFieldName geometry field name, may be {@code null} / 几何字段名，可为 {@code null}
         * @return index, or -1 when unset / 索引；未指定时返回 -1
         * @throws IllegalArgumentException if the named field is missing / 指定字段不存在时抛出
         */
        private int resolveGeometryFieldIndex(MemorySegment layer, String geometryFieldName) {
            if (geometryFieldName == null || geometryFieldName.isBlank()) {
                return -1;
            }

            MemorySegment layerDefinition = GdalGenerated.OGR_L_GetLayerDefn(layer);
            if (CStrings.isNull(layerDefinition)) {
                return -1;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geometryFieldNameCString = arena.allocateFrom(geometryFieldName);
                int geometryFieldIndex = GdalGenerated.OGR_FD_GetGeomFieldIndex(layerDefinition, geometryFieldNameCString);
                if (geometryFieldIndex < 0) {
                    throw new IllegalArgumentException(
                            "Geometry field '" + geometryFieldName + "' does not exist in target layer"
                    );
                }
                return geometryFieldIndex;
            }
        }

        /**
         * Merges FID/geometry names into creation options. English + 合并 FID 与几何字段名到创建选项。
         */
        private static Map<String, String> withOptionalIdFieldNames(
                Map<String, String> layerCreationOptions,
                String fidFieldName,
                String geometryFieldName
        ) {
            Map<String, String> normalized = new LinkedHashMap<>();
            if (layerCreationOptions != null) {
                normalized.putAll(layerCreationOptions);
            }
            if (fidFieldName != null && !fidFieldName.isBlank()) {
                normalized.putIfAbsent("FID", fidFieldName.trim());
            }
            if (geometryFieldName != null && !geometryFieldName.isBlank()) {
                normalized.putIfAbsent("GEOMETRY_NAME", geometryFieldName.trim());
            }
            return Map.copyOf(normalized);
        }

        /**
         * Formats field names for diagnostics. English + 格式化字段名用于诊断信息。
         */
        private static String formatFieldNames(List<OgrFieldDefinition> fields) {
            List<String> names = new ArrayList<>(fields.size());
            for (OgrFieldDefinition field : fields) {
                names.add(field.name());
            }
            return names.toString();
        }

        /**
         * Applies reader filters to a layer. English + 为图层应用读取过滤器。
         */
        private static void configureLayer(
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                OgrOptions.ReaderOptions options
        ) {
            applyIgnoredFields(layer, layerDefinition, options.selectedFieldsLowercase());
            applyAttributeFilter(layer, options.attributeFilter());
            applySpatialFilter(layer, options);
        }

        /**
         * Applies field projection via ignored fields. English + 按字段投影设置忽略字段。
         */
        private static void applyIgnoredFields(
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                Set<String> selectedFieldsLowercase
        ) {
            if (selectedFieldsLowercase.isEmpty()) {
                applyIgnoredFields(layer, List.of());
                return;
            }

            List<String> ignoredFields = new ArrayList<>();
            for (OgrFieldDefinition field : layerDefinition.fields()) {
                if (!selectedFieldsLowercase.contains(field.name().toLowerCase(Locale.ROOT))) {
                    ignoredFields.add(field.name());
                }
            }
            applyIgnoredFields(layer, ignoredFields);
        }

        /**
         * Forwards ignored fields to native. English + 透传忽略字段到本地库。
         */
        private static void applyIgnoredFields(MemorySegment layer, List<String> ignoredFields) {
            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment ignoredFieldsArgv = ignoredFields.isEmpty()
                        ? MemorySegment.NULL
                        : CArgv.toCStringArray(ignoredFields.toArray(String[]::new), arena);

                int ogrError = GdalGenerated.OGR_L_SetIgnoredFields(layer, ignoredFieldsArgv);
                throwIfOgrError(ogrError, "Failed to configure ignored fields");
            }
        }

        /**
         * Applies an attribute filter. English + 应用属性过滤。
         */
        private static void applyAttributeFilter(MemorySegment layer, String attributeFilter) {
            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment attributeFilterCString = attributeFilter == null
                        ? MemorySegment.NULL
                        : arena.allocateFrom(attributeFilter);

                int ogrError = GdalGenerated.OGR_L_SetAttributeFilter(layer, attributeFilterCString);
                throwIfOgrError(ogrError, "Failed to configure attribute filter");
            }
        }

        /**
         * Applies a bbox or WKT spatial filter. English + 应用 BBOX 或 WKT 空间过滤。
         */
        private static void applySpatialFilter(MemorySegment layer, OgrOptions.ReaderOptions options) {
            if (options.bbox() != null) {
                OgrOptions.BoundingBox bbox = options.bbox();
                GdalGenerated.OGR_L_SetSpatialFilterRect(layer, bbox.minX(), bbox.minY(), bbox.maxX(), bbox.maxY());
                return;
            }

            if (options.spatialFilterWkt() == null) {
                GdalGenerated.OGR_L_SetSpatialFilter(layer, MemorySegment.NULL);
                return;
            }

            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment wktCString = arena.allocateFrom(options.spatialFilterWkt());
                MemorySegment wktPointerPointer = arena.allocate(ValueLayout.ADDRESS);
                wktPointerPointer.set(ValueLayout.ADDRESS, 0, wktCString);

                MemorySegment geometryOut = arena.allocate(ValueLayout.ADDRESS);
                int createGeometryErr = GdalGenerated.OGR_G_CreateFromWkt(
                        wktPointerPointer,
                        MemorySegment.NULL,
                        geometryOut
                );
                throwIfOgrError(createGeometryErr, "Failed to parse spatial filter WKT");

                MemorySegment filterGeometry = geometryOut.get(ValueLayout.ADDRESS, 0);
                if (CStrings.isNull(filterGeometry)) {
                    throw GdalErrors.lastError("Failed to parse spatial filter WKT");
                }

                try {
                    GdalGenerated.OGR_L_SetSpatialFilter(layer, filterGeometry);
                } finally {
                    GdalGenerated.OGR_G_DestroyGeometry(filterGeometry);
                }
            }
        }
    }

    /**
     * Native single-iterator layer reader.
     * <p>
     * 基于本地句柄的单迭代器图层读取器。
     */
    private static final class NativeOgrLayerReader implements OgrLayerReader {
        private final NativeOgrDataSource dataSource;
        private final MemorySegment layer;
        private final OgrLayerDefinition layerDefinition;
        private final int[] projectedFieldIndices;
        private final long rowLimit;

        private boolean closed;
        private boolean iteratorCreated;
        private boolean fetched;
        private OgrFeature buffered;
        private long emitted;

        /**
         * Creates a reader. English + 创建读取器。
         *
         * @param dataSource            owning datasource / 所属数据源
         * @param layer                 native layer handle / 本地图层句柄
         * @param layerDefinition       layer definition snapshot / 图层定义快照
         * @param projectedFieldIndices projected field indexes / 投影字段索引
         * @param rowLimit              max rows to emit / 最多返回行数
         */
        private NativeOgrLayerReader(
                NativeOgrDataSource dataSource,
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                int[] projectedFieldIndices,
                long rowLimit
        ) {
            this.dataSource = dataSource;
            this.layer = layer;
            this.layerDefinition = layerDefinition;
            this.projectedFieldIndices = projectedFieldIndices;
            this.rowLimit = rowLimit;
        }

        /**
         * Returns the single supported iterator.
         * <p>
         * 返回唯一支持的迭代器。
         *
         * @return feature iterator, never {@code null} / 要素迭代器，永不为 {@code null}
         * @throws IllegalStateException if closed or already iterated / 已关闭或已迭代时抛出
         */
        @Override
        public synchronized Iterator<OgrFeature> iterator() {
            ensureOpen();
            if (iteratorCreated) {
                throw new IllegalStateException("Only a single iterator is supported per OgrLayerReader");
            }
            iteratorCreated = true;

            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return fetchNextIfNeeded() != null;
                }

                @Override
                public OgrFeature next() {
                    OgrFeature next = fetchNextIfNeeded();
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                    fetched = false;
                    buffered = null;
                    return next;
                }
            };
        }

        /**
         * Closes the reader and drops buffered state.
         * <p>
         * 关闭读取器并丢弃缓冲状态。
         */
        @Override
        public synchronized void close() {
            closed = true;
            fetched = true;
            buffered = null;
        }

        /**
         * Fetches the next feature if needed. English + 按需获取下一个要素。
         *
         * @return next feature, or {@code null} at end or limit / 下一个要素；结束或达上限时返回 {@code null}
         * @throws IllegalStateException if closed / 已关闭时抛出
         */
        private OgrFeature fetchNextIfNeeded() {
            if (fetched) {
                return buffered;
            }
            ensureOpen();
            fetched = true;

            if (emitted >= rowLimit) {
                buffered = null;
                return null;
            }

            MemorySegment nativeFeature = GdalGenerated.OGR_L_GetNextFeature(layer);
            if (CStrings.isNull(nativeFeature)) {
                buffered = null;
                return null;
            }

            try {
                buffered = toFeature(nativeFeature, layerDefinition, projectedFieldIndices);
                emitted++;
                return buffered;
            } finally {
                GdalGenerated.OGR_F_Destroy(nativeFeature);
            }
        }

        /**
         * Ensures the reader is open. English + 确保读取器仍处于打开状态。
         *
         * @throws IllegalStateException if closed / 已关闭时抛出
         */
        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Layer reader is closed");
            }
            dataSource.ensureOpen();
        }
    }

    /**
     * Native layer writer.
     * <p>
     * 基于本地句柄的图层写入器。
     */
    private static final class NativeOgrLayerWriter implements OgrLayerWriter {
        private final NativeOgrDataSource dataSource;
        private final MemorySegment layer;
        private final OgrLayerDefinition layerDefinition;
        private final int geometryFieldIndex;
        private final Map<String, Integer> boundFieldIndexesByRequestedName;

        private boolean closed;

        /**
         * Creates a writer. English + 创建写入器。
         *
         * @param dataSource                       owning datasource / 所属数据源
         * @param layer                            native layer handle / 本地图层句柄
         * @param layerDefinition                  layer definition snapshot / 图层定义快照
         * @param geometryFieldIndex               geometry field index, -1 for default / 几何字段索引，-1 表示默认
         * @param boundFieldIndexesByRequestedName bound field indexes, may be {@code null} / 绑定字段索引，可为 {@code null}
         */
        private NativeOgrLayerWriter(
                NativeOgrDataSource dataSource,
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                int geometryFieldIndex,
                Map<String, Integer> boundFieldIndexesByRequestedName
        ) {
            this.dataSource = dataSource;
            this.layer = layer;
            this.layerDefinition = layerDefinition;
            this.geometryFieldIndex = geometryFieldIndex;
            this.boundFieldIndexesByRequestedName = boundFieldIndexesByRequestedName;
        }

        /**
         * Writes one feature.
         * <p>
         * 写入单个要素（含 FID、属性与几何）。
         *
         * @param feature feature to write, must not be {@code null} / 待写入要素，不能为 {@code null}
         * @throws IllegalStateException    if closed / 已关闭时抛出
         * @throws NullPointerException     if {@code feature} is {@code null} / {@code feature} 为 {@code null} 时抛出
         * @throws IllegalArgumentException if a field or geometry is invalid / 字段或几何非法时抛出
         */
        @Override
        public synchronized void write(OgrFeature feature) {
            ensureOpen();
            Objects.requireNonNull(feature, "feature must not be null");

            GdalGenerated.CPLErrorReset();

            MemorySegment layerDefinitionHandle = GdalGenerated.OGR_L_GetLayerDefn(layer);
            if (CStrings.isNull(layerDefinitionHandle)) {
                throw GdalErrors.lastError("Failed to resolve layer definition for writing");
            }

            MemorySegment nativeFeature = GdalGenerated.OGR_F_Create(layerDefinitionHandle);
            if (CStrings.isNull(nativeFeature)) {
                throw GdalErrors.lastError("Failed to create native OGR feature");
            }

            try (Arena arena = Arena.ofConfined()) {
                if (feature.fid() >= 0) {
                    int setFidError = GdalGenerated.OGR_F_SetFID(nativeFeature, feature.fid());
                    throwIfOgrError(setFidError, "Failed to set feature FID");
                }

                writeAttributes(nativeFeature, feature.attributes(), arena);
                writeGeometry(nativeFeature, feature.geometry(), arena);

                int createFeatureError = GdalGenerated.OGR_L_CreateFeature(layer, nativeFeature);
                throwIfOgrError(createFeatureError, "Failed to write feature");
            } finally {
                GdalGenerated.OGR_F_Destroy(nativeFeature);
            }
        }

        /**
         * Closes the writer.
         * <p>
         * 关闭写入器。
         */
        @Override
        public synchronized void close() {
            closed = true;
        }

        /**
         * Ensures the writer is open. English + 确保写入器仍处于打开状态。
         *
         * @throws IllegalStateException if closed / 已关闭时抛出
         */
        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Layer writer is closed");
            }
            dataSource.ensureOpen();
        }

        /**
         * Writes attribute values. English + 写入属性值。
         */
        private void writeAttributes(
                MemorySegment nativeFeature,
                Map<String, Object> attributes,
                Arena arena
        ) {
            if (attributes == null || attributes.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }

                int fieldIndex = resolveFieldIndex(nativeFeature, fieldName, arena);

                Object value = entry.getValue();
                if (value == null) {
                    GdalGenerated.OGR_F_SetFieldNull(nativeFeature, fieldIndex);
                    continue;
                }

                if (value instanceof Boolean boolValue) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, boolValue ? 1L : 0L);
                    continue;
                }

                if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, ((Number) value).longValue());
                    continue;
                }

                if (value instanceof BigInteger bigInteger) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, bigInteger.longValue());
                    continue;
                }

                if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
                    GdalGenerated.OGR_F_SetFieldDouble(nativeFeature, fieldIndex, ((Number) value).doubleValue());
                    continue;
                }

                MemorySegment stringValue = arena.allocateFrom(value.toString());
                GdalGenerated.OGR_F_SetFieldString(nativeFeature, fieldIndex, stringValue);
            }
        }

        /**
         * Resolves a field index. English + 解析字段索引。
         *
         * @param nativeFeature native feature handle / 本地要素句柄
         * @param fieldName     field name / 字段名
         * @param arena         arena for transient strings / 临时字符串的 Arena
         * @return field index / 字段索引
         * @throws IllegalArgumentException if the field is not in the target schema / 字段不在目标模式中时抛出
         */
        private int resolveFieldIndex(MemorySegment nativeFeature, String fieldName, Arena arena) {
            if (boundFieldIndexesByRequestedName != null) {
                Integer boundFieldIndex = boundFieldIndexesByRequestedName.get(fieldName.toLowerCase(Locale.ROOT));
                if (boundFieldIndex == null) {
                    throw new IllegalArgumentException(
                            "Field '" + fieldName + "' is not part of the bound target schema for layer '"
                                    + layerDefinition.name() + "'. Available target fields: "
                                    + layerDefinition.fields().stream().map(OgrFieldDefinition::name).toList()
                    );
                }
                return boundFieldIndex;
            }

            MemorySegment fieldNameCString = arena.allocateFrom(fieldName);
            int fieldIndex = GdalGenerated.OGR_F_GetFieldIndex(nativeFeature, fieldNameCString);
            if (fieldIndex < 0) {
                throw new IllegalArgumentException(
                        "Field '" + fieldName + "' does not exist in target layer '" + layerDefinition.name() + "'"
                );
            }
            return fieldIndex;
        }

        /**
         * Writes feature geometry. English + 写入要素几何。
         */
        private void writeGeometry(
                MemorySegment nativeFeature,
                OgrGeometry geometry,
                Arena arena
        ) {
            if (geometry == null) {
                return;
            }

            byte[] wkb = normalizeToWkb(geometry.ewkb());
            MemorySegment ewkbNative = arena.allocate(wkb.length);
            MemorySegment.copy(MemorySegment.ofArray(wkb), 0, ewkbNative, 0, wkb.length);
            MemorySegment geometryOut = arena.allocate(ValueLayout.ADDRESS);
            int createGeometryErr = GdalGenerated.OGR_G_CreateFromWkb(
                    ewkbNative,
                    MemorySegment.NULL,
                    geometryOut,
                    wkb.length
            );
            throwIfOgrError(createGeometryErr, "Failed to decode feature geometry from EWKB/WKB");

            MemorySegment nativeGeometry = geometryOut.get(ValueLayout.ADDRESS, 0);
            if (CStrings.isNull(nativeGeometry)) {
                throw GdalErrors.lastError("Failed to decode feature geometry from EWKB/WKB");
            }

            try {
                int setGeometryError;
                if (geometryFieldIndex >= 0) {
                    setGeometryError = GdalGenerated.OGR_F_SetGeomField(nativeFeature, geometryFieldIndex, nativeGeometry);
                } else {
                    setGeometryError = GdalGenerated.OGR_F_SetGeometry(nativeFeature, nativeGeometry);
                }
                throwIfOgrError(setGeometryError, "Failed to set feature geometry");
            } finally {
                GdalGenerated.OGR_G_DestroyGeometry(nativeGeometry);
            }
        }

        /**
         * Strips EWKB SRID to plain WKB. English + 去除 EWKB 中的 SRID 得到纯 WKB。
         */
        private static byte[] normalizeToWkb(byte[] ewkbOrWkb) {
            if (ewkbOrWkb.length < WKB_HEADER_SIZE) {
                return ewkbOrWkb;
            }

            ByteOrder order = switch (ewkbOrWkb[0]) {
                case 0 -> ByteOrder.BIG_ENDIAN;
                case 1 -> ByteOrder.LITTLE_ENDIAN;
                default -> throw new IllegalArgumentException(
                        "Unsupported WKB byte order marker: " + ewkbOrWkb[0]
                );
            };

            ByteBuffer in = ByteBuffer.wrap(ewkbOrWkb).order(order);
            int rawType = in.getInt(1);
            if ((rawType & EWKB_SRID_FLAG) == 0) {
                return ewkbOrWkb;
            }

            if (ewkbOrWkb.length < WKB_HEADER_SIZE + EWKB_SRID_SIZE) {
                throw new IllegalArgumentException("Invalid EWKB payload with SRID flag: payload too short");
            }

            byte[] normalized = new byte[ewkbOrWkb.length - EWKB_SRID_SIZE];
            normalized[0] = ewkbOrWkb[0];
            ByteBuffer out = ByteBuffer.wrap(normalized).order(order);
            out.putInt(1, rawType & ~EWKB_SRID_FLAG);

            System.arraycopy(
                    ewkbOrWkb,
                    WKB_HEADER_SIZE + EWKB_SRID_SIZE,
                    normalized,
                    WKB_HEADER_SIZE,
                    ewkbOrWkb.length - WKB_HEADER_SIZE - EWKB_SRID_SIZE
            );
            return normalized;
        }
    }

    /**
     * Describes a native layer.
     * <p>
     * 读取本地图层的名称、几何类型与字段定义。
     *
     * @param layer native layer handle, must not be {@code null} / 本地图层句柄，不能为 {@code null}
     * @return layer definition snapshot, never {@code null} / 图层定义快照，永不为 {@code null}
     */
    private static OgrLayerDefinition describeLayer(MemorySegment layer) {
        String layerName = CStrings.fromCString(GdalGenerated.OGR_L_GetName(layer));
        int geometryType = GdalGenerated.OGR_L_GetGeomType(layer);

        MemorySegment layerDefinition = GdalGenerated.OGR_L_GetLayerDefn(layer);
        if (CStrings.isNull(layerDefinition)) {
            return new OgrLayerDefinition(layerName, geometryType, List.of());
        }

        int fieldCount = GdalGenerated.OGR_FD_GetFieldCount(layerDefinition);
        List<OgrFieldDefinition> fields = new ArrayList<>(Math.max(fieldCount, 0));
        for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
            MemorySegment fieldDefn = GdalGenerated.OGR_FD_GetFieldDefn(layerDefinition, fieldIndex);
            if (CStrings.isNull(fieldDefn)) {
                continue;
            }

            String fieldName = CStrings.fromCString(GdalGenerated.OGR_Fld_GetNameRef(fieldDefn));
            int nativeFieldType = GdalGenerated.OGR_Fld_GetType(fieldDefn);
            fields.add(new OgrFieldDefinition(fieldName, OgrFieldType.fromNativeCode(nativeFieldType)));
        }
        return new OgrLayerDefinition(layerName, geometryType, List.copyOf(fields));
    }

    /**
     * Resolves projected field indexes.
     * <p>
     * 按请求字段解析投影字段索引。
     *
     * @param layerDefinition layer definition snapshot / 图层定义快照
     * @param options         parsed reader options / 解析后的读取选项
     * @return projected indexes, never {@code null} / 投影索引数组，永不为 {@code null}
     * @throws IllegalArgumentException if a selected field is unknown / 请求字段未知时抛出
     */
    private static int[] resolveProjectedFieldIndices(
            OgrLayerDefinition layerDefinition,
            OgrOptions.ReaderOptions options
    ) {
        if (options.selectedFields().isEmpty()) {
            int[] allFieldIndexes = new int[layerDefinition.fields().size()];
            for (int i = 0; i < allFieldIndexes.length; i++) {
                allFieldIndexes[i] = i;
            }
            return allFieldIndexes;
        }

        Set<String> selectedLowercase = options.selectedFieldsLowercase();
        Set<String> knownFieldsLowercase = new java.util.HashSet<>();
        for (OgrFieldDefinition field : layerDefinition.fields()) {
            knownFieldsLowercase.add(field.name().toLowerCase(Locale.ROOT));
        }
        List<String> unknownSelectedFields = new ArrayList<>();
        for (String selectedField : options.selectedFields()) {
            if (!knownFieldsLowercase.contains(selectedField.toLowerCase(Locale.ROOT))) {
                unknownSelectedFields.add(selectedField);
            }
        }
        if (!unknownSelectedFields.isEmpty()) {
            throw new IllegalArgumentException("Selected fields were not found in layer: " + unknownSelectedFields);
        }

        List<Integer> projected = new ArrayList<>();
        List<OgrFieldDefinition> fields = layerDefinition.fields();
        for (int index = 0; index < fields.size(); index++) {
            String lower = fields.get(index).name().toLowerCase(Locale.ROOT);
            if (selectedLowercase.contains(lower)) {
                projected.add(index);
            }
        }

        int[] projectedArray = new int[projected.size()];
        for (int i = 0; i < projected.size(); i++) {
            projectedArray[i] = projected.get(i);
        }
        return projectedArray;
    }

    /**
     * Converts a native feature. English + 转换本地要素为 Java 对象。
     */
    private static OgrFeature toFeature(
            MemorySegment feature,
            OgrLayerDefinition layerDefinition,
            int[] projectedFieldIndices
    ) {
        long fid = GdalGenerated.OGR_F_GetFID(feature);
        Map<String, Object> attributes = extractAttributes(feature, layerDefinition, projectedFieldIndices);
        OgrGeometry geometry = extractGeometry(feature);
        return new OgrFeature(fid, attributes, geometry);
    }

    /**
     * Extracts projected attributes. English + 提取投影字段的属性值。
     */
    private static Map<String, Object> extractAttributes(
            MemorySegment feature,
            OgrLayerDefinition layerDefinition,
            int[] projectedFieldIndices
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>(projectedFieldIndices.length);
        for (int projectedFieldIndex : projectedFieldIndices) {
            OgrFieldDefinition fieldDefinition = layerDefinition.fields().get(projectedFieldIndex);
            Object value = readFieldValue(feature, projectedFieldIndex, fieldDefinition.type());
            attributes.put(fieldDefinition.name(), value);
        }
        return attributes;
    }

    /**
     * Reads one field value. English + 读取单个字段值。
     */
    private static Object readFieldValue(MemorySegment feature, int fieldIndex, OgrFieldType fieldType) {
        if (GdalGenerated.OGR_F_IsFieldSetAndNotNull(feature, fieldIndex) == 0) {
            return null;
        }

        return switch (fieldType) {
            case INTEGER, INTEGER64 -> GdalGenerated.OGR_F_GetFieldAsInteger64(feature, fieldIndex);
            case REAL -> GdalGenerated.OGR_F_GetFieldAsDouble(feature, fieldIndex);
            default -> CStrings.fromCString(GdalGenerated.OGR_F_GetFieldAsString(feature, fieldIndex));
        };
    }

    /**
     * Extracts geometry as WKB. English + 提取几何为 WKB。
     */
    private static OgrGeometry extractGeometry(MemorySegment feature) {
        MemorySegment geometry = GdalGenerated.OGR_F_GetGeometryRef(feature);
        if (CStrings.isNull(geometry)) {
            return null;
        }

        int wkbSize = GdalGenerated.OGR_G_WkbSize(geometry);
        if (wkbSize <= 0) {
            return null;
        }

        byte[] wkb = new byte[wkbSize];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeWkb = arena.allocate(wkbSize);
            int exportErr = GdalGenerated.OGR_G_ExportToWkb(geometry, WKB_BYTE_ORDER_NDR, nativeWkb);
            throwIfOgrError(exportErr, "Failed to export geometry as WKB");
            MemorySegment.copy(nativeWkb, 0, MemorySegment.ofArray(wkb), 0, wkbSize);
        }

        OptionalInt srid = readSrid(geometry);
        if (srid.isPresent()) {
            return OgrGeometry.fromWkb(wkb, srid.getAsInt());
        }
        return OgrGeometry.fromWkb(wkb);
    }

    /**
     * Reads the SRID authority code. English + 读取 SRID 权威编码。
     */
    private static OptionalInt readSrid(MemorySegment geometry) {
        MemorySegment spatialReference = GdalGenerated.OGR_G_GetSpatialReference(geometry);
        if (CStrings.isNull(spatialReference)) {
            return OptionalInt.empty();
        }

        String authorityCode = readAuthorityCode(spatialReference, "PROJCS");
        if (authorityCode == null) {
            authorityCode = readAuthorityCode(spatialReference, "GEOGCS");
        }
        if (authorityCode == null) {
            authorityCode = readAuthorityCode(spatialReference, null);
        }
        if (authorityCode == null) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(authorityCode));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    /**
     * Reads one authority code. English + 读取单个权威编码。
     */
    private static String readAuthorityCode(MemorySegment spatialReference, String targetKey) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment targetKeyCString = targetKey == null ? MemorySegment.NULL : arena.allocateFrom(targetKey);
            String authorityCode = CStrings.fromCString(
                    GdalGenerated.OSRGetAuthorityCode(spatialReference, targetKeyCString)
            ).trim();
            return authorityCode.isEmpty() ? null : authorityCode;
        }
    }

    /**
     * Throws on OGR error. English + 遇 OGR 错误时抛异常。
     *
     * @param errorCode OGR error code / OGR 错误码
     * @param message   failure message / 失败信息
     */
    private static void throwIfOgrError(int errorCode, String message) {
        if (errorCode == OGRERR_NONE) {
            return;
        }
        throw GdalErrors.lastError(message);
    }

    /**
     * Initializes native GDAL once in a thread-safe way.
     * <p>
     * 以线程安全方式一次性初始化本地 GDAL。
     */
    private static void ensureInitialized() {
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
     * Closes a dataset best-effort. English + 尽力关闭数据集。
     */
    private static void closeDatasetQuietly(MemorySegment dataset) {
        if (CStrings.isNull(dataset)) {
            return;
        }
        try {
            GdalGenerated.GDALClose(dataset);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause.
        }
    }
}
