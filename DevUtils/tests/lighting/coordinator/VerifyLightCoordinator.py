#!/usr/bin/env python3
"""Actual Frozen/pre/current lighting policy traces; no copied expected engine."""
import importlib.util
import json
import os
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('threaded_campaign', HERE.parent / 'threaded/VerifyThreadedLighting.py')
campaign_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(campaign_module)


def main():
    args = campaign_module.arguments(__doc__).parse_args()
    campaign = campaign_module.Campaign(args)
    campaign.result['scope'] = 'Actual normally constructed block/sky engines, ordered effects and retained published layers; no client claim'
    driver = HERE / 'VerifyLightCoordinator.java'
    archive=campaign.output/'driver-source'
    archive.mkdir()
    for source in HERE.iterdir():
        if source.suffix in ('.java','.py'):
            campaign.result['driver_sha256'][str(source.relative_to(campaign_module.ROOT))]=campaign_module.sha(source)
            (archive/source.name).write_bytes(source.read_bytes())
    try:
        for side in ('frozen', 'control', 'current'):
            campaign.run([str(campaign.jdk / 'javac'), '-g', '-nowarn', '-cp', campaign.classpaths[side],
                '-d', str(campaign.drivers[side]), str(driver)], 'compile-coordinator-' + side + '.log')
        campaign.class_images = campaign_module.images({**campaign.classpaths,
            **{'observer-' + s: str(p) for s, p in campaign.drivers.items()}})
        campaign.result['class_images'] = campaign.class_images
        for side in ('frozen', 'control', 'current'):
            trace = campaign.output / (side + '-trace.txt')
            command = campaign.java(side, 'VerifyLightCoordinator', [trace])
            command[command.index('net.minecraft.server.level.VerifyLightCoordinator')] = 'net.minecraft.world.level.lighting.VerifyLightCoordinator'
            campaign.run(command, side + '.log')
            campaign.result[side] = {'sha256': campaign_module.sha(trace), 'rows': len(trace.read_text().splitlines())}
        rows = [campaign.result[s] for s in ('frozen', 'control', 'current')]
        campaign.result['passed'] = rows[0] == rows[1] == rows[2] and rows[0]['rows'] > 1000
        current_log=(campaign.output/'current.log').read_text()
        campaign.result['native_policy_routes']=[line for line in current_log.splitlines() if 'NATIVE_POLICY ' in line]
        campaign.result['normal_native_composition']='NATIVE_COMPOSITION mode=normal bindings=2 independent-work/context=true owners-after=0' in current_log
        campaign.result['passed'] &= len(campaign.result['native_policy_routes'])>=3 and campaign.result['normal_native_composition']
        if not campaign.result['passed']:
            a = (campaign.output / 'frozen-trace.txt').read_text().splitlines()
            b = (campaign.output / 'current-trace.txt').read_text().splitlines()
            campaign.result['first_differences'] = [{'line': i+1, 'frozen': x, 'current': y}
                for i, (x, y) in enumerate(zip(a, b)) if x != y][:20]
    finally:
        campaign.seal()
    assert campaign.result['passed'] and campaign.result['guards_pass'], str(campaign.output / 'result.json')
    print('PASS', json.dumps(campaign.result['current']))


if __name__ == '__main__':
    main()
