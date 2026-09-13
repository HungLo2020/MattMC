"""Frozen-derived stopped equipment pixel and fixture checks.

These checks are prerequisites only: they never admit equipment rendering.
Native draw/upload ownership, reload, execution and presentation must be
verified separately before integration. References were inspected Frozen-first
in r686 (base) and r687 (enchanted), with identical preselected coordinates.
"""
from PIL import Image, ImageChops, ImageStat

REGION = (574, 277, 706, 519)
REFERENCES = {'base': {'probes': [(610, 290,
                      [[17, 72, 66], [17, 72, 66], [93, 93, 94], [17, 72, 66], [17, 72, 66], [93, 93, 94],
                       [17, 72, 66], [17, 72, 66], [93, 93, 94]]),
                     (665, 290,
                      [[17, 82, 79], [17, 82, 79], [17, 82, 79], [17, 82, 79], [17, 82, 79], [17, 82, 79],
                       [17, 82, 79], [17, 82, 79], [17, 82, 79]]),
                     (612, 305,
                      [[18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70],
                       [18, 76, 70], [18, 76, 70], [18, 76, 70]]),
                     (666, 305,
                      [[18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70], [18, 76, 70],
                       [18, 76, 70], [18, 76, 70], [18, 76, 70]]),
                     (583, 340,
                      [[40, 173, 157], [40, 173, 157], [40, 173, 157], [40, 173, 157], [40, 173, 157],
                       [40, 173, 157], [40, 173, 157], [40, 173, 157], [40, 173, 157]]),
                     (694, 340,
                      [[66, 209, 192], [40, 173, 157], [40, 173, 157], [43, 184, 168], [40, 173, 157],
                       [40, 173, 157], [43, 184, 168], [40, 173, 157], [40, 173, 157]]),
                     (620, 415,
                      [[17, 82, 80], [17, 82, 80], [17, 82, 80], [17, 72, 66], [17, 72, 66], [17, 72, 66],
                       [17, 72, 66], [17, 72, 66], [17, 72, 66]]),
                     (660, 415,
                      [[17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66],
                       [17, 72, 66], [17, 72, 66], [17, 72, 66]]),
                     (620, 449,
                      [[39, 89, 83], [39, 89, 83], [39, 89, 83], [39, 89, 83], [39, 89, 83], [39, 89, 83],
                       [27, 86, 79], [27, 86, 79], [27, 86, 79]]),
                     (660, 449,
                      [[40, 89, 84], [40, 89, 84], [40, 89, 84], [28, 87, 80], [28, 87, 80], [28, 87, 80],
                       [28, 87, 80], [28, 87, 80], [28, 87, 80]]),
                     (622, 500,
                      [[27, 86, 79], [27, 86, 79], [27, 86, 79], [27, 86, 79], [27, 86, 79], [27, 86, 79],
                       [27, 86, 79], [27, 86, 79], [27, 86, 79]]),
                     (659, 500,
                      [[28, 87, 80], [28, 87, 80], [28, 87, 80], [28, 87, 80], [28, 87, 80], [28, 87, 80],
                       [28, 87, 80], [28, 87, 80], [28, 87, 80]])],
          'sha256': '90013fd361627831a647ee008c5c7b05a204e6fa2436b86b7289d3ab66970943'},
 'foil': {'probes': [(610, 290,
                      [[17, 72, 68], [17, 72, 68], [93, 93, 96], [17, 72, 68], [17, 72, 68], [93, 93, 96],
                       [17, 72, 68], [17, 72, 68], [93, 93, 96]]),
                     (665, 290,
                      [[17, 82, 82], [17, 82, 82], [17, 82, 82], [17, 82, 82], [17, 82, 82], [17, 82, 82],
                       [17, 82, 82], [17, 82, 82], [17, 82, 83]]),
                     (612, 305,
                      [[18, 76, 73], [18, 76, 73], [18, 76, 73], [18, 76, 73], [18, 76, 73], [18, 76, 73],
                       [18, 76, 73], [18, 76, 73], [18, 76, 73]]),
                     (666, 305,
                      [[19, 76, 75], [19, 76, 75], [19, 76, 75], [19, 76, 75], [19, 76, 75], [19, 76, 75],
                       [19, 76, 76], [19, 76, 76], [19, 76, 76]]),
                     (583, 340,
                      [[40, 173, 158], [40, 173, 158], [40, 173, 158], [40, 173, 158], [40, 173, 158],
                       [40, 173, 158], [40, 173, 158], [40, 173, 158], [40, 173, 158]]),
                     (694, 340,
                      [[66, 209, 193], [40, 173, 158], [40, 173, 158], [43, 184, 169], [40, 173, 158],
                       [40, 173, 158], [43, 184, 169], [40, 173, 158], [40, 173, 158]]),
                     (620, 415,
                      [[17, 82, 80], [17, 82, 80], [17, 82, 80], [17, 72, 66], [17, 72, 66], [17, 72, 66],
                       [17, 72, 66], [17, 72, 66], [17, 72, 66]]),
                     (660, 415,
                      [[17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66], [17, 72, 66],
                       [17, 72, 66], [17, 72, 66], [17, 72, 66]]),
                     (620, 449,
                      [[40, 89, 89], [40, 89, 89], [40, 89, 88], [40, 89, 89], [40, 89, 88], [40, 89, 88],
                       [28, 86, 84], [28, 86, 84], [28, 86, 84]]),
                     (660, 449,
                      [[41, 89, 89], [41, 89, 90], [41, 89, 90], [29, 87, 85], [29, 87, 85], [29, 87, 86],
                       [29, 87, 85], [29, 87, 85], [29, 87, 85]]),
                     (622, 500,
                      [[27, 86, 82], [27, 86, 82], [27, 86, 82], [27, 86, 82], [27, 86, 82], [27, 86, 82],
                       [27, 86, 82], [27, 86, 82], [27, 86, 82]]),
                     (659, 500,
                      [[28, 87, 83], [28, 87, 83], [28, 87, 83], [28, 87, 83], [28, 87, 83], [28, 87, 83],
                       [28, 87, 83], [28, 87, 83], [28, 87, 83]])],
          'sha256': '294877b9cf6211aa291381bd7866588fd7348ca2bbb5060e3be05eb9db4f43ad'}}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_fixture(receipt, mode, *, phase=None, stepped=False, trim=None, material=None, age=None):
    require(type(stepped) is bool and (not stepped or phase is not None), "invalid stepped equipment scope")
    require(mode in REFERENCES, "unsupported equipment scope")
    require(phase is None or type(phase) is int and phase in (10000,40000) and mode=='foil',
            'unsupported moving equipment fixture')
    expected = dict(schema="equipped-zombie-stopped-v1" if phase is None else "equipped-zombie-moving-v1", requested=True, mode=mode,
        ready=True, speed=0.0 if phase is None else 0.5, strength=0.5, complete=True,
        x=146.6949012917378, y=99.57557514877936, z=529.4806875161336,
        yaw=-75.9375 if phase is None or stepped else -75.0, bodyYaw=-75.9375, headYaw=-75.9375,
        equipment=[dict(slot=slot,item="minecraft:diamond_"+item,count=1,foil=mode=="foil")
            for slot,item in (("head","helmet"),("chest","chestplate"),
                              ("legs","leggings"),("feet","boots"))])
    require(material is None or material == 'leather-dyed' and phase == 10000
            and mode == 'foil' and stepped and trim is None, 'unsupported equipment material fixture')
    require(age is None or age == 'baby' and material == 'leather-dyed' and phase == 10000
            and mode == 'foil' and stepped and trim is None, 'unsupported equipment age fixture')
    if age is not None:
        expected.update(ageVariant=age,isBaby=True)
    if material is not None:
        expected['materialVariant'] = material
        for piece in expected['equipment']:
            piece['item'] = piece['item'].replace('diamond_', 'leather_')
            piece['dyedColor'] = 0x3366CC
    require(trim is None or trim in ('gold-spire','gold-spire-decal') and phase==10000 and mode=='foil' and stepped,
            'unsupported trimmed equipment fixture')
    if trim is not None:
        expected['trimVariant']=trim
        for piece in expected['equipment']:
            piece['trim']=dict(material='minecraft:gold',patternAsset='minecraft:spire',decal=trim=='gold-spire-decal',patternKind='inline' if trim=='gold-spire-decal' else 'registry')
    def exact(actual, wanted):
        if type(actual) is not type(wanted): return False
        if isinstance(wanted, dict):
            return actual.keys() == wanted.keys() and all(exact(actual[k],v) for k,v in wanted.items())
        if isinstance(wanted, list):
            return len(actual)==len(wanted) and all(exact(a,b) for a,b in zip(actual,wanted))
        return actual == wanted
    require(exact(receipt, expected), "equipment fixture differs from inspected scope")


def compare_images(frozen, current, mode, *, phase=None, pose=0, trim=None):
    require(mode in REFERENCES, "unsupported equipment scope")
    probes = REFERENCES[mode]['probes']
    if phase is not None:
        from equipment_moving_anchors import MOVING_REFERENCES
        require(mode == 'foil' and type(phase) is int and phase in MOVING_REFERENCES
                and type(pose) is int and 0 <= pose < 5, 'uninspected moving equipment pixels')
        probes = MOVING_REFERENCES[phase][pose]['probes']
    if trim is not None:
        from equipment_trim_anchors import TRIM_REFERENCES
        require(trim in ('gold-spire','gold-spire-decal') and mode=='foil' and type(phase) is int and phase==10000
                and type(pose) is int and 0<=pose<5, 'uninspected trim pixels')
        if trim=='gold-spire':
            from equipment_ordinary_trim_anchors import ORDINARY_TRIM_REFERENCES
            probes=ORDINARY_TRIM_REFERENCES[pose]['probes']
        else:
            probes=TRIM_REFERENCES[pose]['probes']
    require(frozen.size == current.size == (1280,720), "equipment requires 1280x720")
    frozen, current = frozen.convert("RGB"), current.convert("RGB")
    errors = []
    for x,y,expected in probes:
        box=(x-1,y-1,x+2,y+2)
        baseline=list(frozen.crop(box).getdata())
        candidate=list(current.crop(box).getdata())
        require(all(abs(a-b)<=2 for pixel,ref in zip(baseline,expected) for a,b in zip(pixel,ref)),
                "Frozen equipment anchor changed")
        error=max(abs(a-b) for pixel,ref in zip(candidate,baseline) for a,b in zip(pixel,ref))
        require(error<=2, "equipment probe mismatch")
        errors.append(error)
    means=ImageStat.Stat(ImageChops.difference(frozen.crop(REGION),current.crop(REGION))).mean
    require(all(value<=2 for value in means), "equipment region mismatch")
    return dict(passed=True, capability_admitted=False, mode=mode, probe_errors=errors, region_mean_rgb=means)


def compare_paths(frozen, current, mode, *, phase=None, pose=0, trim=None):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        return compare_images(baseline,candidate,mode,phase=phase,pose=pose,trim=trim)


def validate_native(receipt, owner, ack, capture, mode, *, phase=None, per_face=False, trim=None, trim_sources=None):
    """Validate stopped world equipment; never substitutes for paired gameplay."""
    import math
    import re
    from collections import Counter
    from shield_static_blocking_reference import validate_owner
    from held_special_foil_reference import integer, vector
    require(mode in REFERENCES, 'unsupported equipment native scope')
    require(type(per_face) is bool, 'invalid equipment lighting scope')
    require(trim is None and trim_sources is None or trim in ('gold-spire','gold-spire-decal')
            and mode=='foil' and phase==10000 and per_face and isinstance(trim_sources,dict),
            'unsupported trim native scope or missing CPU sources')
    require(phase is None or type(phase) is int and phase in (10000,40000) and mode=='foil',
            'unsupported moving native equipment scope')
    validate_owner(owner, ack, capture)
    require(isinstance(receipt,dict) and receipt.get('schema')=='equipment-submission-inputs-v1'
            and receipt.get('complete') is True and receipt.get('gpu_readback') is False
            and receipt.get('capability_admitted') is False, 'missing complete equipment native receipt')
    for key in ('gameplay_frame_id','correlation_id','gal_submission_id','deterministic_rendered_frame_index'):
        require(integer(receipt.get(key),1) and receipt[key]==owner.get(key), 'equipment receipt frame mismatch')
    semantics,draws=receipt.get('semantic_instances'),receipt.get('draws')
    ordinary_atlas_ids=set()
    if trim=='gold-spire':
        from equipment_trim_reference import native_atlas_sources
        ordinary_source_proof=native_atlas_sources(receipt,trim_sources,receipt['deterministic_rendered_frame_index'],decal=False)
        ordinary_atlas_ids={t['texture_id'] for t in receipt['texture_sources']}
    count=8 if mode=='foil' else 4
    if trim is not None:count+=4
    require(isinstance(semantics,list) and isinstance(draws,list) and len(semantics)==len(draws)==count,
            'equipment requires four base pieces and their requested overlays')
    view=receipt.get('world_view_matrix')
    require(vector(view,16),'missing equipment source view')
    expected_view=[v*(4095/4096) if i<12 else v for i,v in enumerate(view)]
    def close(a,b):
        return vector(a,len(b)) and all(math.isclose(x,y,rel_tol=1e-6,abs_tol=1e-7) for x,y in zip(a,b))
    remaining={}
    for s in semantics:
        require(isinstance(s,dict) and s.get('context')=='world' and s.get('projection')=='Perspective'
                and vector(s.get('model_pose'),16) and s.get('section_index')==2**32-1,
                'unsupported equipment semantic context')
        key=(s.get('mesh_key'),s.get('mesh_generation'))
        require(all(integer(v,1) for v in key) and key not in remaining,'duplicate equipment source')
        foil=s.get('foil')
        if foil is None:
            require(type(s.get('flags')) is int and s['flags']==4,'missing equipment base layering')
        else:
            require(mode=='foil' and s.get('flags')==0 and isinstance(foil,dict)
                    and foil.get('kind')=='Armor' and integer(foil.get('clock_millis'))
                    and foil['clock_millis']<=2**63-1 and type(foil.get('speed')) is float
                    and foil['speed']==(0.0 if phase is None else 0.5) and type(foil.get('strength')) is float
                    and foil['strength']==0.5,'wrong equipment foil semantics')
        remaining[key]=s
    textures=Counter(); commands=[]; base_count=foil_count=trim_count=0; trim_indices=[]
    c,s=math.cos(math.pi/18)*0.16,math.sin(math.pi/18)*0.16
    expected_foil=[c,-s,0,0,s,c,0,0,0.5,0,0,0]
    for d in draws:
        require(isinstance(d,dict),'malformed equipment draw')
        key=(d.get('mesh_key'),d.get('mesh_generation'));semantic=remaining.pop(key,None)
        require(semantic is not None and d.get('section_index')==0
                and type(d.get('instance_count')) is int and d['instance_count']==1
                and integer(d.get('command_index'),1) and integer(d.get('index_count'),1)
                and d['index_count']%6==0,'equipment draw/source mismatch')
        commands.append(d['command_index'])
        require(d.get('projection')=='Perspective' and close(d.get('view_matrix'),expected_view),
                'equipment view offset differs from Frozen')
        uploads=d.get('uploaded_instances')
        require(isinstance(uploads,list) and len(uploads)==1 and isinstance(uploads[0],dict)
                and close(uploads[0].get('model_pose'),semantic['model_pose']), 'equipment uploaded pose mismatch')
        upload=uploads[0];foil=semantic['foil'] is not None
        require(d.get('standard_foil') is foil,'equipment material family mismatch')
        sampler=d.get('sampler')
        require(isinstance(sampler,str),'missing equipment sampler')
        # The observer currently formats the immutable GAL descriptor. Anchor
        # its fields after the label so label text cannot satisfy the check.
        fields=re.search(r'", min_filter: (\w+), mag_filter: (\w+), mip_filter: (\w+), address_u: (\w+), address_v: (\w+), address_w: (\w+), comparison: (\w+) \}$',sampler)
        require(fields is not None,'malformed equipment sampler declaration')
        if foil:
            clock=semantic['foil']
            ticks=min(int(clock['clock_millis']*clock['speed']*8.0),2**63-1)
            expected_foil=[c,-s,-(ticks%110000)/110000,0,s,c,(ticks%30000)/30000,0,0.5,0,0,0]
            foil_count+=1
            require(d.get('program')=='vulkanic:builtin/direct_standard_item_foil_v1'
                    and d.get('material_mode')==4 and d.get('depth_policy')==2
                    and d.get('depth_compare')=='Equal' and d.get('depth_write') is False
                    and d.get('blend')=='Glint' and d.get('texture_rgba_xxh32')=='724fc018'
                    and (d.get('texture_width'),d.get('texture_height'))==(128,128)
                    and fields.groups()==('Linear','Linear','Nearest','Repeat','Repeat','Repeat','None')
                    and close(upload.get('foil'),expected_foil),'equipment glint resource/pipeline/upload mismatch')
        elif trim is not None and (d.get('depth_policy')==3 if trim=='gold-spire-decal' else
                type(d.get('texture_id')) is int and d['texture_id'] in ordinary_atlas_ids):
            trim_count+=1;trim_indices.append(d['index_count'])
            require(d.get('program')=='vulkanic:builtin/direct_terrain_cutout_v1'
                    and d.get('material_mode')==2
                    and type(d.get('depth_policy')) is int and d['depth_policy']==(3 if trim=='gold-spire-decal' else 1)
                    and d.get('depth_compare')==('Equal' if trim=='gold-spire-decal' else 'LessOrEqual')
                    and d.get('depth_write') is True and d.get('blend')=='Disabled'
                    and upload.get('foil') is None
                    and fields.groups()==('Nearest','Nearest','Nearest','ClampToEdge','ClampToEdge','ClampToEdge','None')
                    and close(upload.get('material'),[0.1,0,0,114]), 'trim pipeline/sampler/upload mismatch')
        else:
            base_count+=1;textures[d.get('texture_rgba_xxh32')]+=1
            require(d.get('program')=='vulkanic:builtin/direct_terrain_cutout_v1'
                    and d.get('material_mode')==2 and d.get('depth_policy')==1
                    and d.get('depth_compare')=='LessOrEqual' and d.get('depth_write') is True
                    and d.get('blend')=='Disabled' and upload.get('foil') is None
                    and (d.get('texture_width'),d.get('texture_height'))==(64,32)
                    and fields.groups()==('Nearest','Nearest','Nearest','ClampToEdge','ClampToEdge','ClampToEdge','None')
                    and close(upload.get('material'),[0.1,0,0,114 if per_face else 50]),'equipment base resource/pipeline/upload mismatch')
    require(not remaining and commands==sorted(set(commands)) and base_count==4
            and foil_count==(4 if mode=='foil' else 0)
            and textures==Counter({'fa62c001':3,'54527ceb':1}), 'equipment draw membership incomplete')
    require(trim_count==(4 if trim is not None else 0)
            and (trim is None or sorted(trim_indices)==[72,72,108,108]), 'trim draw membership incomplete')
    result=dict(passed=True,capability_admitted=False,base_draws=base_count,foil_draws=foil_count,
                correlation=receipt['correlation_id'],submission=receipt['gal_submission_id'],per_face_lighting=per_face)
    if trim is not None:
        from equipment_trim_reference import native_atlas_sources
        result['trim']=(ordinary_source_proof if trim=='gold-spire' else
                        native_atlas_sources(receipt,trim_sources,receipt['deterministic_rendered_frame_index']))
    return result


def validate_document(doc, mode, *, phase=None, stepped=False, trim=None, material=None, age=None):
    validate_fixture(doc.get('equipmentFixture'),mode,phase=phase,stepped=stepped,trim=trim,material=material,age=age)
    reload=doc.get('worldResourceReload',{})
    require(reload.get('schema')=='normal-world-resource-reload-v1'
            and all(reload.get(k) is True for k in ('requested','futureComplete','complete'))
            and type(reload.get('presentations')) is int and reload['presentations']>=2
            and reload.get('selectedBefore')==reload.get('selectedAtCapture')==['vanilla'],
            'equipment requires completed vanilla resource reload')
    captures=doc.get('captures')
    require(isinstance(captures,list) and len(captures)==5,'equipment requires complete five-frame capture')
    for c in captures:
        require(c.get('window')==dict(width=1280,height=720) and c.get('gameTime')==6000
                and c.get('dimension')=='minecraft:overworld'
                and c.get('position')==dict(x=150.5,y=100.0,z=530.5)
                and ('requestedPosition' not in c or c['requestedPosition']==c['position'])
                and c.get('shaderEnabled')=='false'
                and all(c.get(k)==v for k,v in (('requestedYaw',105.0),('observedYaw',105.0),
                    ('requestedPitch',10.0),('observedPitch',10.0))), 'equipment fixed camera/world mismatch')
    return captures


def validate_local(doc):
    """Replace the bare head classifier only with full equipped-frame proof."""
    import graphics_harness as h
    from pathlib import Path
    if doc.get("equipmentFixture",{}).get("materialVariant") is not None:
        from equipment_leather_acceptance import validate_local as leather_local
        return leather_local(doc)
    mode=doc.get('equipmentFixture',{}).get('mode')
    trim=doc.get('equipmentFixture',{}).get('trimVariant')
    moving=doc.get('equipmentFixture',{}).get('schema')=='equipped-zombie-moving-v1'
    phase=None
    if moving:
        first=doc.get('captures')
        require(isinstance(first,list) and len(first)==5, 'equipment requires complete five-frame capture')
        timing=h.read_json(Path(first[0]['screenshot']+'.equipment-timing.json'))
        phase=timing.get('requestedPhase')
        require(type(phase) is int and phase in (10000,40000), 'missing moving equipment phase')
    captures=validate_document(doc,mode,phase=phase,stepped=moving,trim=trim)
    rows=[]
    frames=[]
    for pose,c in enumerate(captures):
        image=Path(c['screenshot'])
        ack=h.read_json(image.with_name('capture_request_'+image.stem+'.ack.json'))
        require(ack.get('status')=='captured' and ack.get('captureMethod')=='rust-vulkan-final-output'
                and ack.get('screenshot')==str(image),'equipment requires actual selected Rust screenshot')
        receipt=h.read_json(Path(str(image)+'.equipment-inputs.json'))
        frame=receipt.get('gameplay_frame_id')
        require(type(frame) is int and frame>0,'missing selected equipment frame')
        owner=h.read_json(image.parent.parent/'whole_frame_gameplay_attachments'/f'gameplay-correlation-frame-{frame}.json')
        timing=h.read_json(Path(str(image)+'.equipment-timing.json')) if moving else None
        row=validate_native(receipt,owner,ack,c,mode,phase=phase,per_face=moving,trim=trim,
                            trim_sources=timing.get('trimSources') if trim is not None else None)
        if moving:
            from equipment_timing_reference import validate_clocks, paired_stepped_geometry
            timing=h.read_json(Path(str(image)+'.equipment-timing.json'))
            frame=c['renderedFrameIndex']
            row['timing']=validate_clocks(timing,frame,receipt,phase=phase,pose=pose,step=8000)
            row['geometry']=paired_stepped_geometry(timing,timing,frame,frame,pose)
            row['pixels']=compare_paths(image,image,mode,phase=phase,pose=pose,trim=trim)
            frames.append(frame)
        rows.append(row)
    # The fixed Frozen anchor covers the selected initial image. The remaining
    # frames retain producer/crop correlation plus their native draw proof.
    if moving:
        require(frames==sorted(set(frames)), 'moving equipment requires five distinct ordered frames')
    else:
        compare_paths(captures[0]['screenshot'],captures[0]['screenshot'],mode)
    return dict(passed=True,capability_admitted=False,mode=mode,phase=phase,frames=rows)


def requested_moving_phase(jvm_args):
    """Reject ambiguous launch scopes rather than choosing one duplicate flag."""
    def option(name):
        prefix='-Dmattmc.dev.'+name+'='
        values=[arg[len(prefix):] for arg in (jvm_args or []) if arg.startswith(prefix)]
        require(len(values)<=1, 'duplicate equipment option: '+name)
        return values[0] if values else None
    phase=option('equipmentFoilPhase')
    step=option('equipmentFoilPoseStep')
    stepped=option('equipmentTickStepping')
    timing=option('equipmentFoilTiming')
    if phase is None:
        require(step is None and stepped in (None,'false'), 'moving options require equipment phase')
        return None
    require(phase in ('10000','40000') and step=='8000' and stepped=='true' and timing=='true',
            'moving equipment requires explicit phase, step8000, timing and simulation stepping')
    return int(phase)


def paired_moving_frames(frozen, current, phase, *, trim=None):
    import graphics_harness as h
    from pathlib import Path
    from equipment_timing_reference import validate_clocks, paired_ticks, paired_stepped_geometry
    baseline=validate_document(frozen,'foil',phase=phase,stepped=True,trim=trim)
    candidate=validate_document(current,'foil',phase=phase,stepped=True,trim=trim)
    native=validate_local(current)
    require(native['phase']==phase, 'equipment launch and captured phase differ')
    frames=[]
    for pose,(a,b) in enumerate(zip(baseline,candidate)):
        af,bf=a['renderedFrameIndex'],b['renderedFrameIndex']
        at=h.read_json(Path(a['screenshot']+'.equipment-timing.json'))
        bt=h.read_json(Path(b['screenshot']+'.equipment-timing.json'))
        inputs=h.read_json(Path(b['screenshot']+'.equipment-inputs.json'))
        clocks=[validate_clocks(at,af,phase=phase,pose=pose,step=8000),
                validate_clocks(bt,bf,inputs,phase=phase,pose=pose,step=8000)]
        row=dict(pose=pose,geometry=paired_stepped_geometry(at,bt,af,bf,pose),
            clock_distance=paired_ticks(*clocks),timing=clocks,
            pixels=compare_paths(a['screenshot'],b['screenshot'],'foil',phase=phase,pose=pose,trim=trim))
        if trim is not None:
            from equipment_trim_reference import paired_sources
            row['trim_sources']=paired_sources(at.get('trimSources'),bt.get('trimSources'),af,bf,decal=trim=='gold-spire-decal')
        frames.append(row)
    indices=[capture['renderedFrameIndex'] for capture in baseline]
    require(indices==sorted(set(indices)), 'Frozen equipment requires five distinct ordered frames')
    return dict(passed=True,capability_admitted=False,phase=phase,native=native,frames=frames)


def report(visual, jvm_args, reference=None):
    import graphics_harness as h
    from pathlib import Path
    from shield_static_blocking_reference import validate_execution
    requested=[arg.split('=',1)[1] for arg in (jvm_args or [])
               if arg.startswith('-Dmattmc.dev.graphicsAuditEquipment=')]
    trims=[arg.split('=',1)[1] for arg in (jvm_args or [])
           if arg.startswith('-Dmattmc.dev.graphicsAuditEquipmentTrim=')]
    materials=[arg.split("=",1)[1] for arg in (jvm_args or []) if arg.startswith("-Dmattmc.dev.graphicsAuditEquipmentMaterial=")]
    ages=[arg.split("=",1)[1] for arg in (jvm_args or []) if arg.startswith("-Dmattmc.dev.graphicsAuditEquipmentAge=")]
    enabled=bool(requested) or bool(trims) or bool(materials) or bool(ages) or reference is not None
    result=dict(requested=enabled,passed=not enabled,capability_admitted=False,pairs=[])
    if not enabled:return result
    try:
        require(not ages or ages==['baby'] and materials==['leather-dyed'],
                'unsupported or duplicate equipment age request')
        require(len(requested)==1 and requested[0] in REFERENCES,'unsupported or duplicate equipment request')
        mode=requested[0]
        phase=requested_moving_phase(jvm_args)
        require(len(trims)<=1 and (not trims or trims[0] in ('gold-spire','gold-spire-decal') and phase==10000 and mode=='foil'),
                'unsupported or duplicate trim request')
        trim=trims[0] if trims else None
        require(phase is None or mode=='foil', 'moving equipment requires enchanted scope')
        require((phase==40000) == (reference is not None),
                'only the second equipment phase requires an accepted first-phase reference')
        require(visual.get('passed') is True and bool(visual.get('pairs')),'equipment requires paired fixture equivalence')
        if materials:
            require(materials==['leather-dyed'] and mode=='foil' and phase==10000 and trim is None and reference is None,
                    'unsupported or duplicate leather request')
            from equipment_leather_acceptance import validate_pair
            result['pairs']=[validate_pair(pair,age='baby') if ages else validate_pair(pair)
                             for pair in visual['pairs']]
            result['passed']=True
            return result
        for pair in visual['pairs']:
            docs=[]
            for side,backend in [('baseline','frozen-opengl-shaders-off'),('current','current-rust-vulkan-shaders-off')]:
                path=Path(pair[side+'_artifact']);validate_execution(h.read_json(path),backend)
                doc=h.deterministic_capture_document(path) or {}
                captures=validate_document(doc,mode,phase=phase,stepped=phase is not None,trim=trim)
                require(captures[0]['screenshot']==pair[side+'_image'],'equipment selected image mismatch')
                meta=h.latest_capture_meta_path(path.parent/'capture')
                settings=h.read_key_values(meta) if meta else {}
                require(settings.get('forced_option_guiScale')=='3' and not settings.get('gui_resource_pack_scenario'),
                        'equipment requires vanilla resources and scale3')
                docs.append(doc)
            require(docs[0]['equipmentFixture']==docs[1]['equipmentFixture'],'paired equipment differs')
            if phase is not None:
                result['pairs'].append(paired_moving_frames(docs[0],docs[1],phase,trim=trim))
                continue
            native=validate_local(docs[1]);pixels=compare_paths(pair['baseline_image'],pair['current_image'],mode)
            timing=[]
            if '-Dmattmc.dev.equipmentFoilTiming=true' in (jvm_args or []):
                from equipment_timing_reference import validate_stopped
                require(mode=='foil','equipment timing requires enchanted scope')
                for side,doc in enumerate(docs):
                    frames=[]
                    for capture in doc['captures']:
                        image=capture['screenshot']
                        clocks=h.read_json(Path(image+'.equipment-timing.json'))
                        inputs=h.read_json(Path(image+'.equipment-inputs.json')) if side else None
                        frames.append(validate_stopped(clocks,capture['renderedFrameIndex'],inputs))
                    timing.append(frames)
            result['pairs'].append(dict(passed=True,native=native,pixels=pixels,timing=timing))
        if phase==40000:
            from equipment_temporal_reference import validate_transition
            result['temporal']=validate_transition(visual,reference)
        result['passed']=True
    except (OSError,KeyError,TypeError,ValueError,IndexError) as error:
        result['reason']=str(error)
    return result
