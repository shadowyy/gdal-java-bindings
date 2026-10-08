package ch.so.agi.gdal.ffm.internal;

import java.lang.foreign.MemorySegment;

/**
 * Helpers for reading NUL-terminated C strings via FFM.
 * <p>
 * 通过 FFM 读取 NUL 结尾 C 字符串的内部工具类，统一处理空指针与解码逻辑。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class CStrings {
    /**
     * Maximum bytes to scan when decoding a C string.
     * <p>
     * 解码 C 字符串时最多扫描的字节数，防止越界读取。
     */
    private static final long MAX_C_STRING_BYTES = 8L * 1024L * 1024L;

    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态工具方法。
     */
    private CStrings() {
    }

    /**
     * Decodes a NUL-terminated C string to a Java {@code String}.
     * <p>
     * 将 NUL 结尾的 C 字符串解码为 Java 字符串，空指针返回空字符串。
     *
     * @param cString native C string pointer, may be {@code null} or {@code NULL} /
     *                本地 C 字符串指针，可为 {@code null} 或 {@code NULL}
     * @return decoded string, or {@code ""} if the pointer is null / 解码后的字符串，指针为空时返回 {@code ""}
     */
    static String fromCString(MemorySegment cString) {
        if (isNull(cString)) {
            return "";
        }
        return cString.reinterpret(MAX_C_STRING_BYTES).getString(0);
    }

    /**
     * Checks whether a native pointer should be treated as null.
     * <p>
     * 判断本地指针是否应视为空，包括 {@code null}、{@code NULL} 与地址为 0 的情况。
     *
     * @param segment native pointer to check, may be {@code null} / 待检查的本地指针，可为 {@code null}
     * @return {@code true} if the pointer is null-like, otherwise {@code false} /
     * 若指针为空则返回 {@code true}，否则返回 {@code false}
     */
    static boolean isNull(MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL) || segment.address() == 0L;
    }
}
