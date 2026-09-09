package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GdalIntegrationTest {
    @Test
    void listWritableRasterDriversIncludesGtiff() {
        List<RasterDriverInfo> drivers = Gdal.listWritableRasterDrivers();

        assertTrue(drivers.stream().anyMatch(driver -> "GTiff".equalsIgnoreCase(driver.shortName())));
    }

    @Test
    void listCompressionOptionsForGtiffReturnsValues() {
        List<String> compressionOptions = Gdal.listCompressionOptions("GTiff");

        assertTrue(compressionOptions.stream().anyMatch(value -> "NONE".equalsIgnoreCase(value)));
    }

    @Test
    void vectorTranslateGeoJsonToGpkg() {
        Path input = testData("sample.geojson");
        Path output = outputFile("vector-translate.gpkg");

        Gdal.vectorTranslate(output, input, "-f", "GPKG", "-overwrite");

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    @Test
    void rasterConvertProducesCog() {
        Path input = testData("sample.tif");
        Path output = outputFile("translate-cog.tif");

        Gdal.rasterConvert(
                output,
                input,
                "--overwrite",
                "--output-format",
                "COG",
                "--creation-option",
                "COMPRESS=ZSTD"
        );

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    @Test
    void rasterReprojectChangesCrs() {
        Path input = testData("sample.tif");
        Path output = outputFile("warp-reprojected.tif");

        Gdal.rasterReproject(
                output,
                input,
                "--overwrite",
                "--dst-crs",
                "EPSG:2056",
                "--resampling",
                "bilinear"
        );

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    @Test
    void infoReturnsJsonForBundledRaster() throws Exception {
        Path input = bundledRaster();

        String json = Gdal.rasterInfo(input, "--output-format", "json");

        assertTrue(json.contains("\"driverShortName\""), "Expected GDAL info JSON");
        assertTrue(json.contains("\"size\""), "Expected raster size block in info JSON");
    }

    @Test
    void vectorInfoReturnsJsonForVectorDataset() {
        Path input = testData("sample.geojson");

        String json = Gdal.vectorInfo(input, "--output-format", "json");

        assertTrue(json.contains("\"layers\""), "Expected vector info JSON with layers");
    }

    @Test
    void rasterMosaicCreatesVirtualMosaic() throws Exception {
        Path input = bundledRaster();
        Path output = outputFile("buildvrt.vrt");

        Gdal.rasterMosaic(output, List.of(input, input), "--overwrite", "--output-format", "VRT");

        assertTrue(output.toFile().isFile(), "Expected VRT output file to exist: " + output);
        assertTrue(Gdal.rasterInfo(output, "--output-format", "json").contains("\"driverShortName\":\"VRT\""));
    }

    @Test
    void rasterizeBurnsGeoJsonIntoRaster() throws Exception {
        Path vectorInput = outputFile("rasterize-input.geojson");
        Path rasterOutput = outputFile("rasterize-output.tif");
        Files.writeString(vectorInput, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"class_id": 7},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[2,2],[2,8],[8,8],[8,2],[2,2]]]
                      }
                    }
                  ]
                }
                """);

        Gdal.vectorRasterize(
                rasterOutput,
                vectorInput,
                "--overwrite",
                "--input-layer", "rasterize-input",
                "--attribute-name", "class_id",
                "--extent", "0,0,10,10",
                "--resolution", "1,1",
                "--output-data-type", "Byte",
                "--nodata", "0",
                "--output-format", "GTiff"
        );

        assertTrue(rasterOutput.toFile().isFile(), "Expected rasterized output file to exist: " + rasterOutput);
        assertTrue(Gdal.rasterInfo(rasterOutput, "--output-format", "json").contains("\"driverShortName\":\"GTiff\""));
    }

    @Test
    void rasterZonalStatsCreatesVectorOutputWithMeanValues() throws Exception {
        Path rasterInput = outputFile("zonal-grid.asc");
        Path zonesInput = outputFile("zonal-zones.geojson");
        Path output = outputFile("zonal-stats.gpkg");

        writeAsciiGrid(rasterInput);
        writeZonesGeoJson(zonesInput);

        Gdal.rasterZonalStats(
                output,
                rasterInput,
                zonesInput,
                "--overwrite",
                "--output-format",
                "GPKG",
                "--stat",
                "mean",
                "--include-field",
                "zone_id",
                "--include-field",
                "name"
        );

        assertTrue(output.toFile().isFile(), "Expected zonal stats output file to exist: " + output);

        try (OgrDataSource dataSource = Ogr.open(output)) {
            OgrLayerDefinition layer = dataSource.listLayers().getFirst();
            assertTrue(layer.fields().stream().anyMatch(field -> "zone_id".equals(field.name())));
            assertTrue(layer.fields().stream().anyMatch(field -> "name".equals(field.name())));
            assertTrue(layer.fields().stream().anyMatch(field -> "mean".equals(field.name())));

            try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of())) {
                List<OgrFeature> features = collect(reader);
                assertEquals(2, features.size());
                assertEquals(7.5d, ((Number) featureByZoneId(features, 1L).attributes().get("mean")).doubleValue(), 1e-9);
                assertEquals(9.5d, ((Number) featureByZoneId(features, 2L).attributes().get("mean")).doubleValue(), 1e-9);
            }
        }
    }

    private static Path testData(String fileName) {
        String testDataRoot = System.getenv("GDAL_FFM_TESTDATA_DIR");
        Assumptions.assumeTrue(testDataRoot != null && !testDataRoot.isBlank(), "GDAL_FFM_TESTDATA_DIR is not set");

        Path path = Path.of(testDataRoot, fileName);
        Assumptions.assumeTrue(path.toFile().isFile(), "Missing integration test file: " + path);
        return path;
    }

    private static Path outputFile(String fileName) {
        String outputRoot = System.getenv("GDAL_FFM_TEST_OUTPUT_DIR");
        Path directory = (outputRoot == null || outputRoot.isBlank())
                ? Path.of("build", "integration-test-output")
                : Path.of(outputRoot);
        directory.toFile().mkdirs();
        return directory.resolve(fileName);
    }

    private static Path bundledRaster() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                GdalIntegrationTest.class.getResource("/smoke/reclass.tif"),
                "Missing bundled raster smoke resource"
        ).toURI());
    }

    private static void writeAsciiGrid(Path path) throws Exception {
        Files.writeString(path, """
                ncols 4
                nrows 4
                xllcorner 0
                yllcorner 0
                cellsize 1
                NODATA_value -9999
                1 2 3 4
                5 6 7 8
                9 10 11 12
                13 14 15 16
                """);
    }

    private static void writeZonesGeoJson(Path path) throws Exception {
        Files.writeString(path, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"zone_id": 1, "name": "left"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[0,0],[0,4],[2,4],[2,0],[0,0]]]
                      }
                    },
                    {
                      "type": "Feature",
                      "properties": {"zone_id": 2, "name": "right"},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[2,0],[2,4],[4,4],[4,0],[2,0]]]
                      }
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }

    private static List<OgrFeature> collect(OgrLayerReader reader) {
        List<OgrFeature> features = new ArrayList<>();
        for (OgrFeature feature : reader) {
            features.add(feature);
        }
        return features;
    }

    private static OgrFeature featureByZoneId(List<OgrFeature> features, long zoneId) {
        return features.stream()
                .filter(feature -> ((Number) feature.attributes().get("zone_id")).longValue() == zoneId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing feature for zone_id=" + zoneId));
    }
}
