"""Leather draw/source/upload contract. Full execution and pixels compose outside."""
from collections import Counter
import hashlib
import io
import math
import re
import struct
from PIL import Image
from equipment_reference import require
from equipment_leather_reference import BODY, LEGS, BODY_OVERLAY, LEGS_OVERLAY, GLINT, TINT
from equipment_geometry_reference import compare_geometry, packed_source
from equipment_timing_reference import validate_entity_transforms
from shield_static_blocking_reference import validate_owner
from wolf_draw_reference import float32_vector

# Frozen r742 observed declarations and independently decoded vanilla asset bytes.
TEXTURES={
 BODY: (64,32,'4af11607d88cdfd2e665657dcba822adf4b319fefaea21d25050c94d6d8aa815'),
 LEGS: (64,32,'a927be2d273cdd9506c89eaa13f11c7a93018e713aa51790155c5cf0bef105fe'),
 BODY_OVERLAY: (64,32,'cd7f5955c6d8392ba405b9fddc99248dfadac9f23cf9e544d3f4479ebf1d5495'),
 LEGS_OVERLAY: (64,32,'f49ebdaa866b19e9a5cc89149346cce4a0405a361255bf3a02c2f82b8b30a135'),
 GLINT: (128,128,'0cdd3ee2f5b35382fad3f7a16886221de323ad8b7f41cf672a14349c10ae686b')}


def texture_sources(native):
    sources=native.get('model_texture_sources')
    require(isinstance(sources,list) and len(sources)==5,'missing five decoded leather textures')
    resolved={}
    for source in sources:
        require(isinstance(source,dict) and type(source.get('texture_id')) is int
                and 0<source['texture_id']<2**32 and source['texture_id'] not in resolved
                and source.get('encoding')=='png-rgba8-hex', 'invalid leather texture identity')
        width,height=source.get('width'),source.get('height')
        require(type(width) is int and type(height) is int and (width,height) in ((64,32),(128,128)),
                'invalid leather texture extent')
        encoded=source.get('png_hex')
        require(isinstance(encoded,str) and 0<len(encoded)<=262144 and len(encoded)%2==0
                and re.fullmatch('[0-9a-f]+',encoded),'invalid bounded leather texture bytes')
        with Image.open(io.BytesIO(bytes.fromhex(encoded))) as image:
            require(image.format=='PNG' and image.mode=='RGBA' and image.size==(width,height), 'wrong leather decoded texture')
            digest=hashlib.sha256(image.tobytes()).hexdigest()
        names=[name for name,expected in TEXTURES.items() if expected==(width,height,digest)]
        require(len(names)==1,'leather texture differs from Frozen vanilla source')
        resolved[source['texture_id']]=names[0]
    require(set(resolved.values())==set(TEXTURES),'duplicate or missing leather textures')
    return resolved


def validate_draws(frozen,native,owner,ack,capture):
    validate_owner(owner,ack,capture)
    frame=frozen.get('renderedFrameIndex');current=capture.get('renderedFrameIndex')
    geometry=frozen.get('modelGeometry')
    compare_geometry(geometry,native,frame,current)
    for key in ('gameplay_frame_id','correlation_id','gal_submission_id','deterministic_rendered_frame_index'):
        require(type(native.get(key)) is int and native[key]>0 and native[key]==owner.get(key),'leather owner correlation mismatch')
    transforms=validate_entity_transforms(frozen,frame)
    require(all(t==transforms[0] for t in transforms),'inconsistent Frozen leather transforms')
    pose=float32_vector(transforms[0]['modelView'],16)
    textures=texture_sources(native)
    sources=native.get('semantic_instances')
    require(isinstance(sources,list) and len(sources)==12,'missing leather semantic instances')
    semantics={}
    for source in sources:
        require(isinstance(source,dict) and all(type(source.get(k)) is int and source[k]>0 for k in ('mesh_key','mesh_generation')),
                'invalid leather semantic identity')
        key=(source['mesh_key'],source['mesh_generation'])
        require(key not in semantics and source.get('context')=='world' and source.get('projection')=='Perspective'
                and type(source.get('section_index')) is int and source['section_index']==2**32-1
                and float32_vector(source.get('model_pose'),16)==pose,'wrong leather semantic pose/context')
        semantics[key]=source
    expected=Counter((row['texture'],packed_source(row)[0]) for row in geometry['models'])
    observed=Counter();commands=[];order=[]
    for draw in native['draws']:
        source=semantics.pop((draw.get('mesh_key'),draw.get('mesh_generation')),None)
        name=textures.get(draw.get('texture_id'));glint=name==GLINT
        require(source is not None and name in TEXTURES,'unowned or substituted leather draw')
        require(type(draw.get('command_index')) is int and draw['command_index']>=0
                and type(draw.get('instance_count')) is int and draw['instance_count']==1
                and type(draw.get('section_index')) is int and draw['section_index']==0,'invalid leather draw range')
        commands.append(draw['command_index']);order.append(glint)
        require(draw.get('projection')=='Perspective' and draw.get('standard_foil') is glint
                and (draw.get('texture_width'),draw.get('texture_height'))==TEXTURES[name][:2], 'wrong leather draw projection/texture')
        views=[d['view'] for d in geometry['completedDraws'] if d['texture']==name]
        require(views and all(float32_vector(v,16)==float32_vector(draw.get('view_matrix'),16) for v in views),
                'leather view differs from Frozen draw')
        tint=0xffffffff if name not in (BODY,LEGS) else TINT&0xffffffff
        require(type(source.get('color_argb')) is int and source['color_argb']==tint
                and type(source.get('model_submission_order')) is int
                and source['model_submission_order']==(2 if glint else 1 if name in (BODY,LEGS) else 3)
                and type(source.get('flags')) is int and source['flags']==(0 if glint else 4), 'wrong leather tint/order/flags')
        require(source.get('entity_identity')==('minecraft:model-part/armor-texture-glint/glint' if glint else 'minecraft:zombie'),
                'wrong leather producer')
        uploaded=draw.get('uploaded_instances')
        require(isinstance(uploaded,list) and len(uploaded)==1 and isinstance(uploaded[0],dict),'missing leather instance upload')
        upload=uploaded[0]
        require(float32_vector(upload.get('model_pose'),16)==pose
                and float32_vector(upload.get('color'),4)==float32_vector([((tint>>s)&255)/255 for s in (16,8,0,24)],4)
                and float32_vector(upload.get('material'),4)==float32_vector([0.0,0.0,0.0,18.0] if glint else [0.1,0.0,0.0,114.0],4),
                'leather instance upload differs from source')
        require(draw.get('program')=='vulkanic:builtin/'+('direct_standard_item_foil_v1' if glint else 'direct_terrain_cutout_v1')
                and type(draw.get('material_mode')) is int and draw['material_mode']==(4 if glint else 2)
                and type(draw.get('depth_policy')) is int and draw['depth_policy']==(2 if glint else 1)
                and draw.get('depth_compare')==('Equal' if glint else 'LessOrEqual')
                and draw.get('depth_write') is (not glint) and draw.get('blend')==('Glint' if glint else 'Disabled'),
                'wrong leather pipeline/material')
        fields=re.search(r'", min_filter: (\w+), mag_filter: (\w+), mip_filter: (\w+), address_u: (\w+), address_v: (\w+), address_w: (\w+), comparison: (\w+) \}$',draw.get('sampler',''))
        require(fields is not None and fields.groups()==(('Linear','Linear','Nearest','Repeat','Repeat','Repeat','None') if glint
                else ('Nearest','Nearest','Nearest','ClampToEdge','ClampToEdge','ClampToEdge','None')), 'wrong leather sampler')
        if glint:
            foil=source.get('foil')
            require(isinstance(foil,dict) and foil.get('kind')=='Armor' and type(foil.get('clock_millis')) is int
                    and 0<=foil['clock_millis']<=2**63-1 and type(foil.get('speed')) is float and foil['speed']==0.5
                    and type(foil.get('strength')) is float and foil['strength']==0.5,'wrong leather foil semantics')
            ticks=min(int(foil['clock_millis']*foil['speed']*8.0),2**63-1)
            c,s=math.cos(math.pi/18)*0.16,math.sin(math.pi/18)*0.16
            wanted=[c,-s,-(ticks%110000)/110000,0,s,c,(ticks%30000)/30000,0,0.5,0,0,0]
            actual=upload.get('foil');float32_vector(actual,12)
            require(all(math.isclose(a,b,rel_tol=1e-6,abs_tol=1e-7) for a,b in zip(actual,wanted)), 'wrong leather foil upload')
        else:
            require(source.get('foil') is None and upload.get('foil') is None,'unexpected leather foil')
        observed[(name,bytes.fromhex(draw['mesh_source_geometry']['vertex_hex']))]+=1
    require(not semantics and commands==sorted(set(commands)) and order==[False]*8+[True]*4
            and observed==expected,'leather layer/geometry/draw membership differs')
    return dict(passed=True,capability_admitted=False,texture_geometry_tint_correspondence=True,
                accepted_uploads=True,completed_owner=True,draws=12)
