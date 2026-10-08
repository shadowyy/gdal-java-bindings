package ch.so.agi.gdal.ffm;

/**
 * Progress callback for long-running GDAL operations.
 * <p>
 * GDAL 长耗时操作的进度回调；返回 {@code false} 可请求中断任务。
 * <p>
 * Implementations must be fast, non-blocking and thread-safe, as they are
 * invoked from native code / 实现须快速、非阻塞且线程安全，因为会从本地代码回调。
 */
@FunctionalInterface
public interface ProgressCallback {
    /**
     * Invoked periodically with the current progress.
     * <p>
     * 按进度周期性回调。
     *
     * @param complete progress in range {@code [0.0, 1.0]} / 进度，范围 {@code [0.0, 1.0]}
     * @param message  informational text from GDAL, can be empty / GDAL 附带信息，可为空
     * @return {@code true} to continue, {@code false} to abort / {@code true} 继续，{@code false} 中断
     */
    boolean onProgress(double complete, String message);
}
