#!/usr/bin/env python3
"""Real CLI subprocess checks; standard library only, no display or desktop preferences."""
import argparse
import hashlib
import json
import math
import os
import resource
import signal
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile
import zlib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def png(path, empty=False):
    width = height = 64
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            v = 255 if not empty and (abs(x-32) <= 3 or (y < 32 and abs(x-y) <= 2)) else 0
            rows.extend([v, v, v])
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind+data))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT/'AngioTool2.3.2.jar')
    args = parser.parse_args()
    jar = args.jar.resolve()
    (ROOT/'verification').mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='cli-', dir=ROOT/'verification'))
    env = dict(os.environ)
    env.pop('DISPLAY', None)
    env.pop('WAYLAND_DISPLAY', None)
    home = work/'home'
    home.mkdir()
    # Malformed desktop preferences must never be read by CLI.
    (home/'AT_Prefs.txt').write_text('invalid desktop preferences')
    java = ['java', '-Djava.awt.headless=true', '-Duser.home='+str(home)]
    calls = []
    def run(options, code=0):
        result = subprocess.run(java+['-jar', str(jar)]+list(map(str, options)), env=env,
                                text=True, capture_output=True, timeout=120)
        calls.append({'args': list(map(str, options)), 'code': result.returncode,
                      'stdout': result.stdout, 'stderr': result.stderr})
        (work/'commands.json').write_text(json.dumps(calls, ensure_ascii=False, indent=2))
        assert result.returncode == code, (options, result.returncode, result.stderr)
        return result
    def analyze(source, destination, *extra, code=0):
        return run(['analyze', '--input', source, '--output', destination, *extra], code)
    def snapshot(folder, source):
        return folder/'images'/source.name/'result overlay-data.json'
    run(['--help'])
    run(['analyze', '--help'])
    assert 'AngioTool 2.' in run(['--version']).stdout
    for options in (['wat'], ['analyze'], ['analyze', '--wat'], ['analyze', '--input'],
                    ['analyze', '--recursive', '--recursive']):
        run(options, 2)
    image = work/'输入 图像.png'
    png(image)
    first = work/'first'
    result = analyze(image, first, '--mode', 'binary', '--normalize', 'true')
    report = json.loads(result.stdout)
    assert report['succeeded'] == 1 and report['failed'] == 0
    assert report == json.loads((first/'run-report.json').read_text())
    assert report['uiParameters']['analysis']['useBinaryMask'] is True
    for workbook in first.glob('*.xlsx'):
        with zipfile.ZipFile(workbook) as archive:
            for name in archive.namelist():
                if name.endswith(('.xml', '.rels')):
                    ET.fromstring(archive.read(name))
    original = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in first.rglob('*') if p.is_file()}
    analyze(image, first, code=2)
    assert original == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in first.rglob('*') if p.is_file()}
    analyze(image, first, '--mode', 'binary', '--normalize', 'true', '--overwrite')
    for option, value in [('--scale', 'NaN'), ('--scale', '1e308'), ('--resize', 'Infinity'), ('--prune', '-1'),
                          ('--mode', 'oops'), ('--skeleton', 'oops'), ('--normalize', 'yes')]:
        analyze(image, work/'bad-config', option, value, code=2)
    analyze(image, first, '--mode', 'binary', '--normalize', 'false', '--overwrite')
    assert not (first/'images'/image.name/'result normalized-data.json').exists()
    assert not (first/'normalized-results.xlsx').exists()
    params = snapshot(first, image)
    overrides = work/'overrides'
    analyze(image, overrides, '--prune', 'off', '--parameters', params, '--merge', 'off',
            '--scale', '2', '--normalize', 'false', '--skeleton', 'zha84')
    override_data = json.loads(snapshot(overrides, image).read_text())
    restored = override_data['uiParameters']['analysis']
    assert override_data['skeletonProcessing']['pruneTerminalBranches'] is False and restored['mergeNearbyJunctions'] is False
    assert restored['measurementScalePercent'] == 200 and restored['skeletonizer'] == 'Fast (Zha84)'
    empty = work/'empty.png'
    png(empty, empty=True)
    assert json.loads(analyze(empty, work/'empty-results', '--mode', 'binary').stdout)['images'][0]['metricsValid'] is False
    batch = work/'batch'
    png(batch/'a.png')
    (batch/'b.png').write_text('broken image')
    png(batch/'子目录'/'a.png')
    png(batch/'a.jpg')  # Same stem; preserve extension in the result directory.
    nested = batch/'results'
    report = json.loads(analyze(batch, nested, '--recursive', '--mode', 'binary', code=1).stdout)
    assert report['succeeded'] == 3 and report['failed'] == 1
    assert (nested/'images'/'子目录'/'a.png'/'result.png').is_file()
    report = json.loads(analyze(batch, nested, '--recursive', '--mode', 'binary', '--overwrite', code=1).stdout)
    assert report['succeeded'] == 3 and report['failed'] == 1  # Exclude own output.
    # Separate output avoids intentionally treating previous outputs as new input.
    fail_input = work/'fail-input'
    fail_input.mkdir()
    (fail_input/'0.png').write_text('broken')
    png(fail_input/'1.png')
    report = json.loads(analyze(fail_input, work/'fail-fast', '--fail-fast', code=1).stdout)
    assert report['failed'] == 1 and report['notRun'] == 1
    bad_json = work/'bad.json'
    bad_json.write_text('{broken')
    analyze(image, work/'bad-json-output', '--parameters', bad_json, code=2)
    assert not (work/'bad-json-output').exists()
    def file_limit():
        signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
        resource.setrlimit(resource.RLIMIT_FSIZE, (1024, 1024))
    limited = subprocess.run(java+['-jar', str(jar), 'analyze', '--input', str(image),
                                  '--output', str(work/'limited'), '--mode', 'binary'],
                             env=env, text=True, capture_output=True, timeout=120, preexec_fn=file_limit)
    assert limited.returncode == 1 and 'Cannot save report' in limited.stderr
    no_images = work/'no-images'
    no_images.mkdir()
    analyze(no_images, work/'no-images-output', code=2)
    conflict = work/'conflict'
    conflict.mkdir()
    (conflict/'results.xlsx').mkdir()
    analyze(image, conflict, '--overwrite', code=2)
    symlink = work/'linked-output'
    symlink.mkdir()
    (symlink/'images').symlink_to(first/'images', target_is_directory=True)
    analyze(image, symlink, '--overwrite', code=2)
    # Symlink loops must not be followed.
    (no_images/'loop').symlink_to(no_images, target_is_directory=True)
    analyze(no_images, work/'loop-output', '--recursive', code=2)

    classes = work/'classes'
    classes.mkdir()
    baseline = ROOT/'AngioTool2.2.3.jar'
    subprocess.run(['javac', '--release=8', '-cp', str(baseline), '-d', str(classes),
                    str(ROOT/'benchmarks/AngioTool/CliReference.java')], check=True, capture_output=True)
    comparisons = 0
    def equivalent(a, b):
        if isinstance(a, dict):
            assert a.keys() == b.keys()
            for key in a:
                if key not in ('programVersion', 'exportDate', 'exportTime'):
                    equivalent(a[key], b[key])
        elif isinstance(a, list):
            assert len(a) == len(b)
            for x, y in zip(a, b): equivalent(x, y)
        elif isinstance(a, (float, int)) and not isinstance(a, bool):
            assert math.isclose(a, b, rel_tol=1e-10, abs_tol=1e-10), (a, b)
        else:
            assert a == b, (a, b)
    for mode in ['binary', 'tubeness']:
        for skeleton in ['zha84', 'lee94']:
            target = work/(mode+'-'+skeleton)
            analyze(image, target, '--mode', mode, '--skeleton', skeleton, '--scale', '2',
                    '--prune', '2', '--merge', '2', '--normalize', 'true', '--resize', '0.5')
            ref = work/('reference-'+mode+'-'+skeleton)
            ref.mkdir()
            subprocess.run(java+['-cp', os.pathsep.join([str(classes), str(baseline)]), 'AngioTool.CliReference',
                                 str(image), str(snapshot(target, image)), str(ref/'result')],
                           env=env, check=True, capture_output=True, timeout=120)
            for expected in ref.iterdir():
                actual = target/'images'/image.name/expected.name
                if expected.suffix == '.png': assert expected.read_bytes() == actual.read_bytes()
                else: equivalent(json.loads(expected.read_text()), json.loads(actual.read_text()))
            comparisons += 1
    example = ROOT/'source/deprecated/images/ImageExample.png'
    target = work/'example'
    analyze(example, target)
    ref = work/'example-reference'
    ref.mkdir()
    subprocess.run(java+['-cp', os.pathsep.join([str(classes), str(baseline)]), 'AngioTool.CliReference',
                         str(example), str(snapshot(target, example)), str(ref/'result')],
                   env=env, check=True, capture_output=True, timeout=120)
    for expected in ref.iterdir():
        actual = target/'images'/example.name/expected.name
        if expected.suffix == '.png': assert expected.read_bytes() == actual.read_bytes()
        else: equivalent(json.loads(expected.read_text()), json.loads(actual.read_text()))
    comparisons += 1
    print('PASS CLI subprocess checks and %d baseline parity cases: %s' % (comparisons, work))
    (work/'summary.json').write_text(json.dumps({'status': 'passed', 'cliCommands': len(calls),
                                              'baselineParityCases': comparisons}, indent=2))


if __name__ == '__main__':
    main()
