package ch.so.agi.gdal.ffm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Neutral geometry container for vector streaming APIs.
 * <p>
 * 矢量流式 API 的中立几何容器。
 * <p>
 * Internally stores EWKB bytes (WKB with optional SRID flag/value). If an SRID is provided,
 * the EWKB payload is normalized to include it.
 * <p>
 * 内部存储 EWKB 字节（带可选 SRID 标志/值的 WKB）。若提供 SRID，会归一化写入 EWKB 头。
 */
public final class OgrGeometry {
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int WKB_HEADER_SIZE = 5;
    private static final int EWKB_SRID_SIZE = 4;

    private final byte[] ewkb;
    private final Integer srid;

    private OgrGeometry(byte[] ewkb, Integer srid) {
        this.ewkb = ewkb;
        this.srid = srid;
    }

    /**
     * Wraps EWKB bytes (SRID is parsed from the payload when present).
     * <p>
     * 由 EWKB 字节构造几何，若负载中含 SRID 则自动解析。
     *
     * @param ewkb EWKB payload, must not be {@code null} (defensively copied) /
     *             EWKB 负载，不能为 {@code null}（内部会拷贝）
     * @return new geometry instance, never {@code null} / 新几何实例，不会为 {@code null}
     * @throws NullPointerException if {@code ewkb} is {@code null} / 参数为 {@code null} 时抛出
     */
    public static OgrGeometry fromEwkb(byte[] ewkb) {
        Objects.requireNonNull(ewkb, "ewkb must not be null");
        byte[] copy = Arrays.copyOf(ewkb, ewkb.length);
        OptionalInt parsedSrid = extractSrid(copy);
        Integer srid = parsedSrid.isPresent() ? parsedSrid.getAsInt() : null;
        return new OgrGeometry(copy, srid);
    }

    /**
     * Wraps plain WKB bytes without SRID.
     * <p>
     * 由普通 WKB 字节构造几何（无 SRID）。
     *
     * @param wkb WKB payload, must not be {@code null} (defensively copied) /
     *            WKB 负载，不能为 {@code null}（内部会拷贝）
     * @return new geometry instance, never {@code null} / 新几何实例，不会为 {@code null}
     * @throws NullPointerException if {@code wkb} is {@code null} / 参数为 {@code null} 时抛出
     */
    public static OgrGeometry fromWkb(byte[] wkb) {
        Objects.requireNonNull(wkb, "wkb must not be null");
        return new OgrGeometry(Arrays.copyOf(wkb, wkb.length), null);
    }

    /**
     * Wraps WKB bytes and attaches an SRID (normalized into EWKB).
     * <p>
     * 由 WKB 字节构造几何并附加 SRID（归一化为 EWKB）。
     *
     * @param wkb WKB/EWKB payload, must not be {@code null} / WKB/EWKB 负载，不能为 {@code null}
     * @param srid spatial reference id, must be {@code >= 0}, e.g. {@code 2056} /
     *             空间参考 ID，必须 {@code >= 0}，例如 {@code 2056}
     * @return new geometry instance with SRID, never {@code null} / 带 SRID 的新几何实例，不会为 {@code null}
     * @throws NullPointerException if {@code wkb} is {@code null} / 参数为 {@code null} 时抛出
     * @throws IllegalArgumentException if {@code srid} is negative or payload is too short /
     *                                  SRID 为负或负载过短时抛出
     */
    public static OgrGeometry fromWkb(byte[] wkb, int srid) {
        Objects.requireNonNull(wkb, "wkb must not be null");
        if (srid < 0) {
            throw new IllegalArgumentException("srid must be >= 0");
        }
        byte[] ewkb = ensureSridInEwkb(wkb, srid);
        return new OgrGeometry(ewkb, srid);
    }

    /**
     * Returns a defensive copy of the EWKB payload.
     * <p>
     * 返回 EWKB 负载的防御性拷贝。
     *
     * @return EWKB bytes, never {@code null} / EWKB 字节，不会为 {@code null}
     */
    public byte[] ewkb() {
        return Arrays.copyOf(ewkb, ewkb.length);
    }

    /**
     * Returns the SRID when present in the payload.
     * <p>
     * 返回负载中的 SRID（若存在）。
     *
     * @return SRID, or empty when the geometry has none / SRID，无则为空
     */
    public OptionalInt srid() {
        return srid == null ? OptionalInt.empty() : OptionalInt.of(srid);
    }

    private static OptionalInt extractSrid(byte[] candidate) {
        if (candidate.length < WKB_HEADER_SIZE) {
            return OptionalInt.empty();
        }
        ByteOrder order = byteOrder(candidate[0]);
        ByteBuffer buffer = ByteBuffer.wrap(candidate).order(order);
        int rawType = buffer.getInt(1);
        if ((rawType & EWKB_SRID_FLAG) == 0) {
            return OptionalInt.empty();
        }
        if (candidate.length < WKB_HEADER_SIZE + EWKB_SRID_SIZE) {
            return OptionalInt.empty();
        }
        int srid = buffer.getInt(WKB_HEADER_SIZE);
        return srid >= 0 ? OptionalInt.of(srid) : OptionalInt.empty();
    }

    private static byte[] ensureSridInEwkb(byte[] wkbOrEwkb, int srid) {
        if (wkbOrEwkb.length < WKB_HEADER_SIZE) {
            throw new IllegalArgumentException("WKB/EWKB payload is too short");
        }
        byte orderMarker = wkbOrEwkb[0];
        ByteOrder order = byteOrder(orderMarker);
        ByteBuffer in = ByteBuffer.wrap(wkbOrEwkb).order(order);
        int rawType = in.getInt(1);
        boolean hasSrid = (rawType & EWKB_SRID_FLAG) != 0;

        if (hasSrid) {
            byte[] updated = Arrays.copyOf(wkbOrEwkb, wkbOrEwkb.length);
            ByteBuffer out = ByteBuffer.wrap(updated).order(order);
            out.putInt(WKB_HEADER_SIZE, srid);
            return updated;
        }

        byte[] result = new byte[wkbOrEwkb.length + EWKB_SRID_SIZE];
        result[0] = orderMarker;
        ByteBuffer out = ByteBuffer.wrap(result).order(order);
        out.putInt(1, rawType | EWKB_SRID_FLAG);
        out.putInt(WKB_HEADER_SIZE, srid);
        System.arraycopy(wkbOrEwkb, WKB_HEADER_SIZE, result, WKB_HEADER_SIZE + EWKB_SRID_SIZE,
                wkbOrEwkb.length - WKB_HEADER_SIZE);
        return result;
    }

    private static ByteOrder byteOrder(byte marker) {
        return switch (marker) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalArgumentException("Unsupported WKB byte order marker: " + marker);
        };
    }
}
