package ch.so.agi.gdal.ffm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CplErrorTypeTest {
    @Test
    void mapsKnownCodes() {
        assertEquals(CplErrorType.NONE, CplErrorType.fromCode(0));
        assertEquals(CplErrorType.DEBUG, CplErrorType.fromCode(1));
        assertEquals(CplErrorType.WARNING, CplErrorType.fromCode(2));
        assertEquals(CplErrorType.FAILURE, CplErrorType.fromCode(3));
        assertEquals(CplErrorType.FATAL, CplErrorType.fromCode(4));
    }

    @Test
    void mapsUnknownCodesToUnknown() {
        assertEquals(CplErrorType.UNKNOWN, CplErrorType.fromCode(99));
    }
}
