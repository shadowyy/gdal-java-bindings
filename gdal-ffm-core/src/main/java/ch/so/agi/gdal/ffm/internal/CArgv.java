package ch.so.agi.gdal.ffm.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Helpers for building NUL-terminated {@code char[]} argument vectors for GDAL.
 * <p>
 * 为 GDAL 构建 NUL 结尾字符串指针数组的内部工具类，用于传递命令行参数与算法路径。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class CArgv {
    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态工具方法。
     */
    private CArgv() {
    }

    /**
     * Copies Java strings into a NUL-terminated native {@code char[]} array.
     * <p>
     * 将 Java 字符串数组复制为 NUL 结尾的本地字符串指针数组，末尾自动补 {@code NULL} 哨兵。
     *
     * @param args Java string arguments, may be {@code null} (treated as empty) /
     *             Java 字符串参数，可为 {@code null}（视为空数组）
     * @param arena FFM arena that owns the allocated native memory, must not be {@code null} /
     *              拥有本地内存的 FFM arena，不能为 {@code null}
     * @return native pointer to the first element of the array, lifetime bound to {@code arena} /
     *         数组首元素的本地指针，生命周期与 {@code arena} 绑定
     * @throws IllegalArgumentException if any element of {@code args} is {@code null} /
     *                                  若 {@code args} 中任一元素为 {@code null} 则抛出
     */
    static MemorySegment toCStringArray(String[] args, Arena arena) {
        String[] safeArgs = args == null ? new String[0] : args;
        MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, safeArgs.length + 1L);
        for (int i = 0; i < safeArgs.length; i++) {
            String arg = safeArgs[i];
            if (arg == null) {
                throw new IllegalArgumentException("args[" + i + "] must not be null");
            }
            MemorySegment value = arena.allocateFrom(arg);
            argv.setAtIndex(ValueLayout.ADDRESS, i, value);
        }
        argv.setAtIndex(ValueLayout.ADDRESS, safeArgs.length, MemorySegment.NULL);
        return argv;
    }
}
