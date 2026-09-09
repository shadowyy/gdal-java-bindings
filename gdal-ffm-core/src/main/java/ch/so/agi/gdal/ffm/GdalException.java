package ch.so.agi.gdal.ffm;

/**
 * Unchecked exception for GDAL native failures, carrying CPL error details.
 * <p>
 * GDAL 本地调用失败的非受检异常，携带 CPL 错误细节（类型、编号、原始信息）。
 */
public final class GdalException extends RuntimeException {
    private final int errorNo;
    private final CplErrorType errorType;
    private final String gdalMessage;

    /**
     * Creates an exception with CPL details.
     * <p>
     * 由 CPL 错误细节构造异常。
     *
     * @param message high-level message, must not be {@code null} / 高层错误描述，不能为 {@code null}
     * @param errorType CPL error type, must not be {@code null} / CPL 错误类型，不能为 {@code null}
     * @param errorNo CPL error number ({@code CPLGetLastErrorNo}) / CPL 错误编号
     * @param gdalMessage raw GDAL message, may be {@code null} / GDAL 原始信息，可为 {@code null}
     */
    public GdalException(String message, CplErrorType errorType, int errorNo, String gdalMessage) {
        super(messageWithDetail(message, errorType, errorNo, gdalMessage));
        this.errorNo = errorNo;
        this.errorType = errorType;
        this.gdalMessage = gdalMessage;
    }

    /**
     * Creates an exception with CPL details and a cause.
     * <p>
     * 由 CPL 错误细节与原因构造异常。
     *
     * @param message high-level message, must not be {@code null} / 高层错误描述，不能为 {@code null}
     * @param errorType CPL error type, must not be {@code null} / CPL 错误类型，不能为 {@code null}
     * @param errorNo CPL error number / CPL 错误编号
     * @param gdalMessage raw GDAL message, may be {@code null} / GDAL 原始信息，可为 {@code null}
     * @param cause root cause, may be {@code null} / 根本原因，可为 {@code null}
     */
    public GdalException(String message, CplErrorType errorType, int errorNo, String gdalMessage, Throwable cause) {
        super(messageWithDetail(message, errorType, errorNo, gdalMessage), cause);
        this.errorNo = errorNo;
        this.errorType = errorType;
        this.gdalMessage = gdalMessage;
    }

    /**
     * Returns the CPL error number.
     * <p>
     * 返回 CPL 错误编号。
     *
     * @return error number / 错误编号
     */
    public int getErrorNo() {
        return errorNo;
    }

    /**
     * Returns the CPL error type.
     * <p>
     * 返回 CPL 错误类型。
     *
     * @return error type, never {@code null} / 错误类型，不会为 {@code null}
     */
    public CplErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns the raw GDAL message.
     * <p>
     * 返回 GDAL 原始错误信息。
     *
     * @return raw message, may be {@code null} / 原始信息，可为 {@code null}
     */
    public String getGdalMessage() {
        return gdalMessage;
    }

    private static String messageWithDetail(String message, CplErrorType errorType, int errorNo, String gdalMessage) {
        String detail = "type=" + errorType + ", no=" + errorNo + ", gdal='" + gdalMessage + "'";
        return message + " (" + detail + ")";
    }
}
