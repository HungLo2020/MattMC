"""Equipment prerequisites reject missing glint and plausible wrong evidence."""
import copy
from pathlib import Path
import sys
import unittest
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Common'))
import equipment_reference as ref


def anchored(mode):
    image = Image.new('RGB', (1280, 720))
    for x, y, pixels in ref.REFERENCES[mode]['probes']:
        for index, rgb in enumerate(pixels):
            image.putpixel((x-1+index%3, y-1+index//3), tuple(rgb))
    return image


class EquipmentReferenceTest(unittest.TestCase):
    def test_missing_glint_and_identical_wrong_outputs_rejected(self):
        base, foil = anchored('base'), anchored('foil')
        self.assertFalse(ref.compare_images(foil, foil.copy(), 'foil')['capability_admitted'])
        for left, right in ((foil, base), (base, base)):
            with self.assertRaises(ValueError):
                ref.compare_images(left, right, 'foil')

    def test_each_equipment_probe_and_unprobed_region_required(self):
        a = anchored('base')
        for x, y, _ in ref.REFERENCES['base']['probes']:
            b = a.copy(); b.putpixel((x,y), (255,255,255))
            with self.assertRaises(ValueError): ref.compare_images(a,b,'base')
        b = a.copy(); b.paste((255,255,255),(600,365,675,400))
        with self.assertRaises(ValueError): ref.compare_images(a,b,'base')

    def test_unknown_scope_and_viewport_rejected(self):
        a = anchored('base')
        with self.assertRaises(ValueError): ref.compare_images(a,a,'moving')
        with self.assertRaises(ValueError): ref.compare_images(a,a.resize((640,360)),'base')

    def test_fixture_requires_all_slots_exact_pose_and_typed_readiness(self):
        r = dict(schema='equipped-zombie-stopped-v1', requested=True, mode='foil', ready=True,
            speed=0.0,strength=0.5,complete=True,x=146.6949012917378,y=99.57557514877936,
            z=529.4806875161336,yaw=-75.9375,bodyYaw=-75.9375,headYaw=-75.9375,
            equipment=[dict(slot=s,item='minecraft:diamond_'+i,count=1,foil=True)
                for s,i in [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]])
        ref.validate_fixture(r,'foil')
        moving=dict(r,schema='equipped-zombie-moving-v1',speed=0.5)
        ref.validate_fixture(moving,'foil',phase=10000,stepped=True)
        with self.assertRaises(ValueError):ref.validate_fixture(moving,'foil',phase=10000)
        moving['yaw']=-75.0
        ref.validate_fixture(moving,'foil',phase=10000)
        with self.assertRaises(ValueError):ref.validate_fixture(moving,'foil',phase=10000,stepped=True)
        variants=[]
        for key,value in [('ready',1),('speed',0.5),('yaw',0.0),('mode','base')]:
            b=copy.deepcopy(r);b[key]=value;variants.append(b)
        b=copy.deepcopy(r);b['equipment'].pop();variants.append(b)
        b=copy.deepcopy(r);b['equipment'][0]['foil']=False;variants.append(b)
        for b in variants:
            with self.assertRaises(ValueError):ref.validate_fixture(b,'foil')

    def test_leather_fixture_requires_exact_ordinary_dye_and_inspected_scope(self):
        r = dict(schema='equipped-zombie-moving-v1', requested=True, mode='foil', ready=True,
            speed=0.5, strength=0.5, complete=True, x=146.6949012917378, y=99.57557514877936,
            z=529.4806875161336, yaw=-75.9375, bodyYaw=-75.9375, headYaw=-75.9375,
            materialVariant='leather-dyed', equipment=[dict(slot=s,item='minecraft:leather_'+i,
                count=1,foil=True,dyedColor=0x3366CC)
                for s,i in [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]])
        args=dict(phase=10000,stepped=True,material='leather-dyed')
        ref.validate_fixture(r,'foil',**args)
        # Adult texture/geometry evidence must never admit a newly selected age.
        for age in ({'ageVariant':'baby','isBaby':True}, {'ageVariant':'baby','isBaby':False},
                    {'isBaby':True}, {'ageVariant':'unknown'}):
            with self.assertRaises(ValueError): ref.validate_fixture(r|age,'foil',**args)
        for i in range(4):
            for value in (None,False,float(0x3366CC),0x3366CD):
                bad=copy.deepcopy(r)
                if value is None: del bad['equipment'][i]['dyedColor']
                else: bad['equipment'][i]['dyedColor']=value
                with self.assertRaises(ValueError): ref.validate_fixture(bad,'foil',**args)
            bad=copy.deepcopy(r);bad['equipment'][i]['item']=bad['equipment'][i]['item'].replace('leather_', 'diamond_')
            with self.assertRaises(ValueError): ref.validate_fixture(bad,'foil',**args)
        for change in (dict(material=None),dict(material='unknown'),dict(phase=40000),dict(stepped=False),dict(trim='gold-spire')):
            with self.assertRaises(ValueError): ref.validate_fixture(r,'foil',**(args|change))


class EquipmentNativeReferenceTest(unittest.TestCase):
    def evidence(self):
        import math
        owner=dict(artifact_class='rust_vulkan_whole_frame_gameplay_correlation',
            gameplay_frame_id=8,correlation_id=8,gal_submission_id=9,
            acquired_swapchain_image=12,presented_swapchain_image=12,
            deterministic_rendered_frame_index=7,rust_whole_frame_presenter=True,
            java_vulkan_frame_execution=False,same_acquired_presented_image=True,
            world_lod_instances=0,world_lod_route_selected=False,present_completed_submission_id=9)
        ack=dict(renderedFrameIndex=7,wholeFramePresentationCorrelation=dict(
            gameplayFrameId=8,correlationId=8,submissionId=9,acquiredSwapchainImage=12,presentedSwapchainImage=12))
        capture=dict(renderedFrameIndex=7)
        view=[1.0 if i%5==0 else 0.0 for i in range(16)]
        r=dict(schema='equipment-submission-inputs-v1',complete=True,gpu_readback=False,
            capability_admitted=False,gameplay_frame_id=8,correlation_id=8,gal_submission_id=9,
            deterministic_rendered_frame_index=7,world_view_matrix=view,semantic_instances=[],draws=[])
        for i in range(8):
            foil=i>=4
            semantic=dict(context='world',flags=0 if foil else 4,
                foil=dict(kind='Armor',clock_millis=123,speed=0.0,strength=0.5) if foil else None,
                projection='Perspective',model_pose=view,section_index=2**32-1,mesh_key=i+1,mesh_generation=1)
            r['semantic_instances'].append(semantic)
            c,s=math.cos(math.pi/18)*0.16,math.sin(math.pi/18)*0.16
            filt='Linear' if foil else 'Nearest';address='Repeat' if foil else 'ClampToEdge'
            r['draws'].append(dict(mesh_key=i+1,mesh_generation=1,section_index=0,instance_count=1,
                command_index=i+1,index_count=72,projection='Perspective',
                view_matrix=[v*4095/4096 if j<12 else v for j,v in enumerate(view)],
                uploaded_instances=[dict(model_pose=view,material=[0.1,0,0,50],
                    foil=[c,-s,0,0,s,c,0,0,0.5,0,0,0] if foil else None)],
                standard_foil=foil,program='vulkanic:builtin/direct_'+('standard_item_foil' if foil else 'terrain_cutout')+'_v1',
                material_mode=4 if foil else 2,depth_policy=2 if foil else 1,
                depth_compare='Equal' if foil else 'LessOrEqual',depth_write=not foil,
                blend='Glint' if foil else 'Disabled',texture_rgba_xxh32='724fc018' if foil else ('54527ceb' if i==1 else 'fa62c001'),
                texture_width=128 if foil else 64,texture_height=128 if foil else 32,
                sampler=f'SamplerDesc {{ label: "test", min_filter: {filt}, mag_filter: {filt}, mip_filter: Nearest, address_u: {address}, address_v: {address}, address_w: {address}, comparison: None }}'))
        return r,owner,ack,capture

    def test_moving_native_uvs_must_follow_actual_clock(self):
        r,o,a,c=self.evidence()
        for semantic in r['semantic_instances'][4:]:
            semantic['foil'].update(clock_millis=2505,speed=0.5)
        for draw in r['draws'][4:]:
            row=draw['uploaded_instances'][0]['foil'];row[2]=-10020/110000;row[6]=10020/30000
        self.assertTrue(ref.validate_native(r,o,a,c,'foil',phase=10000)['passed'])
        with self.assertRaises(ValueError):ref.validate_native(r,o,a,c,'foil')
        r['draws'][4]['uploaded_instances'][0]['foil'][2]=0
        with self.assertRaises(ValueError):ref.validate_native(r,o,a,c,'foil',phase=10000)

    def test_per_face_scope_requires_actual_uploaded_lighting_bit_for_every_base(self):
        r,o,a,c=self.evidence()
        with self.assertRaises(ValueError):ref.validate_native(r,o,a,c,'foil',per_face=True)
        for draw in r['draws'][:4]:
            draw['uploaded_instances'][0]['material'][3]=114
        self.assertTrue(ref.validate_native(r,o,a,c,'foil',per_face=True)['per_face_lighting'])
        with self.assertRaises(ValueError):ref.validate_native(r,o,a,c,'foil')
        for index in range(4):
            bad=copy.deepcopy(r);bad['draws'][index]['uploaded_instances'][0]['material'][3]=50
            with self.assertRaises(ValueError):ref.validate_native(bad,o,a,c,'foil',per_face=True)
        for invalid in (1,None,'true'):
            with self.assertRaises(ValueError):ref.validate_native(r,o,a,c,'foil',per_face=invalid)

    def test_native_rejects_missing_stale_or_substituted_draw_evidence(self):
        r,o,a,c=self.evidence()
        self.assertFalse(ref.validate_native(r,o,a,c,'foil')['capability_admitted'])
        mutations=[lambda x:x.update(correlation_id=7),lambda x:x.update(complete=False),
            lambda x:x['draws'].pop(),lambda x:x['draws'][0].update(mesh_key=99),
            lambda x:x['draws'][4].update(depth_write=True),lambda x:x['draws'][4].update(depth_compare='LessOrEqual'),
            lambda x:x['draws'][4].update(texture_rgba_xxh32='fa62c001'),
            lambda x:x['draws'][4]['uploaded_instances'][0]['foil'].__setitem__(0,8.0),
            lambda x:x['draws'][0]['uploaded_instances'][0]['material'].__setitem__(0,0.5),
            lambda x:x['draws'][0].update(view_matrix=x['world_view_matrix']),
            lambda x:x['semantic_instances'][4]['foil'].update(kind='Entity'),
            lambda x:x['draws'][4].update(sampler='min_filter: Linear')]
        for mutate in mutations:
            bad=copy.deepcopy(r);mutate(bad)
            with self.assertRaises(ValueError):ref.validate_native(bad,o,a,c,'foil')
        for key,value in [('java_vulkan_frame_execution',True),('present_completed_submission_id',8),('world_lod_instances',1)]:
            with self.assertRaises(ValueError):ref.validate_native(r,dict(o,**{key:value}),a,c,'foil')

class EquipmentHarnessScopeTest(unittest.TestCase):
    def test_requested_equipment_cannot_pass_without_a_real_pair(self):
        self.assertFalse(ref.report({'passed':True,'pairs':[]},
            ['-Dmattmc.dev.graphicsAuditEquipment=foil'])['passed'])
        for args in [['-Dmattmc.dev.graphicsAuditEquipment=unknown'],
                     ['-Dmattmc.dev.graphicsAuditEquipment=base','-Dmattmc.dev.graphicsAuditEquipment=foil']]:
            self.assertFalse(ref.report({'passed':True,'pairs':[{}]},args)['passed'])
        result=ref.report({},[])
        self.assertFalse(result['requested']);self.assertFalse(result['capability_admitted'])

    def test_equipped_head_route_requires_full_local_evidence(self):
        # A forged request cannot make missing native/capture data acceptable.
        with self.assertRaises(ValueError):ref.validate_local({'equipmentFixture':{'requested':True,'mode':'foil'}})



if __name__ == '__main__': unittest.main()
