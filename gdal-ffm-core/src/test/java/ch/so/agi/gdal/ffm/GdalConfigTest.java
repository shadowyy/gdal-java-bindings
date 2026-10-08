package ch.so.agi.gdal.ffm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdalConfigTest {
    @Test
    void emptyConfigShouldBeReusableSingleton() {
        assertTrue(GdalConfig.empty().isEmpty());
        assertEquals(GdalConfig.empty(), GdalConfig.empty());
    }

    @Test
    void withConfigOptionShouldReturnNewNormalizedConfig() {
        GdalConfig config = GdalConfig.empty().withConfigOption(" GDAL_HTTP_BEARER ", "token");

        assertEquals("token", config.options().get("GDAL_HTTP_BEARER"));
        assertTrue(GdalConfig.empty().options().isEmpty());
    }

    @Test
    void withConfigShouldMergeAdditionalOptions() {
        GdalConfig config =
                GdalConfig.empty()
                        .withConfigOption("GDAL_HTTP_AUTH", "BASIC")
                        .withConfig(Map.of("GDAL_HTTP_USERPWD", "user:pw"));

        assertEquals(2, config.options().size());
        assertEquals("BASIC", config.options().get("GDAL_HTTP_AUTH"));
        assertEquals("user:pw", config.options().get("GDAL_HTTP_USERPWD"));
    }

    @Test
    void shouldRejectBlankOptionKeys() {
        assertThrows(IllegalArgumentException.class, () -> GdalConfig.empty().withConfigOption(" ", "x"));
    }
}
