package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrFieldType;
import ch.so.agi.gdal.ffm.OgrOpenOptions;
import ch.so.agi.gdal.ffm.OgrReaderOptions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OgrOptionsTest {
    @Test
    void parsesOpenOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(OgrOpenOptions.ALLOWED_DRIVERS, "GPKG, GeoJSON ; FlatGeobuf");
        options.put("AUTODETECT_TYPE", "YES");
        options.put("EMPTY_VALUE", "");

        OgrOptions.OpenOptions parsed = OgrOptions.parseOpenOptions(options);

        assertEquals(3, parsed.allowedDrivers().size());
        assertEquals("GPKG", parsed.allowedDrivers().get(0));
        assertEquals("GeoJSON", parsed.allowedDrivers().get(1));
        assertEquals("FlatGeobuf", parsed.allowedDrivers().get(2));

        assertEquals("YES", parsed.datasetOptions().get("AUTODETECT_TYPE"));
        assertEquals("", parsed.datasetOptions().get("EMPTY_VALUE"));
        assertEquals(2, parsed.datasetOptions().size());
    }

    @Test
    void parsesReaderOptions() {
        OgrOptions.ReaderOptions parsed = OgrOptions.parseReaderOptions(Map.of(
                OgrReaderOptions.ATTRIBUTE_FILTER, "name = 'abc'",
                OgrReaderOptions.BBOX, "1,2,3,4",
                OgrReaderOptions.SELECTED_FIELDS, "name; value, id",
                OgrReaderOptions.LIMIT, "42"
        ));

        assertEquals("name = 'abc'", parsed.attributeFilter());
        assertEquals(1.0d, parsed.bbox().minX());
        assertEquals(2.0d, parsed.bbox().minY());
        assertEquals(3.0d, parsed.bbox().maxX());
        assertEquals(4.0d, parsed.bbox().maxY());
        assertEquals(42L, parsed.limit());
        assertEquals(3, parsed.selectedFields().size());
        assertTrue(parsed.selectedFieldsLowercase().contains("name"));
        assertTrue(parsed.selectedFieldsLowercase().contains("value"));
        assertTrue(parsed.selectedFieldsLowercase().contains("id"));
    }

    @Test
    void rejectsConflictingSpatialFilters() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(
                        OgrReaderOptions.BBOX, "1,2,3,4",
                        OgrReaderOptions.SPATIAL_FILTER_WKT, "POLYGON((0 0,1 0,1 1,0 0))"
                )));

        assertTrue(error.getMessage().contains("Only one spatial filter"));
    }

    @Test
    void rejectsInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(OgrReaderOptions.LIMIT, "0")));

        assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(OgrReaderOptions.LIMIT, "abc")));
    }

    @Test
    void rejectsInvalidBbox() {
        assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(OgrReaderOptions.BBOX, "1,2,3")));

        assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(OgrReaderOptions.BBOX, "1,2,3,a")));

        assertThrows(IllegalArgumentException.class, () ->
                OgrOptions.parseReaderOptions(Map.of(OgrReaderOptions.BBOX, "10,2,3,4")));
    }

    @Test
    void mapsNativeFieldTypeCodes() {
        assertEquals(OgrFieldType.INTEGER, OgrFieldType.fromNativeCode(0));
        assertEquals(OgrFieldType.STRING, OgrFieldType.fromNativeCode(4));
        assertEquals(OgrFieldType.INTEGER64, OgrFieldType.fromNativeCode(12));
        assertEquals(OgrFieldType.UNKNOWN, OgrFieldType.fromNativeCode(9999));
    }

    @Test
    void keepsNullFiltersAsNull() {
        OgrOptions.ReaderOptions parsed = OgrOptions.parseReaderOptions(Map.of());
        assertNull(parsed.attributeFilter());
        assertNull(parsed.bbox());
        assertNull(parsed.spatialFilterWkt());
        assertNull(parsed.limit());
        assertEquals(0, parsed.selectedFields().size());
    }
}
