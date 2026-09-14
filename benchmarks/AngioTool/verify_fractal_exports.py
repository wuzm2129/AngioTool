"""Independently parse FractalIntegrationTest artifacts and recompute counts/fits.

Usage: python benchmarks/AngioTool/verify_fractal_exports.py <artifact-directory>
Requires Pillow for reading the exported PNG masks; no spreadsheet library.
"""
import json
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image


def reject_constant(value):
    raise AssertionError("Non-JSON numeric constant: " + value)


def read_workbook(path):
    ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with zipfile.ZipFile(path) as z:
        shared = ET.fromstring(z.read("xl/sharedStrings.xml"))
        strings = ["".join(item.itertext()) for item in shared]
        sheets = []
        for name in sorted(z.namelist()):
            if not name.startswith("xl/worksheets/sheet") or not name.endswith(".xml"):
                continue
            rows = []
            for row in ET.fromstring(z.read(name)).findall("s:sheetData/s:row", ns):
                cells = {}
                for cell in row.findall("s:c", ns):
                    value = cell.find("s:v", ns)
                    if value is None:
                        continue
                    column = "".join(c for c in cell.attrib["r"] if c.isalpha())
                    cells[column] = strings[int(value.text)] if cell.get("t") == "s" else float(value.text)
                rows.append(cells)
            sheets.append(rows)
        return sheets


def main(root):
    exports = sorted(root.rglob("* overlay-data.json"))
    assert len(exports) >= 7, "Run FractalIntegrationTest before this verifier"
    for path in exports:
        payload = json.loads(path.read_text(encoding="utf-8"), parse_constant=reject_constant)
        fractal = payload["metrics"]["fractalBoxCount"]
        assert fractal["analysisObject"] == "final-vessel-mask"
        assert fractal["method"] == "box-counting"
        assert fractal["gridPolicy"] == "minimum-count-of-four-half-box-offsets"
        assert fractal["scalePolicy"] == "powers-of-two-from-2-to-quarter-shorter-image-side"
        mask_path = path.with_name(path.name.replace(" overlay-data.json", " vessel-mask.png"))
        with Image.open(mask_path) as image:
            width, height = image.size
            gray = list(image.convert("L").getdata())
        assert (fractal["imageWidth"], fractal["imageHeight"]) == (width, height)
        points = [(i % width, i // width) for i, value in enumerate(gray) if value]
        expected_sizes = []
        size = 2
        while size <= min(width, height) // 4:
            expected_sizes.append(size)
            size *= 2
        assert fractal["boxSizes"] == expected_sizes
        counts = []
        for size in expected_sizes:
            counts.append(min(len({((x - ox) // size, (y - oy) // size) for x, y in points})
                              for ox in (0, size // 2) for oy in (0, size // 2)))
        assert counts == fractal["boxCounts"], path
        if fractal["valid"]:
            xs = [-math.log(s) for s in expected_sizes]
            ys = [math.log(n) for n in counts]
            mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
            slope = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum((x - mx) ** 2 for x in xs)
            residual = sum((y - (my + slope * (x - mx))) ** 2 for x, y in zip(xs, ys))
            r2 = 1 - residual / sum((y - my) ** 2 for y in ys)
            assert math.isclose(fractal["dimension"], slope, abs_tol=1e-12)
            assert math.isclose(fractal["rSquared"], r2, abs_tol=1e-12)
        else:
            assert fractal["dimension"] is None and fractal["rSquared"] is None
            assert fractal["invalidReason"]

    extra_headers = ["Fractal Dimension (Box-counting)", "Fractal Fit R squared", "Fractal Valid",
                     "Fractal Invalid Reason", "Fractal Box Sizes (px)", "Fractal Box Counts"]
    for name in ("single.xlsx", "batch.xlsx"):
        matches = [rows[index:] for rows in read_workbook(root / name)
                   for index, row in enumerate(rows) if extra_headers[0] in row.values()]
        assert len(matches) == 1, name
        header, *rows = matches[0]
        columns = {value: key for key, value in header.items()}
        assert all(label in columns for label in extra_headers)
        assert list(header.values())[-6:] == extra_headers, "Existing columns must stay in place"
        dim, fit, valid, reason, sizes, counts = (columns[h] for h in extra_headers)
        available = [row for row in rows if row.get(valid) == "true"]
        unavailable = [row for row in rows if row.get(valid) == "false"]
        assert len(available) == len(unavailable) == 1, (name, rows)
        assert math.isclose(available[0][dim], 2.0, abs_tol=1e-12)
        assert math.isclose(available[0][fit], 1.0, abs_tol=1e-12)
        assert json.loads(available[0][sizes]) == [2, 4, 8, 16, 32]
        assert json.loads(available[0][counts]) == [1024, 256, 64, 16, 4]
        assert unavailable[0].get(dim) in (None, "") and unavailable[0].get(fit) in (None, "")
        assert unavailable[0].get(reason)
    print(f"Fractal export verification: PASS ({len(exports)} strict JSON/mask pairs, 2 XLSX files)")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
