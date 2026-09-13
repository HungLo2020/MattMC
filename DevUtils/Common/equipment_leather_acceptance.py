"""Composed private dyed-leather acceptance; no normal armor admission."""
from pathlib import Path
import copy
import json
from PIL import Image,ImageChops,ImageStat
from equipment_reference import require,validate_document
from equipment_leather_draws import validate_draws
from equipment_timing_reference import validate_clocks,paired_ticks,paired_stepped_geometry

REFERENCE=json.loads((Path(__file__).parent/'references/equipment_leather_r742.json').read_text())
BABY_REFERENCE=json.loads((Path(__file__).parent/'references/equipment_baby_leather_r747.json').read_text())


def reference(age=None):
    require(age is None or age=='baby','unsupported leather reference age')
    return REFERENCE if age is None else BABY_REFERENCE



def frozen_reference(pose, *, age=None):
    require(type(pose) is int and 0<=pose<5,'invalid leather reference pose')
    row=copy.deepcopy(reference(age)['poses'][pose]);timing=row['timing']
    for model in timing['modelGeometry']['models']:
        model['quads']=row['geometrySources'][model.pop('geometrySource')]
    return timing


def pixels(frozen,current,pose, *, age=None):
    require(type(pose) is int and 0<=pose<5,'invalid leather pixel pose')
    with Image.open(frozen) as a,Image.open(current) as b:
        require(a.size==b.size==(1280,720),'leather requires 1280x720')
        a,b=a.convert('RGB'),b.convert('RGB');errors=[]
        for x,y,expected in reference(age)['poses'][pose]['probes']:
            box=(x-1,y-1,x+2,y+2)
            av,bv=list(a.crop(box).getdata()),list(b.crop(box).getdata())
            anchor=max(abs(v-e) for pixel,ref in zip(av,expected) for v,e in zip(pixel,ref))
            error=max(abs(v-e) for pixel,ref in zip(av,bv) for v,e in zip(pixel,ref))
            require(anchor<=2 and error<=2,'leather pixel probe differs from independent Frozen reference')
            errors.append(error)
        means=ImageStat.Stat(ImageChops.difference(a,b).crop(tuple(reference(age)['region']))).mean
        require(all(v<=2 for v in means),'leather region exceeds RGB2')
    return dict(passed=True,probe_errors=errors,region_mean_rgb=means,capability_admitted=False)


def document(doc, *, age=None):
    require(isinstance(doc,dict),'missing leather capture document')
    captures=validate_document(doc,'foil',phase=10000,stepped=True,material='leather-dyed',age=age)
    frames=[];images=[]
    for i,c in enumerate(captures):
        require(type(c.get('index')) is int and c['index']==i+1
                and type(c.get('renderedFrameIndex')) is int and c['renderedFrameIndex']>0
                and type(c.get('gameTime')) is int and c['gameTime']==6000
                and all(type(c.get(k)) is float for k in ('requestedYaw','observedYaw','requestedPitch','observedPitch'))
                and isinstance(c.get('screenshot'),str) and c['screenshot'],'invalid leather capture identity')
        frames.append(c['renderedFrameIndex']);images.append(c['screenshot'])
    require(frames==sorted(set(frames)) and len(set(images))==5,'duplicate or unordered leather captures')
    return captures


def selected(c):
    import graphics_harness as h
    image=Path(c['screenshot']);ack=h.read_json(image.with_name('capture_request_'+image.stem+'.ack.json'))
    require(isinstance(ack,dict) and ack.get('status')=='captured' and ack.get('captureMethod')=='rust-vulkan-final-output'
            and ack.get('screenshot')==str(image),'missing actual Rust leather screenshot')
    native=h.read_json(Path(str(image)+'.equipment-inputs.json'))
    require(isinstance(native,dict),'missing native leather inputs')
    frame=native.get('gameplay_frame_id')
    require(type(frame) is int and frame>0,'missing leather gameplay frame')
    owner=h.read_json(image.parent.parent/'whole_frame_gameplay_attachments'/f'gameplay-correlation-frame-{frame}.json')
    timing=h.read_json(Path(str(image)+'.equipment-timing.json'))
    require(isinstance(owner,dict) and isinstance(timing,dict),'missing leather ownership or timing')
    return native,owner,ack,timing


def validate_local(doc):
    age=doc.get('equipmentFixture',{}).get('ageVariant')
    reference(age)
    rows=[]
    for pose,c in enumerate(document(doc,age=age)):
        native,owner,ack,timing=selected(c)
        row=validate_draws(frozen_reference(pose,age=age),native,owner,ack,c)
        row['timing']=validate_clocks(timing,c['renderedFrameIndex'],native,phase=10000,pose=pose,step=8000)
        row['pixels']=pixels(c['screenshot'],c['screenshot'],pose,age=age)
        # Actual paired simulation/model inputs are additionally required by validate_pair.
        rows.append(row)
    return dict(passed=True,capability_admitted=False,scope='private dyed leather local prerequisites',age=age,frames=rows)


def validate_pair(pair, *, age=None):
    import graphics_harness as h
    from shield_static_blocking_reference import validate_execution
    docs=[];captures=[]
    for side,backend in [('baseline','frozen-opengl-shaders-off'),('current','current-rust-vulkan-shaders-off')]:
        path=Path(pair[side+'_artifact']);artifact=h.read_json(path)
        require(isinstance(artifact,dict),'missing leather execution artifact')
        validate_execution(artifact,backend)
        doc=h.deterministic_capture_document(path);cs=document(doc,age=age)
        require(cs[0]['screenshot']==pair[side+'_image'],'leather selected image mismatch')
        meta=h.latest_capture_meta_path(path.parent/'capture');settings=h.read_key_values(meta) if meta else {}
        require(settings.get('forced_option_guiScale')=='3' and not settings.get('gui_resource_pack_scenario'),
                'leather requires vanilla resources and GUI scale3')
        docs.append(doc);captures.append(cs)
    require(docs[0]['equipmentFixture']==docs[1]['equipmentFixture'],'paired leather fixture mismatch')
    rows=[]
    for pose,(a,b) in enumerate(zip(*captures)):
        image=Path(a['screenshot']);ack=h.read_json(image.with_name('capture_request_'+image.stem+'.ack.json'))
        require(isinstance(ack,dict) and ack.get('status')=='captured' and ack.get('screenshot')==str(image)
                and type(ack.get('renderedFrameIndex')) is int and ack['renderedFrameIndex']==a['renderedFrameIndex'],
                'missing Frozen leather screenshot acknowledgement')
        at=h.read_json(Path(str(image)+'.equipment-timing.json'));native,owner,ba,bt=selected(b)
        clocks=[validate_clocks(at,a['renderedFrameIndex'],phase=10000,pose=pose,step=8000),
                validate_clocks(bt,b['renderedFrameIndex'],native,phase=10000,pose=pose,step=8000)]
        rows.append(dict(pose=pose,draws=validate_draws(at,native,owner,ba,b),clock_distance=paired_ticks(*clocks),
            cpu_geometry=paired_stepped_geometry(at,bt,a['renderedFrameIndex'],b['renderedFrameIndex'],pose),
            pixels=pixels(a['screenshot'],b['screenshot'],pose,age=age)))
    return dict(passed=True,capability_admitted=False,scope='private dyed leather five stepped front poses and vanilla reload',age=age,poses=rows)
