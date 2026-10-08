package ch.so.agi.gdal.ffm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OgrWriteApiTest {
    @Test
    void parsesWriteModeFromString() {
        assertEquals(OgrWriteMode.FAIL_IF_EXISTS, OgrWriteMode.fromString(null));
        assertEquals(OgrWriteMode.FAIL_IF_EXISTS, OgrWriteMode.fromString("  "));
        assertEquals(OgrWriteMode.APPEND, OgrWriteMode.fromString("append"));
        assertEquals(OgrWriteMode.OVERWRITE, OgrWriteMode.fromString("OverWrite"));
    }

    @Test
    void validatesWriteSpec() {
        OgrLayerWriteSpec spec = new OgrLayerWriteSpec(
                "my_layer",
                1,
                List.of(new OgrFieldDefinition("id", OgrFieldType.INTEGER64)),
                OgrWriteMode.APPEND,
                Map.of("SPATIAL_INDEX", "YES"),
                Map.of("FID", "fid"),
                "fid",
                "geom"
        );

        assertEquals("my_layer", spec.layerName());
        assertEquals(1, spec.geometryTypeCode());
        assertEquals(OgrWriteMode.APPEND, spec.writeMode());
    }

    @Test
    void rejectsBlankLayerName() {
        assertThrows(IllegalArgumentException.class, () ->
                new OgrLayerWriteSpec("  ", 1, List.of()));
    }

    @Test
    void rejectsNegativeGeometryType() {
        assertThrows(IllegalArgumentException.class, () ->
                new OgrLayerWriteSpec("layer", -1, List.of()));
    }
}
