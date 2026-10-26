#!/usr/bin/env python3
"""Dry-run, export selection, multiple-input ownership and filtering integration checks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from run_cli_tests import png

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT/'AngioTool2.3.2.jar')
    args = parser.parse_args()
    jar = args.jar.resolve()
    (ROOT/'verification').mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='plan-', dir=ROOT/'verification'))
    env = dict(os.environ)
    env.pop('DISPLAY', None)
    env.pop('WAYLAND_DISPLAY', None)
    java = ['java', '-Djava.awt.headless=true', '-Duser.home='+str(work/'home'), '-jar', str(jar)]
    calls = []
    def run(*options, code=0):
        p = subprocess.run(java+list(map(str, options)), env=env, capture_output=True, text=True, timeout=120)
        calls.append({'args': list(map(str, options)), 'code': p.returncode, 'out': p.stdout, 'err': p.stderr})
        (work/'commands.json').write_text(json.dumps(calls, ensure_ascii=False, indent=2))
        assert p.returncode == code, (options, p.returncode, p.stderr)
        return json.loads(p.stdout) if p.returncode == 0 or p.stdout.strip().startswith('{') else None
    def analyze(source, output, *options, code=0):
        return run('analyze', '--input', source, '--output', output, *options, code=code)
    def fingerprint(path):
        return {str(p.relative_to(path)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in path.rglob('*') if p.is_file()}
    source = work/'input'
    png(source/'a.png')
    png(source/'nested'/'b.png')
    output = work/'not-created'
    before = fingerprint(source)
    plan = analyze(source, output, '--recursive', '--dry-run', '--include', '**/*.png')
    assert len(plan['tasks']) == 2 and not output.exists()
    assert before == fingerprint(source)
    assert any(w['conditional'] for w in plan['tasks'][0]['writes'])
    assert all(not w['conditional'] for t in plan['tasks'] for w in t['writes'] if not w['path'].endswith('skeleton.png'))
    # Root matches **/*.png, while * cannot span a directory.
    assert len(analyze(source, output, '--recursive', '--dry-run', '--include', '*.png')['tasks']) == 1
    assert len(analyze(source, output, '--recursive', '--dry-run', '--include', '**/*.png', '--exclude', 'nested/**')['tasks']) == 1
    assert len(analyze(source, output, '--recursive', '--dry-run', '--include', 'a.?ng', '--include', 'nested/b.png')['tasks']) == 2
    analyze(source, output, '--recursive', '--dry-run', '--include', '*.tif', code=2)
    assert not output.exists()
    broken = source/'bad.png'
    broken.write_text('broken image')
    analyze(broken, work/'broken-output', '--dry-run')  # No decoding in dry-run.
    assert not (work/'broken-output').exists()
    analyze(broken, work/'broken-output', code=1)
    broken.unlink()

    # Full baseline exports, then select individual families without perturbing metrics.
    base = work/'baseline'
    full = analyze(source/'a.png', base, '--mode', 'binary', '--normalize', 'true')
    baseline_json = json.loads((base/'images'/'a.png'/'result overlay-data.json').read_text())
    baseline_mask = (base/'images'/'a.png'/'result vessel-mask.png').read_bytes()
    for family, expected in [('overlay', ['result.png']), ('mask', ['result vessel-mask.png']),
                             ('raw-skeleton', ['result raw-skeleton.png']),
                             ('pruned-skeleton', ['result pruned-skeleton.png']),
                             ('json', ['result overlay-data.json', 'result normalized-data.json']), ('xlsx', [])]:
        target = work/('export-'+family)
        planned = analyze(source/'a.png', target, '--mode', 'binary', '--normalize', 'true', '--exports', family, '--dry-run')
        actual = analyze(source/'a.png', target, '--mode', 'binary', '--normalize', 'true', '--exports', family)
        files = [Path(p).name for p in actual['images'][0]['outputs']]
        assert sorted(files) == sorted(expected), (family, files)
        assert sorted(str(p.relative_to(target)) for p in target.rglob('*') if p.is_file()) == sorted(
            ['run-report.json'] + (['results.xlsx', 'normalized-results.xlsx'] if family == 'xlsx' else [])
            + ['images/a.png/'+name for name in expected])
        assert set(actual['images'][0]['outputs']).issubset(set(w['path'] for w in planned['tasks'][0]['writes']))
        if family == 'mask': assert (target/'images'/'a.png'/expected[0]).read_bytes() == baseline_mask
        if family == 'json':
            selected = json.loads((target/'images'/'a.png'/expected[0]).read_text())
            assert selected['metrics'] == baseline_json['metrics']
            assert selected['analysisParameters'] == baseline_json['analysisParameters']
    selected = work/'selected'
    keep = selected/'images'/'a.png'/'result.png'
    keep.parent.mkdir(parents=True)
    keep.write_text('must stay byte-for-byte')
    (selected/'results.xlsx').mkdir()  # An unselected output conflict must not block analysis.
    plan = analyze(source/'a.png', selected, '--exports', 'json', '--mode', 'binary', '--dry-run')
    assert str(keep) in plan['tasks'][0]['preserve']
    report = analyze(source/'a.png', selected, '--exports', 'json', '--mode', 'binary')
    assert keep.read_text() == 'must stay byte-for-byte'
    assert str(keep) in report['images'][0]['notUpdated']
    assert (selected/'results.xlsx').is_dir()
    unselected_link = selected/'images'/'a.png'/'result raw-skeleton.png'
    unselected_link.symlink_to(base/'images'/'a.png'/'result raw-skeleton.png')
    assert analyze(source/'a.png', selected, '--exports', 'json', '--overwrite')['succeeded'] == 1
    assert unselected_link.is_symlink()
    before = fingerprint(selected)
    analyze(source/'a.png', selected, '--exports', 'json', '--dry-run', code=2)
    analyze(source/'a.png', selected, '--exports', 'json', code=2)
    assert before == fingerprint(selected)
    old_normalized = selected/'images'/'a.png'/'result normalized-data.json'
    old_normalized.write_text('old optional file')
    analyze(source/'a.png', selected, '--exports', 'json', '--normalize', 'false', '--mode', 'binary', '--overwrite')
    assert old_normalized.read_text() == 'old optional file'
    for family in ['', 'mask,mask', 'wat', 'json,']:
        analyze(source/'a.png', work/'invalid-family', '--exports', family, code=2)
    # Selected parents are protected even when unrelated outputs are ignored.
    blocked = work/'blocked'
    blocked.mkdir()
    (blocked/'images').write_text('not a directory')
    analyze(source/'a.png', blocked, '--exports', 'json', '--dry-run', code=2)
    linked = work/'linked'
    linked.mkdir()
    (linked/'images').symlink_to(base/'images', target_is_directory=True)
    analyze(source/'a.png', linked, '--exports', 'mask', '--overwrite', code=2)

    # Multiple roots with the same basename get deterministic, collision-free namespaces.
    other = work/'other'/'input'
    png(other/'a.png')
    multi_output = work/'multi'
    options = ['analyze', '--input', source, '--input', other, '--output', multi_output, '--recursive', '--mode', 'binary', '--exports', 'json,mask']
    planned = run(*options, '--dry-run')
    reversed_plan = run('analyze', '--input', other, '--input', source, '--output', multi_output, '--recursive', '--mode', 'binary', '--exports', 'json,mask', '--dry-run')
    assert planned == reversed_plan
    assert len(planned['tasks']) == 3 and len({t['outputDirectory'] for t in planned['tasks']}) == 3
    report = run(*options)
    assert report['succeeded'] == 3
    for record in report['images']:
        assert all(Path(p).is_file() for p in record['outputs'])
        if Path(record['input']).name == 'a.png':
            folder = Path(record['outputDirectory'])
            assert (folder/'result vessel-mask.png').read_bytes() == baseline_mask
            assert json.loads((folder/'result overlay-data.json').read_text())['metrics'] == baseline_json['metrics']
    reversed_report = run('analyze', '--input', other, '--input', source, '--output', multi_output,
                          '--recursive', '--mode', 'binary', '--exports', 'json,mask', '--overwrite')
    assert [i['input'] for i in reversed_report['images']] == [i['input'] for i in report['images']]
    for record in reversed_report['images']:
        if Path(record['input']).name == 'a.png':
            folder = Path(record['outputDirectory'])
            assert (folder/'result vessel-mask.png').read_bytes() == baseline_mask
            assert json.loads((folder/'result overlay-data.json').read_text())['metrics'] == baseline_json['metrics']
    # Repeated root collapses to the existing single-input layout.
    dedup = run('analyze', '--input', source, '--input', source, '--output', work/'dedup', '--recursive', '--dry-run')
    assert len(dedup['tasks']) == 2
    assert Path(dedup['tasks'][0]['outputDirectory']).relative_to(work/'dedup').parts[:2] == ('images', 'a.png')
    # Most-specific directory owns b.png; a directly named file takes priority over its parent root.
    overlap = run('analyze', '--input', source, '--input', source/'nested', '--input', source/'a.png',
                  '--output', work/'overlap', '--recursive', '--dry-run')
    assert len(overlap['tasks']) == 2
    owners = {Path(t['input']).name: t['root'] for t in overlap['tasks']}
    assert owners == {'a.png': str(source/'a.png'), 'b.png': str(source/'nested')}
    # Filtering is evaluated after ownership, so b.png is relative to nested, not input.
    filtered = run('analyze', '--input', source, '--input', source/'nested', '--output', work/'filtered',
                   '--recursive', '--include', 'b.png', '--dry-run')
    assert len(filtered['tasks']) == 1
    listing = work/'lists'/'files.txt'
    listing.parent.mkdir()
    listing.write_text('\ufeff../input\n\n../other/input\n../input\n')
    listed = run('analyze', '--input-list', listing, '--output', work/'from-list', '--recursive', '--dry-run')
    assert len(listed['tasks']) == 3
    list_report = run('analyze', '--input-list', listing, '--output', work/'from-list', '--recursive',
                      '--mode', 'binary', '--exports', 'json')
    assert list_report['succeeded'] == 3
    assert len({i['input'] for i in list_report['images']}) == 3
    inputs = {t['input'] for t in listed['tasks']}
    assert inputs == {t['input'] for t in planned['tasks']}
    empty = work/'empty-list.txt'
    empty.write_text('\n')
    run('analyze', '--input-list', empty, '--output', work/'empty-list-output', code=2)
    # Plan cannot use an output ancestor containing any input.
    analyze(source, work, '--dry-run', code=2)
    for flags in [('--exports', 'mask'), ('--include', '*.png'), ('--dry-run',)]:
        run('config', 'resolve', *flags, code=2)
    # The CLI report carries the exact final config and source provenance.
    cfg = work/'config.json'
    cfg.write_text(json.dumps(run('config', 'defaults')))
    source_report = analyze(source/'a.png', work/'provenance', '--parameters', cfg,
                            '--mode', 'binary', '--exports', 'json')
    assert source_report['configurationSource']['source'] == str(cfg)
    assert source_report['configurationSource']['cliOverrides'] == ['--mode']
    assert source_report['configuration']['parameters']['shouldUseBinaryMask'] is True
    assert not (work/'not-created').exists()
    (work/'summary.json').write_text(json.dumps({'status': 'passed', 'commands': len(calls)}, indent=2))
    print('PASS planning/export/multiple-input checks (%d commands): %s' % (len(calls), work))


if __name__ == '__main__':
    main()
