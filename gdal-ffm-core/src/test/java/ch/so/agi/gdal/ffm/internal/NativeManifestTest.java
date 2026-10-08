package ch.so.agi.gdal.ffm.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeManifestTest {
    @Test
    void parsesManifestJson() {
        String json = """
                {
                  "bundleVersion": "3.12.2",
                  "entryLibrary": "lib/libgdal.so.37",
                  "preloadLibraries": ["lib/libproj.so.25", "lib/libgeos_c.so.1"],
                  "gdalDataPath": "share/gdal",
                  "projDataPath": "share/proj",
                  "driverPath": "lib/gdalplugins",
                  "caBundlePath": "ssl/cacert.pem",
                  "cacheKey": "standard-abc123"
                }
                """;

        NativeManifest manifest = NativeManifest.parse(json);

        assertEquals("3.12.2", manifest.bundleVersion());
        assertEquals("lib/libgdal.so.37", manifest.entryLibrary());
        assertEquals(2, manifest.preloadLibraries().size());
        assertEquals("share/gdal", manifest.gdalDataPath());
        assertEquals("share/proj", manifest.projDataPath());
        assertEquals("lib/gdalplugins", manifest.driverPath());
        assertEquals("ssl/cacert.pem", manifest.caBundlePath());
        assertEquals("standard-abc123", manifest.cacheKey());
    }
}
