package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * OGR layer metadata (name, geometry type, fields).
 * <p>
 * OGR 图层元信息：图层名、几何类型码、字段定义。
 *
 * @param name layer name, must not be {@code null} / 图层名，不能为 {@code null}
 * @param geometryType OGR geometry type code ({@code wkb*}, e.g. {@code 1} for Point) /
 *                     OGR 几何类型码（{@code wkb*}，如 Point 为 {@code 1}）
 * @param fields field definitions, must not be {@code null} (defensively copied) /
 *               字段定义列表，不能为 {@code null}（内部拷贝）
 */
public record OgrLayerDefinition(String name, int geometryType, List<OgrFieldDefinition> fields) {
    /**
     * Canonical constructor with null checks.
     * <p>
     * 规范构造器，校验非空并拷贝字段列表。
     *
     * @param name layer name / 图层名
     * @param geometryType OGR geometry type code / OGR 几何类型码
     * @param fields field definitions / 字段定义列表
     * @throws NullPointerException if {@code name} or {@code fields} is {@code null} / 参数为 {@code null} 时抛出
     */
    public OgrLayerDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        fields = List.copyOf(fields);
    }
}
