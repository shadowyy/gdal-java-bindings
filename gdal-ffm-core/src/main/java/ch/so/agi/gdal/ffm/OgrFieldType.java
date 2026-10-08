package ch.so.agi.gdal.ffm;

/**
 * OGR field type codes (mirrors {@code OGRFieldType}).
 * <p>
 * OGR 字段类型码（对应本地 {@code OGRFieldType} 枚举）。
 */
public enum OgrFieldType {
    /**
     * 32-bit integer. / 32 位整数。
     */
    INTEGER(0),
    /**
     * List of 32-bit integers. / 32 位整数列表。
     */
    INTEGER_LIST(1),
    /**
     * Double floating point. / 双精度浮点。
     */
    REAL(2),
    /**
     * List of doubles. / 双精度浮点列表。
     */
    REAL_LIST(3),
    /**
     * UTF-8 string. / 字符串。
     */
    STRING(4),
    /**
     * List of strings. / 字符串列表。
     */
    STRING_LIST(5),
    /**
     * Wide string. / 宽字符串。
     */
    WIDE_STRING(6),
    /**
     * List of wide strings. / 宽字符串列表。
     */
    WIDE_STRING_LIST(7),
    /**
     * Raw binary. / 二进制。
     */
    BINARY(8),
    /**
     * Date ({@code YYYY-MM-DD}). / 日期。
     */
    DATE(9),
    /**
     * Time ({@code HH:MM:SS}). / 时间。
     */
    TIME(10),
    /**
     * Date and time. / 日期时间。
     */
    DATETIME(11),
    /**
     * 64-bit integer. / 64 位整数。
     */
    INTEGER64(12),
    /**
     * List of 64-bit integers. / 64 位整数列表。
     */
    INTEGER64_LIST(13),
    /**
     * Unknown / unmapped native type. / 未知/未映射的本地类型。
     */
    UNKNOWN(-1);

    private final int nativeCode;

    OgrFieldType(int nativeCode) {
        this.nativeCode = nativeCode;
    }

    /**
     * Returns the native {@code OGRFieldType} code.
     * <p>
     * 返回本地 {@code OGRFieldType} 数值码。
     *
     * @return native code / 本地数值码
     */
    public int nativeCode() {
        return nativeCode;
    }

    /**
     * Maps a native code back to the enum, falling back to {@link #UNKNOWN}.
     * <p>
     * 由本地数值码映射回枚举，未知数值返回 {@link #UNKNOWN}。
     *
     * @param nativeCode native {@code OGRFieldType} code / 本地类型码
     * @return matching enum constant, never {@code null} / 匹配的枚举常量，不会为 {@code null}
     */
    public static OgrFieldType fromNativeCode(int nativeCode) {
        for (OgrFieldType value : values()) {
            if (value.nativeCode == nativeCode) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
