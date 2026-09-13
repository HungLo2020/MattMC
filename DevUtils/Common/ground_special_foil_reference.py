"""Private ground SPECIAL oracles from inspected Frozen OpenGL r650/r655."""
from pathlib import Path
from PIL import Image, ImageChops, ImageStat
from held_special_foil_reference import require, validate_native_frame, validate_decal_draw, PACK_SHA256

SCOPES = {
    'minecraft:clock': dict(vertices=88,indices=132,box=(344,256,472,388),sprite='minecraft:item/clock_00',probes=(
        (378,280,(144,111,27)),(409,273,(154,131,54)),(446,307,(147,113,27)),(400,302,(157,153,16)),
        (417,337,(139,107,24)),(374,316,(54,63,105)),(407,352,(146,139,60)),(430,361,(105,63,20)))),
    'minecraft:compass': dict(vertices=72,indices=108,box=(496,272,620,376),sprite='minecraft:item/compass_31',probes=(
        (534,290,(53,49,43)),(580,294,(56,51,43)),(514,317,(35,31,31)),(555,309,(52,48,40)),
        (567,325,(153,31,25)),(572,335,(150,28,24)),(546,347,(128,125,122)),(585,351,(29,26,24))))}


RECOVERY_SCOPES = {
    'minecraft:clock': SCOPES['minecraft:clock'],
    'minecraft:recovery_compass': dict(vertices=88,indices=132,box=(496,272,620,376),
        sprite='minecraft:item/recovery_compass_02',probes=(
            (534,290,(92,88,82)),(560,282,(113,109,101)),(605,316,(92,87,81)),
            (530,340,(56,52,51)),(554,344,(104,102,98)),(558,306,(34,35,26)),
            (547,325,(45,134,134)),(583,306,(35,36,26))))}


# Each variant is an inspected Frozen source, not an interchangeable pixel oracle.
RECOVERY_VARIANTS = {
    'minecraft:item/recovery_compass_02': RECOVERY_SCOPES,
    'minecraft:item/recovery_compass_01': {
        'minecraft:clock': SCOPES['minecraft:clock'],
        'minecraft:recovery_compass': dict(vertices=88,indices=132,box=(496,272,620,376),
            sprite='minecraft:item/recovery_compass_01',probes=(
                (534,290,(92,88,82)),(560,282,(113,109,101)),(605,316,(92,87,81)),
                (530,340,(56,52,51)),(554,344,(104,102,98)),(558,306,(34,35,26)),
                (561,310,(49,137,135)),(583,306,(35,36,26))))}}


def recovery_scopes(fixture):
    sprites={q['sprite'] for q in fixture['items'][1]['extracted']['sources']['quads']}
    require(len(sprites)==1 and next(iter(sprites)) in RECOVERY_VARIANTS,
            'ground recovery source has no inspected Frozen oracle')
    return RECOVERY_VARIANTS[next(iter(sprites))]


def recovery_item_semantic(semantics,item):
    """Identify equal-size meshes by vanilla ground pose and actual fixture position.

    Frozen item/generated ground: scale .5, centered model, no X/Z translation;
    ItemEntity rotates Y by age/20 + bob. Camera X/Z is fixed by this oracle.
    Never assign identity by submission order or candidate pixel location.
    """
    import math
    from held_special_foil_reference import vector
    angle=item['extracted']['age']/20+item['extracted']['bobOffset']
    c,s=math.cos(angle),math.sin(angle)
    expected=[c*.5,0,-s*.5,0,0,.5,0,0,s*.5,0,c*.5,0]
    x=item['position'][0]-150.5-.25*(c+s)
    z=item['position'][2]-530.5+.25*(s-c)
    matches=[]
    for semantic in semantics:
        model=semantic.get('model_pose')
        if (vector(model,16) and all(abs(a-b)<=2e-6 for a,b in zip(model,expected))
                and abs(model[12]-x)<=2e-6 and abs(model[14]-z)<=2e-6):
            matches.append(semantic)
    require(len(matches)==1,'ground recovery item lacks unique fixture-matched native pose')
    return matches[0]


def pixels(frozen,current,phase=None,*,recovery=False,recovery_sprite="minecraft:item/recovery_compass_02"):
    require(phase is None or type(phase) is int and phase in (10000,40000), "unsupported ground pixel phase")
    require(frozen.size == current.size == (1280,720),'ground foil requires fixed1280x720 viewport')
    images=[im.convert('RGB') for im in (frozen,current)];rows=[]
    require(not recovery or recovery_sprite in RECOVERY_VARIANTS, "unknown recovery pixel source")
    for name,scope in (RECOVERY_VARIANTS[recovery_sprite] if recovery else SCOPES).items():
        probes=[]
        for x,y,expected in scope['probes']:
            values=[im.getpixel((x,y)) for im in images]
            presence=max(abs(a-b) for value in values for a,b in zip(value,expected))
            error=presence if phase is None else max(abs(a-b) for a,b in zip(*values))
            probes.append(dict(position=[x,y],expected=expected,frozen=values[0],current=values[1],max_channel_error=error,base_material_visible=phase is None or presence<=64,passed=error<=1 and (phase is None or presence<=64)))
        mean=ImageStat.Stat(ImageChops.difference(*[im.crop(scope['box']) for im in images])).mean
        rows.append(dict(item=name,probes=probes,box=scope['box'],mean_rgb_error=mean,passed=all(p['passed'] for p in probes) and max(mean)<=1))
    return dict(passed=all(row['passed'] for row in rows),items=rows)


def native_inputs(receipt,owner,ack,fixture,*,phase=None,timing=None,recovery=False):
    import graphics_harness as h
    validate_native_frame(receipt,owner,ack)
    clocks={}
    if phase is not None:
        from ground_foil_timing_reference import observed_world,matches_native,phase_ticks
        samples=observed_world(timing,receipt['deterministic_rendered_frame_index'],'semantic-world')
        phase_ticks(samples,phase);matches_native(samples,receipt,receipt['deterministic_rendered_frame_index'])
        clocks={int(sample['meshKey']):sample['scaledTicks'] for sample in samples}

    require(h.dropped_item_foil_fixture_valid(fixture,special=True,recovery=recovery),'missing actual ground fixture/source receipt')
    semantics,draws=receipt.get('semantic_instances'),receipt.get('draws')
    require(isinstance(semantics,list) and len(semantics)==2 and isinstance(draws,list) and len(draws)==2,
            'ground foil requires exactly two world semantics and two actual draws')
    require(all(isinstance(v,dict) for v in semantics+draws),'malformed ground native records')
    require(len({(s.get('mesh_key'),s.get('mesh_generation')) for s in semantics})==2,'ambiguous ground mesh identities')
    rows=[];used=[]
    faces={0x8100:'down',0x7f00:'up',0x810000:'north',0x7f0000:'south',0x81:'west',0x7f:'east'}
    for item in fixture['items']:
        scope=(recovery_scopes(fixture) if recovery else SCOPES)[item['item']];sources=item['extracted']['sources']
        require(sources['renderedFrameIndex']==receipt['deterministic_rendered_frame_index'],'ground sources are not from selected native frame')
        require({q['sprite'] for q in sources['quads']}=={scope['sprite']},'ground source sprite differs from Frozen oracle')
        if recovery:
            identified=recovery_item_semantic(semantics,item)
            candidates=[d for d in draws if (d.get('mesh_key'),d.get('mesh_generation')) ==
                        (identified.get('mesh_key'),identified.get('mesh_generation'))]
        else:
            candidates=[d for d in draws if d.get('vertex_count')==scope['vertices']]
        require(len(candidates)==1,'ground item lacks unique actual geometry')
        draw=candidates[0];matched=[s for s in semantics if s.get('mesh_key')==draw.get('mesh_key') and s.get('mesh_generation')==draw.get('mesh_generation')]
        require(len(matched)==1,'ground draw lacks unique semantic mesh incarnation')
        semantic=matched[0];used.append(semantic['mesh_key'])
        result=validate_decal_draw(semantic,draw,scope,first_person=False,expected_ticks=clocks[semantic["mesh_key"]] if phase is not None else 0,phase=phase)
        vertices=draw['first_source_vertices'];positions=[v for vertex in vertices for v in vertex['position']]
        face=faces[vertices[0]['normal_packed']]
        require(any(q['face']==face and q['positions']==positions for q in sources['quads']),
                'ground native sampled geometry differs from actual resolved source quad')
        rows.append(dict(result,item=item['item']))
    require(len(set(used))==2,'ground items cannot share one semantic witness')
    return dict(passed=True,items=rows,submission=receipt['gal_submission_id'],correlation=receipt['correlation_id'])


def check_pair(pair,phase=None,*,recovery=False):
    import graphics_harness as h
    docs=[];timings=[]
    for key,image_key,mode in (('baseline_artifact','baseline_image','frozen-opengl-shaders-off'),
                                ('current_artifact','current_image','current-rust-vulkan-shaders-off')):
        path=Path(pair[key]);artifact=h.read_json(path);doc=h.deterministic_capture_document(path) or {};docs.append(doc)
        require(artifact.get('mode',{}).get('name')==mode and type(artifact.get('capture',{}).get('exit_code')) is int
                and artifact['capture']['exit_code']==0,'ground foil requires actual successful Rust Vulkan/Frozen OpenGL captures')
        health=artifact.get('validation',{})
        require(all(health.get(k) is True for k in ('complete','crash_free','device_loss_free'))
                and all(health.get(k) is False for k in ('rss_guard_triggered','orphan_process_detected')),'unhealthy ground capture')
        if key=='current_artifact':require(health.get('vulkan_validation_clean') is True,'ground Vulkan validation failed')
        meta=h.read_key_values(h.latest_capture_meta_path(path.parent/'capture'))
        require(meta.get('forced_option_guiScale')=='3' and meta.get('gui_resource_pack_scenario')==('special-item-foil-pattern' if phase is None else 'special-item-foil-moving')
                and meta.get('gui_resource_pack_mattmc-special-item-foil-pattern_sha256')==PACK_SHA256,'ground pack differs from oracle')
        reload=doc.get('worldResourceReload',{})
        require(reload.get('requested') is True and reload.get('complete') is True
                and reload.get('selectedAtCapture')==['vanilla','file/mattmc-special-item-foil-pattern'],'ground requires actual reload')
        captures=doc.get('captures',[])
        require(len(captures)==1 and doc.get('cameraType')=='FIRST_PERSON' and doc.get('selectedHotbarSlot')==1,'ground requires one first-person apple-control capture')
        capture=captures[0]
        require(capture.get('screenshot')==pair[image_key] and capture.get('poseName')=='initial' and capture.get('gameTime')==6000
                and capture.get('position')==dict(x=150.5,y=100.0,z=530.5) and capture.get('observedYaw')==105 and capture.get('observedPitch')==10,
                'ground world/camera differs from Frozen oracle')
        require(h.dropped_item_foil_fixture_valid(doc.get('droppedItemFoilFixture'),special=True,recovery=recovery),'invalid ground fixture/source receipt')
        if phase is not None:
            from ground_foil_timing_reference import observed_world
            timings.append(observed_world(h.read_json(Path(pair[image_key]+'.foil-timing.json')),capture['renderedFrameIndex'],
                'frozen-world-state' if key=='baseline_artifact' else 'semantic-world'))
    require(h.deterministic_visual_fixture_equivalence(*docs)['status']=='passed','ground fixture/source frames differ')
    current=Path(pair['current_image']);receipt=h.read_json(Path(str(current)+'.decal-inputs.json'))
    owner=h.read_json(Path(pair['current_artifact']).parent/'capture'/'whole_frame_gameplay_attachments'/f"gameplay-correlation-frame-{receipt.get('gameplay_frame_id')}.json")
    ack=h.read_json(current.with_name('capture_request_01_initial.ack.json'))
    require(ack.get('status')=='captured' and ack.get('captureMethod')=='rust-vulkan-final-output' and ack.get('screenshot')==str(current),
            'ground requires selected renderer-owned output')
    native=native_inputs(receipt,owner,ack,docs[1]['droppedItemFoilFixture'],phase=phase,recovery=recovery,
                        timing=h.read_json(Path(str(current)+'.foil-timing.json')) if phase is not None else None)
    phases=None
    if phase is not None:
        from ground_foil_timing_reference import paired_ticks
        phases=paired_ticks(*timings,phase)
    sprite=recovery_scopes(docs[1]['droppedItemFoilFixture'])['minecraft:recovery_compass']['sprite'] if recovery else None
    with Image.open(pair['baseline_image']) as f,Image.open(current) as c:
        pixel_result=pixels(f,c,phase,recovery=recovery,recovery_sprite=sprite)
    return dict(passed=pixel_result['passed'],capability_admitted=False,native=native,pixels=pixel_result,phases=phases)


def moving_change(before_frozen,before_current,after_frozen,after_current,*,recovery=False,
                  recovery_sprite="minecraft:item/recovery_compass_02"):
    before=pixels(before_frozen,before_current,10000,recovery=recovery,recovery_sprite=recovery_sprite)
    after=pixels(after_frozen,after_current,40000,recovery=recovery,recovery_sprite=recovery_sprite)
    require(before['passed'] and after['passed'],'ground temporal change needs two passing pixel pairs')
    results=[]
    for old,new in zip(before['items'],after['items']):
        probes=[]
        for a,b in zip(old['probes'],new['probes']):
            delta=[[b[key][c]-a[key][c] for c in range(3)] for key in ('frozen','current')]
            error=max(abs(x-y) for x,y in zip(*delta))
            changed=any(abs(x)>4 and abs(y)>4 and x*y>0 for x,y in zip(*delta))
            probes.append(dict(position=a['position'],deltas=delta,delta_error=error,changed=changed,passed=error<=2))
        groups=({'rim':(0,1,2,4,6,7),'yellow_face':(3,),'blue_face':(5,)} if old['item']=='minecraft:clock' else
                {'rim':(0,1,2,3,4),'dark_face':(5,7),'cyan_needle':(6,)} if recovery else
                {'rim':(0,1,2,6,7),'dark_face':(3,),'red_needle':(4,5)})
        changes={key:any(probes[i]['changed'] for i in indexes) for key,indexes in groups.items()}
        results.append(dict(item=old['item'],groups_changed=changes,probes=probes,
                            passed=all(changes.values()) and all(p['passed'] for p in probes)))
    return dict(passed=all(r['passed'] for r in results),items=results)
