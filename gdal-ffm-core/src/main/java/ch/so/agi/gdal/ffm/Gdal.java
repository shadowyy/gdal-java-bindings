package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.GdalRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * High-level entry points for GDAL raster and vector utilities.
 * <p>
 * GDAL 栅格 / 矢量工具的高层入口，底层通过 {@code gdal} 算法管线调用本地 GDAL。
 * <p>
 * All methods are static and thread-safe after native initialization. Arguments in
 * {@code args} are forwarded to the GDAL command line (e.g. {@code "--format=GTiff"}).
 * <p>
 * 所有方法均为静态方法，本地库初始化后线程安全。{@code args} 会原样透传给 GDAL
 * 命令行参数（例如 {@code "--format=GTiff"}）。
 *
 * @see Ogr for streaming vector read/write / 矢量流式读写请见 Ogr
 * @see DatasetRef for dataset addressing / 数据集寻址请见 DatasetRef
 * @see GdalConfig for GDAL configuration options / GDAL 配置项请见 GdalConfig
 */
public final class Gdal {
    private Gdal() {
    }

    /**
     * Translates/converts a vector dataset ({@code ogr2ogr} equivalent).
     * <p>
     * 矢量数据转换（相当于 {@code ogr2ogr}），例如格式转换、重投影、属性过滤。
     *
     * @param dest output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param src input dataset path, must not be {@code null} / 输入数据集路径，不能为 {@code null}
     * @param args extra GDALVectorTranslate CLI arguments, e.g. {@code "-f", "GPKG"} /
     *             透传给 GDAL 的额外参数，例如 {@code "-f", "GPKG"}
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorTranslate(Path dest, Path src, String... args) {
        vectorTranslate(dest, src, null, args);
    }

    /**
     * Translates/converts a vector dataset with progress reporting.
     * <p>
     * 带进度回调的矢量数据转换。
     *
     * @param dest output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param src input dataset path, must not be {@code null} / 输入数据集路径，不能为 {@code null}
     * @param progress progress callback, may be {@code null} for no reporting /
     *                 进度回调，可为 {@code null} 表示不监听；返回 {@code false} 可中断任务
     * @param args extra GDALVectorTranslate CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails or is aborted / 本地调用失败或被中断时抛出
     */
    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        GdalRuntime.vectorTranslate(dest, src, progress, args);
    }

    /**
     * Translates/converts a vector dataset referenced by {@link DatasetRef}.
     * <p>
     * 矢量数据转换（数据集引用版本），支持 {@code /vsizip/...} 输入与
     * {@code PG:"..."} 等直通连接串输出。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param args extra GDALVectorTranslate CLI arguments, e.g. {@code "-f", "GPKG"} /
     *             透传给 GDAL 的额外参数，例如 {@code "-f", "GPKG"}
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorTranslate(DatasetRef dest, DatasetRef src, String... args) {
        vectorTranslate(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Translates a vector dataset with explicit GDAL configuration ({@code --config} equivalent).
     * <p>
     * 使用指定 GDAL 配置做矢量转换（{@code --config} 等价能力，线程级作用域，调用结束后自动恢复）。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra GDALVectorTranslate CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorTranslate(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        vectorTranslate(dest, src, config, null, args);
    }

    /**
     * Full vector-translate overload with config and progress support.
     * <p>
     * 最完整的矢量转换重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} for no reporting /
     *                 进度回调，可为 {@code null} 表示不监听；返回 {@code false} 可中断任务
     * @param args extra GDALVectorTranslate CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails or is aborted / 本地调用失败或被中断时抛出
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
        GdalRuntime.vectorTranslate(dest, src, config, progress, args);
    }

    /**
     * Converts a raster dataset (format/type translation, {@code gdal raster convert}).
     * <p>
     * 栅格转换（对应 {@code gdal raster convert}），如格式转换、压缩、类型转换。
     *
     * @param dest output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param src input dataset path, must not be {@code null} / 输入数据集路径，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterConvert(Path dest, Path src, String... args) {
        rasterConvert(dest, src, null, args);
    }

    /**
     * Converts a raster dataset with progress reporting.
     * <p>
     * 带进度回调的栅格转换。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterConvert(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterConvert(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    /**
     * Clips a raster to an area of interest ({@code gdal raster clip}).
     * <p>
     * 栅格裁剪（对应 {@code gdal raster clip}），按范围/BBOX 裁剪。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param args extra CLI arguments, e.g. {@code "--bbox=..."} / 额外参数，例如 {@code "--bbox=..."}
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterClip(Path dest, Path src, String... args) {
        rasterClip(dest, src, null, args);
    }

    /**
     * Clips a raster with progress reporting.
     * <p>
     * 带进度回调的栅格裁剪。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterClip(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterClip(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    /**
     * Reprojects a raster to another CRS ({@code gdal raster reproject}).
     * <p>
     * 栅格重投影（对应 {@code gdal raster reproject}），转换坐标系。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param args extra CLI arguments, e.g. {@code "--dst-crs=EPSG:2056"} / 额外参数，例如目标坐标系
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterReproject(Path dest, Path src, String... args) {
        rasterReproject(dest, src, null, args);
    }

    /**
     * Reprojects a raster with progress reporting.
     * <p>
     * 带进度回调的栅格重投影。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterReproject(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterReproject(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    /**
     * Resizes (resamples/scales) a raster ({@code gdal raster resize}).
     * <p>
     * 栅格缩放/重采样（对应 {@code gdal raster resize}），调整分辨率或尺寸。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param args extra CLI arguments, e.g. {@code "--width=...", "--height=..."} / 额外参数，例如宽高
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterResize(Path dest, Path src, String... args) {
        rasterResize(dest, src, null, args);
    }

    /**
     * Resizes a raster with progress reporting.
     * <p>
     * 带进度回调的栅格缩放。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input dataset path / 输入数据集路径
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterResize(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterResize(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    /**
     * Returns {@code gdal raster info} output for a local raster.
     * <p>
     * 获取栅格元信息（对应 {@code gdal raster info}），返回文本/JSON 描述。
     *
     * @param src input dataset path / 输入数据集路径
     * @param args extra CLI arguments, e.g. {@code "--format=json"} / 额外参数，例如指定 JSON 输出
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static String rasterInfo(Path src, String... args) {
        return rasterInfo(DatasetRef.local(src), GdalConfig.empty(), args);
    }

    /**
     * Returns {@code gdal raster info} output for an arbitrary dataset reference.
     * <p>
     * 获取任意数据集引用（本地路径 / HTTP / VSI）的栅格元信息。
     *
     * @param src dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     * @see DatasetRef
     */
    public static String rasterInfo(DatasetRef src, String... args) {
        return rasterInfo(src, GdalConfig.empty(), args);
    }

    /**
     * Returns {@code gdal raster info} output with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置获取栅格元信息。
     *
     * @param src dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static String rasterInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return GdalRuntime.rasterInfo(src, config, args);
    }

    /**
     * Returns {@code gdal vector info} output for a local vector dataset.
     * <p>
     * 中文：获取矢量元信息（对应 {@code gdal vector info}），返回文本/JSON 描述。
     *
     * @param src input dataset path / 输入数据集路径
     * @param args extra CLI arguments, e.g. {@code "--format=json"} / 额外参数，例如指定 JSON 输出
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static String vectorInfo(Path src, String... args) {
        return vectorInfo(DatasetRef.local(src), GdalConfig.empty(), args);
    }

    /**
     * Returns {@code gdal vector info} output for an arbitrary dataset reference.
     * <p>
     * 中文：获取任意数据集引用（本地路径 / HTTP / VSI）的矢量元信息。
     *
     * @param src dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     * @see DatasetRef
     */
    public static String vectorInfo(DatasetRef src, String... args) {
        return vectorInfo(src, GdalConfig.empty(), args);
    }

    /**
     * Returns {@code gdal vector info} output with explicit GDAL configuration.
     * <p>
     * 中文：使用指定 GDAL 配置获取矢量元信息。
     *
     * @param src dataset reference, must not be {@code null} / 数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @return info output, never {@code null} / 元信息文本，不会为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static String vectorInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return GdalRuntime.vectorInfo(src, config, args);
    }

    /**
     * Converts a raster referenced by {@link DatasetRef}.
     * <p>
     * 栅格转换（数据集引用版本），支持本地路径、HTTP、VSI。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterConvert(DatasetRef dest, DatasetRef src, String... args) {
        rasterConvert(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Converts a raster with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格转换。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterConvert(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterConvert(dest, src, config, null, args);
    }

    /**
     * Full raster-convert overload with config and progress support.
     * <p>
     * 最完整的栅格转换重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterConvert(dest, src, config, progress, args);
    }

    /**
     * Clips a raster referenced by {@link DatasetRef}.
     * <p>
     * 栅格裁剪（数据集引用版本）。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterClip(DatasetRef dest, DatasetRef src, String... args) {
        rasterClip(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Clips a raster with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格裁剪。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterClip(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterClip(dest, src, config, null, args);
    }

    /**
     * Full raster-clip overload with config and progress support.
     * <p>
     * 最完整的栅格裁剪重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterClip(dest, src, config, progress, args);
    }

    /**
     * Reprojects a raster referenced by {@link DatasetRef}.
     * <p>
     * 栅格重投影（数据集引用版本）。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterReproject(DatasetRef dest, DatasetRef src, String... args) {
        rasterReproject(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Reprojects a raster with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格重投影。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterReproject(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterReproject(dest, src, config, null, args);
    }

    /**
     * Full raster-reproject overload with config and progress support.
     * <p>
     * 最完整的栅格重投影重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterReproject(dest, src, config, progress, args);
    }

    /**
     * Resizes a raster referenced by {@link DatasetRef}.
     * <p>
     * 栅格缩放（数据集引用版本）。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterResize(DatasetRef dest, DatasetRef src, String... args) {
        rasterResize(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Resizes a raster with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格缩放。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input dataset reference / 输入数据集引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterResize(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterResize(dest, src, config, null, args);
    }

    /**
     * Full raster-resize overload with config and progress support.
     * <p>
     * 最完整的栅格缩放重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input dataset reference, must not be {@code null} / 输入数据集引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterResize(dest, src, config, progress, args);
    }

    /**
     * Mosaics multiple local rasters into one output ({@code gdal raster mosaic}).
     * <p>
     * 多幅栅格镶嵌/拼接（对应 {@code gdal raster mosaic}），合并为一幅输出。
     *
     * @param dest output dataset path, must not be {@code null} / 输出数据集路径，不能为 {@code null}
     * @param sources input dataset paths, must not be {@code null} or empty / 输入数据集路径列表，不能为 {@code null} 或空
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code sources} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code sources} is empty / 输入列表为空时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterMosaic(Path dest, List<Path> sources, String... args) {
        Objects.requireNonNull(sources, "sources must not be null");
        rasterMosaic(
                DatasetRef.local(dest),
                sources.stream().map(DatasetRef::local).toList(),
                GdalConfig.empty(),
                null,
                args
        );
    }

    /**
     * Mosaics multiple rasters referenced by {@link DatasetRef}.
     * <p>
     * 多幅栅格镶嵌（数据集引用版本）。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param sources input dataset references, must not be empty / 输入数据集引用列表，不能为空
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code sources} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code sources} is empty / 输入列表为空时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterMosaic(DatasetRef dest, List<DatasetRef> sources, String... args) {
        rasterMosaic(dest, sources, GdalConfig.empty(), null, args);
    }

    /**
     * Mosaics multiple rasters with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格镶嵌。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param sources input dataset references, must not be empty / 输入数据集引用列表，不能为空
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code sources} is empty / 输入列表为空时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterMosaic(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            String... args
    ) {
        rasterMosaic(dest, sources, config, null, args);
    }

    /**
     * Full raster-mosaic overload with config and progress support.
     * <p>
     * 最完整的栅格镶嵌重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param sources input dataset references, must not be {@code null} or empty /
     *                输入数据集引用列表，不能为 {@code null} 或空
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code sources} or {@code config} is {@code null} /
     *                              参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code sources} is empty / 输入列表为空时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterMosaic(dest, sources, config, progress, args);
    }

    /**
     * Computes zonal statistics of a raster grouped by zones ({@code gdal raster zonal-stats}).
     * <p>
     * 栅格分区统计（对应 {@code gdal raster zonal-stats}），按分区栅格统计数值栅格。
     *
     * @param dest output dataset path / 输出数据集路径
     * @param src input value raster path / 输入数值栅格路径
     * @param zones zone raster path / 分区栅格路径
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any path is {@code null} / 任一路径为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterZonalStats(Path dest, Path src, Path zones, String... args) {
        rasterZonalStats(DatasetRef.local(dest), DatasetRef.local(src), DatasetRef.local(zones), GdalConfig.empty(), null, args);
    }

    /**
     * Computes zonal statistics for datasets referenced by {@link DatasetRef}.
     * <p>
     * 栅格分区统计（数据集引用版本）。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input value raster reference / 输入数值栅格引用
     * @param zones zone raster reference / 分区栅格引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterZonalStats(DatasetRef dest, DatasetRef src, DatasetRef zones, String... args) {
        rasterZonalStats(dest, src, zones, GdalConfig.empty(), null, args);
    }

    /**
     * Computes zonal statistics with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做栅格分区统计。
     *
     * @param dest output dataset reference / 输出数据集引用
     * @param src input value raster reference / 输入数值栅格引用
     * @param zones zone raster reference / 分区栅格引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void rasterZonalStats(
            DatasetRef dest,
            DatasetRef src,
            DatasetRef zones,
            GdalConfig config,
            String... args
    ) {
        rasterZonalStats(dest, src, zones, config, null, args);
    }

    /**
     * Full zonal-stats overload with config and progress support.
     * <p>
     * 最完整的分区统计重载，支持配置项与进度回调。
     *
     * @param dest output dataset reference, must not be {@code null} / 输出数据集引用，不能为 {@code null}
     * @param src input value raster reference, must not be {@code null} / 输入数值栅格引用，不能为 {@code null}
     * @param zones zone raster reference, must not be {@code null} / 分区栅格引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any of {@code dest}/{@code src}/{@code zones}/{@code config} is {@code null} /
     *                              任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.rasterZonalStats(dest, src, zones, config, progress, args);
    }

    /**
     * Burns vector geometries into a raster ({@code gdal vector rasterize}).
     * <p>
     * 矢量栅格化（对应 {@code gdal vector rasterize}），将矢量烧录为栅格。
     *
     * @param dest output raster path / 输出栅格路径
     * @param src input vector path / 输入矢量路径
     * @param args extra CLI arguments, e.g. {@code "--resolution=..."} / 额外参数，例如分辨率
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorRasterize(Path dest, Path src, String... args) {
        vectorRasterize(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), null, args);
    }

    /**
     * Burns vector geometries into a raster for datasets referenced by {@link DatasetRef}.
     * <p>
     * 矢量栅格化（数据集引用版本）。
     *
     * @param dest output raster reference / 输出栅格引用
     * @param src input vector reference / 输入矢量引用
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest} or {@code src} is {@code null} / 参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorRasterize(DatasetRef dest, DatasetRef src, String... args) {
        vectorRasterize(dest, src, GdalConfig.empty(), null, args);
    }

    /**
     * Rasterizes a vector with explicit GDAL configuration.
     * <p>
     * 使用指定 GDAL 配置做矢量栅格化。
     *
     * @param dest output raster reference / 输出栅格引用
     * @param src input vector reference / 输入矢量引用
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
     */
    public static void vectorRasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            String... args
    ) {
        vectorRasterize(dest, src, config, null, args);
    }

    /**
     * Full vector-rasterize overload with config and progress support.
     * <p>
     * 最完整的矢量栅格化重载，支持配置项与进度回调。
     *
     * @param dest output raster reference, must not be {@code null} / 输出栅格引用，不能为 {@code null}
     * @param src input vector reference, must not be {@code null} / 输入矢量引用，不能为 {@code null}
     * @param config GDAL config options, must not be {@code null} / GDAL 配置项，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args extra CLI arguments / 透传给 GDAL 的额外参数
     * @throws NullPointerException if {@code dest}, {@code src} or {@code config} is {@code null} /
     *                              输出/输入/配置为 {@code null} 时抛出
     * @throws GdalException if the native call fails / 本地调用失败时抛出
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
        GdalRuntime.vectorRasterize(dest, src, config, progress, args);
    }

    /**
     * Lists raster drivers that support creation.
     * <p>
     * 列出支持创建的栅格驱动（如 GTiff、COG、PNG 等），按短名称排序。
     *
     * @return immutable list of writable raster drivers, never {@code null} /
     *         可写栅格驱动的不可变列表，不会为 {@code null}
     * @throws GdalException if driver enumeration fails / 枚举驱动失败时抛出
     */
    public static List<RasterDriverInfo> listWritableRasterDrivers() {
        return GdalRuntime.listWritableRasterDrivers();
    }

    /**
     * Returns the raw creation-option-list XML of a raster driver.
     * <p>
     * 返回栅格驱动的原始创建选项 XML（{@code DMD_CREATIONOPTIONLIST}）。
     *
     * @param driverShortName raster driver short name, e.g. {@code "GTiff"}, must not be {@code null} /
     *                        栅格驱动短名称，例如 {@code "GTiff"}，不能为 {@code null}
     * @return XML text, may be empty if the driver exposes none / XML 文本，若驱动未提供则为空
     * @throws NullPointerException if {@code driverShortName} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the name is blank or the driver is unknown / 名称为空或驱动不存在时抛出
     */
    public static String driverCreationOptionListXml(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        return GdalRuntime.driverCreationOptionListXml(driverShortName);
    }

    /**
     * Lists allowed enum values of a driver creation option.
     * <p>
     * 列出某驱动创建选项的枚举可选值，例如 {@code GTiff / COMPRESS} 的可选压缩算法。
     *
     * @param driverShortName raster driver short name, must not be {@code null} / 栅格驱动短名称，不能为 {@code null}
     * @param optionName creation option name, must not be {@code null} / 创建选项名，不能为 {@code null}
     * @return immutable value list, may be empty / 不可变可选值列表，可能为空
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver is unknown / 驱动不存在时抛出
     */
    public static List<String> listCreationOptionEnumValues(String driverShortName, String optionName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(optionName, "optionName must not be null");
        return GdalRuntime.listCreationOptionEnumValues(driverShortName, optionName);
    }

    /**
     * Lists supported {@code COMPRESS} values of a raster driver.
     * <p>
     * 列出某栅格驱动支持的 {@code COMPRESS} 压缩选项值。
     *
     * @param driverShortName raster driver short name, must not be {@code null} / 栅格驱动短名称，不能为 {@code null}
     * @return immutable compression option list, may be empty / 不可变压缩选项列表，可能为空
     * @throws NullPointerException if {@code driverShortName} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if the driver is unknown / 驱动不存在时抛出
     */
    public static List<String> listCompressionOptions(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        return GdalRuntime.listCompressionOptions(driverShortName);
    }
}
