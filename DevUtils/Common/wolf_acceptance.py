"""Composed private wolf armor proof. Normal armor stays unadmitted."""
from equipment_reference import require
from wolf_armor_reference import validate_fixture,compare_images


def validate_document(doc,mode="high"):
    require(isinstance(doc,dict),'missing wolf capture document')
    validate_fixture(doc.get('wolfArmorFixture'),mode)
    reload=doc.get('worldResourceReload')
    require(isinstance(reload,dict) and reload.get('schema')=='normal-world-resource-reload-v1'
            and all(reload.get(k) is True for k in ('requested','futureComplete','complete'))
            and type(reload.get('presentations')) is int and reload['presentations']>=2
            and reload.get('selectedBefore')==reload.get('selectedAtCapture')==['vanilla'],
            'wolf requires completed vanilla resource reload')
    captures=doc.get('captures');frames=[];images=[]
    require(isinstance(captures,list) and len(captures)==5,'wolf requires all five captures')
    for index,c in enumerate(captures):
        require(isinstance(c,dict) and type(c.get('index')) is int and c['index']==index+1
                and c.get('window')==dict(width=1280,height=720)
                and type(c.get('gameTime')) is int and c['gameTime']==6000
                and c.get('dimension')=='minecraft:overworld'
                and c.get('position')==dict(x=150.5,y=100.0,z=530.5)
                and ('requestedPosition' not in c or c['requestedPosition']==c['position'])
                and c.get('shaderEnabled')=='false'
                and all(type(c.get(k)) is float and c[k]==v for k,v in
                        (('requestedYaw',105.0),('observedYaw',105.0),('requestedPitch',10.0),('observedPitch',10.0)))
                and type(c.get('renderedFrameIndex')) is int and c['renderedFrameIndex']>0
                and isinstance(c.get('screenshot'),str) and c['screenshot'],
                'wolf fixed camera/world/frame mismatch')
        frames.append(c['renderedFrameIndex']);images.append(c['screenshot'])
    require(frames==sorted(set(frames)) and len(set(images))==5,'wolf duplicate or unordered captures')
    return captures


def validate_pair(pair,mode="high"):
    from pathlib import Path
    from PIL import Image
    import graphics_harness as h
    from shield_static_blocking_reference import validate_execution
    from wolf_geometry_reference import compare_geometry
    from wolf_draw_reference import compare_owned_inputs
    require(isinstance(pair,dict),'missing wolf visual pair')
    docs=[];captures=[]
    for side,backend in [('baseline','frozen-opengl-shaders-off'),('current','current-rust-vulkan-shaders-off')]:
        path=Path(pair[side+'_artifact']);validate_execution(h.read_json(path),backend)
        doc=h.deterministic_capture_document(path);selected=validate_document(doc,mode)
        require(selected[0]['screenshot']==pair[side+'_image'],'wolf selected image mismatch')
        meta=h.latest_capture_meta_path(path.parent/'capture');settings=h.read_key_values(meta) if meta else {}
        require(settings.get('forced_option_guiScale')=='3' and not settings.get('gui_resource_pack_scenario'),
                'wolf requires vanilla resources and GUI scale3')
        docs.append(doc);captures.append(selected)
    require(docs[0]['wolfArmorFixture']==docs[1]['wolfArmorFixture'],'paired wolf fixture differs')
    rows=[]
    for pose,(baseline,current) in enumerate(zip(*captures)):
        inputs=[];acks=[]
        for side,c in enumerate((baseline,current)):
            image=Path(c['screenshot']);ack=h.read_json(image.with_name('capture_request_'+image.stem+'.ack.json'))
            require(ack.get('status')=='captured' and ack.get('screenshot')==str(image)
                    and type(ack.get('renderedFrameIndex')) is int and ack['renderedFrameIndex']==c['renderedFrameIndex']
                    and (side==0 or ack.get('captureMethod')=='rust-vulkan-final-output'),
                    'wolf selected screenshot acknowledgement mismatch')
            inputs.append(h.read_json(Path(str(image)+'.wolf-inputs.json')));acks.append(ack)
        image=Path(current['screenshot']);native=h.read_json(Path(str(image)+'.wolf-native-inputs.json'))
        frame=native.get('gameplay_frame_id')
        require(type(frame) is int and frame>0,'missing wolf gameplay frame')
        owner=h.read_json(image.parent.parent/'whole_frame_gameplay_attachments'/f'gameplay-correlation-frame-{frame}.json')
        owned=compare_owned_inputs(*inputs,native,owner,acks[1],current,pose=pose,mode=mode)
        geometry=compare_geometry(inputs[0],native,baseline['renderedFrameIndex'],current['renderedFrameIndex'],mode)
        with Image.open(baseline['screenshot']) as a,Image.open(current['screenshot']) as b:
            pixels=compare_images(a,b,pose,mode)
        rows.append(dict(pose=pose,owned_inputs=owned,geometry=geometry,pixels=pixels))
    return dict(passed=True,poses=rows,capability_admitted=False)
