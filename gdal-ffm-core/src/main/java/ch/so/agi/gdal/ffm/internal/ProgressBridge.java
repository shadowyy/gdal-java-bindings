package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.ProgressCallback;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges Java progress callbacks to native GDAL progress functions via FFM upcalls.
 * <p>
 * 通过 FFM 上行调用将 Java 进度回调桥接为本地 GDAL 进度函数，管理回调注册与用户数据传递。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class ProgressBridge {
    /**
     * Generator for unique callback identifiers.
     * <p>
     * 回调标识的唯一 ID 生成器，每次创建回调时自增分配。
     */
    private static final AtomicLong IDS = new AtomicLong(1L);
    /**
     * Active callbacks keyed by identifier.
     * <p>
     * 按标识存储的活跃回调表，供本地上行调用时反查 Java 回调。
     */
    private static final ConcurrentMap<Long, CallbackState> CALLBACKS = new ConcurrentHashMap<>();
    /**
     * Trampoline handle for the native progress upcall.
     * <p>
     * 本地进度上行调用的跳转句柄，指向内部的 {@code invoke} 方法。
     */
    private static final MethodHandle TRAMPOLINE;
    /**
     * Native progress function signature: int(double, const char[], void[]).
     * <p>
     * 本地进度函数签名：返回 int，参数为完成度、消息指针与用户数据指针。
     */
    private static final FunctionDescriptor PROGRESS_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
    );

    static {
        try {
            TRAMPOLINE = MethodHandles.lookup()
                    .findStatic(
                            ProgressBridge.class,
                            "invoke",
                            MethodType.methodType(int.class, double.class, MemorySegment.class, MemorySegment.class)
                    );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态桥接方法。
     */
    private ProgressBridge() {
    }

    /**
     * Creates a native progress callback pair for the given Java callback.
     * <p>
     * 为给定的 Java 回调创建本地进度回调对，包含函数指针与用户数据，生命周期绑定到 arena。
     *
     * @param callback Java progress callback, may be {@code null} (yields a no-op handle) /
     *                 Java 进度回调，可为 {@code null}（此时返回空操作句柄）
     * @param arena FFM arena that owns the stub and user data, must not be {@code null} /
     *              拥有桩代码与用户数据的 FFM arena，不能为 {@code null}
     * @return handle carrying the native function pointer and user data, never {@code null} /
     *         携带本地函数指针与用户数据的句柄，永不为 {@code null}
     */
    static ProgressHandle create(ProgressCallback callback, Arena arena) {
        if (callback == null) {
            return ProgressHandle.NONE;
        }

        long id = IDS.getAndIncrement();
        CallbackState state = new CallbackState(callback);
        CALLBACKS.put(id, state);

        MemorySegment userData = arena.allocate(ValueLayout.JAVA_LONG);
        userData.set(ValueLayout.JAVA_LONG, 0, id);

        MemorySegment stub = Linker.nativeLinker().upcallStub(TRAMPOLINE, PROGRESS_DESCRIPTOR, arena);
        return new ProgressHandle(id, stub, userData, state);
    }

    /**
     * Native upcall entry point invoked by GDAL to report progress.
     * <p>
     * 供 GDAL 调用的本地上行入口，根据用户数据中的标识反查 Java 回调并转发进度。
     *
     * @param complete completion ratio in [0, 1], as reported by GDAL / GDAL 上报的完成度，范围 [0, 1]
     * @param message native message pointer, may be null / 本地消息指针，可能为空
     * @param userData native user-data pointer carrying the callback id, may be null /
     *                 携带回调标识的本地用户数据指针，可能为空
     * @return 1 to continue, 0 to request cancellation / 返回 1 表示继续，0 表示请求中断
     */
    @SuppressWarnings("unused")
    private static int invoke(double complete, MemorySegment message, MemorySegment userData) {
        if (CStrings.isNull(userData)) {
            return 1;
        }

        long id = userData.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0);
        CallbackState state = CALLBACKS.get(id);
        if (state == null) {
            return 1;
        }

        try {
            boolean keepGoing = state.callback.onProgress(complete, CStrings.fromCString(message));
            return keepGoing ? 1 : 0;
        } catch (RuntimeException ex) {
            state.failure = ex;
            return 0;
        }
    }

    /**
     * Lifetime handle for one bridged progress callback.
     * <p>
     * 一次桥接进度回调的生命周期句柄，持有本地函数指针、用户数据，并在关闭时注销回调。
     * <p>
     * Internal API, not public. Do not use from application code; it may change without notice.
     * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
     */
    static final class ProgressHandle implements AutoCloseable {
        /**
         * Shared no-op handle used when no callback was supplied.
         * <p>
         * 未提供回调时的共享空操作句柄，其函数指针与用户数据均为 {@code NULL}。
         */
        static final ProgressHandle NONE = new ProgressHandle(0L, MemorySegment.NULL, MemorySegment.NULL, null);

        /**
         * Callback identifier registered in the bridge table, 0 means none.
         * <p>
         * 在桥接表中注册的回调标识，0 表示无回调。
         */
        private final long id;
        /**
         * Native function pointer passed to GDAL.
         * <p>
         * 传递给 GDAL 的本地函数指针。
         */
        private final MemorySegment callbackFn;
        /**
         * Native user-data pointer carrying the callback id.
         * <p>
         * 携带回调标识的本地用户数据指针。
         */
        private final MemorySegment userData;
        /**
         * Mutable callback state, may be {@code null} for the no-op handle.
         * <p>
         * 可变的回调状态，空操作句柄下为 {@code null}。
         */
        private final CallbackState state;

        /**
         * Creates a handle for one bridged callback.
         * <p>
         * 为一次桥接回调创建句柄，保存标识、函数指针、用户数据与状态。
         *
         * @param id callback identifier, 0 means none / 回调标识，0 表示无回调
         * @param callbackFn native function pointer, must not be {@code null} / 本地函数指针，不能为 {@code null}
         * @param userData native user-data pointer, must not be {@code null} / 本地用户数据指针，不能为 {@code null}
         * @param state mutable callback state, may be {@code null} for the no-op handle /
         *              可变回调状态，空操作句柄下可为 {@code null}
         */
        private ProgressHandle(long id, MemorySegment callbackFn, MemorySegment userData, CallbackState state) {
            this.id = id;
            this.callbackFn = callbackFn;
            this.userData = userData;
            this.state = state;
        }

        /**
         * Returns the native function pointer to pass to GDAL.
         * <p>
         * 返回传递给 GDAL 的本地函数指针。
         *
         * @return native function pointer / 本地函数指针
         */
        MemorySegment callbackFn() {
            return callbackFn;
        }

        /**
         * Returns the native user-data pointer to pass to GDAL.
         * <p>
         * 返回传递给 GDAL 的本地用户数据指针。
         *
         * @return native user-data pointer / 本地用户数据指针
         */
        MemorySegment userData() {
            return userData;
        }

        /**
         * Returns the failure thrown by the Java callback, if any.
         * <p>
         * 返回 Java 回调抛出的异常（若有），无异常时返回 {@code null}。
         *
         * @return callback failure, or {@code null} when the callback did not fail /
         *         回调异常，无失败时返回 {@code null}
         */
        RuntimeException callbackFailure() {
            return state == null ? null : state.failure;
        }

        /**
         * Unregisters the callback from the bridge table.
         * <p>
         * 从桥接表中注销回调，空操作句柄关闭时无任何效果。
         */
        @Override
        public void close() {
            if (id != 0L) {
                CALLBACKS.remove(id);
            }
        }
    }

    /**
     * Mutable per-callback state shared between Java and the native upcall.
     * <p>
     * 在 Java 与本地上行调用之间共享的单次回调可变状态，保存回调实例与异常。
     * <p>
     * Internal API, not public. Do not use from application code; it may change without notice.
     * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
     */
    private static final class CallbackState {
        /**
         * Java progress callback to invoke.
         * <p>
         * 待调用的 Java 进度回调。
         */
        private final ProgressCallback callback;
        /**
         * Failure thrown by the callback, observed by the calling thread.
         * <p>
         * 回调抛出的异常，由调用线程观察并重新抛出。
         */
        private volatile RuntimeException failure;

        /**
         * Creates the state for one Java callback.
         * <p>
         * 为一个 Java 回调创建状态对象。
         *
         * @param callback Java progress callback, must not be {@code null} / Java 进度回调，不能为 {@code null}
         */
        private CallbackState(ProgressCallback callback) {
            this.callback = callback;
        }
    }
}
