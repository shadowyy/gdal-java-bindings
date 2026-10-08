package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.Gdal;
import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.Ogr;
import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrFeature;
import ch.so.agi.gdal.ffm.OgrGeometry;
import ch.so.agi.gdal.ffm.OgrLayerDefinition;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriteSpec;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import ch.so.agi.gdal.ffm.OgrWriteMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class GdalPackagedNativeSmoke {
    private static final int WKB_LINESTRING = 2;
    private static final int WKB_POLYGON = 3;
    private static final int WKB_CIRCULARSTRING = 8;
    private static final int WKB_CURVEPOLYGON = 10;
    private static final int WKB_MULTICURVE = 11;
    private static final int WKB_MULTISURFACE = 12;
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int EWKB_TYPE_MASK = 0x1fff_ffff;

    private static final byte[] CURVE_POLYGON_WKB = HexFormat.of().parseHex(
            "010A00000001000000010800000005000000"
                    + "00000000000000000000000000000000"
                    + "00000000000010400000000000000000"
                    + "00000000000010400000000000001040"
                    + "00000000000000000000000000001040"
                    + "00000000000000000000000000000000"
    );

    private static final byte[] MULTI_CURVE_WKB = HexFormat.of().parseHex(
            "010B00000002000000"
                    + "010200000002000000"
                    + "00000000000000000000000000000000"
                    + "000000000000F03F000000000000F03F"
                    + "010800000003000000"
                    + "00000000000000400000000000000000"
                    + "0000000000000840000000000000F03F"
                    + "00000000000010400000000000000000"
    );

    private static final byte[] MULTI_SURFACE_WKB = HexFormat.of().parseHex(
            "010C00000002000000"
                    + "01030000000100000005000000"
                    + "00000000000024400000000000002440"
                    + "0000000000002C400000000000002440"
                    + "0000000000002C400000000000002C40"
                    + "00000000000024400000000000002C40"
                    + "00000000000024400000000000002440"
                    + "010A00000001000000010800000005000000"
                    + "00000000000000000000000000000000"
                    + "00000000000010400000000000000000"
                    + "00000000000010400000000000001040"
                    + "00000000000000000000000000001040"
                    + "00000000000000000000000000000000"
    );

    private GdalPackagedNativeSmoke() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly 2 args: <raster-output> <raster-input>");
        }

        Path rasterOutput = Path.of(args[0]);
        Path rasterInput = Path.of(args[1]);
        Path workDir = rasterOutput.toAbsolutePath().getParent();
        if (workDir == null) {
            throw new IllegalArgumentException("Raster output must have a parent directory: " + rasterOutput);
        }

        Gdal.rasterConvert(rasterOutput, rasterInput, "--overwrite", "--output-format", "GTiff");
        if (!Files.isRegularFile(rasterOutput)) {
            throw new IllegalStateException("Raster smoke output file is missing: " + rasterOutput);
        }

        Path vectorInput = copyBundledVectorSample(workDir.resolve("ogr-smoke-input.geojson"));
        runOgrReadSmoke(vectorInput);

        Path vectorOutput = workDir.resolve("ogr-smoke-output.gpkg");
        Gdal.vectorTranslate(vectorOutput, vectorInput, "-f", "GPKG", "-overwrite");
        if (!Files.isRegularFile(vectorOutput)) {
            throw new IllegalStateException("Vector smoke output file is missing: " + vectorOutput);
        }
        runOgrReadSmoke(vectorOutput);
        runTrueCurveRoundTripSmokes(workDir);

        if (Boolean.getBoolean("gdal.ffm.smoke.expectBundledCaBundle")) {
            assertScopedBundledCaBundle();
        }
        System.out.println("OK");
    }

    private static Path copyBundledVectorSample(Path target) {
        try (InputStream inputStream = GdalPackagedNativeSmoke.class.getResourceAsStream("/smoke/sample.geojson")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing bundled vector smoke resource: /smoke/sample.geojson");
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize bundled vector smoke resource", e);
        }
    }

    private static void runOgrReadSmoke(Path dataset) {
        try (OgrDataSource dataSource = Ogr.open(dataset)) {
            List<OgrLayerDefinition> layers = dataSource.listLayers();
            if (layers.isEmpty()) {
                throw new IllegalStateException("OGR smoke dataset has no layers: " + dataset);
            }

            OgrLayerDefinition layer = layers.getFirst();
            if (layer.fields().isEmpty()) {
                throw new IllegalStateException("OGR smoke layer has no fields: " + layer.name());
            }

            try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of())) {
                Iterator<OgrFeature> iterator = reader.iterator();
                if (!iterator.hasNext()) {
                    throw new IllegalStateException("OGR smoke reader returned no features: " + dataset);
                }

                OgrFeature feature = iterator.next();
                if (feature.attributes().isEmpty()) {
                    throw new IllegalStateException("OGR smoke feature has no attributes: " + dataset);
                }
                if (feature.geometry() == null) {
                    throw new IllegalStateException("OGR smoke feature has no geometry: " + dataset);
                }
            }
        }
    }

    private static void runTrueCurveRoundTripSmokes(Path workDir) {
        byte[] curvePolygon = roundTripGeometry(
                workDir.resolve("ogr-curve-smoke-output.gpkg"),
                "curves",
                WKB_CURVEPOLYGON,
                CURVE_POLYGON_WKB
        );
        assertGeometryType(curvePolygon, 0, WKB_CURVEPOLYGON, "CURVEPOLYGON");
        int curveRingOffset = collectionChildOffset(curvePolygon, 0, 0);
        assertGeometryType(curvePolygon, curveRingOffset, WKB_CIRCULARSTRING, "nested CIRCULARSTRING");

        byte[] multiCurve = roundTripGeometry(
                workDir.resolve("ogr-multicurve-smoke-output.gpkg"),
                "multicurves",
                WKB_MULTICURVE,
                MULTI_CURVE_WKB
        );
        assertGeometryType(multiCurve, 0, WKB_MULTICURVE, "MULTICURVE");
        int linearCurveOffset = collectionChildOffset(multiCurve, 0, 0);
        int circularCurveOffset = collectionChildOffset(multiCurve, 0, 1);
        assertGeometryType(multiCurve, linearCurveOffset, WKB_LINESTRING, "MULTICURVE LINESTRING member");
        assertGeometryType(multiCurve, circularCurveOffset, WKB_CIRCULARSTRING, "MULTICURVE CIRCULARSTRING member");

        byte[] multiSurface = roundTripGeometry(
                workDir.resolve("ogr-multisurface-smoke-output.gpkg"),
                "multisurfaces",
                WKB_MULTISURFACE,
                MULTI_SURFACE_WKB
        );
        assertGeometryType(multiSurface, 0, WKB_MULTISURFACE, "MULTISURFACE");
        int polygonOffset = collectionChildOffset(multiSurface, 0, 0);
        int curvePolygonOffset = collectionChildOffset(multiSurface, 0, 1);
        assertGeometryType(multiSurface, polygonOffset, WKB_POLYGON, "MULTISURFACE POLYGON member");
        assertGeometryType(multiSurface, curvePolygonOffset, WKB_CURVEPOLYGON, "MULTISURFACE CURVEPOLYGON member");
        int nestedRingOffset = collectionChildOffset(multiSurface, curvePolygonOffset, 0);
        assertGeometryType(multiSurface, nestedRingOffset, WKB_CIRCULARSTRING, "MULTISURFACE nested CIRCULARSTRING");
    }

    private static byte[] roundTripGeometry(Path dataset, String layerName, int geometryType, byte[] wkb) {
        try {
            Files.deleteIfExists(dataset);
            try (OgrDataSource dataSource = Ogr.create(dataset, "GPKG", OgrWriteMode.FAIL_IF_EXISTS);
                 OgrLayerWriter writer = dataSource.openWriter(
                         new OgrLayerWriteSpec(layerName, geometryType, List.of()))) {
                writer.write(new OgrFeature(1L, Map.of(), OgrGeometry.fromWkb(wkb)));
            }

            try (OgrDataSource dataSource = Ogr.open(dataset)) {
                OgrLayerDefinition layer = dataSource.listLayers().getFirst();
                if (baseType(layer.geometryType()) != geometryType) {
                    throw new IllegalStateException(
                            "Expected native OGR layer type " + geometryType + ", got " + layer.geometryType()
                    );
                }

                try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of())) {
                    Iterator<OgrFeature> iterator = reader.iterator();
                    if (!iterator.hasNext()) {
                        throw new IllegalStateException("Native curve smoke returned no feature for " + layerName);
                    }

                    OgrGeometry geometry = iterator.next().geometry();
                    if (geometry == null) {
                        throw new IllegalStateException("Native curve smoke returned null geometry for " + layerName);
                    }
                    return geometry.ewkb();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare native curve smoke dataset " + dataset, e);
        } finally {
            try {
                Files.deleteIfExists(dataset);
            } catch (IOException ignored) {
                // Best-effort cleanup for smoke output.
            }
        }
    }

    private static void assertGeometryType(byte[] wkb, int offset, int expectedType, String label) {
        int actualType = baseType(rawType(wkb, offset));
        if (actualType != expectedType) {
            throw new IllegalStateException(
                    "Native GDAL round-trip changed " + label + ": expected WKB type="
                            + expectedType + ", got " + actualType
            );
        }
    }

    private static int collectionChildOffset(byte[] wkb, int collectionOffset, int childIndex) {
        int rawType = rawType(wkb, collectionOffset);
        int type = baseType(rawType);
        if (type != 9 && type != WKB_CURVEPOLYGON && type != WKB_MULTICURVE && type != WKB_MULTISURFACE) {
            throw new IllegalStateException("WKB type " + type + " does not contain child geometries");
        }

        ByteOrder order = byteOrder(wkb, collectionOffset);
        int position = collectionOffset + geometryHeaderSize(rawType) + Integer.BYTES;
        int count = readInt(wkb, collectionOffset + geometryHeaderSize(rawType), order);
        if (childIndex < 0 || childIndex >= count) {
            throw new IllegalStateException(
                    "Child index " + childIndex + " out of range for WKB type " + type + " with " + count + " children"
            );
        }

        for (int i = 0; i < childIndex; i++) {
            position = geometryEndOffset(wkb, position);
        }
        return position;
    }

    private static int geometryEndOffset(byte[] wkb, int offset) {
        int rawType = rawType(wkb, offset);
        int type = baseType(rawType);
        ByteOrder order = byteOrder(wkb, offset);
        int position = offset + geometryHeaderSize(rawType);

        if (type == WKB_LINESTRING || type == WKB_CIRCULARSTRING) {
            int coordinateCount = readInt(wkb, position, order);
            return checkedOffset(wkb, position + Integer.BYTES + coordinateCount * 2L * Double.BYTES);
        }

        if (type == WKB_POLYGON) {
            int ringCount = readInt(wkb, position, order);
            position += Integer.BYTES;
            for (int i = 0; i < ringCount; i++) {
                int coordinateCount = readInt(wkb, position, order);
                position = checkedOffset(wkb, position + Integer.BYTES + coordinateCount * 2L * Double.BYTES);
            }
            return position;
        }

        if (type == 9 || type == WKB_CURVEPOLYGON || type == WKB_MULTICURVE || type == WKB_MULTISURFACE) {
            int childCount = readInt(wkb, position, order);
            position += Integer.BYTES;
            for (int i = 0; i < childCount; i++) {
                position = geometryEndOffset(wkb, position);
            }
            return position;
        }

        throw new IllegalStateException("Unsupported WKB type while walking curve smoke payload: " + type);
    }

    private static int rawType(byte[] wkb, int offset) {
        if (offset < 0 || offset + 5 > wkb.length) {
            throw new IllegalStateException(
                    "Invalid WKB geometry offset " + offset + " for payload length " + wkb.length
            );
        }
        return readInt(wkb, offset + 1, byteOrder(wkb, offset));
    }

    private static ByteOrder byteOrder(byte[] wkb, int offset) {
        if (offset < 0 || offset >= wkb.length) {
            throw new IllegalStateException("Invalid WKB byte-order offset " + offset);
        }
        return switch (wkb[offset] & 0xff) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalStateException(
                    "Invalid WKB byte-order marker at offset " + offset + ": " + (wkb[offset] & 0xff)
            );
        };
    }

    private static int readInt(byte[] wkb, int offset, ByteOrder order) {
        if (offset < 0 || offset + Integer.BYTES > wkb.length) {
            throw new IllegalStateException("Invalid WKB integer offset " + offset + " for payload length " + wkb.length);
        }
        return ByteBuffer.wrap(wkb, offset, Integer.BYTES).order(order).getInt();
    }

    private static int geometryHeaderSize(int rawType) {
        return 5 + ((rawType & EWKB_SRID_FLAG) != 0 ? Integer.BYTES : 0);
    }

    private static int checkedOffset(byte[] wkb, long offset) {
        if (offset < 0 || offset > wkb.length) {
            throw new IllegalStateException("WKB geometry exceeds payload length: offset=" + offset + ", length=" + wkb.length);
        }
        return (int) offset;
    }

    private static int baseType(int rawType) {
        int type = rawType & EWKB_TYPE_MASK;
        return type >= 1000 ? type % 1000 : type;
    }

    private static void assertScopedBundledCaBundle() {
        String previousCurlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
        String previousSslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);

        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(GdalConfig.empty())) {
            String curlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
            String sslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);
            if (curlCaBundle == null || curlCaBundle.isBlank()) {
                throw new IllegalStateException("Scoped CURL_CA_BUNDLE thread-local option is not set");
            }
            if (sslCertFile == null || sslCertFile.isBlank()) {
                throw new IllegalStateException("Scoped SSL_CERT_FILE thread-local option is not set");
            }
            if (!curlCaBundle.equals(sslCertFile)) {
                throw new IllegalStateException(
                        "Scoped CURL_CA_BUNDLE and SSL_CERT_FILE must resolve to the same bundle"
                );
            }

            Path caBundle = Path.of(curlCaBundle);
            if (!caBundle.isAbsolute()) {
                throw new IllegalStateException("Bundled CA path must be absolute: " + caBundle);
            }
            if (!caBundle.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath())) {
                throw new IllegalStateException("Bundled CA path must live below java.io.tmpdir: " + caBundle);
            }
            if (!Files.isRegularFile(caBundle)) {
                throw new IllegalStateException("Bundled CA file is missing: " + caBundle);
            }
            if (!caBundle.endsWith("ssl/cacert.pem")) {
                throw new IllegalStateException("Bundled CA file must end with ssl/cacert.pem: " + caBundle);
            }
        }

        String restoredCurlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
        String restoredSslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);
        if (!equalsNullable(previousCurlCaBundle, restoredCurlCaBundle)) {
            throw new IllegalStateException("Scoped CURL_CA_BUNDLE did not restore the previous thread-local value");
        }
        if (!equalsNullable(previousSslCertFile, restoredSslCertFile)) {
            throw new IllegalStateException("Scoped SSL_CERT_FILE did not restore the previous thread-local value");
        }
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
