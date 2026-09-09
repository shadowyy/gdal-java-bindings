package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.ProgressCallback;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;

/**
 * Executes {@code gdal} algorithm pipelines via the native algorithm registry.
 * <p>
 * 通过本地算法注册表执行 {@code gdal} 算法管线的内部运行器，封装实例化、参数解析、运行与收尾。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class GdalAlgorithmRunner {
    /**
     * Native {@code GDALAlgorithmArgType::GAAT_STRING} discriminator.
     * <p>
     * 本地 {@code GDALAlgorithmArgType::GAAT_STRING} 类型判别值，用于识别字符串输出参数。
     */
    // GDALAlgorithmArgType::GAAT_STRING from gdalalgorithm.h.
    private static final int GAAT_STRING = 1;

    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态运行方法。
     */
    private GdalAlgorithmRunner() {
    }

    /**
     * Runs the algorithm at the given registry path and discards string output.
     * <p>
     * 运行指定注册表路径的算法，不关心字符串输出，仅关注是否成功执行。
     *
     * @param algorithmPath algorithm path segments, e.g. {@code ["raster", "convert"]}, must not be {@code null} /
     *                      算法路径片段，例如 {@code ["raster", "convert"]}，不能为 {@code null}
     * @param config GDAL config applied around the call, must not be {@code null} / 调用期间生效的 GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} for no reporting / 进度回调，可为 {@code null} 表示不监听
     * @param args CLI-style arguments forwarded to the algorithm, must not be {@code null} / 透传给算法的命令行风格参数，不能为 {@code null}
     * @throws NullPointerException if {@code algorithmPath}, {@code config} or {@code args} is {@code null} /
     *                              任一必需参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code algorithmPath} is empty / {@code algorithmPath} 为空时抛出
     * @throws ch.so.agi.gdal.ffm.GdalException if the native call fails / 本地调用失败时抛出
     */
    static void run(List<String> algorithmPath, GdalConfig config, ProgressCallback progress, List<String> args) {
        execute(algorithmPath, config, progress, args, false);
    }

    /**
     * Runs the algorithm and returns its first non-blank string output.
     * <p>
     * 运行算法并返回其首个非空字符串输出，适用于信息查询类算法。
     *
     * @param algorithmPath algorithm path segments, must not be {@code null} / 算法路径片段，不能为 {@code null}
     * @param config GDAL config applied around the call, must not be {@code null} / 调用期间生效的 GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} for no reporting / 进度回调，可为 {@code null} 表示不监听
     * @param args CLI-style arguments forwarded to the algorithm, must not be {@code null} / 透传给算法的命令行风格参数，不能为 {@code null}
     * @return first non-blank string output, or {@code ""} when none exists / 首个非空字符串输出，无输出时返回 {@code ""}
     * @throws NullPointerException if {@code algorithmPath}, {@code config} or {@code args} is {@code null} /
     *                              任一必需参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code algorithmPath} is empty / {@code algorithmPath} 为空时抛出
     * @throws ch.so.agi.gdal.ffm.GdalException if the native call fails / 本地调用失败时抛出
     */
    static String runForStringOutput(
            List<String> algorithmPath,
            GdalConfig config,
            ProgressCallback progress,
            List<String> args
    ) {
        return execute(algorithmPath, config, progress, args, true);
    }

    /**
     * Instantiates, parses, runs and finalizes one algorithm invocation.
     * <p>
     * 单次算法调用的完整流程：实例化、解析参数、运行、读取输出并收尾，失败时释放本地资源。
     *
     * @param algorithmPath algorithm path segments, must not be {@code null} / 算法路径片段，不能为 {@code null}
     * @param config GDAL config applied around the call, must not be {@code null} / 调用期间生效的 GDAL 配置，不能为 {@code null}
     * @param progress progress callback, may be {@code null} / 进度回调，可为 {@code null}
     * @param args CLI-style arguments forwarded to the algorithm, must not be {@code null} / 透传给算法的命令行风格参数，不能为 {@code null}
     * @param expectStringOutput {@code true} to read the first string output, {@code false} to skip it /
     *                           是否读取首个字符串输出，{@code true} 表示读取，{@code false} 表示跳过
     * @return string output when {@code expectStringOutput} is {@code true}, otherwise {@code ""} /
     *         需要输出时返回字符串结果，否则返回 {@code ""}
     * @throws NullPointerException if {@code algorithmPath}, {@code config} or {@code args} is {@code null} /
     *                              任一必需参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code algorithmPath} is empty / {@code algorithmPath} 为空时抛出
     * @throws ch.so.agi.gdal.ffm.GdalException if any native step fails / 任一本地步骤失败时抛出
     */
    private static String execute(
            List<String> algorithmPath,
            GdalConfig config,
            ProgressCallback progress,
            List<String> args,
            boolean expectStringOutput
    ) {
        Objects.requireNonNull(algorithmPath, "algorithmPath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(args, "args must not be null");
        if (algorithmPath.isEmpty()) {
            throw new IllegalArgumentException("algorithmPath must not be empty");
        }

        MemorySegment registry = MemorySegment.NULL;
        MemorySegment algorithm = MemorySegment.NULL;
        String stringOutput = "";

        GdalGenerated.CPLErrorReset();
        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(config);
             Arena arena = Arena.ofConfined();
             ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            registry = GdalGenerated.GDALGetGlobalAlgorithmRegistry();
            if (CStrings.isNull(registry)) {
                throw GdalErrors.lastError("Failed to obtain GDAL algorithm registry");
            }

            MemorySegment algorithmPathArray = CArgv.toCStringArray(algorithmPath.toArray(String[]::new), arena);
            algorithm = GdalGenerated.GDALAlgorithmRegistryInstantiateAlgFromPath(registry, algorithmPathArray);
            if (CStrings.isNull(algorithm)) {
                throw GdalErrors.lastError("Failed to instantiate GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            MemorySegment argv = CArgv.toCStringArray(args.toArray(String[]::new), arena);
            if (!GdalGenerated.GDALAlgorithmParseCommandLineArguments(algorithm, argv)) {
                throw GdalErrors.lastError(
                        "Failed to parse arguments for GDAL algorithm: " + String.join(" ", algorithmPath)
                );
            }

            if (!GdalGenerated.GDALAlgorithmRun(algorithm, progressHandle.callbackFn(), progressHandle.userData())) {
                throwIfCallbackFailed(progressHandle);
                throw GdalErrors.lastError("GDAL algorithm failed: " + String.join(" ", algorithmPath));
            }

            throwIfCallbackFailed(progressHandle);

            if (expectStringOutput) {
                stringOutput = readFirstStringOutput(algorithm);
            }

            if (!GdalGenerated.GDALAlgorithmFinalize(algorithm)) {
                throw GdalErrors.lastError("Failed to finalize GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            return stringOutput;
        } finally {
            if (!CStrings.isNull(algorithm)) {
                GdalGenerated.GDALAlgorithmRelease(algorithm);
            }
            if (!CStrings.isNull(registry)) {
                GdalGenerated.GDALAlgorithmRegistryRelease(registry);
            }
        }
    }

    /**
     * Reads the first non-blank string output argument of the executed algorithm.
     * <p>
     * 读取已执行算法的首个非空字符串输出参数，遍历实际算法的输出参数并取首个有效值。
     *
     * @param algorithm native algorithm handle, must not be {@code null} / 本地算法句柄，不能为 {@code null}
     * @return first non-blank string output, or {@code ""} when none exists / 首个非空字符串输出，无输出时返回 {@code ""}
     */
    private static String readFirstStringOutput(MemorySegment algorithm) {
        MemorySegment actualAlgorithm = GdalGenerated.GDALAlgorithmGetActualAlgorithm(algorithm);
        if (CStrings.isNull(actualAlgorithm)) {
            actualAlgorithm = algorithm;
        }

        MemorySegment argNames = GdalGenerated.GDALAlgorithmGetArgNames(actualAlgorithm);
        if (CStrings.isNull(argNames)) {
            return "";
        }

        try {
            int argCount = GdalGenerated.CSLCount(argNames);
            MemorySegment namesArray = argNames.reinterpret((long) argCount * ValueLayout.ADDRESS.byteSize());
            for (int i = 0; i < argCount; i++) {
                MemorySegment argNamePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                MemorySegment arg = GdalGenerated.GDALAlgorithmGetArg(actualAlgorithm, argNamePtr);
                if (CStrings.isNull(arg)) {
                    continue;
                }
                try {
                    if (!GdalGenerated.GDALAlgorithmArgIsOutput(arg)) {
                        continue;
                    }
                    if (GdalGenerated.GDALAlgorithmArgGetType(arg) != GAAT_STRING) {
                        continue;
                    }
                    String value = CStrings.fromCString(GdalGenerated.GDALAlgorithmArgGetAsString(arg));
                    if (!value.isBlank()) {
                        return value;
                    }
                } finally {
                    GdalGenerated.GDALAlgorithmArgRelease(arg);
                }
            }
            return "";
        } finally {
            GdalGenerated.CSLDestroy(argNames);
        }
    }

    /**
     * Rethrows a progress-callback failure captured by the bridge, if any.
     * <p>
     * 若桥接器捕获到进度回调的异常则重新抛出，确保调用方能感知回调失败。
     *
     * @param progressHandle bridge handle holding a possible callback failure, must not be {@code null} /
     *                       持有可能回调异常的桥接句柄，不能为 {@code null}
     * @throws RuntimeException the original callback failure, when present / 当存在回调异常时抛出原始异常
     */
    private static void throwIfCallbackFailed(ProgressBridge.ProgressHandle progressHandle) {
        RuntimeException callbackFailure = progressHandle.callbackFailure();
        if (callbackFailure != null) {
            throw callbackFailure;
        }
    }
}
