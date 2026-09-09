package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.CplErrorType;
import ch.so.agi.gdal.ffm.GdalException;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;

/**
 * Helpers for converting the GDAL last-error state into exceptions.
 * <p>
 * 将 GDAL 的 last-error 状态转换为异常的内部工具类，统一读取错误码、类型与信息。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class GdalErrors {
    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态工具方法。
     */
    private GdalErrors() {
    }

    /**
     * Builds an exception from the current GDAL last-error state.
     * <p>
     * 根据当前 GDAL last-error 状态构建异常，包含错误码、错误类型与本地错误信息。
     *
     * @param message caller-supplied context message describing the failed operation /
     *                调用方提供的上下文信息，用于描述失败的操作
     * @return a new exception carrying both {@code message} and the GDAL error details /
     *         携带上下文信息与 GDAL 错误详情的新异常
     */
    static GdalException lastError(String message) {
        int errorNo = GdalGenerated.CPLGetLastErrorNo();
        CplErrorType errorType = CplErrorType.fromCode(GdalGenerated.CPLGetLastErrorType());
        String gdalMessage = CStrings.fromCString(GdalGenerated.CPLGetLastErrorMsg());
        if (gdalMessage.isBlank()) {
            gdalMessage = "No GDAL message available";
        }
        return new GdalException(message, errorType, errorNo, gdalMessage);
    }
}
