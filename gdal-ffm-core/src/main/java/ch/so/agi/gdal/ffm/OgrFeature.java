package ch.so.agi.gdal.ffm;

import java.util.Map;
import java.util.Objects;

/**
 * Neutral feature DTO for stream-oriented OGR integrations.
 * <p>
 * 面向流式集成的中立要素 DTO：与具体驱动解耦，只含 FID、属性与几何。
 *
 * @param fid feature id ({@code OGR_FID}); may be {@code 0}/negative when unset /
 *            要素 ID（{@code OGR_FID}）；未设置时可为 {@code 0} 或负数
 * @param attributes attribute map (name -&gt; value), must not be {@code null}, immutable copy recommended /
 *                   属性映射（字段名 -&gt; 值），不能为 {@code null}
 * @param geometry feature geometry, may be {@code null} for attribute-only features /
 *                 要素几何，可为 {@code null} 表示纯属性要素
 */
public record OgrFeature(long fid, Map<String, Object> attributes, OgrGeometry geometry) {
    /**
     * Canonical constructor with null check.
     * <p>
     * 规范构造器，校验属性映射非空。
     *
     * @param fid feature id / 要素 ID
     * @param attributes attribute map, must not be {@code null} / 属性映射，不能为 {@code null}
     * @param geometry feature geometry, may be {@code null} / 要素几何，可为 {@code null}
     * @throws NullPointerException if {@code attributes} is {@code null} / 属性映射为 {@code null} 时抛出
     */
    public OgrFeature {
        Objects.requireNonNull(attributes, "attributes must not be null");
    }
}
