#!/usr/bin/env python3
"""Run the existing ticket differential with a portable holder-lookup fixture.

The legacy fixture writes ChunkMap.updatingChunkMap, a field removed by native
holder ownership. Only replace that injected lookup boundary with an override
on every classpath. Keep the actual ticket operations, listeners, futures and
observer unchanged. A successful original Frozen trace must still match.
"""
import argparse
import difflib
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[4]
LEGACY = ROOT / 'DevUtils/tests/world/tickets/VerifyTicketStorage.py'
SOURCE = LEGACY.with_suffix('.java')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True)
    parser.add_argument('--control-ref', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--original-frozen-log', type=Path, required=True)
    args = parser.parse_args()
    reference = re.search(r'TRACE (\w+) (\d+)', args.original_frozen_log.read_text())
    assert reference, 'Require the successful original Frozen observer trace'
    output = args.output.resolve()
    observer = output.parent / (output.name + '-observer')
    observer.mkdir(parents=True, exist_ok=False)
    original = SOURCE.read_text()
    adapted = original

    def replace(old, new, count=1):
        nonlocal adapted
        assert adapted.count(old) == count, (old, 'legacy fixture changed')
        adapted = adapted.replace(old, new)

    replace('public final class VerifyTicketStorage {', '''public final class VerifyTicketStorage {
    // This supplies exactly the old injected holder map through its getter.
    // No ChunkMap constructor, scheduling or policy is executed by this fixture.
    static final class HolderLookup extends ChunkMap {
        Long2ObjectLinkedOpenHashMap<ChunkHolder> fixtureHolders;
        private HolderLookup() { super(null, null, null, null, null, null, null, null, null, null, null, 0, false); }
        @Override public ChunkHolder getUpdatingChunkIfPresent(long key) { return fixtureHolders.get(key); }
    }''')
    replace('ChunkMap chunkMap = allocate(ChunkMap.class);', 'HolderLookup chunkMap = allocate(HolderLookup.class);', 2)
    replace('field(ChunkMap.class, "updatingChunkMap").set(chunkMap, holders);', 'chunkMap.fixtureHolders = holders;')
    replace('field(ChunkMap.class, "updatingChunkMap").set(chunkMap, manager.holders);',
            'chunkMap.fixtureHolders = manager.holders;')
    target = observer / SOURCE.name
    target.write_text(adapted)
    (observer / 'fixture.diff').write_text(''.join(difflib.unified_diff(
        original.splitlines(True), adapted.splitlines(True), fromfile=str(SOURCE), tofile=str(target))))
    spec = importlib.util.spec_from_file_location('existing_ticket_differential', LEGACY)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.DRIVER = target
    sys.argv = [str(LEGACY), '--java-home', args.java_home, '--control-ref', args.control_ref,
                '--output', str(output)]
    code = 1
    error = None
    try:
        module.main()
    except SystemExit as done:
        code = done.code
    except Exception as failure:
        error = f'{type(failure).__name__}: {failure}'
    path = output / 'result.json'
    result = json.loads(path.read_text()) if path.exists() else {'passed': False}
    result['fixture_adapter'] = {
        'source': str(SOURCE), 'original_sha256': hashlib.sha256(original.encode()).hexdigest(),
        'adapted_source': str(target), 'adapted_sha256': hashlib.sha256(adapted.encode()).hexdigest(),
        'diff': str(observer / 'fixture.diff'), 'original_frozen_log': str(args.original_frozen_log.resolve()),
        'original_frozen_trace': reference[1], 'original_frozen_steps': int(reference[2]),
        'frozen_fixture_equivalent': result.get('frozen', {}).get('trace') == reference[1],
    }
    result['passed'] = code == 0 and result.get('passed') and result['fixture_adapter']['frozen_fixture_equivalent']
    if error:
        result['adapter_error'] = error
    path.write_text(json.dumps(result, indent=2) + '\n')
    print('Portable fixture gate:', result['passed'], error or '')
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
