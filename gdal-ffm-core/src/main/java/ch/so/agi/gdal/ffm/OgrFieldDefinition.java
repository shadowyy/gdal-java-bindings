package ch.so.agi.gdal.ffm;

import java.util.Objects;

/**
 * OGR field metadata (name and type).
 * <p>
 * OGR 字段元信息：字段名与字段类型。
 *
 * @param name field name, must not be {@code null} / 字段名，不能为 {@code null}
 * @param type field type, must not be {@code null} / 字段类型，不能为 {@code null}
 * @see OgrFieldType
 */
public record OgrFieldDefinition(String name, OgrFieldType type) {
    /**
     * Canonical constructor with null checks.
     * <p>
     * 规范构造器，校验字段名与类型非空。
     *
     * @param name field name / 字段名
     * @param type field type / 字段类型
     * @throws NullPointerException if any argument is {@code null} / 任一参数为 {@code null} 时抛出
     */
    public OgrFieldDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
