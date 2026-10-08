package ch.so.agi.gdal.ffm.internal;

/**
 * Test-only compatibility helper for packaged-native smoke assertions.
 */
final class GdalNative {
    private GdalNative() {
    }

    static String getThreadLocalConfigOption(String key) {
        return GdalConfigScope.getThreadLocalConfigOption(key);
    }
}
