package ch.so.agi.gdal.ffm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetRefTest {
    @Test
    void localPathShouldNormalizeAndExposeAbsolutePath() {
        DatasetRef datasetRef = DatasetRef.local(Path.of("build", "..", "build", "test.tif"));

        assertEquals(DatasetRefType.LOCAL_PATH, datasetRef.type());
        assertTrue(datasetRef.localPath().isAbsolute());
        assertEquals(datasetRef.localPath().toString(), datasetRef.toGdalIdentifier());
    }

    @Test
    void httpUrlShouldUseVsicurlIdentifier() {
        DatasetRef datasetRef = DatasetRef.httpUrl("https://example.com/data.tif");

        assertEquals(DatasetRefType.HTTP_URL, datasetRef.type());
        assertEquals("/vsicurl/https://example.com/data.tif", datasetRef.toGdalIdentifier());
    }

    @Test
    void gdalVsiShouldKeepExplicitIdentifier() {
        DatasetRef datasetRef = DatasetRef.gdalVsi("/vsimem/example.tif");

        assertEquals(DatasetRefType.GDAL_VSI, datasetRef.type());
        assertEquals("/vsimem/example.tif", datasetRef.toGdalIdentifier());
    }

    @Test
    void shouldRejectInvalidHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> DatasetRef.httpUrl("ftp://example.com/data.tif"));
    }

    @Test
    void shouldRejectInvalidVsiPrefix() {
        assertThrows(IllegalArgumentException.class, () -> DatasetRef.gdalVsi("vsimem/example.tif"));
    }

    @Test
    void rawShouldKeepPgConnectionStringVerbatim() {
        String connection = "PG:\"host=192.168.0.112 port=65432 dbname=test user=postgres password=123456\"";

        DatasetRef datasetRef = DatasetRef.raw(connection);

        assertEquals(DatasetRefType.RAW, datasetRef.type());
        assertEquals(connection, datasetRef.identifier());
        assertEquals(connection, datasetRef.toGdalIdentifier());
    }

    @Test
    void rawShouldKeepChineseIdentifierVerbatim() {
        DatasetRef datasetRef = DatasetRef.raw("PG:dbname=三亚市 layer=河流管理范围线");

        assertEquals(DatasetRefType.RAW, datasetRef.type());
        assertEquals("PG:dbname=三亚市 layer=河流管理范围线", datasetRef.toGdalIdentifier());
    }

    @Test
    void rawShouldNotBeLocalPath() {
        DatasetRef datasetRef = DatasetRef.raw("PG:dbname=test");

        assertFalse(datasetRef.isLocalPath());
        assertThrows(IllegalStateException.class, datasetRef::localPath);
    }

    @Test
    void rawShouldRejectBlankIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> DatasetRef.raw("   "));
    }

    @Test
    void rawShouldRejectNullIdentifier() {
        assertThrows(NullPointerException.class, () -> DatasetRef.raw(null));
    }
}
