package ch.so.agi.gdal.ffm;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OgrGeometryTest {
    @Test
    void fromWkbWithoutSridKeepsPayloadAndNoSrid() {
        byte[] wkbPoint = littleEndianPointWkb(7.0, 8.0);

        OgrGeometry geometry = OgrGeometry.fromWkb(wkbPoint);

        assertArrayEquals(wkbPoint, geometry.ewkb());
        assertFalse(geometry.srid().isPresent());
    }

    @Test
    void fromWkbWithSridConvertsToEwkbAndStoresSrid() {
        byte[] wkbPoint = littleEndianPointWkb(7.0, 8.0);

        OgrGeometry geometry = OgrGeometry.fromWkb(wkbPoint, 2056);

        assertEquals(2056, geometry.srid().orElseThrow());
        byte[] ewkb = geometry.ewkb();
        assertEquals(1, ewkb[0]);
        int typeWithFlags = ByteBuffer.wrap(ewkb).order(ByteOrder.LITTLE_ENDIAN).getInt(1);
        assertEquals(0x2000_0001, typeWithFlags);
        int srid = ByteBuffer.wrap(ewkb).order(ByteOrder.LITTLE_ENDIAN).getInt(5);
        assertEquals(2056, srid);
    }

    @Test
    void fromEwkbReadsEmbeddedSrid() {
        byte[] ewkb = littleEndianPointEwkb(2600, 1.0, 2.0);

        OgrGeometry geometry = OgrGeometry.fromEwkb(ewkb);

        assertEquals(2600, geometry.srid().orElseThrow());
        assertArrayEquals(ewkb, geometry.ewkb());
    }

    @Test
    void rejectsUnsupportedByteOrderMarker() {
        byte[] invalid = littleEndianPointWkb(1.0, 2.0);
        invalid[0] = 2;

        assertThrows(IllegalArgumentException.class, () -> OgrGeometry.fromEwkb(invalid));
    }

    private static byte[] littleEndianPointWkb(double x, double y) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1);
        buffer.putInt(1);
        buffer.putDouble(x);
        buffer.putDouble(y);
        return buffer.array();
    }

    private static byte[] littleEndianPointEwkb(int srid, double x, double y) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 4 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1);
        buffer.putInt(0x2000_0001);
        buffer.putInt(srid);
        buffer.putDouble(x);
        buffer.putDouble(y);
        return buffer.array();
    }
}
