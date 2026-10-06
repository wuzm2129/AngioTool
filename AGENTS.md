# Repository Guidelines

## Project Structure & Module Organization

AngioTool 2 is a Java 8 desktop application with experimental ImageJ plugin support. Production code is under `source/`, organized by package: `AngioTool/` contains the GUI and analysis orchestration, `Algorithms/` contains image-processing algorithms, and `Pixels/`, `Utils/`, `Xlsx/`, and `IJPlugin/` provide supporting functionality. `source/deprecated/` is retained legacy code and is excluded from normal builds. Packaged assets live in `source/images/`, `source/manual.html`, and `source/META-INF/`; `stubs/` supplies minimal ImageJ APIs for compilation. `benchmarks/` contains standalone performance programs, while `images/` and `screenshots/` hold repository artwork and documentation media. `build/`, `source/sources.txt`, and generated JAR/EXE files are build outputs.

## Build, Test, and Development Commands

- `python compile.py` — requires Python 3 and a JDK (`java` and `javac` on `PATH`); expands `.tj` templates, compiles with `--release=8`, writes classes to `build/`, and creates the current development JAR (`AngioTool2.2.3-preview5.jar`). The last verified release is `AngioTool2.2.2.jar`; the preview awaits runtime verification.
- `java -jar AngioTool2.2.3-preview5.jar` — launches the development preview after a successful build. Use `java -jar AngioTool2.2.2.jar` for the last verified release.
- `python buildexe.py <path-to-tcc>` — legacy native Windows launcher build; it still reads `AngioTool2.jar` and is not adapted to current versioned JARs. Do not use it to package the current release without updating that workflow. It requires TCC, MinGW resource compiler, and JDK JNI headers.

## Version and Build Artifact Rule

Use Git history instead of timestamped pre-compilation JAR backups. Do not create a backup solely because `python compile.py` will run. Commit each completed development change with its focused source, documentation, any relevant tests, and a new versioned JAR. If the user requests skipping runtime tests, commit it as a development preview and record the unverified scope; only promote a completed release after its relevant verification succeeds. Preserve unrelated work and never overwrite the legacy `AngioTool2.jar` unless the user explicitly requests it.

## Coding Style & Naming Conventions

Use four-space indentation, braces on the next line for classes and methods, and Java 8-compatible APIs and syntax. Keep package directories aligned with their package declarations. Name classes in `PascalCase`, methods and variables in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Keep `.tj` templates authoritative for generated `source/Utils/*.java` files, and avoid unrelated formatting churn.

## Testing Guidelines

No JUnit suite or coverage threshold is configured. Treat `python compile.py` as the baseline compile check, then smoke-test the JAR with representative images and any affected ImageJ/plugin path. For performance-sensitive changes, compile and run a targeted benchmark, for example `javac -d build benchmarks/FiltersBenchmark.java` followed by `java -cp build FiltersBenchmark`.

Run `python benchmarks/run_regression.py` to build an isolated verification JAR and execute the headless regression suite, or `python benchmarks/run_regression.py --jar AngioTool2.2.2.jar` to test a release artifact. Logs and generated fixtures are saved under `verification/regression-*`; see `VALIDATION_CN.md` for coverage and limits.

## Analysis Pipeline & Metrics

`Analyzer.analyze()` converts each input into a binary vessel mask. It optionally resizes or remaps colors (otherwise using the brightest channel), then `Tubeness` applies Gaussian smoothing at each configured `sigma`/“Vessel Diameter” scale and keeps the strongest tubular Hessian response. The low/high intensity range binarizes that response; max/min filters and `Particles` connected-component heuristics clean gaps, holes, and small particles.

Metrics come from the final mask and its skeleton. `Vessels Area` is the foreground-pixel count; `Explant/Allantois Area` is the shoelace area of the foreground convex hull; percentage is vessel area divided by hull area. Zha84 or Lee94 skeletonization feeds `AnalyzeSkeleton2`, which classifies 8-neighbor endpoints, junctions, and slab points, groups connected trees, and sums Euclidean edge lengths. Junction density divides isolated junction clusters by scaled hull area. `VesselThickness` reports twice the median nearest-background distance at slab points, despite the field being named “Average Vessel Diameter.” Optional lacunarity uses multi-size sliding boxes (E includes empty boxes, F excludes them). Measurement scale multiplies lengths/diameters by `s` and areas by `s²`; input resizing occurs before analysis and can change segmentation. The exported average vessel length currently divides total length by tree count, not branch count.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects consistent with project history, such as `Add ...`, `Update ...`, `Document ...`, or `Align ...`. Keep commits focused. Pull requests should describe user-visible or algorithmic impact, list validation commands and results, identify generated artifacts, and include screenshots for GUI changes. Link a related issue or discussion when one exists.
