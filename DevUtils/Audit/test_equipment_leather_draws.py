import copy
import math
from pathlib import Path
import unittest
from PIL import Image
import io
import equipment_leather_draws as ref
from test_equipment_leather_reference import evidence
from test_equipment_geometry_reference import native_for
from test_equipment_reference import EquipmentNativeReferenceTest


def fixture():
    geometry=evidence();native=native_for(geometry)
    _,owner,ack,capture=EquipmentNativeReferenceTest().evidence()
    owner.update(deterministic_rendered_frame_index=13,gal_submission_id=20,present_completed_submission_id=20)
    ack['renderedFrameIndex']=13;ack['wholeFramePresentationCorrelation']['submissionId']=20
    capture['renderedFrameIndex']=13
    native.update(gameplay_frame_id=8,correlation_id=8)
    pose=[1.0 if i%5==0 else 0.0 for i in range(16)]
    frozen=dict(schema='equipment-foil-clock-observation-v1',enabled=True,renderedFrameIndex=12,
        entityTransformsComplete=True,modelGeometry=geometry,entityTransforms=[dict(modelView=pose,
            normal=[1.0 if i%4==0 else 0.0 for i in range(9)],state=[0.0]*8,lightCoords=15728640)])
    ids={name:i+1 for i,name in enumerate(ref.TEXTURES)};textures=[]
    for name,identity in ids.items():
        path=Path(__file__).resolve().parents[2]/'src/main/resources/assets/minecraft'/name.split(':',1)[1]
        with Image.open(path) as image:
            stream=io.BytesIO();image.convert('RGBA').save(stream,format='PNG')
            textures.append(dict(texture_id=identity,encoding='png-rgba8-hex',width=image.width,height=image.height,png_hex=stream.getvalue().hex()))
    native['model_texture_sources']=textures;native['semantic_instances']=[]
    ordered=sorted(zip(geometry['models'],native['draws']),key=lambda x:x[0]['texture']==ref.GLINT)
    native['draws']=[d for _,d in ordered]
    for i,(row,draw) in enumerate(ordered):
        name=row['texture'];glint=name==ref.GLINT;tint=0xffffffff if name not in (ref.BODY,ref.LEGS) else ref.TINT&0xffffffff
        foil=dict(kind='Armor',clock_millis=2500,speed=0.5,strength=0.5) if glint else None
        native['semantic_instances'].append(dict(mesh_key=i+1,mesh_generation=1,context='world',projection='Perspective',
            section_index=2**32-1,model_pose=pose,color_argb=tint,model_submission_order=2 if glint else 1 if name in (ref.BODY,ref.LEGS) else 3,
            flags=0 if glint else 4,foil=foil,entity_identity='minecraft:model-part/armor-texture-glint/glint' if glint else 'minecraft:zombie'))
        c,s=math.cos(math.pi/18)*0.16,math.sin(math.pi/18)*0.16
        filt='Linear' if glint else 'Nearest';address='Repeat' if glint else 'ClampToEdge'
        draw.update(mesh_key=i+1,mesh_generation=1,texture_id=ids[name],command_index=i+1,instance_count=1,section_index=0,
            projection='Perspective',standard_foil=glint,texture_width=ref.TEXTURES[name][0],texture_height=ref.TEXTURES[name][1],view_matrix=pose,
            program='vulkanic:builtin/'+('direct_standard_item_foil_v1' if glint else 'direct_terrain_cutout_v1'),material_mode=4 if glint else 2,
            depth_policy=2 if glint else 1,depth_compare='Equal' if glint else 'LessOrEqual',depth_write=not glint,blend='Glint' if glint else 'Disabled',
            sampler=f'SamplerDesc {{ label: "test", min_filter: {filt}, mag_filter: {filt}, mip_filter: Nearest, address_u: {address}, address_v: {address}, address_w: {address}, comparison: None }}',
            uploaded_instances=[dict(model_pose=pose,color=[((tint>>shift)&255)/255 for shift in (16,8,0,24)],
                material=[0.0,0.0,0.0,18.0] if glint else [0.1,0.0,0.0,114.0],
                foil=[c,-s,-10000/110000,0.0,s,c,10000/30000,0.0,0.5,0.0,0.0,0.0] if glint else None)])
    return frozen,native,owner,ack,capture


class LeatherDrawTest(unittest.TestCase):
    def test_complete_contract_and_substitution_rejection(self):
        args=fixture();self.assertFalse(ref.validate_draws(*args)['capability_admitted'])
        for mutate in [lambda n:n['draws'][0].update(texture_id=4),
            lambda n:n['draws'][0]['uploaded_instances'][0].update(color=[1.0]*4),
            lambda n:n['semantic_instances'][0].update(model_submission_order=3),
            lambda n:n['draws'][0]['uploaded_instances'][0]['model_pose'].__setitem__(12,0.01),
            lambda n:n['draws'][-1].update(blend='Disabled'),
            lambda n:n.update(correlation_id=9),
            lambda n:n['model_texture_sources'].pop(),
            lambda n:n['model_texture_sources'][0].update(png_hex='00'),
            lambda n:n['draws'].insert(0,n['draws'].pop())]:
            bad=copy.deepcopy(args);mutate(bad[1])
            with self.assertRaises((ValueError,OSError)):ref.validate_draws(*bad)
