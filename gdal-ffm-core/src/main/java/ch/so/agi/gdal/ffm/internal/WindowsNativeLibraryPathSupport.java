package ch.so.agi.gdal.ffm.internal;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registers the Windows DLL search path of the extracted native bundle.
 * <p>
 * 注册解压后本地 bundle 的 Windows DLL 搜索路径，使入口库的传递依赖能被解析；非 Windows 平台直接跳过。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 */
final class WindowsNativeLibraryPathSupport {
    /**
     * Flag enabling the default DLL search directories.
     * <p>
     * 启用默认 DLL 搜索目录的标志。
     */
    private static final int LOAD_LIBRARY_SEARCH_DEFAULT_DIRS = 0x00001000;
    /**
     * Flag enabling user-directory DLL search.
     * <p>
     * 启用用户目录 DLL 搜索的标志。
     */
    private static final int LOAD_LIBRARY_SEARCH_USER_DIRS = 0x00000400;
    /**
     * Process-wide registration state for the default Kernel32 access.
     * <p>
     * 默认 Kernel32 访问的进程级注册状态。
     */
    private static final RegistrationState GLOBAL_STATE = new RegistrationState();

    /**
     * Prevents instantiation; all members are static.
     * <p>
     * 禁止实例化，所有成员均为静态。
     */
    private WindowsNativeLibraryPathSupport() {
    }

    /**
     * Registers the bundle DLL directory on Windows; no-op on other platforms.
     * <p>
     * 在 Windows 上注册 bundle 的 DLL 目录，其他平台直接跳过。
     *
     * @param platform current platform, must not be {@code null} / 当前平台，不能为 {@code null}
     * @param extractionRoot bundle root directory, must not be {@code null} on Windows / bundle 根目录，Windows 上不能为 {@code null}
     * @throws NullPointerException if {@code platform} is {@code null} / {@code platform} 为 {@code null} 时抛出
     * @throws IllegalStateException if the DLL directory is missing or registration fails / DLL 目录缺失或注册失败时抛出
     */
    static void configureIfNeeded(NativePlatform platform, Path extractionRoot) {
        Objects.requireNonNull(platform, "platform must not be null");
        if (!"windows".equals(platform.os())) {
            return;
        }
        configureIfNeeded(platform, extractionRoot, GLOBAL_STATE, Kernel32Holder.INSTANCE);
    }

    /**
     * Registers the bundle DLL directory with explicit state and Kernel32 access.
     * <p>
     * 使用显式传入的状态与 Kernel32 访问注册 bundle 的 DLL 目录（便于测试）；非 Windows 直接返回，已注册路径不再重复注册。
     *
     * @param platform current platform, must not be {@code null} / 当前平台，不能为 {@code null}
     * @param extractionRoot bundle root directory, must not be {@code null} / bundle 根目录，不能为 {@code null}
     * @param state registration state, must not be {@code null} / 注册状态，不能为 {@code null}
     * @param kernel32 Kernel32 access, must not be {@code null} / Kernel32 访问，不能为 {@code null}
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     * @throws IllegalStateException if the DLL directory is missing or registration fails / DLL 目录缺失或注册失败时抛出
     */
    static void configureIfNeeded(
            NativePlatform platform,
            Path extractionRoot,
            RegistrationState state,
            Kernel32Access kernel32
    ) {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(extractionRoot, "extractionRoot must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(kernel32, "kernel32 must not be null");

        if (!"windows".equals(platform.os())) {
            return;
        }

        Path dllDirectory = resolveDllDirectory(extractionRoot);
        synchronized (state.lock()) {
            if (state.registeredPaths().contains(dllDirectory)) {
                return;
            }

            String preferredFailure = null;
            if (kernel32.supportsAddDllDirectory()) {
                preferredFailure = tryPreferredRegistration(kernel32, dllDirectory);
                if (preferredFailure == null) {
                    state.registeredPaths().add(dllDirectory);
                    return;
                }
            }

            String fallbackFailure = tryFallbackRegistration(kernel32, dllDirectory);
            if (fallbackFailure == null) {
                state.registeredPaths().add(dllDirectory);
                return;
            }

            throw registrationFailure(platform.classifier(), dllDirectory, preferredFailure, fallbackFailure);
        }
    }

    /**
     * Resolves the {@code bin} DLL directory below the extraction root.
     * <p>
     * 解析解压根目录下的 {@code bin} DLL 目录。
     *
     * @param extractionRoot bundle root directory, must not be {@code null} / bundle 根目录，不能为 {@code null}
     * @return DLL directory, never {@code null} / DLL 目录，永不为 {@code null}
     * @throws IllegalStateException if the directory is missing / 目录缺失时抛出
     */
    private static Path resolveDllDirectory(Path extractionRoot) {
        Path dllDirectory = extractionRoot.resolve("bin").toAbsolutePath().normalize();
        if (!Files.isDirectory(dllDirectory)) {
            throw new IllegalStateException("Windows native DLL directory is missing: " + dllDirectory);
        }
        return dllDirectory;
    }

    /**
     * Registers the directory via AddDllDirectory after setting default search flags.
     * <p>
     * 先设置默认搜索标志，再通过 AddDllDirectory 注册目录（首选方式）。
     *
     * @param kernel32 Kernel32 access, must not be {@code null} / Kernel32 访问，不能为 {@code null}
     * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
     * @return failure description, or {@code null} on success / 失败描述，成功时为 {@code null}
     */
    private static String tryPreferredRegistration(Kernel32Access kernel32, Path dllDirectory) {
        if (!kernel32.setDefaultDllDirectories(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_USER_DIRS)) {
            return "SetDefaultDllDirectories failed (GetLastError=" + kernel32.getLastError() + ")";
        }
        if (!kernel32.addDllDirectory(dllDirectory)) {
            return "AddDllDirectory failed for '" + dllDirectory + "' (GetLastError=" + kernel32.getLastError() + ")";
        }
        return null;
    }

    /**
     * Registers the directory via the legacy SetDllDirectory fallback.
     * <p>
     * 通过传统的 SetDllDirectory 兜底方式注册目录。
     *
     * @param kernel32 Kernel32 access, must not be {@code null} / Kernel32 访问，不能为 {@code null}
     * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
     * @return failure description, or {@code null} on success / 失败描述，成功时为 {@code null}
     */
    private static String tryFallbackRegistration(Kernel32Access kernel32, Path dllDirectory) {
        if (!kernel32.setDllDirectory(dllDirectory)) {
            return "SetDllDirectoryW failed for '" + dllDirectory + "' (GetLastError=" + kernel32.getLastError() + ")";
        }
        return null;
    }

    /**
     * Builds the exception for a failed DLL search-path registration.
     * <p>
     * 为 DLL 搜索路径注册失败构建异常，合并首选与兜底两种方式的失败信息。
     *
     * @param classifier platform classifier, must not be {@code null} / 平台分类串，不能为 {@code null}
     * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
     * @param preferredFailure preferred-path failure, may be {@code null} / 首选方式失败描述，可为 {@code null}
     * @param fallbackFailure fallback-path failure, may be {@code null} / 兜底方式失败描述，可为 {@code null}
     * @return exception to throw, never {@code null} / 待抛出的异常，永不为 {@code null}
     */
    private static IllegalStateException registrationFailure(
            String classifier,
            Path dllDirectory,
            String preferredFailure,
            String fallbackFailure
    ) {
        StringBuilder message = new StringBuilder()
                .append("Failed to register Windows DLL search path '")
                .append(dllDirectory)
                .append("' for classifier '")
                .append(classifier)
                .append("'");
        if (preferredFailure != null) {
            message.append(": ").append(preferredFailure);
            if (fallbackFailure != null) {
                message.append("; fallback ").append(fallbackFailure);
            }
        } else if (fallbackFailure != null) {
            message.append(": ").append(fallbackFailure);
        }
        return new IllegalStateException(message.toString());
    }

    /**
     * Mutable bookkeeping of already registered DLL directories.
     * <p>
     * 已注册 DLL 目录的可变记账，避免重复注册。此为内部 API（internal, not public），请勿在业务代码中直接使用。
     *
     * @param lock monitor guarding the registration set / 保护注册集合的监视器
     * @param registeredPaths already registered directories / 已注册的目录集合
     */
    record RegistrationState(Object lock, Set<Path> registeredPaths) {
        /**
         * Creates empty registration state.
         * <p>
         * 创建空的注册状态。
         */
        RegistrationState() {
            this(new Object(), new HashSet<>());
        }
    }

    /**
     * Minimal Kernel32 surface needed for DLL directory registration.
     * <p>
     * 注册 DLL 目录所需的最小 Kernel32 接口（便于测试替身）。此为内部 API（internal, not public），请勿在业务代码中直接使用。
     */
    interface Kernel32Access {
        /**
         * Checks whether AddDllDirectory registration is available.
         * <p>
         * 检查是否可用 AddDllDirectory 注册方式。
         *
         * @return {@code true} if the preferred APIs are present / 首选 API 存在时返回 {@code true}
         */
        boolean supportsAddDllDirectory();

        /**
         * Sets the default DLL search directories.
         * <p>
         * 设置默认 DLL 搜索目录标志。
         *
         * @param flags search flags to set / 待设置的搜索标志
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        boolean setDefaultDllDirectories(int flags);

        /**
         * Adds a DLL directory to the search path.
         * <p>
         * 将 DLL 目录加入搜索路径。
         *
         * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        boolean addDllDirectory(Path dllDirectory);

        /**
         * Sets the DLL directory via the legacy API.
         * <p>
         * 通过传统 API 设置 DLL 目录。
         *
         * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        boolean setDllDirectory(Path dllDirectory);

        /**
         * Returns the last native error code.
         * <p>
         * 返回最近一次本地错误码。
         *
         * @return native error code / 本地错误码
         */
        int getLastError();
    }

    /**
     * Lazy holder of the process-wide FFM Kernel32 access.
     * <p>
     * 进程级 FFM Kernel32 访问的延迟持有者。
     */
    private static final class Kernel32Holder {
        /**
         * Shared Kernel32 access instance.
         * <p>
         * 共享的 Kernel32 访问实例。
         */
        private static final Kernel32Access INSTANCE = new FfmKernel32Access();

        /**
         * Prevents instantiation; only the holder instance is used.
         * <p>
         * 禁止实例化，仅使用持有者实例。
         */
        private Kernel32Holder() {
        }
    }

    /**
     * FFM-based {@link Kernel32Access} implementation using downcall handles.
     * <p>
     * 基于 FFM downcall 句柄的 Kernel32 访问实现；符号缺失时对应句柄为 {@code null} 并优雅降级。
     */
    private static final class FfmKernel32Access implements Kernel32Access {
        /**
         * Arena backing the kernel32 symbol lookup.
         * <p>
         * 支撑 kernel32 符号查找的 Arena。
         */
        private static final Arena LOOKUP_ARENA = Arena.ofShared();
        /**
         * Linker used for downcall handles.
         * <p>
         * 用于创建 downcall 句柄的 Linker。
         */
        private static final Linker LINKER = Linker.nativeLinker();

        /**
         * Symbol lookup for the kernel32 library.
         * <p>
         * kernel32 库的符号查找。
         */
        private final SymbolLookup symbolLookup = SymbolLookup.libraryLookup("kernel32", LOOKUP_ARENA);
        /**
         * Optional handle for SetDefaultDllDirectories.
         * <p>
         * SetDefaultDllDirectories 的可选句柄，符号缺失时为 {@code null}。
         */
        private final MethodHandle setDefaultDllDirectories = downcallOptional(
                "SetDefaultDllDirectories",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        /**
         * Optional handle for AddDllDirectory.
         * <p>
         * AddDllDirectory 的可选句柄，符号缺失时为 {@code null}。
         */
        private final MethodHandle addDllDirectory = downcallOptional(
                "AddDllDirectory",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        /**
         * Optional handle for SetDllDirectoryW.
         * <p>
         * SetDllDirectoryW 的可选句柄，符号缺失时为 {@code null}。
         */
        private final MethodHandle setDllDirectoryW = downcallOptional(
                "SetDllDirectoryW",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        /**
         * Optional handle for GetLastError.
         * <p>
         * GetLastError 的可选句柄，符号缺失时为 {@code null}。
         */
        private final MethodHandle getLastError = downcallOptional(
                "GetLastError",
                FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );

        /**
         * Checks whether AddDllDirectory registration is available.
         * <p>
         * 检查是否可用 AddDllDirectory 注册方式。
         *
         * @return {@code true} if the preferred handles are present / 首选句柄存在时返回 {@code true}
         */
        @Override
        public boolean supportsAddDllDirectory() {
            return setDefaultDllDirectories != null && addDllDirectory != null;
        }

        /**
         * Sets the default DLL search directories.
         * <p>
         * 设置默认 DLL 搜索目录标志；句柄缺失时返回 {@code false}。
         *
         * @param flags search flags to set / 待设置的搜索标志
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        @Override
        public boolean setDefaultDllDirectories(int flags) {
            if (setDefaultDllDirectories == null) {
                return false;
            }
            return invokeInt(setDefaultDllDirectories, flags) != 0;
        }

        /**
         * Adds a DLL directory to the search path.
         * <p>
         * 将 DLL 目录加入搜索路径；句柄缺失时返回 {@code false}。
         *
         * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        @Override
        public boolean addDllDirectory(Path dllDirectory) {
            if (addDllDirectory == null) {
                return false;
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment widePath = allocateWideString(arena, dllDirectory);
                MemorySegment cookie = invokeAddress(addDllDirectory, widePath);
                return !CStrings.isNull(cookie);
            }
        }

        /**
         * Sets the DLL directory via the legacy API.
         * <p>
         * 通过传统 API 设置 DLL 目录；句柄缺失时返回 {@code false}。
         *
         * @param dllDirectory DLL directory, must not be {@code null} / DLL 目录，不能为 {@code null}
         * @return {@code true} on success / 成功时返回 {@code true}
         */
        @Override
        public boolean setDllDirectory(Path dllDirectory) {
            if (setDllDirectoryW == null) {
                return false;
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment widePath = allocateWideString(arena, dllDirectory);
                return invokeInt(setDllDirectoryW, widePath) != 0;
            }
        }

        /**
         * Returns the last native error code.
         * <p>
         * 返回最近一次本地错误码；句柄缺失时返回 -1。
         *
         * @return native error code / 本地错误码
         */
        @Override
        public int getLastError() {
            if (getLastError == null) {
                return -1;
            }
            return invokeInt(getLastError);
        }

        /**
         * Creates an optional downcall handle, tolerating missing symbols.
         * <p>
         * 创建可选的 downcall 句柄，符号缺失时返回 {@code null} 而不抛异常。
         *
         * @param symbolName native symbol name, must not be {@code null} / 本地符号名，不能为 {@code null}
         * @param descriptor function descriptor, must not be {@code null} / 函数描述符，不能为 {@code null}
         * @return downcall handle, or {@code null} if the symbol is absent / downcall 句柄，符号缺失时为 {@code null}
         */
        private MethodHandle downcallOptional(String symbolName, FunctionDescriptor descriptor) {
            return symbolLookup.find(symbolName)
                    .map(symbol -> LINKER.downcallHandle(symbol, descriptor))
                    .orElse(null);
        }

        /**
         * Allocates a NUL-terminated wide string for the given path.
         * <p>
         * 为给定路径分配 NUL 结尾的宽字符串（UTF-16）。
         *
         * @param arena arena owning the allocation, must not be {@code null} / 拥有该分配的 Arena，不能为 {@code null}
         * @param path path to encode, must not be {@code null} / 待编码的路径，不能为 {@code null}
         * @return wide-string segment, never {@code null} / 宽字符串内存段，永不为 {@code null}
         */
        private static MemorySegment allocateWideString(Arena arena, Path path) {
            String value = path.toAbsolutePath().normalize().toString() + "\0";
            MemorySegment segment = arena.allocate(
                    ValueLayout.JAVA_CHAR.byteSize() * value.length(),
                    ValueLayout.JAVA_CHAR.byteAlignment()
            );
            for (int i = 0; i < value.length(); i++) {
                segment.setAtIndex(ValueLayout.JAVA_CHAR, i, value.charAt(i));
            }
            return segment;
        }

        /**
         * Invokes a downcall handle returning an address.
         * <p>
         * 调用返回地址的 downcall 句柄。
         *
         * @param handle downcall handle, must not be {@code null} / downcall 句柄，不能为 {@code null}
         * @param args call arguments / 调用参数
         * @return returned address segment / 返回的地址内存段
         * @throws IllegalStateException if invocation fails / 调用失败时抛出
         */
        private static MemorySegment invokeAddress(MethodHandle handle, Object... args) {
            try {
                return (MemorySegment) handle.invokeWithArguments(args);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Native Windows loader invocation failed", e);
            }
        }

        /**
         * Invokes a downcall handle returning an int.
         * <p>
         * 调用返回 int 的 downcall 句柄。
         *
         * @param handle downcall handle, must not be {@code null} / downcall 句柄，不能为 {@code null}
         * @param args call arguments / 调用参数
         * @return returned int value / 返回的 int 值
         * @throws IllegalStateException if invocation fails / 调用失败时抛出
         */
        private static int invokeInt(MethodHandle handle, Object... args) {
            try {
                return (int) handle.invokeWithArguments(args);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Native Windows loader invocation failed", e);
            }
        }
    }
}
