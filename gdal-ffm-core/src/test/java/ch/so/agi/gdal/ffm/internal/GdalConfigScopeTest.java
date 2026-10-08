package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GdalConfigScopeTest {
    @Test
    void effectiveConfigOptionsIncludeImplicitScopedDefaultsForEmptyConfig() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("osx-aarch64");

        Map<String, String> options = GdalConfigScope.effectiveConfigOptions(
                GdalConfig.empty(),
                bundleInfo,
                Map.of(),
                new Properties()
        );

        assertEquals(
                bundleInfo.caBundle().toAbsolutePath().toString(),
                options.get(NativeBundleRuntimeConfig.CURL_CA_BUNDLE)
        );
        assertEquals(
                bundleInfo.caBundle().toAbsolutePath().toString(),
                options.get(NativeBundleRuntimeConfig.SSL_CERT_FILE)
        );
    }

    @Test
    void effectiveConfigOptionsLetExplicitConfigOverrideImplicitDefaults() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("linux-x86_64");

        Map<String, String> options = GdalConfigScope.effectiveConfigOptions(
                GdalConfig.empty()
                        .withConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE, "/explicit/ca.pem")
                        .withConfigOption("GDAL_HTTP_BEARER", "token"),
                bundleInfo,
                Map.of(),
                new Properties()
        );

        assertEquals("/explicit/ca.pem", options.get(NativeBundleRuntimeConfig.CURL_CA_BUNDLE));
        assertEquals(
                bundleInfo.caBundle().toAbsolutePath().toString(),
                options.get(NativeBundleRuntimeConfig.SSL_CERT_FILE)
        );
        assertEquals("token", options.get("GDAL_HTTP_BEARER"));
    }

    @Test
    void effectiveConfigOptionsSkipImplicitDefaultsWhenEnvironmentDefinesSslCertFile() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("linux-aarch64");

        Map<String, String> options = GdalConfigScope.effectiveConfigOptions(
                GdalConfig.empty().withConfigOption("GDAL_HTTP_BEARER", "token"),
                bundleInfo,
                Map.of(NativeBundleRuntimeConfig.SSL_CERT_FILE, "/env/ca.pem"),
                new Properties()
        );

        assertFalse(options.containsKey(NativeBundleRuntimeConfig.CURL_CA_BUNDLE));
        assertFalse(options.containsKey(NativeBundleRuntimeConfig.SSL_CERT_FILE));
        assertEquals("token", options.get("GDAL_HTTP_BEARER"));
    }

    private static NativeBundleInfo createBundleInfo(String classifier) throws Exception {
        Path root = Files.createTempDirectory("gdal-config-scope");
        Path gdalData = Files.createDirectories(root.resolve("share/gdal"));
        Path projData = Files.createDirectories(root.resolve("share/proj"));
        Path driverPath = Files.createDirectories(root.resolve("lib/gdalplugins"));
        Path caBundle = root.resolve("ssl/cacert.pem");
        Files.createDirectories(caBundle.getParent());
        Files.writeString(caBundle, "bundle-ca", StandardCharsets.UTF_8);
        return new NativeBundleInfo(classifier, "3.12.2", root, gdalData, projData, driverPath, caBundle);
    }
}
