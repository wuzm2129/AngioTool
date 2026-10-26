# AngioTool 2.3.1

Version 2.3.1 adds complete CLI coverage for all 53 analysis/display parameters,
a strict schema-1 configuration format and `config defaults/validate/resolve`.
See the [complete parameter reference](CLI_PARAMETERS_CN.md). Legacy overlay-data
snapshots and 2.3.0 shorthand options remain supported.

Version 2.3.0 adds a headless command-line interface while preserving the desktop
entry when started without arguments. Analysis algorithms and metric definitions
are inherited from 2.2.3. The CLI uses explicit defaults/JSON parameters rather
than desktop preferences, runs images sequentially, and exports PNG artifacts,
JSON, a fresh XLSX workbook and a machine-readable run report.

```bash
java -jar AngioTool2.3.1.jar --help
java -jar AngioTool2.3.1.jar analyze --input image.png --output output/
java -jar AngioTool2.3.1.jar analyze --input images/ --recursive --output output/
```

See [CLI usage, parameters and failure semantics](CLI_CN.md) and
[Current validation](VALIDATION_CN.md). Build with `python3 compile.py`;
run CLI integration checks with
`python3 benchmarks/run_cli_tests.py --jar AngioTool2.3.1.jar`.
The older release notes below describe the inherited desktop features.

## AngioTool 2.2.3 release history

Version 2.2.3 removes the Reset defaults confirmation and the successful
parameter-import information dialog. Load parameters... reports success in the
status bar; its tooltip shows the source version. Invalid files still show an
error dialog. Reset defaults restores the controls immediately and follows the
current Auto update setting.

The workspace uses a 350-logical-pixel parameter panel and a
350-pixel measurement panel. It is an independent third column aligned
with image tabs, using the full workspace height. Switching tabs shows that image's
measurements; the Measurements checkbox hides the column. All six groups keep full
labels and headings, with E/F lacunarity side by side. A unified neutral gray and white with restrained blue-gray accents
palette also covers selections and checkboxes. The development previews' offscreen layouts fit all controls (including
expanded cleanup) and metrics at a 1480x900 content area with normal scaling and
2559x1495 with 150% scaling. Narrow panels retain a scrolling fallback. See the
[UI layout record](UI_MODERNIZATION_CN.md) for dimensions and verification limits.

## Parameter snapshot import (2.2.3, 2026-10-06)

`Load parameters...` restores analysis settings, color preprocessing and overlay
styles from any AngioTool `overlay-data.json` file. Import fills in the controls
without updating open images or changing the user's `Auto update` selection.
`Apply parameters` applies the controls to all open images. With automatic updates
off, parameter edits wait for this button; switching automatic updates on applies
the current controls. Opening a new image and starting Batch still use the current
controls immediately.

Older snapshots use defaults for missing fields without an information dialog;
the status tooltip shows the source version. Missing pruning/merging switches
remain off. Invalid JSON leaves the controls unchanged;
normalized-data JSON has no complete parameter snapshot. Existing results keep
their actual analysis parameters until updated, including in saved JSON/XLSX.

Build and run the release with:

    python compile.py
    java -jar AngioTool2.2.3.jar

Release validation covers the headless regression suite, parameter import and
GUI smoke tests at normal and 150% scaling. See [validation](VALIDATION_CN.md) for
reproducible commands, results and remaining limits.

## Local 2.2.2 Update (2026-09-16)

AngioTool 2.2.3 inherits the 2.2.2 fixes in this local extension of the upstream 2.0
batch edition. It keeps the original Tubeness analysis path and adds:

- corrected junction counting at raster corners: a one-pixel connection inside
  the same junction region no longer counts as an external branch;
- Excel-compatible XLSX metadata timestamps that no longer trigger workbook
  repair when Batch results are opened in Microsoft Excel;
- a single-window workspace with image tabs and Original, Final mask and
  Skeleton overlay previews;
- direct binary-mask input with independent optional hole filling and particle
  removal settings;
- convergent skeletonization with optional terminal-branch pruning and nearby
  junction merging;
- box-counting fractal dimension for the final vessel mask; and
- optional image-relative normalized JSON/XLSX output.

Binary masks bypass Tubeness and resizing; cleanup is off by default and is
saved separately from normal-mode settings. Invalid or unavailable derived
measurements are reported explicitly in the exports rather than being silently
replaced with zero.

- [Chinese user guide](USER_GUIDE_CN.md): workflows, controls and parameter meanings.
- [Program report](PROGRAM_REPORT_CN.md): architecture and analysis pipelines.
- [Export format](OVERLAY_EXPORT_FORMAT_CN.md): mask, JSON and input-mode metadata.
- [Fractal box-counting guide](FRACTAL_BOX_COUNT_CN.md): method and interpretation.
- [Skeleton 2.2 guide](SKELETON_2.2_CN.md): pruning, junction merging and verification.
- [Current validation](VALIDATION_CN.md): reproducible tests, release evidence and remaining limits.
- [Build artifact retention](BUILD_ARTIFACTS_CN.md): retained releases, cleanup and Git recovery.
- [UI changes and verification](UI_MODERNIZATION_CN.md): screenshots, tests and backups.
- [English manual](source/manual.html).

Run the current release with:

    java -jar AngioTool2.2.3.jar

The build targets Java 8. Each completed development change is committed to Git with
its versioned JAR and documented verification status; routine builds do not require
timestamped JAR backups. Previews with skipped runtime tests remain unverified. The
legacy `AngioTool2.jar` remains protected from accidental replacement.

Run the headless regression suite against the release artifact with:

    python benchmarks/run_regression.py --jar AngioTool2.2.3.jar

Omit `--jar` to build and test an isolated verification JAR. Logs, synthetic
exports and a JSON summary are saved under `verification/regression-*`.
Recompute junction counts and densities for affected images when moving from
2.2.1 to 2.2.2; keep software versions consistent within a comparison.

![Single-window workspace](screenshots/ui-2.1/analysis.png)

## Upstream 2.0 Background

The description and release link below refer to the upstream project, not
the locally modified 2.2 build.

This is a rewritten version of AngioTool, which features advanced analysis settings and batch processing.

<img style="float: left;" src="screenshots/main-window.png" alt="original window" width="48%">
<img style="float: right;" src="screenshots/analysis-window.png" alt="batch window" width="48%">

AngioTool is a program designed to analyse eye scans for potential defects,
using various algorithms to extract data from the scanned image.
It was developed by the Center for Cancer Research from 2011-2014, and is commonly used in the ophthalmology field.

The intended use case was to view and analyse a scanned image, while also saving the results for record keeping and further analysis.
However, in ophthalmology research, it is also used to process large collections of scans.
Since AngioTool only lets the user analyse one scan at a time, this process becomes extremely tedious,
especially when the scanning settings don't need to change.

This version of AngioTool lets the user select one or more folders,
which are recursed fully such that every single non-output image inside those folders
(and the folders inside those folders, and so on) are analysed all in go.
The settings as specified in the user interface are applied to each scan.

Additional settings were also implemented, for example the "Transform Colors" feature which allows for colour OCT scans to be properly analysed.
See the [manual](source/manual.html) for more details.

To download binaries, check the [Releases](https://github.com/jbendtsen/AngioTool-Batch/releases) tab.

The upstream version can be run either as a standalone program or as a plugin
inside ImageJ. ImageJ support remains experimental; for this local 2.2 build,
use the standalone JAR command shown above.
