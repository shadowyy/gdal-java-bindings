package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.GdalConfigScope;
import java.util.Objects;

/**
 * Scoped (try-with-resources) GDAL configuration that is reverted on close.
 * <p>
 * 作用域限定的 GDAL 配置（try-with-resources），关闭时自动恢复之前的配置。
 * <pre>{@code
 * GdalConfig config = GdalConfig.empty().withConfigOption("GDAL_HTTP_TIMEOUT", "30");
 * try (ScopedGdalConfig ignored = ScopedGdalConfig.apply(config)) {
 *     // ... GDAL calls inside this block see the config / 块内 GDAL 调用可见该配置
 * }
 * }</pre>
 */
public final class ScopedGdalConfig implements AutoCloseable {
    private final GdalConfigScope.ScopedConfigHandle delegate;

    private ScopedGdalConfig(GdalConfigScope.ScopedConfigHandle delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Applies the given config for the current thread/scope until closed.
     * <p>
     * 在当前作用域内应用给定配置，直到关闭为止。
     *
     * @param config config to apply, must not be {@code null} / 待应用配置，不能为 {@code null}
     * @return scoped handle, must be closed by the caller / 作用域句柄，调用方负责关闭
     * @throws NullPointerException if {@code config} is {@code null} / 参数为 {@code null} 时抛出
     */
    public static ScopedGdalConfig apply(GdalConfig config) {
        return new ScopedGdalConfig(GdalConfigScope.applyScoped(config));
    }

    /**
     * Reverts the scoped configuration.
     * <p>
     * 恢复作用域之前的配置。
     */
    @Override
    public void close() {
        delegate.close();
    }
}
