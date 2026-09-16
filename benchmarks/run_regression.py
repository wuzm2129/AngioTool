#!/usr/bin/env python3
"""Build an isolated JAR and run headless regression tests using only Python and a JDK."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent.parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="Test an existing JAR instead of rebuilding")
    args = parser.parse_args()
    if args.jar is not None and not args.jar.is_file():
        parser.error("JAR does not exist: " + str(args.jar))
    verification = ROOT / "verification"
    verification.mkdir(exist_ok=True)
    output = Path(tempfile.mkdtemp(prefix="regression-", dir=verification))
    jar = args.jar.resolve() if args.jar is not None else output / "AngioTool-verification.jar"
    classes = output / "test-classes"
    classes.mkdir()
    protected = {p.name: sha256(p) for p in ROOT.glob("AngioTool*.jar")}
    report = {"started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "steps": [],
              "protected_jars": protected, "status": "running"}
    print("Verification directory: " + str(output), flush=True)

    def run(name, command, timeout=180):
        start = time.monotonic()
        log = output / (name + ".log")
        with log.open("w", encoding="utf-8") as stream:
            stream.write(json.dumps(command, ensure_ascii=False) + "\n")
            stream.flush()
            try:
                result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                        timeout=timeout, check=False)
                code = result.returncode
            except subprocess.TimeoutExpired:
                stream.write("\nTIMEOUT\n")
                code = -1
        report["steps"].append({"name": name, "exit_code": code,
                                "seconds": round(time.monotonic() - start, 2), "log": log.name})
        print(("PASS " if code == 0 else "FAIL ") + name, flush=True)
        if code != 0:
            print(log.read_text(encoding="utf-8", errors="replace"))
            raise RuntimeError(name + " failed; see " + str(log))

    try:
        run("java-version", ["java", "-version"])
        if args.jar is None:
            run("build", [sys.executable, "compile.py", "--output", str(jar)])
        report["tested_jar"] = str(jar)
        report["tested_jar_sha256"] = sha256(jar)
        sources = sorted((ROOT / "benchmarks" / "Algorithms").glob("*Test.java"))
        sources += sorted((ROOT / "benchmarks" / "AngioTool").glob("*Test.java"))
        run("compile-tests", ["javac", "-encoding", "UTF-8", "--release=8", "-cp", str(jar),
                              "-d", str(classes)] + [str(p) for p in sources])
        tests = [
            ("Algorithms.SkeletonPruningTest", []),
            ("Algorithms.SkeletonJunctionMergeTest", []),
            ("AngioTool.SkeletonPipelineTest", [str(output / "skeleton")]),
            ("benchmarks.AngioTool.AnalysisRegressionTest", []),
            ("AngioTool.BinaryMaskTest", []),
            ("AngioTool.BatchOutputPathTest", [str(ROOT / "source/deprecated/images/ImageExample.png")]),
            ("AngioTool.FractalBoxCountTest", []),
            ("AngioTool.FractalIntegrationTest", [str(output / "fractal")]),
            ("AngioTool.NormalizedMetricsTest", []),
            ("AngioTool.SpreadsheetWriterCompatibilityTest", [str(output / "xlsx")]),
        ]
        for test, args in tests:
            run(test, ["java", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-cp",
                       os.pathsep.join([str(classes), str(jar)]), test] + args)
        # Independently parse every skeleton JSON and XLSX, rather than relying only on Java writers/readers.
        for path in (output / "skeleton").glob("*.json"):
            def reject_constant(value):
                raise ValueError("Non-JSON number: " + value)
            json.loads(path.read_text(encoding="utf-8"), parse_constant=reject_constant)
        for path in (output / "skeleton").glob("*.xlsx"):
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith((".xml", ".rels")):
                        ET.fromstring(archive.read(name))
        report["export_syntax_check"] = "PASS"
        report["status"] = "passed"
    except (OSError, RuntimeError, ValueError, ET.ParseError, zipfile.BadZipFile) as error:
        report["status"] = "failed"
        report["error"] = str(error)
        print(str(error), file=sys.stderr)
    finally:
        unchanged = all((ROOT / name).is_file() and sha256(ROOT / name) == digest
                        for name, digest in protected.items())
        report["protected_jars_unchanged"] = unchanged
        if not unchanged:
            report["status"] = "failed"
            report["error"] = "A pre-existing root JAR changed"
        (output / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                           encoding="utf-8")
    print("Result: " + report["status"] + "; " + str(output / "summary.json"), flush=True)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
