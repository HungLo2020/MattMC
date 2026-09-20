"""Predeclared equipment glint changes; supplemental to full paired acceptance.

Both independently anchored phase pairs, native ownership, completed executions,
and matching simulation/geometry must be validated by the caller. Pixel deltas
alone never admit a capability.
"""
from equipment_reference import REFERENCES, require


GROUPS = {'helmet': range(0, 4), 'chestplate': range(4, 8),
          'leggings': range(8, 10), 'boots': range(10, 12)}


def moving_changes(before_frozen, before_current, after_frozen, after_current):
    sequences = (before_frozen, before_current, after_frozen, after_current)
    require(all(isinstance(items, (list, tuple)) and len(items) == 5 for items in sequences),
            'equipment temporal proof requires all five corresponding poses')
    changed = {group: False for group in GROUPS}
    frames = []
    for pose, images in enumerate(zip(*sequences)):
        require(all(image.size == (1280, 720) for image in images),
                'equipment temporal proof requires 1280x720')
        images = [image.convert('RGB') for image in images]
        probes = []
        for index, (x, y, _) in enumerate(REFERENCES['foil']['probes']):
            box = (x - 1, y - 1, x + 2, y + 2)
            pixels = [list(image.crop(box).get_flattened_data()) for image in images]
            deltas = [[[b - a for a, b in zip(old, new)]
                       for old, new in zip(pixels[side], pixels[side + 2])]
                      for side in (0, 1)]
            error = max(abs(a - b) for left, right in zip(*deltas) for a, b in zip(left, right))
            require(error <= 2, 'equipment temporal pixel deltas differ')
            visible = any(abs(a) > 4 and abs(b) > 4 and a * b > 0
                          for left, right in zip(*deltas) for a, b in zip(left, right))
            for group, indices in GROUPS.items():
                if index in indices:
                    changed[group] |= visible
            probes.append(dict(position=[x, y], deltas=deltas, delta_error=error, changed=visible))
        frames.append(dict(pose=pose, probes=probes))
    require(all(changed.values()), 'equipment temporal proof lacks visible change on every armor piece')
    return dict(passed=True, capability_admitted=False, groups_changed=changed, frames=frames)


def validate_transition(visual, reference):
    """Revalidate an originally accepted phase10000 run before comparing phase40000.

    The current phase's full report is a prerequisite at the callsite; repeat its
    paired frame checks here so this function cannot accept mere image paths.
    """
    from contextlib import ExitStack
    from pathlib import Path
    from PIL import Image
    import graphics_harness as h
    from equipment_reference import report, paired_moving_frames
    from equipment_timing_reference import paired_stepped_geometry
    from shield_static_blocking_reference import validate_execution

    require(reference is not None, 'second equipment phase requires an accepted first-phase manifest')
    reference = Path(reference)
    old = h.read_json(reference)
    require(old.get('success') is True and old.get('equipment_parity', {}).get('passed') is True,
            'equipment temporal reference must have original full acceptance')
    before = old.get('cross_repository_visual_parity', {})
    args = ['-Dmattmc.dev.graphicsAuditEquipment=foil', '-Dmattmc.dev.equipmentFoilPhase=10000',
            '-Dmattmc.dev.equipmentFoilPoseStep=8000', '-Dmattmc.dev.equipmentTickStepping=true',
            '-Dmattmc.dev.equipmentFoilTiming=true']
    replay = report(before, args)
    require(replay.get('passed') is True, 'equipment first-phase reference failed full replay')
    require(visual.get('passed') is True and isinstance(visual.get('pairs'), list)
            and len(visual['pairs']) == len(before.get('pairs', [])) == 1,
            'equipment temporal proof requires one corresponding pair per phase')
    docs = []
    for phase, pair in ((10000, before['pairs'][0]), (40000, visual['pairs'][0])):
        phase_docs = []
        for side, mode in (('baseline', 'frozen-opengl-shaders-off'),
                           ('current', 'current-rust-vulkan-shaders-off')):
            path = Path(pair[side + '_artifact'])
            validate_execution(h.read_json(path), mode)
            doc = h.deterministic_capture_document(path) or {}
            require(doc.get('captures', [{}])[0].get('screenshot') == pair[side + '_image'],
                    'equipment temporal selected image mismatch')
            phase_docs.append(doc)
        paired_moving_frames(*phase_docs, phase)
        docs.extend(phase_docs)
    require(all(doc['equipmentFixture'] == docs[0]['equipmentFixture'] for doc in docs),
            'equipment temporal fixtures differ')
    geometry = []
    for pose in range(5):
        comparisons = []
        for side in (0, 1):
            a, b = docs[side]['captures'][pose], docs[side + 2]['captures'][pose]
            at = h.read_json(Path(a['screenshot'] + '.equipment-timing.json'))
            bt = h.read_json(Path(b['screenshot'] + '.equipment-timing.json'))
            comparisons.append(paired_stepped_geometry(at, bt, a['renderedFrameIndex'],
                                                       b['renderedFrameIndex'], pose))
        geometry.append(comparisons)
    with ExitStack() as stack:
        images = [[stack.enter_context(Image.open(c['screenshot'])) for c in doc['captures']] for doc in docs]
        changes = moving_changes(*images)
    return dict(passed=True, capability_admitted=False, reference=str(reference.resolve()),
                geometry=geometry, changes=changes)
