#!/usr/bin/env python3
"""Configuration command integration tests, with no display and isolated preferences."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT/'AngioTool2.3.2.jar')
    args = parser.parse_args()
    jar = args.jar.resolve()
    (ROOT/'verification').mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='config-', dir=ROOT/'verification'))
    env = dict(os.environ)
    env.pop('DISPLAY', None)
    env.pop('WAYLAND_DISPLAY', None)
    java = ['java', '-Djava.awt.headless=true', '-Duser.home='+str(work/'home'), '-jar', str(jar)]
    calls = []
    def run(*options, code=0):
        p = subprocess.run(java+list(map(str, options)), env=env, capture_output=True, text=True, timeout=30)
        calls.append({'args': list(map(str, options)), 'code': p.returncode, 'out': p.stdout, 'err': p.stderr})
        (work/'commands.json').write_text(json.dumps(calls, ensure_ascii=False, indent=2))
        assert p.returncode == code, (options, p.returncode, p.stderr)
        return json.loads(p.stdout) if p.stdout.strip().startswith('{') else p.stdout
    defaults = run('config', 'defaults')
    assert defaults['schemaVersion'] == 1
    path = work/'parameters.json'
    path.write_text(json.dumps(defaults))
    assert run('config', 'validate', '--parameters', path)['valid'] is True
    assert run('config', 'resolve', '--parameters', path)['parameters'] == defaults['parameters']
    # Exercise every advertised parameter option through its actual CLI parser.
    options = []
    for name, value in defaults['parameters'].items():
        if name.startswith('should'):
            name = name[6].lower()+name[7:]
        import re
        option = '--'+re.sub(r'([a-z0-9])([A-Z])', r'\1-\2', name).lower()
        if isinstance(value, bool): text = str(value).lower()
        elif isinstance(value, list): text = ','.join(map(str, value))
        else: text = str(value)
        options += [option, text]
    assert run('config', 'resolve', *options)['parameters'] == defaults['parameters']
    dest = work/'new'/'defaults.json'
    run('config', 'defaults', '--output', dest)
    before = dest.read_bytes()
    run('config', 'defaults', '--output', dest, code=2)
    assert dest.read_bytes() == before
    run('config', 'defaults', '--output', dest, '--overwrite')
    bad = work/'bad.json'
    for data in [dict(defaults, schemaVersion=2), dict(defaults, unknown=1),
                 {'schemaVersion': 1, 'parameters': {'typo': True}},
                 {'schemaVersion': 1, 'parameters': {'thresholdLow': '20'}},
                 {'parameters': {}}, {'schemaVersion': 1, 'parameters': None}]:
        bad.write_text(json.dumps(data))
        assert run('config', 'validate', '--parameters', bad, code=2)['valid'] is False
    for text in ['{"schemaVersion":1,"schemaVersion":1,"parameters":{}}',
                 '{"schemaVersion":1,"parameters":{"thresholdLow":1,"thresholdLow":2}}',
                 '{"schemaVersion":1,"parameters":{"linearScalingFactor":1e400}}']:
        bad.write_text(text)
        run('config', 'validate', '--parameters', bad, code=2)
    invalid = dict(defaults['parameters'], thresholdLow=-1, thresholdHigh=300, outlineSize=0)
    bad.write_text(json.dumps({'schemaVersion': 1, 'parameters': invalid}))
    errors = run('config', 'validate', '--parameters', bad, code=2)['errors']
    assert len(errors) >= 3 and any('thresholdLow' in x for x in errors)
    # Range errors may be repaired by CLI; wrong JSON types may not.
    fixed = run('config', 'resolve', '--parameters', bad, '--threshold-low', '15', '--threshold-high', '255', '--outline-size', '1')
    assert fixed['parameters'] == defaults['parameters']
    for flags in [('--scale', '2', '--apply-linear-scale', 'true'),
                  ('--linear-scaling-factor', '2', '--scale', '2'),
                  ('--prune', 'off', '--prune-skeleton', 'false'),
                  ('--skeleton', 'zha84', '--use-fast-skeletonizer', 'true'),
                  ('--normalize', 'true', '--normalize-output-data', 'true'),
                  ('--threshold-low', '10', '--threshold-low', '10')]:
        run('config', 'resolve', *flags, code=2)
    dormant = run('config', 'resolve', '--mode', 'binary', '--threshold-low', '-42', '--sigmas', '-1')
    assert dormant['parameters']['thresholdLow'] == -42
    bad.write_text(json.dumps(dormant))
    assert run('config', 'validate', '--parameters', bad)['valid']
    for flags in [('--sigmas', '0,5'), ('--brightness-line-segments', '-1,0,100,100'),
                  ('--outline-color', '#GG0000'), ('--threshold-low', '1.5'),
                  ('--remap-colors', 'true', '--brightness-line-segments', '0,0,0,10,100,100')]:
        run('config', 'resolve', *flags, code=2)
    from run_cli_tests import png
    source = work/'mask.png'
    png(source)
    run('analyze', '--input', source, '--output', work/'analysis', '--parameters', bad)
    snapshot = work/'analysis'/'images'/'mask.png'/'result overlay-data.json'
    assert run('config', 'resolve', '--parameters', snapshot)['parameters'] == dormant['parameters']
    classes = work/'classes'
    classes.mkdir()
    subprocess.run(['javac', '--release=8', '-cp', str(jar), '-d', str(classes),
                    str(ROOT/'benchmarks/AngioTool/CliConfigTest.java')], check=True, capture_output=True)
    inventory = subprocess.run(['java', '-cp', os.pathsep.join([str(classes), str(jar)]), 'AngioTool.CliConfigTest'],
                              env=env, check=True, capture_output=True, text=True, timeout=30)
    (work/'inventory.log').write_text(inventory.stdout)
    (work/'summary.json').write_text(json.dumps({'status': 'passed', 'commands': len(calls),
                                               'parameters': len(defaults['parameters'])}, indent=2))
    print('PASS configuration commands (%d fields, %d commands): %s' % (len(defaults['parameters']), len(calls), work))


if __name__ == '__main__':
    main()
