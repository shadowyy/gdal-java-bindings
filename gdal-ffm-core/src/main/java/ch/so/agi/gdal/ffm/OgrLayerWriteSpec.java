package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Layer creation/write specification for OGR streaming exports.
 * <p>
 * OGR 流式导出的图层创建/写入规格：图层名、几何类型、字段、写模式与创建选项。
 *
 * @param layerName layer name, must not be blank / 图层名，不能为空白
 * @param geometryTypeCode OGR geometry type code ({@code wkb*}), may be {@code null} to let the driver decide /
 *                         OGR 几何类型码，可为 {@code null} 表示由驱动决定
 * @param fields field definitions, must not be {@code null} / 字段定义列表，不能为 {@code null}
 * @param writeMode behavior when dataset/layer exists, must not be {@code null} / 数据集/图层已存在时的策略，不能为 {@code null}
 * @param datasetCreationOptions driver dataset-creation options, must not be {@code null} / 数据集创建选项，不能为 {@code null}
 * @param layerCreationOptions driver layer-creation options, must not be {@code null} / 图层创建选项，不能为 {@code null}
 * @param fidFieldName FID field name, may be {@code null} / FID 字段名，可为 {@code null}
 * @param geometryFieldName geometry field name, may be {@code null} / 几何字段名，可为 {@code null}
 */
public record OgrLayerWriteSpec(
        String layerName,
        Integer geometryTypeCode,
        List<OgrFieldDefinition> fields,
        OgrWriteMode writeMode,
        Map<String, String> datasetCreationOptions,
        Map<String, String> layerCreationOptions,
        String fidFieldName,
        String geometryFieldName
) {
    /**
     * Canonical constructor with validation and defensive copies.
     * <p>
     * 规范构造器，校验并做防御性拷贝（空白 FID/几何字段名会被归一化为 {@code null}）。
     *
     * @param layerName layer name / 图层名
     * @param geometryTypeCode OGR geometry type code / OGR 几何类型码
     * @param fields field definitions / 字段定义列表
     * @param writeMode write mode / 写模式
     * @param datasetCreationOptions dataset creation options / 数据集创建选项
     * @param layerCreationOptions layer creation options / 图层创建选项
     * @param fidFieldName FID field name / FID 字段名
     * @param geometryFieldName geometry field name / 几何字段名
     * @throws NullPointerException if a required argument is {@code null} / 必填参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code layerName} is blank or {@code geometryTypeCode} is negative /
     *                                  图层名为空白或几何类型码为负时抛出
     */
    public OgrLayerWriteSpec {
        Objects.requireNonNull(layerName, "layerName must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        Objects.requireNonNull(layerCreationOptions, "layerCreationOptions must not be null");

        layerName = layerName.trim();
        if (layerName.isEmpty()) {
            throw new IllegalArgumentException("layerName must not be blank");
        }

        if (geometryTypeCode != null && geometryTypeCode < 0) {
            throw new IllegalArgumentException("geometryTypeCode must be >= 0 when set");
        }

        fields = List.copyOf(fields);
        datasetCreationOptions = Map.copyOf(datasetCreationOptions);
        layerCreationOptions = Map.copyOf(layerCreationOptions);

        if (fidFieldName != null && fidFieldName.isBlank()) {
            fidFieldName = null;
        }
        if (geometryFieldName != null && geometryFieldName.isBlank()) {
            geometryFieldName = null;
        }
    }

    /**
     * Convenience constructor with default write mode ({@link OgrWriteMode#FAIL_IF_EXISTS}).
     * <p>
     * 便捷构造器，写模式默认为“存在即失败”，创建选项为空。
     *
     * @param layerName layer name, must not be blank / 图层名，不能为空白
     * @param geometryTypeCode OGR geometry type code, may be {@code null} / OGR 几何类型码，可为 {@code null}
     * @param fields field definitions, must not be {@code null} / 字段定义列表，不能为 {@code null}
     * @throws NullPointerException if {@code layerName} or {@code fields} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code layerName} is blank / 图层名为空白时抛出
     */
    public OgrLayerWriteSpec(String layerName, Integer geometryTypeCode, List<OgrFieldDefinition> fields) {
        this(
                layerName,
                geometryTypeCode,
                fields,
                OgrWriteMode.FAIL_IF_EXISTS,
                Map.of(),
                Map.of(),
                null,
                null
        );
    }

    /**
     * Returns a copy with a different write mode.
     * <p>
     * 返回替换写模式后的新规格（原对象不变）。
     *
     * @param mode new write mode, must not be {@code null} / 新写模式，不能为 {@code null}
     * @return new spec, never {@code null} / 新规格，不会为 {@code null}
     * @throws NullPointerException if {@code mode} is {@code null} / 参数为 {@code null} 时抛出
     */
    public OgrLayerWriteSpec withWriteMode(OgrWriteMode mode) {
        return new OgrLayerWriteSpec(
                layerName,
                geometryTypeCode,
                fields,
                mode,
                datasetCreationOptions,
                layerCreationOptions,
                fidFieldName,
                geometryFieldName
        );
    }
}
