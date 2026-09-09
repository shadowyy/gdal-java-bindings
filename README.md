# gdal-java-bindings

Java 25 FFM bindings for GDAL/OGR utilities and vector streaming with bundled native libraries.
The public API now covers the raster operations required by the Hop raster suite as well:
`rasterInfo`, `rasterConvert`, `rasterClip`, `rasterReproject`, `rasterResize`, `rasterMosaic`,
`rasterZonalStats`, `vectorRasterize`, structured dataset references and scoped
GDAL/VSI configuration.

## Modules

- `gdal-ffm-core`: public Java API (`Gdal.rasterInfo`, `Gdal.rasterConvert`, `Gdal.rasterClip`, `Gdal.rasterReproject`, `Gdal.rasterResize`, `Gdal.rasterMosaic`, `Gdal.rasterZonalStats`, `Gdal.vectorRasterize`, `Gdal.vectorTranslate`, `Ogr.*`), native loader, error/progress bridge.
- `gdal-ffm-natives`: classifier JARs that package native GDAL libs + `share/gdal` + `share/proj` (+ bundled Unix CA bundle for libcurl-based HTTPS).
- `gdal-ffm-natives-swiss`: optional classifier JARs with the same native libs, but a Swiss-focused `share/proj` subset.
- `gdal-ffm-natives-cn`: optional classifier JARs with the same native libs, but a China-focused `share/proj` subset.

## Requirements

- JDK 25+

- Native access enabled at runtime:
  - Classpath: `--enable-native-access=ALL-UNNAMED`
  - JPMS: `--enable-native-access=ch.so.agi.gdal.ffm`

## Dependency setup

```kotlin
dependencies {
    implementation("ch.so.agi:gdal-ffm-core:<version>")

    // Standard PROJ data bundle:
    runtimeOnly("ch.so.agi:gdal-ffm-natives:<version>:natives-linux-x86_64")

    // OR Swiss-focused PROJ data subset:
    // runtimeOnly("ch.so.agi:gdal-ffm-natives-swiss:<version>:natives-linux-x86_64")

    // OR China-focused PROJ data subset:
    // runtimeOnly("ch.so.agi:gdal-ffm-natives-cn:<version>:natives-linux-x86_64")
}
```

Available classifiers:

- `natives-linux-x86_64`
- `natives-linux-aarch64`
- `natives-osx-x86_64`
- `natives-osx-aarch64`
- `natives-windows-x86_64`

Important: include exactly one runtime native artifact line per classifier (either `gdal-ffm-natives`, `gdal-ffm-natives-swiss` or `gdal-ffm-natives-cn`, not more than one), otherwise the native loader aborts due to ambiguous manifests.

For plugin-based hosts (for example Apache Hop plugin folders with isolated classloaders), prefer a shared runtime layout:
- keep all transforms using `gdal-ffm-core` in one plugin folder/classloader
- keep native/runtime jars (`gdal-ffm-core`, `gdal-ffm-natives:*`) only once in that shared `lib/`

This avoids duplicated multi-hundred-MB payloads and classloader-native edge cases.

Swiss `share/proj` subset keeps:

- `proj.db`
- `CHENyx06a.gsb` or `ch_swisstopo_CHENyx06a.tif`
- `CHENyx06_ETRS.gsb` or `ch_swisstopo_CHENyx06_ETRS.tif`
- `egm96_15.gtx` or `us_nga_egm96_15.tif`

China (`gdal-ffm-natives-cn`) `share/proj` subset keeps:

- `proj.db` (covers CGCS2000 / Beijing 1954 / Xi'an 1980 horizontal transforms via parametric definitions; no public Chinese horizontal grid exists in PROJ-data)
- `egm96_15.gtx` or `us_nga_egm96_15.tif`
- `egm08_25.gtx` or `us_nga_egm08_25.tif`

## Usage

```java
Gdal.vectorTranslate(
    Path.of("output.gpkg"),
    Path.of("input.geojson"),
    "-f", "GPKG",
    "-overwrite"
);

// Vector translate from a zipped shapefile to PostgreSQL with scoped config options.
// `--config` equivalents (PG_USE_COPY, SHAPE_ENCODING) go into GdalConfig, not args.
GdalConfig pgConfig = GdalConfig.empty()
    .withConfigOption("PG_USE_COPY", "YES")
    .withConfigOption("SHAPE_ENCODING", "UTF-8");

Gdal.vectorTranslate(
    DatasetRef.raw("PG:\"host=localhost port=5432 dbname=gdal user=postgres password=***\""),
    DatasetRef.gdalVsi("/vsizip/D:/data/_矢量_三亚市_三亚市河流管理范围线3857.shp.zip/内层.shp"),
    pgConfig,
    "-f", "PostgreSQL",
    "-nln", "shp_test",
    "-overwrite",
    "-lco", "GEOMETRY_NAME=geom",
    "-lco", "FID=gid",
    "-lco", "SPATIAL_INDEX=GIST"
);

String infoJson = Gdal.rasterInfo(Path.of("input.tif"), "--output-format", "json");

String vectorInfoJson = Gdal.vectorInfo(Path.of("input.geojson"), "--output-format", "json");

Gdal.rasterConvert(
    Path.of("output-cog.tif"),
    Path.of("input.tif"),
    "--output-format", "COG",
    "--creation-option", "COMPRESS=ZSTD"
);

Gdal.rasterReproject(
    Path.of("warped.tif"),
    Path.of("input.tif"),
    "--dst-crs", "EPSG:2056",
    "--resampling", "bilinear"
);

Gdal.rasterMosaic(
    Path.of("mosaic.vrt"),
    List.of(Path.of("a.tif"), Path.of("b.tif")),
    "--output-format", "VRT"
);

Gdal.rasterZonalStats(
    Path.of("zones-stats.gpkg"),
    Path.of("ndom.tif"),
    Path.of("buildings.gpkg"),
    "--output-format", "GPKG",
    "--zones-layer", "buildings",
    "--stat", "mean"
);

Gdal.vectorRasterize(
    Path.of("out.tif"),
    Path.of("features.geojson"),
    "--burn", "1",
    "--extent", "0,0,100,100",
    "--resolution", "1,1"
);
```

## Dataset references and scoped config

Raster and OGR entrypoints can use either `Path` or explicit `DatasetRef`.

```java
DatasetRef remoteCog = DatasetRef.httpUrl("https://example.org/cog.tif");
GdalConfig config = GdalConfig.empty()
    .withConfigOption("GDAL_HTTP_BEARER", "token");

String json = Gdal.rasterInfo(remoteCog, config, "--output-format", "json");
```

Supported `DatasetRef` types:

- `LOCAL_PATH`
- `HTTP_URL`
- `GDAL_VSI`

`HTTP_URL` inputs are translated into GDAL `/vsicurl/` identifiers. Config values are applied per
operation through `ScopedGdalConfig`, so auth/config does not remain globally active after the call.
On Linux/macOS `GdalConfigScope` also injects bundled `CURL_CA_BUNDLE` and `SSL_CERT_FILE` defaults
from the native classifier when neither environment variables nor system properties already define
either key. Those CA defaults are operation-scoped and explicit `GdalConfig` values still override
them key-by-key.

Progress callback:

```java
Gdal.rasterConvert(
    Path.of("out.tif"),
    Path.of("in.tif"),
    (complete, message) -> {
        System.out.printf("%.1f%% %s%n", complete * 100.0, message);
        return true;
    },
    "--output-format", "COG"
);
```


## OGR streaming API

`gdal-ffm-core` exposes a neutral vector streaming surface intended for integrations like Apache Hop plugins:

- `Ogr.open(Path, Map<String,String>)`
- `Ogr.open(DatasetRef, Map<String,String>, GdalConfig)`
- `Ogr.create(Path, driverShortName, writeMode, datasetCreationOptions)`
- `Ogr.create(DatasetRef, driverShortName, writeMode, datasetCreationOptions, GdalConfig)`
- `Ogr.listWritableVectorDrivers()`
- `OgrDataSource` / `OgrLayerReader` / `OgrLayerWriter`
- `OgrFeature`
- `OgrGeometry`
- `OgrLayerDefinition` / `OgrFieldDefinition` / `OgrFieldType`
- `OgrLayerWriteSpec` / `OgrWriteMode` / `OgrDriverInfo`

Geometry transport is neutral and uses EWKB-compatible payloads with optional SRID support.
Use `OgrGeometry.fromWkb(wkb, srid)` when the SRID must be embedded directly in the binary payload.

Example:

```java
try (OgrDataSource dataSource = Ogr.open(Path.of("input.geojson"), Map.of())) {
    OgrLayerDefinition layer = dataSource.listLayers().getFirst();
    try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of(
            OgrReaderOptions.ATTRIBUTE_FILTER, "value >= 10",
            OgrReaderOptions.BBOX, "2600000,1200000,2700000,1300000",
            OgrReaderOptions.SELECTED_FIELDS, "id,name",
            OgrReaderOptions.LIMIT, "1000"
    ))) {
        for (OgrFeature feature : reader) {
            // stream rows
        }
    }
}

try (OgrDataSource dataSource = Ogr.create(
        Path.of("output.gpkg"),
        "GPKG",
        OgrWriteMode.OVERWRITE,
        Map.of("SPATIAL_INDEX", "YES"))) {
    OgrLayerWriteSpec spec = new OgrLayerWriteSpec(
            "features",
            1, // POINT
            List.of(
                    new OgrFieldDefinition("id", OgrFieldType.INTEGER64),
                    new OgrFieldDefinition("name", OgrFieldType.STRING)
            )
    ).withWriteMode(OgrWriteMode.OVERWRITE);

    try (OgrLayerWriter writer = dataSource.openWriter(spec)) {
        writer.write(new OgrFeature(
                -1,
                Map.of("id", 1L, "name", "A"),
                OgrGeometry.fromWkb(new byte[] { /* WKB */ })
        ));
    }
}
```

`DatasetRef.gdalVsi("/vsimem/example.geojson")` is supported for temporary in-memory datasets,
which is used by the Hop rasterization path for row-based geometries.

## Regenerating low-level bindings

Low-level bindings are checked into `gdal-ffm-core/src/generated/java`.

```bash
GDAL_INCLUDE_DIR=/path/to/gdal/includes tools/jextract/regenerate.sh
```

If `jextract` is not on `PATH`, override the binary explicitly:

```bash
GDAL_INCLUDE_DIR=/path/to/gdal/includes \
JEXTRACT_BIN=/path/to/jextract \
tools/jextract/regenerate.sh
```

## Native bundle staging

Bundles are staged as runtime-only payloads:

- shared libraries (`.so*`, `.dylib*`, `.dll`)
- optional `gdalplugins`
- `share/gdal` and `share/proj`

CLI binaries (`ogr2ogr`, `gdal_translate`, etc.) are intentionally not shipped in classifier JARs.

1. Review `tools/natives/binaries.lock` (roots + resolver-managed `extra_*` entries).
2. Validate lock integrity:

```bash
tools/natives/verify-lock.sh
```

Optional strict mode (also runs resolver drift check):

```bash
GDAL_FFM_VERIFY_RESOLVER=true tools/natives/verify-lock.sh
```

3. Refresh and validate transitive dependency closure (deterministic):

```bash
tools/natives/refresh-lock-closure.sh --check
```

Resolver debug (bei Constraint-Konflikten):

```bash
tools/natives/refresh-lock-closure.sh --max-backtracks 5000 --debug-resolver
```

4. Ensure `cph` is installed (`conda-package-handling` CLI):

```bash
python -m pip install --user conda-package-handling
```

Linux only: ensure `patchelf` is available (required for ELF relocation of conda placeholder paths).

5. Stage one classifier:

```bash
tools/natives/fetch-and-stage.sh linux-x86_64
```

The staging step runs relocation/sanitization automatically via `tools/natives/relocate-runtime-deps.sh`:

- Linux: rewrites absolute `DT_NEEDED` placeholder paths to bundled sonames using `patchelf`
- macOS: rewrites absolute install names to `@rpath/*` and ensures required `LC_RPATH` entries
- Windows: fails fast if placeholder markers are detected in runtime DLLs

6. Audit runtime link closure for that classifier:

```bash
tools/natives/audit-runtime-deps.sh linux-x86_64
```

7. Build native classifier jars (standard + swiss + cn by default):

```bash
./gradlew :gdal-ffm-natives:assemble
```

Disable Swiss jar creation/publication when needed:

```bash
./gradlew :gdal-ffm-natives:assemble -PgdalSwissNativesEnabled=false
```

Disable China-focused jar creation/publication when needed:

```bash
./gradlew :gdal-ffm-natives:assemble -PgdalCnNativesEnabled=false
```

## CI runtime smoke coverage

- `Build Natives` and `Release` run packaged runtime smoke tests for every supported classifier, including `windows-x86_64`.
- All variants are validated per classifier:
  - standard (`gdal-ffm-natives`)
  - swiss (`gdal-ffm-natives-swiss`)
  - china (`gdal-ffm-natives-cn`)
- Each smoke run uses a dedicated label and isolated temp dir (`build/tmp/smoke/<label>`), and packaged smokes repeat once against the same temp dir to catch cache carry-over and stale extraction problems.
- Packaged smokes cover both raster conversion and OGR open/read/vector translate against bundled smoke data.
- Resolver drift check (`tools/natives/refresh-lock-closure.sh --check`) is available as optional workflow input `verify-lock-closure` and is disabled by default.
- Linux runners install `patchelf` before staging natives.

## Developing / Smoke tests

Use this flow when iterating on native bundles and validating that the Java API can load GDAL end-to-end.

1. Refresh lock closure and stage natives for your platform (example: macOS arm64):

```bash
./tools/natives/refresh-lock-closure.sh
./tools/natives/fetch-and-stage.sh osx-aarch64
```

2. Audit staged runtime deps:

```bash
./tools/natives/audit-runtime-deps.sh osx-aarch64
```

3. Rebuild the core JAR and platform-native JAR:

```bash
./gradlew :gdal-ffm-core:jar :gdal-ffm-natives:nativesJarOsxAarch64
```

Swiss variant for the same classifier:

```bash
./gradlew :gdal-ffm-core:jar :gdal-ffm-natives:nativesSwissJarOsxAarch64
```

China-focused variant for the same classifier:

```bash
./gradlew :gdal-ffm-core:jar :gdal-ffm-natives:nativesCnJarOsxAarch64
```

4. Clear extracted native cache for the exact GDAL/platform tuple:

```bash
rm -rf "$TMPDIR/gdal-ffm/3.13.3/osx-aarch64"
```

Why: `NativeLoader` extracts once into `java.io.tmpdir`. If you do not clear this folder, updated native jars may not be re-extracted.

5. Run the dedicated smoke test task:

```bash
./gradlew :gdal-ffm-core:smokeTest
```

The task:

- uses input file `gdal-ffm-core/src/integrationTest/resources/smoke/reclass.tif`
- writes output to `gdal-ffm-core/build/smoke-test-output/reclass-smoke.tif`
- requires staged natives for your current classifier under `gdal-ffm-natives/src/main/resources/META-INF/gdal-native/<classifier>`

Expected result:

- process exits with code `0`
- output file is created

6. Run packaged-native smoke against assembled classifier JARs:

```bash
./gradlew :gdal-ffm-core:smokeTestPackagedNative \
  -PgdalFfmSmokeNativeJar=gdal-ffm-natives/build/libs/gdal-ffm-natives-<version>-natives-osx-aarch64.jar \
  -PgdalFfmSmokeLabel=osx-aarch64-standard

./gradlew :gdal-ffm-core:smokeTestPackagedNative \
  -PgdalFfmSmokeNativeJar=gdal-ffm-natives/build/libs/gdal-ffm-natives-swiss-<version>-natives-osx-aarch64.jar \
  -PgdalFfmSmokeLabel=osx-aarch64-swiss

./gradlew :gdal-ffm-core:smokeTestPackagedNative \
  -PgdalFfmSmokeNativeJar=gdal-ffm-natives/build/libs/gdal-ffm-natives-cn-<version>-natives-osx-aarch64.jar \
  -PgdalFfmSmokeLabel=osx-aarch64-cn
```

Packaged smoke output is written to:

- `gdal-ffm-core/build/smoke-test-output/<label>-reclass-smoke.tif`

If you get `UnsatisfiedLinkError` (`Library not loaded`), run:

```bash
./tools/natives/refresh-lock-closure.sh
```

Then repeat the flow. The resolver rewrites `extra_*` entries from pinned roots and avoids manual per-lib edits.

If resolver conflicts appear, rerun with trace:

```bash
./tools/natives/refresh-lock-closure.sh --debug-resolver
```

If you hit a backtrack cap, increase it explicitly:

```bash
./tools/natives/refresh-lock-closure.sh --max-backtracks 5000
```

See `docs/runtime.md` for startup flags and troubleshooting.
