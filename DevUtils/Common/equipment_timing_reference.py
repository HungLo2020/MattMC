"""Verify observed stopped armor clocks against exact captured native inputs.

This is timing evidence only, never equipment or animation admission.
"""
from collections import Counter
import math
from held_special_foil_reference import require, integer


def validate_stopped(receipt, frame, native=None):
    return validate_clocks(receipt,frame,native)


def validate_clocks(receipt, frame, native=None, *, phase=None, pose=0, step=4000):
    require(phase is None or type(phase) is int and phase in (10000,40000), 'unsupported equipment phase')
    require(type(step) is int and step in (4000,8000), 'unsupported equipment pose step')
    require(type(pose) is int and 0<=pose<5,'invalid equipment capture pose')
    require(integer(frame,1) and isinstance(receipt,dict)
            and receipt.get('schema')=='equipment-foil-clock-observation-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and type(receipt.get('renderedFrameIndex')) is int
            and receipt['renderedFrameIndex']==frame,'missing or stale equipment timing')
    samples=receipt.get('samples')
    require(isinstance(samples,list) and 1<=len(samples)<=4,'missing or excessive equipment clock samples')
    require(all(isinstance(s,dict) and integer(s.get('scaledTicks')) and s['scaledTicks']<=2**63-1
                for s in samples),'invalid equipment clocks')
    ticks=[s['scaledTicks'] for s in samples]
    if 'simulation' in receipt:
        validate_simulation(receipt['simulation'], pose)
    if phase is None:
        require(all(t==0 for t in ticks),'stopped equipment clock changed')
    else:
        require(type(receipt.get('requestedPhase')) is int and receipt['requestedPhase']==phase
                and type(receipt.get('requestedPoseStep',4000)) is int
                and receipt.get('requestedPoseStep',4000)==step
                and all(t>0 and (t-phase-pose*step)%330000<=512 for t in ticks),
                'equipment capture missed requested natural phase')
        require(all(min((a-b)%330000,(b-a)%330000)<=16 for a in ticks for b in ticks),
                'equipment clocks disagree within captured frame')
    if native is None:
        require(all(s.get('provider')=='frozen-armor-state' for s in samples),'Frozen clock provider mismatch')
    else:
        require(native.get('schema')=='equipment-submission-inputs-v1' and native.get('complete') is True
                and type(native.get('deterministic_rendered_frame_index')) is int
                and native['deterministic_rendered_frame_index']==frame,'native clock frame mismatch')
        observed=[]
        for s in samples:
            require(s.get('provider')=='semantic-armor' and isinstance(s.get('meshKey'),str)
                    and s['meshKey'].isdigit() and 0<int(s['meshKey'])<2**64
                    and integer(s.get('clockMillis')) and s['clockMillis']<=2**63-1
                    and type(s.get('speed')) is float and s['speed']==(0.0 if phase is None else 0.5)
                    and type(s.get('strength')) is float and s['strength']==0.5,'invalid equipment semantic clock')
            require(s['scaledTicks']==min(int(s['clockMillis']*s['speed']*8.0),2**63-1),
                    'observed equipment ticks differ from clock/options')
            observed.append((int(s['meshKey']),s['clockMillis'],s['speed'],s['strength']))
        expected=[]
        for s in native.get('semantic_instances',[]):
            f=s.get('foil')
            if f is not None:
                require(s.get('context')=='world' and f.get('kind')=='Armor','wrong native timing scope')
                expected.append((s['mesh_key'],f['clock_millis'],f['speed'],f['strength']))
        require(len(observed)==len(expected)==4 and len({s[0] for s in observed})==4
                and Counter(observed)==Counter(expected),'equipment clocks differ from accepted native inputs')
    return dict(passed=True,capability_admitted=False,frame=frame,samples=len(samples),ticks=ticks)


def validate_simulation(simulation, pose):
    require(type(pose) is int and 0 <= pose < 5 and isinstance(simulation, dict)
            and simulation.get('schema') == 'equipment-game-tick-step-v1'
            and all(simulation.get(key) is True for key in ('complete', 'clientFrozen', 'serverFrozen'))
            and type(simulation.get('pose')) is int and simulation['pose'] == pose
            and all(type(simulation.get(key)) is int and simulation[key] == pose * 10
                    for key in ('targetTicks', 'clientTicks', 'serverTicks')),
            'equipment requires matching completed vanilla simulation steps')


def paired_ticks(frozen,current):
    a,b=frozen.get('ticks'),current.get('ticks')
    require(isinstance(a,list) and isinstance(b,list) and a and b
            and all(integer(t) for t in a+b), 'missing paired equipment clocks')
    distance=max(min((x-y)%330000,(y-x)%330000) for x in a for y in b)
    require(distance<=16,'equipment captures have unequal animation phases')
    return distance


def validate_inventory_clocks(receipt, frame, *, current, phase):
    """Validate the one-view inventory preview's actual armor glint clocks."""
    require(type(current) is bool and phase in (10000, 40000), 'invalid inventory clock scope')
    require(integer(frame, 1) and isinstance(receipt, dict)
            and receipt.get('schema') == 'equipment-foil-clock-observation-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and receipt.get('renderedFrameIndex') == frame
            and receipt.get('requestedPhase') == phase
            and receipt.get('requestedPoseStep', 4000) == 4000,
            'missing or stale moving inventory timing')
    samples = receipt.get('samples')
    require(isinstance(samples, list) and len(samples) == (4 if current else 1),
            'moving inventory has the wrong clock sample count')
    ticks = []
    for sample in samples:
        require(isinstance(sample, dict) and integer(sample.get('scaledTicks'))
                and sample['scaledTicks'] > 0
                and (sample['scaledTicks'] - phase) % 330000 <= 512,
                'moving inventory missed its requested natural phase')
        if current:
            require(sample.get('provider') == 'semantic-armor-orthographic'
                    and integer(sample.get('batchSequence'), 0)
                    and integer(sample.get('clockMillis'), 0)
                    and type(sample.get('speed')) is float and sample['speed'] == 0.5
                    and type(sample.get('strength')) is float and sample['strength'] == 0.5
                    and sample['scaledTicks'] == int(sample['clockMillis'] * sample['speed'] * 8.0),
                    'invalid Current orthographic armor clock')
        else:
            require(sample == {'provider': 'frozen-armor-state', 'scaledTicks': sample['scaledTicks']},
                    'invalid Frozen inventory armor clock')
        ticks.append(sample['scaledTicks'])
    require(all(min((a-b)%330000, (b-a)%330000) <= 16 for a in ticks for b in ticks),
            'moving inventory clocks disagree within the captured frame')
    preview = receipt.get('inventoryPreview')
    require(isinstance(preview, dict) and preview.get('schema') == 'inventory-preview-inputs-v1'
            and preview.get('enabled') is True and preview.get('complete') is True
            and preview.get('viewStable') is True and preview.get('renderedFrameIndex') == frame,
            'moving inventory lacks stable capture-local preview inputs')
    return dict(passed=True, capability_admitted=False, frame=frame, ticks=ticks)


def validate_inventory_fixture(fixture):
    require(isinstance(fixture, dict) and fixture.get('schema') == 'inventory-equipment-fixture-v1'
            and fixture.get('requested') is True and fixture.get('mode') == 'foil-moving'
            and all(fixture.get(k) is True for k in ('serverApplied', 'mouseCentered', 'inventoryOpen', 'replicated', 'complete'))
            and fixture.get('speed') == 0.5 and fixture.get('strength') == 0.5,
            'moving inventory fixture is incomplete')
    equipment = fixture.get('equipment')
    expected = [('head','minecraft:leather_helmet'), ('chest','minecraft:leather_chestplate'),
                ('legs','minecraft:leather_leggings'), ('feet','minecraft:leather_boots')]
    require(isinstance(equipment, list) and len(equipment) == 4, 'moving inventory equipment count mismatch')
    for row, (slot, item) in zip(equipment, expected):
        require(isinstance(row, dict) and row.get('slot') == slot and row.get('item') == item
                and row.get('count') == 1 and row.get('foil') is True
                and row.get('dyedColor') == 0x3366CC and row.get('trim') is False,
                'moving inventory equipment differs from the authored fixture')


def compare_inventory_temporal_images(frozen_10000, current_10000, frozen_40000, current_40000):
    """Require visible two-phase inventory glint motion with matching slot deltas."""
    from PIL import ImageChops, ImageStat
    images = (frozen_10000, current_10000, frozen_40000, current_40000)
    require(all(image.size == (1280, 720) for image in images),
            'moving inventory requires four 1280x720 images')

    def delta_metrics(first, second, box):
        a = first.convert('RGB').crop(box)
        b = second.convert('RGB').crop(box)
        difference = ImageChops.difference(a, b)
        mean = ImageStat.Stat(difference).mean
        pixels = list(difference.get_flattened_data())
        return dict(mean_rgb_abs=mean, mean_absolute_rgb=sum(mean) / 3,
                    max_channel_abs=max(max(pixel) for pixel in pixels),
                    changed_pixels=sum(pixel != (0, 0, 0) for pixel in pixels),
                    pixel_count=len(pixels))

    slots = (397, 134, 451, 347)
    preview = (453, 135, 600, 345)
    frozen_slots = delta_metrics(frozen_10000, frozen_40000, slots)
    current_slots = delta_metrics(current_10000, current_40000, slots)
    frozen_preview = delta_metrics(frozen_10000, frozen_40000, preview)
    current_preview = delta_metrics(current_10000, current_40000, preview)
    require(all(result['mean_absolute_rgb'] >= 0.5 and result['changed_pixels'] >= 1000
                for result in (frozen_slots, current_slots, frozen_preview, current_preview)),
            'moving inventory did not visibly change between accepted phases')

    slot_crops = [image.convert('RGB').crop(slots) for image in images]
    frozen_pixels = list(zip(slot_crops[0].get_flattened_data(), slot_crops[2].get_flattened_data()))
    current_pixels = list(zip(slot_crops[1].get_flattened_data(), slot_crops[3].get_flattened_data()))
    errors = []
    for (f0, f1), (c0, c1) in zip(frozen_pixels, current_pixels):
        errors.extend(abs((b - a) - (d - c)) for a, b, c, d in zip(f0, f1, c0, c1))
    require(sum(errors) / len(errors) <= 0.1 and max(errors) <= 3,
            'inventory armor-slot motion differs from Frozen')
    return dict(passed=True, capability_admitted=False,
                phases=[10000, 40000], frozen_slots=frozen_slots,
                current_slots=current_slots, frozen_preview=frozen_preview,
                current_preview=current_preview,
                slot_signed_delta_mean_abs=sum(errors) / len(errors),
                slot_signed_delta_max_abs=max(errors))


def compare_inventory_temporal_paths(frozen_10000, current_10000, frozen_40000, current_40000):
    from PIL import Image
    with Image.open(frozen_10000) as f10, Image.open(current_10000) as c10, \
            Image.open(frozen_40000) as f40, Image.open(current_40000) as c40:
        return compare_inventory_temporal_images(f10, c10, f40, c40)


def current_inventory_phase_targets(path, phase):
    """Derive one Frozen screenshot selector from an owned Current inventory frame."""
    import graphics_harness as h
    from pathlib import Path
    path = Path(path)
    validate_current_timing_execution(h.read_json(path))
    doc = h.deterministic_capture_document(path) or {}
    validate_inventory_fixture(doc.get('inventoryEquipmentFixture'))
    captures = doc.get('captures')
    require(isinstance(captures, list) and len(captures) == 1, 'moving inventory requires one capture')
    capture = captures[0]
    image = Path(capture.get('screenshot', ''))
    frame = capture.get('renderedFrameIndex')
    require(integer(frame, 1) and image.is_file(), 'moving inventory capture is missing')
    ack = h.read_json(image.with_name('capture_request_' + image.stem + '.ack.json'))
    require(ack.get('status') == 'captured' and ack.get('captureMethod') == 'rust-vulkan-final-output'
            and ack.get('renderedFrameIndex') == frame and ack.get('screenshot') == str(image),
            'moving inventory requires the selected Rust final output')
    presentation = ack.get('wholeFramePresentationCorrelation', {})
    gameplay_frame = presentation.get('gameplayFrameId')
    require(integer(gameplay_frame, 1)
            and presentation.get('acquiredSwapchainImage') == presentation.get('presentedSwapchainImage'),
            'moving inventory lacks same-image presentation evidence')
    owner = h.read_json(image.parent.parent / 'whole_frame_gameplay_attachments'
                        / f'gameplay-correlation-frame-{gameplay_frame}.json')
    require(owner.get('deterministic_rendered_frame_index') == frame
            and owner.get('gameplay_frame_id') == gameplay_frame
            and owner.get('same_acquired_presented_image') is True
            and owner.get('rust_whole_frame_presenter') is True
            and owner.get('java_vulkan_frame_execution') is False
            and owner.get('gui_entity_preview_items') == 1
            and owner.get('gui_entity_preview_batches') == 13
            and owner.get('gui_entity_preview_draws') == 14
            and owner.get('gui_entity_preview_material_mask') == 400
            and owner.get('gui_entity_preview_vertices') == 1008
            and owner.get('gui_entity_preview_indices') == 1512,
            'moving inventory lacks exact Rust entity-preview ownership')
    receipt = h.read_json(Path(str(image) + '.equipment-timing.json'))
    observed = validate_inventory_clocks(receipt, frame, current=True, phase=phase)
    ticks = [value % 330000 for value in observed['ticks']]
    return dict(centers=[(min(ticks)+max(ticks))//2], ranges=[[min(ticks),max(ticks)]])


def validate_zombie_pose(receipt, frame):
    """Validate bounded observations of the equipped fixture's actual model pose.

    Multiple setupAnim calls are expected, but each must describe the same
    fixture pose. This is input evidence, not a pixel or capability verdict.
    """
    require(isinstance(receipt, dict)
            and receipt.get('schema') == 'equipment-foil-clock-observation-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and integer(frame, 1) and type(receipt.get('renderedFrameIndex')) is int
            and receipt['renderedFrameIndex'] == frame
            and receipt.get('zombiePosesComplete') is True,
            'missing or stale equipment model pose')
    poses = receipt.get('zombiePoses')
    require(isinstance(poses, list) and 1 <= len(poses) <= 16,
            'missing or excessive equipment model poses')
    def finite(value):
        return type(value) in (int, float) and math.isfinite(value)
    for pose in poses:
        basic = {'ageInTicks', 'attackTime', 'aggressive', 'leftArm', 'rightArm'}
        require(isinstance(pose, dict) and set(pose) in (basic, basic | {'remainingParts'}),
            'invalid equipment model pose fields')
        require(finite(pose['ageInTicks']) and pose['ageInTicks'] >= 0
                and finite(pose['attackTime']) and pose['attackTime'] == 0
                and pose['aggressive'] is False,
                'invalid equipped zombie animation state')
        for name in ('leftArm', 'rightArm'):
            require(isinstance(pose[name], list) and len(pose[name]) == 9
                    and all(finite(v) for v in pose[name]),
                    'invalid equipment arm transform')
        if 'remainingParts' in pose:
            parts = pose['remainingParts']
            require(isinstance(parts, dict) and set(parts) == {'head', 'body', 'leftLeg', 'rightLeg', 'root'}
                    and all(isinstance(v, list) and len(v) == 9 and all(finite(x) for x in v)
                            for v in parts.values()), 'invalid complete equipment model parts')
        require(pose == poses[0], 'equipment model poses disagree within captured frame')
    if 'simulation' in receipt:
        simulation = receipt['simulation']
        require(isinstance(simulation, dict), 'invalid equipment simulation observation')
        validate_simulation(simulation, simulation.get('pose'))
        require(poses[0]['ageInTicks'] == simulation['targetTicks'] + 1,
                'equipment pose differs from normal stopped-tick interpolation')
    return dict(passed=True, capability_admitted=False, frame=frame,
                samples=len(poses), pose=poses[0])


def paired_zombie_poses(frozen, current, frozen_frame, current_frame):
    a = validate_zombie_pose(frozen, frozen_frame)['pose']
    b = validate_zombie_pose(current, current_frame)['pose']
    # Compare the actual geometry inputs exactly. Age is retained as diagnostic
    # evidence; equivalent periodic poses need not have equal entity ages.
    require(a.keys() == b.keys() and all(a[key] == b[key] for key in a if key != 'ageInTicks'),
            'equipment captures have unequal model poses')
    return dict(passed=True, capability_admitted=False,
                frozen_age=a['ageInTicks'], current_age=b['ageInTicks'],
                observed_parts=7 if 'remainingParts' in a else 2)


def validate_entity_transforms(receipt, frame):
    require(isinstance(receipt, dict) and receipt.get('enabled') is True
            and receipt.get('schema') == 'equipment-foil-clock-observation-v1'
            and integer(frame,1) and type(receipt.get('renderedFrameIndex')) is int
            and receipt['renderedFrameIndex'] == frame
            and receipt.get('entityTransformsComplete') is True,
            'missing or stale equipment entity transforms')
    entries = receipt.get('entityTransforms')
    require(isinstance(entries,list) and 1 <= len(entries) <= 16, 'invalid equipment transform count')
    for entry in entries:
        require(isinstance(entry,dict) and set(entry) == {'modelView','normal','state','lightCoords'}
                and integer(entry['lightCoords']), 'invalid equipment entity transform fields')
        for key,size in (('modelView',16),('normal',9),('state',8)):
            values=entry[key]
            require(isinstance(values,list) and len(values)==size
                    and all(type(v) in (int,float) and math.isfinite(v) for v in values),
                    'invalid equipment entity transform values')
    return entries


def paired_stepped_geometry(frozen, current, frozen_frame, current_frame, pose):
    """Require full, equal geometry for the authored moving equipment fixture.

    Duplicate layer observations are allowed only when all geometry inputs agree.
    This supplements clocks and native ownership; it cannot admit rendering.
    """
    for receipt in (frozen, current):
        validate_simulation(receipt.get('simulation'), pose)
    models = paired_zombie_poses(frozen, current, frozen_frame, current_frame)
    require(models['observed_parts'] == 7, 'moving equipment requires all seven model parts')
    baseline = validate_entity_transforms(frozen, frozen_frame)
    candidate = validate_entity_transforms(current, current_frame)
    require(all(entry == baseline[0] for entry in baseline + candidate),
            'equipment captures have unequal entity transforms or lighting')
    return dict(passed=True, capability_admitted=False, pose=pose, models=models,
                frozen_transform_count=len(baseline), current_transform_count=len(candidate))


def frozen_phase_centers(path,phase,step=4000):
    """Capture selection scalars from a completed, independently rendered baseline."""
    import graphics_harness as h
    from equipment_reference import validate_document
    from shield_static_blocking_reference import validate_execution
    from pathlib import Path
    path=Path(path)
    validate_execution(h.read_json(path),'frozen-opengl-shaders-off')
    doc=h.deterministic_capture_document(path) or {}
    captures=validate_document(doc,'foil',phase=phase)
    centers=[]
    for pose,capture in enumerate(captures):
        receipt=h.read_json(Path(capture['screenshot']+'.equipment-timing.json'))
        observed=validate_clocks(receipt,capture['renderedFrameIndex'],phase=phase,pose=pose,step=step)
        centers.append(observed['ticks'][0]%330000)
    return centers


def validate_current_timing_execution(artifact):
    """Runtime prerequisites for a diagnostic, never aggregate parity acceptance.

    The aggregate `complete` flag also includes the moving pixel gate, which
    cannot pass before the Frozen reference exists. Do not alter that flag.
    Each selected native capture is checked separately by current_phase_centers.
    """
    health = artifact.get('validation', {})
    require(artifact.get('mode', {}).get('name') == 'current-rust-vulkan-shaders-off'
            and artifact.get('tool') == 'capture'
            and type(artifact.get('capture', {}).get('exit_code')) is int
            and artifact['capture']['exit_code'] == 0
            and all(health.get(k) is True for k in (
                'crash_free', 'device_loss_free', 'vulkan_validation_clean',
                'deterministic_capture_complete', 'workload_entered',
                'frame_sampler_validity_passed', 'frame_samples_complete', 'subsystem_complete'))
            and all(health.get(k) is False for k in ('rss_guard_triggered', 'orphan_process_detected')),
            'equipment timing requires clean completed diagnostic execution')
    memory = artifact.get('metrics', {}).get('rss_and_native_memory', {}).get('client_observation', {})
    require(memory.get('complete') is True and memory.get('provider') == 'linux-proc-status'
            and all(type(memory.get(k)) is int and memory[k] > 0
                    for k in ('sample_count', 'peak_rss_kb', 'peak_hwm_kb')),
            'equipment timing requires complete process-memory evidence')
    return dict(passed=True, capability_admitted=False, purpose='diagnostic capture selection only')


def current_phase_centers(path, phase, step=4000):
    return current_phase_targets(path, phase, step)["centers"]


def current_phase_targets(path, phase, step=4000, *, stepped=False, per_face=False):
    """Select Frozen screenshots using observed, owned Current draw clocks.

    Current supplies timing only. Frozen remains the pixel correctness oracle;
    the eventual pair must still verify every clock and actual model pose.
    """
    import graphics_harness as h
    from equipment_reference import validate_document, validate_native
    from pathlib import Path
    path = Path(path)
    validate_current_timing_execution(h.read_json(path))
    doc = h.deterministic_capture_document(path) or {}
    trim = doc.get('equipmentFixture',{}).get('trimVariant')
    captures = validate_document(doc, 'foil', phase=phase, stepped=stepped, trim=trim)
    centers = []
    ranges = []
    for pose, capture in enumerate(captures):
        image = Path(capture['screenshot'])
        ack = h.read_json(image.with_name('capture_request_' + image.stem + '.ack.json'))
        require(ack.get('status') == 'captured' and ack.get('captureMethod') == 'rust-vulkan-final-output'
                and ack.get('screenshot') == str(image), 'equipment timing requires actual Rust capture')
        native = h.read_json(Path(str(image) + '.equipment-inputs.json'))
        frame = native.get('gameplay_frame_id')
        require(integer(frame, 1), 'equipment timing requires selected native frame')
        owner = h.read_json(image.parent.parent / 'whole_frame_gameplay_attachments'
                            / f'gameplay-correlation-frame-{frame}.json')
        receipt = h.read_json(Path(str(image) + '.equipment-timing.json'))
        validate_native(native, owner, ack, capture, 'foil', phase=phase, per_face=per_face,
                        trim=trim, trim_sources=receipt.get('trimSources') if trim is not None else None)
        if stepped:
            validate_simulation(receipt.get('simulation'), pose)
        observed = validate_clocks(receipt, capture['renderedFrameIndex'], native, phase=phase, pose=pose, step=step)
        validate_zombie_pose(receipt, capture['renderedFrameIndex'])
        ticks = [t % 330000 for t in observed['ticks']]
        centers.append((min(ticks) + max(ticks)) // 2)
        ranges.append([min(ticks),max(ticks)])
    return dict(centers=centers,ranges=ranges)
