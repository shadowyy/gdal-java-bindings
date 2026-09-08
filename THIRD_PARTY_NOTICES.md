# Third-Party Notices

This project bundles third-party native GDAL distributions in `gdal-ffm-natives` classifier artifacts.

## Source of bundled binaries

- Source type: third-party binary distributions (pinned via URL + SHA256)
- Lock file: `tools/natives/binaries.lock`
- Initial GDAL target: `3.13.3`

## License responsibilities

When producing distributable classifier artifacts, include the upstream license files for:

- GDAL
- PROJ
- GEOS
- SQLite
- other transitive native dependencies included in the bundle

Place notices and license texts under the corresponding classifier bundle tree before release.

## Current repository state

The repository currently contains:

- a concrete, SHA-pinned `conda-forge` seed lock (`tools/natives/binaries.lock`)
- bundling layout and manifests
- staging scripts for classifier-specific payload assembly

Before publishing production artifacts, verify runtime closure per classifier (all required transitive native libraries present) and include the corresponding third-party license files in the bundled payload.
