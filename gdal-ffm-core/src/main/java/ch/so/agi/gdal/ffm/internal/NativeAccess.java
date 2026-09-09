package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.Gdal;

/**
 * Guards Foreign Function and Memory (FFM) native access for the GDAL module.
 * <p>
 * GDAL 模块 FFM 本地访问守卫，本地调用前校验 JVM 已启用 native access。此为内部 API（internal, not public），请勿在业务代码中直接使用。
 */
final class NativeAccess {
    /**
     * Prevents instantiation; all members are static.
     * <p>
     * 禁止实例化，所有成员均为静态。
     */
    private NativeAccess() {
    }

    /**
     * Ensures native access is enabled for the module containing {@code Gdal}.
     * <p>
     * 确保 {@code Gdal} 所在模块已启用 native access，否则提示所需的 JVM 启动参数。
     *
     * @throws IllegalStateException if native access is not enabled / 未启用 native access 时抛出
     */
    static void ensureEnabled() {
        Module module = Gdal.class.getModule();
        if (module.isNativeAccessEnabled()) {
            return;
        }

        String moduleToken = module.isNamed() ? module.getName() : "ALL-UNNAMED";
        throw new IllegalStateException(
                "Native access is not enabled. Start the JVM with --enable-native-access=" + moduleToken
        );
    }
}
