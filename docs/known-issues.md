# Known Issues / 已知问题

Analysis of the `gdal-java-bindings` project state (generated 2026-09-10). Findings are
evidence-based and verified against `binaries.lock`, the Gradle build scripts, and the
staged native bundles under `gdal-ffm-natives/src/main/resources/META-INF/gdal-native/`.

## 🔴 Critical: `osx-aarch64` is missing `libpq`, PostgreSQL driver unusable there

The commit `4d36f8f` ("Add DatasetRef-based vectorTranslate with PostgreSQL driver support")
added `ogr_PG` driver support, but the staged bundle dependency closure is inconsistent
across platforms:

| Platform        | `libpq` files |
|-----------------|---------------|
| linux-x86_64    | 4 ✅          |
| linux-aarch64   | 4 ✅          |
| osx-x86_64      | 3 ✅          |
| **osx-aarch64** | **0 ❌**      |
| windows-x86_64  | 2 ✅          |

The `ogr_PG.so` plugin links against `libpq` at runtime. Without it, any PG-target operation
fails to load the driver on macOS arm64. `binaries.lock:1036` does pin `libpq` for
`osx-aarch64`, so the library is dropped during staging/relocation (most likely the macOS
branch of `relocate-runtime-deps.sh` only preserves `lib/gdalplugins/*.dylib` and drops the
other `lib/*.dylib` files).

**Fix:** ensure `libpq` survives staging on osx-aarch64; add a `libpq`-must-exist assertion to
`audit-runtime-deps.sh` / `verifyNativeBundleLayout`.

## 🟠 Major: PostgreSQL server extensions are bundled (pure dead weight)

`binaries.lock` pins **both** `libpq` and the full `postgresql` conda package on every
platform (e.g. `binaries.lock:268` and `:575`). GDAL's `ogr_PG` driver only needs the `libpq`
client library and never needs PostgreSQL server extensions. `fetch-and-stage.sh` copies the
whole `lib/` of each conda package, and `relocate-runtime-deps.sh` relocates every
`lib/*.so`, with only a single hardcoded removal of `lib/uuid-ossp.so` (acknowledged in a
script comment as a one-off patch) — there is no deny-list for PG server extensions.

Only `libgdal` + `libproj` are preloaded by `NativeLoader`; the PG server `.so` files are
never loaded, so they are dead weight that inflates every classifier jar:

| Platform        | Total `.so`/`.dll` | Suspected PG server extensions |
|-----------------|--------------------|--------------------------------|
| linux-aarch64   | 463                | ~48 (bundle is **1.3 GB**)     |
| linux-x86_64    | 466                | ~48                            |
| osx-x86_64      | 396                | ~49                            |
| windows-x86_64  | 225                | `plpgsql.dll`, `libpqwalreceiver.dll`, `postgres_fdw.dll`, ... |
| osx-aarch64     | —                  | 0 (mac ships them under `lib/postgresql/`, so the glob misses them) |

The 48-vs-0 spread proves the filtering is accidental, not intentional.
Examples of bundled-but-unused server extensions: `amcheck`, `auth_delay`, `autoinc`,
`auto_explain`, `hstore`, `dblink`, `pgcrypto`, `pg_stat_statements`, `plpgsql`,
`postgres_fdw`, ...

**Fix:** remove `postgresql` from `binaries.lock` (keep `libpq` only), or add an explicit
PG server-extension deny-list in the staging/relocation scripts and assert it in
`verifyNativeBundleLayout` (`build.gradle.kts`).

## 🟡 Moderate: no library-content consistency check for native bundles

Existing tooling (`tools/natives/verify-lock.sh` for lock integrity,
`audit-runtime-deps.sh` for link closure) does not assert *which* files live in `lib/`.
That is exactly why the two issues above slip past CI. The `verifyNativeBundleLayout` task in
`gdal-ffm-natives/build.gradle.kts` only checks `manifest.json` presence and the `caBundlePath`
metadata.

**Fix:** add a cross-platform library manifest/allowlist assertion (require `libpq`, forbid
PG server extensions) so the contents are identical in shape across classifiers.

## 🟢 Minor: `NativeLoader` dead code and redundancy

- `NativeLoader.extractFromFileTree` (`gdal-ffm-core/.../internal/NativeLoader.java:352`) is
  effectively unreachable: `resolveBundleRoot` (`:230`) returns the file-tree root directly for
  the `file` protocol and never calls `extractBundle`, so the `file`-protocol branch of
  `extractBundle` is never exercised.
- `resolveBundleRoot` (`:240`) calls `Files.createDirectories(extractionRoot)` again even
  though `extractBundle` (`:284`) already created it. Harmless but redundant.

## 🟢 Minor: `GdalConfigScope` scope boundary

`GdalConfigScope` (`gdal-ffm-core/.../internal/GdalConfigScope.java:90-112`) uses thread-local
GDAL config options and correctly restores previous values on close, including nested calls.
However, GDAL progress callbacks run on an internal GDAL worker thread, which will not see the
thread-local config set on the calling thread. Most use cases are unaffected; only config that
must be visible inside the progress callback is impacted.

## What is solid

- One-time lazy native load with caching, `manifest.json` multi-instance detection,
  `safeResolve` path-traversal guard, scoped config auto-restore, `jar`/`file` dual-protocol
  support, bundled CA bundle injection, content-hashed `cacheKey`, Swiss/China PROJ subset
  trimming, legacy `libntlm` `.tar.bz2` compatibility, and Windows DLL path support are all
  implemented carefully.

## Priority order

1. Ship `libpq` on osx-aarch64 (functional breakage).
2. Stop bundling PostgreSQL server extensions (size + consistency).
3. Add a library-content consistency check to CI.


要点回顾：
- 🔴 严重 — osx-aarch64 缺 libpq，PG 驱动在该平台 broken
- 🟠 较重 — PG 服务端扩展被错误打包（linux/windows/osx-x86_64 各 ~48 个死重 .so/.dll）
- 🟡 中等 — 缺 native bundle 库内容一致性校验
- 🟢 轻微 — NativeLoader 死代码/冗余、GdalConfigScope 工作线程可见性边界
