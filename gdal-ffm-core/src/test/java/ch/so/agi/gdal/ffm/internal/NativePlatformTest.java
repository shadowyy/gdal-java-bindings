package ch.so.agi.gdal.ffm.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativePlatformTest {
    @Test
    void normalizesMacArm64() {
        NativePlatform platform = NativePlatform.from("Mac OS X", "aarch64");
        assertEquals("osx-aarch64", platform.classifier());
    }

    @Test
    void normalizesWindowsAmd64() {
        NativePlatform platform = NativePlatform.from("Windows 11", "amd64");
        assertEquals("windows-x86_64", platform.classifier());
    }

    @Test
    void rejectsUnsupportedArch() {
        assertThrows(IllegalStateException.class, () -> NativePlatform.from("Linux", "ppc64"));
    }
}
