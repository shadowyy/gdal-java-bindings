package ch.so.agi.gdal.ffm;

/**
 * Mirrors GDAL's {@code CPLErr} enum values.
 * <p>
 * 对应 GDAL 本地 {@code CPLErr} 枚举值。
 */
public enum CplErrorType {
    /** No error. / 无错误。 */
    NONE(0),
    /** Debug message. / 调试信息。 */
    DEBUG(1),
    /** Warning (operation usually continues). / 警告（通常可继续）。 */
    WARNING(2),
    /** Failure (operation failed). / 失败（操作未成功）。 */
    FAILURE(3),
    /** Fatal error. / 致命错误。 */
    FATAL(4),
    /** Unmapped/unknown code. / 未映射的未知码。 */
    UNKNOWN(Integer.MIN_VALUE);

    private final int code;

    CplErrorType(int code) {
        this.code = code;
    }

    /**
     * Returns the native {@code CPLErr} code.
     * <p>
     * 返回本地 {@code CPLErr} 数值码。
     *
     * @return native code / 本地数值码
     */
    public int code() {
        return code;
    }

    /**
     * Maps a native code back to the enum, falling back to {@link #UNKNOWN}.
     * <p>
     * 由本地数值码映射回枚举，未知数值返回 {@link #UNKNOWN}。
     *
     * @param code native {@code CPLErr} code / 本地错误码
     * @return matching constant, never {@code null} / 匹配的枚举常量，不会为 {@code null}
     */
    public static CplErrorType fromCode(int code) {
        return switch (code) {
            case 0 -> NONE;
            case 1 -> DEBUG;
            case 2 -> WARNING;
            case 3 -> FAILURE;
            case 4 -> FATAL;
            default -> UNKNOWN;
        };
    }
}
