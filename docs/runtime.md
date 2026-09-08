# Runtime Notes

## Native access

FFM calls require native access for the module that performs the call.

- Classpath use:
  - `--enable-native-access=ALL-UNNAMED`
- JPMS use:
  - `--enable-native-access=ch.so.agi.gdal.ffm`

If native access is missing, startup fails early with an explicit message.

## Native classifier selection

`NativeLoader` resolves runtime classifier from:

- `os.name`
- `os.arch`

Mapped classifiers:

- `linux-x86_64`
- `linux-aarch64`
- `osx-x86_64`
- `osx-aarch64`
- `windows-x86_64`

If no matching bundle exists on classpath, initialization fails with a dependency hint.

Packaged native manifests may also carry an internal `cacheKey`. When present, `NativeLoader`
extracts the bundle under `java.io.tmpdir/gdal-ffm/<cacheKey>/<classifier>` instead of using
`bundleVersion`, which prevents stale bundle reuse across bindings releases and across standard vs
swiss vs cn native variants. Older manifests without `cacheKey` continue to fall back to `bundleVersion`.

## Runtime config options

After native extraction/loading, the runtime sets:

- `GDAL_DATA`
- `PROJ_LIB`
- `GDAL_DRIVER_PATH` (if present)

Values point to extracted bundle directories in `java.io.tmpdir`.

## Scoped GDAL/VSI config

The high-level API now distinguishes explicit runtime config from the global process environment.

- `GdalConfig` is an immutable key/value container
- `ScopedGdalConfig` applies config for one operation and restores previous thread-local values

This is the basis for row/job-scoped remote access in downstream integrations such as Apache Hop.
On Linux/macOS, `GdalConfigScope` also injects bundled `CURL_CA_BUNDLE` and `SSL_CERT_FILE`
defaults from the extracted classifier when the manifest declares `caBundlePath`.
These implicit Unix CA defaults are scoped to the operation, not left behind as global config.
If either `CURL_CA_BUNDLE` or `SSL_CERT_FILE` is already defined via environment or Java system
property, neither implicit default is applied. Explicit `GdalConfig` values still override the
implicit scoped defaults key-by-key.

Typical remote/auth mappings:

- `GDAL_HTTP_AUTH=BASIC`
- `GDAL_HTTP_USERPWD=user:password`
- `GDAL_HTTP_BEARER=<token>`
- `GDAL_HTTP_HEADERS=Header: value`

## Dataset references

Raster and OGR entrypoints accept either `Path` or `DatasetRef`.

`DatasetRef` distinguishes:

- `LOCAL_PATH`
- `HTTP_URL`
- `GDAL_VSI`

`HTTP_URL` becomes a GDAL `/vsicurl/` identifier and is therefore suitable for HTTP/HTTPS COG
access without an explicit Java-side pre-download.

## Bundled source packages

`tools/natives/binaries.lock` is split into:

- pinned roots (`platform.<classifier>.url` + `platform.<classifier>.closure_roots`)
- resolver-managed `extra_url_<n>` entries (full transitive runtime closure)

Regenerate closure deterministically:

- `tools/natives/refresh-lock-closure.sh`
- `tools/natives/refresh-lock-closure.sh --check`
- `tools/natives/refresh-lock-closure.sh --max-backtracks 5000`
- `tools/natives/refresh-lock-closure.sh --debug-resolver`
- `GDAL_FFM_VERIFY_RESOLVER=true tools/natives/verify-lock.sh` (verifies index/groups and resolver drift)

Resolver defaults:

- bounded backtracking (`--max-backtracks`, default `2000`)
- deterministic candidate order (version desc, build desc, platform-subdir before noarch)
- MRV package selection (smallest domain first)

Tooling env vars:

- `GDAL_FFM_VERIFY_RESOLVER=true`: `verify-lock.sh` additionally runs `refresh-lock-closure.sh --check`
- `JEXTRACT_BIN=/path/to/jextract`: overrides the `jextract` executable used by `tools/jextract/regenerate.sh`

For OGR streaming API regeneration, `GDAL_INCLUDE_DIR` must also contain `ogr_api.h` and `ogr_srs_api.h` in addition to the GDAL/CPL headers.

GitHub `Build Natives` / `Release` run `verify-lock.sh` by default. The resolver drift check
(`refresh-lock-closure.sh --check`) is optional via workflow input `verify-lock-closure`.

## Runtime-only bundle content

The staged classifier payload intentionally excludes CLI binaries.

- Linux/macOS: only shared libraries in `lib/` (`*.so*`, `*.dylib*`), optional `lib/gdalplugins`, plus `share/gdal` and `share/proj`
- Linux/macOS additionally keep `ssl/cacert.pem` when libcurl is bundled
- Windows: only DLL runtime in `bin/` (`*.dll`), optional `bin/gdalplugins`, plus `share/gdal` and `share/proj`

Build/dev artifacts (`cmake`, `pkgconfig`, docs, man pages, completions, headers) are stripped during staging.

## Conda relocation and placeholder handling

`tools/natives/fetch-and-stage.sh` calls `tools/natives/relocate-runtime-deps.sh` for every classifier.

- Linux: rewrites absolute conda placeholder `DT_NEEDED` entries to bundled sonames (`patchelf --replace-needed`)
- macOS: rewrites absolute non-system install names to `@rpath/<basename>`, sets dylib ids, and ensures required `LC_RPATH`
- Windows: performs strict placeholder-marker scan on staged runtime DLLs

Linux CI runners install `patchelf` before staging (`Build Natives` / `Release` workflows).

## Runtime dependency audit

Use `tools/natives/audit-runtime-deps.sh <classifier>` after staging.

- macOS: `otool -L`
- Linux: `readelf -d` (fallback `objdump -p`)
- Windows: `dumpbin /DEPENDENTS` (fallback `llvm-objdump -p`)

The audit fails fast with missing soname/dll hints mapped to the requiring binary and treats
absolute non-relocatable linkage as an error (`absolute DT_NEEDED dependency` / `non-relocatable install name`).

## Packaged runtime smoke

GitHub Actions runs packaged runtime smokes in both `Build Natives` and `Release`:

- for every supported classifier, including `windows-x86_64`
- for all bundle variants (`gdal-ffm-natives`, `gdal-ffm-natives-swiss` and `gdal-ffm-natives-cn`)
- with per-run isolation via `-Djava.io.tmpdir=.../build/tmp/smoke/<label>` to avoid cache carry-over
- with a second run against the same shared temp directory to catch stale extraction/cache reuse issues
- packaged smoke exercises both raster conversion and OGR open/read/vector-translate against bundled test data
- packaged Unix smoke also asserts extracted `ssl/cacert.pem` exists and that `GdalConfigScope`
  sets both `CURL_CA_BUNDLE` and `SSL_CERT_FILE` thread-locally during an operation

Local equivalent:

```bash
./gradlew :gdal-ffm-core:smokeTestPackagedNative \
  -PgdalFfmSmokeNativeJar=gdal-ffm-natives/build/libs/gdal-ffm-natives-<version>-natives-linux-x86_64.jar \
  -PgdalFfmSmokeLabel=linux-x86_64-standard
```

Required properties:

- `gdalFfmSmokeNativeJar`: path to exactly one native classifier JAR
- `gdalFfmSmokeLabel`: label used for isolated output and temp directories

## Troubleshooting

1. `Native access is not enabled`
   - Add `--enable-native-access=...` JVM arg.
2. `Required GDAL symbol not found`
   - Bundle content and expected GDAL version are out of sync.
3. `No bundled GDAL native resources found for classifier ...`
   - Missing `runtimeOnly` dependency for the matching `natives-<classifier>` artifact.
4. `SHA256 mismatch` while staging natives
   - Verify package URL and digest in `tools/natives/binaries.lock`.
5. `UnsatisfiedLinkError` / `Library not loaded ...`
   - Run `tools/natives/refresh-lock-closure.sh`, restage classifier, clear `$TMPDIR/gdal-ffm/<gdal-version>/<classifier>`, rerun smoke.
6. `Resolver conflict for '<classifier>'`
   - Means no candidate satisfied all aggregated constraints in current search path.
   - Re-run with `--debug-resolver` to see decision/backtrack trace and exact constraint origins.
7. `Resolver backtrack limit reached`
   - Re-run with higher `--max-backtracks` (for example `5000`) and keep `--debug-resolver` enabled for analysis.
