import copy
import io
import sys
import unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from wolf_draw_reference import LAYERS,ALPHA,compare_draws,compare_owned_inputs


def fixture():
    view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    frozen=dict(schema='wolf-model-inputs-v1',complete=True,enabled=True,gpuReadback=False,
                capabilityAdmitted=False,renderedFrameIndex=7,completedDraws=[])
    native=dict(schema='wolf-submission-inputs-v1',complete=True,gpu_readback=False,capability_admitted=False,
                deterministic_rendered_frame_index=19,draws=[],semantic_instances=[],texture_sources=[])
    root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/minecraft'
    for i,(texture,pipeline,digest,order,projection) in enumerate(LAYERS):
        identity=i+1
        frozen['completedDraws'].append(dict(textureDeclaration=texture,pipeline='minecraft:pipeline/'+pipeline,
            depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend=ALPHA if i==1 else 'none',
            vertices=264,indices=396,view=view.copy()))
        encoded=io.BytesIO()
        with Image.open(root/texture.split(':',1)[1]) as image:
            image.convert('RGBA').save(encoded,format='PNG')
        native['texture_sources'].append(dict(texture_id=identity,width=64,height=32,encoding='png-rgba8-hex',
            png_hex=encoded.getvalue().hex()))
        native['semantic_instances'].append(dict(context='world',entity_identity='minecraft:wolf',foil=None,
            mesh_key=identity,mesh_generation=1,model_submission_order=order,projection=projection,
            flags=0 if projection is None else 4))
        native['draws'].append(dict(texture_id=identity,mesh_key=identity,mesh_generation=1,
            entity_identity='minecraft:wolf',projection=projection,index_count=396,instance_count=1,
            standard_foil=False,depth_compare='LessOrEqual',depth_write=True,depth_policy=1,
            material_mode=7 if i==1 else 2,blend='Alpha' if i==1 else 'Disabled',
            program='vulkanic:builtin/'+('direct_model_translucent_cutout_v1' if i==1 else 'direct_terrain_cutout_v1'),
            view_matrix=view.copy(),command_index=identity*5))
    return frozen,native


class WolfDrawReferenceTest(unittest.TestCase):
    def test_undamaged_requires_exact_body_and_armor_membership(self):
        a,b=fixture()
        with self.assertRaises(ValueError):compare_draws(a,b,7,19,mode='none')
        a['completedDraws'].pop(1)
        for key in ('draws','semantic_instances','texture_sources'):b[key].pop(1)
        self.assertEqual(2,compare_draws(a,b,7,19,mode='none')['draws'])
        for mode in ('high','low','medium'):
            with self.assertRaises(ValueError):compare_draws(a,b,7,19,mode=mode)
        for key in ('draws','semantic_instances','texture_sources'):
            bad=copy.deepcopy(b);bad[key].append(copy.deepcopy(bad[key][-1]))
            with self.assertRaises(ValueError):compare_draws(a,bad,7,19,mode='none')
        bad=copy.deepcopy(b);bad['draws'][1].update(material_mode=7,blend='Alpha')
        with self.assertRaises(ValueError):compare_draws(a,bad,7,19,mode='none')

    def test_crack_texture_modes_cannot_be_interchanged(self):
        for mode in ('low','medium'):
            a,b=fixture()
            with self.assertRaises(ValueError):compare_draws(a,b,7,19,mode=mode)
            texture=f'minecraft:textures/entity/wolf/wolf_armor_crackiness_{mode}.png'
            a['completedDraws'][1]['textureDeclaration']=texture
            # A matching name alone cannot certify a different uploaded image.
            with self.assertRaises(ValueError):compare_draws(a,b,7,19,mode=mode)
            encoded=io.BytesIO()
            with Image.open(Path(__file__).resolve().parents[2]/'src/main/resources/assets/minecraft'/texture.split(':',1)[1]) as im:
                im.convert('RGBA').save(encoded,format='PNG')
            b['texture_sources'][1]['png_hex']=encoded.getvalue().hex()
            self.assertTrue(compare_draws(a,b,7,19,mode=mode)['passed'])
            for other in set(('high','low','medium'))-{mode}:
                with self.assertRaises(ValueError):compare_draws(a,b,7,19,mode=other)

    def test_owned_inputs_reject_cross_frame_and_upload_substitution(self):
        a,b=fixture()
        state=dict(values=[1.0]+[0.0]*11,angry=False,sitting=False,baby=False,light=15728640,
                   texture=LAYERS[0][0])
        paths=['root','root/body','root/head','root/head/real_head','root/left_front_leg',
               'root/left_hind_leg','root/right_front_leg','root/right_hind_leg',
               'root/tail','root/tail/real_tail','root/upper_body']
        matrix=a['completedDraws'][0]['view']
        a.update(models=[dict(state=state,parts={p:dict(pose=[0.0]*9,visible=True,skipDraw=False) for p in paths})],
                 transforms=[dict(state=state,modelView=matrix.copy(),normal=[0.0]*9)],
                 simulation=dict(schema='wolf-game-tick-step-v1',pose=0,targetTicks=0,clientTicks=0,
                                 serverTicks=0,clientFrozen=True,serverFrozen=True,complete=True))
        current=copy.deepcopy(a);current['renderedFrameIndex']=19
        owner=dict(gameplay_frame_id=10,correlation_id=11,gal_submission_id=12,
                   acquired_swapchain_image=13,presented_swapchain_image=13,
                   deterministic_rendered_frame_index=19,present_completed_submission_id=12,
                   artifact_class='rust_vulkan_whole_frame_gameplay_correlation',rust_whole_frame_presenter=True,
                   java_vulkan_frame_execution=False,same_acquired_presented_image=True,
                   world_lod_instances=0,world_lod_route_selected=False)
        ack=dict(renderedFrameIndex=19,wholeFramePresentationCorrelation=dict(gameplayFrameId=10,
                 correlationId=11,submissionId=12,acquiredSwapchainImage=13,presentedSwapchainImage=13))
        capture=dict(renderedFrameIndex=19)
        b.update(gameplay_frame_id=10,correlation_id=11,gal_submission_id=12)
        for i,(source,draw) in enumerate(zip(b['semantic_instances'],b['draws'])):
            source.update(section_index=2**32-1,model_pose=matrix.copy())
            draw.update(section_index=0,uploaded_instances=[dict(model_pose=matrix.copy(),foil=None,
                        material=[0.0,0.0,0.0,82.0] if i==1 else [0.1,0.0,0.0,114.0])],
                        sampler='SamplerDesc { label: "wolf", min_filter: Nearest, mag_filter: Nearest, mip_filter: Nearest, address_u: ClampToEdge, address_v: ClampToEdge, address_w: ClampToEdge, comparison: None }')
        def check(native=b,owned=owner):
            return compare_owned_inputs(a,current,native,owned,ack,capture,pose=0)
        result=check()
        self.assertTrue(result['presentation_ownership_verified'])
        self.assertTrue(result['instance_uploads_verified'])
        self.assertFalse(result['geometry_correspondence_verified'])
        self.assertFalse(result['capability_admitted'])
        mutations=[lambda v:v.update(gal_submission_id=13),lambda v:v.update(correlation_id=True),
            lambda v:v['draws'][0].update(section_index=True),
            lambda v:v['draws'][0].update(uploaded_instances=[]),
            lambda v:v['draws'][0]['uploaded_instances'][0]['model_pose'].__setitem__(0,1.000001),
            lambda v:v['semantic_instances'][0]['model_pose'].__setitem__(0,1.000001),
            lambda v:v['draws'][1]['uploaded_instances'][0].update(material=[0.1,0.0,0.0,114.0]),
            lambda v:v['draws'][0]['uploaded_instances'][0].update(foil={}),
            lambda v:v['draws'][0].update(sampler=v['draws'][0]['sampler'].replace('mag_filter: Nearest','mag_filter: Linear'))]
        for mutate in mutations:
            bad=copy.deepcopy(b);mutate(bad)
            with self.assertRaises(ValueError):check(bad)
        with self.assertRaises(ValueError):check(owned=dict(owner,java_vulkan_frame_execution=True))
        undamaged_a,undamaged_b=copy.deepcopy(a),copy.deepcopy(b)
        undamaged_a['completedDraws'].pop(1)
        for key in ('draws','semantic_instances','texture_sources'):undamaged_b[key].pop(1)
        self.assertTrue(compare_owned_inputs(undamaged_a,current,undamaged_b,owner,ack,capture,
                        pose=0,mode='none')['instance_uploads_verified'])
        undamaged_b['draws'][1]['uploaded_instances'][0]['material']=[0.0,0.0,0.0,82.0]
        with self.assertRaises(ValueError):
            compare_owned_inputs(undamaged_a,current,undamaged_b,owner,ack,capture,pose=0,mode='none')


    def test_actual_asset_pixels_and_order_remain_supplemental(self):
        a,b=fixture();result=compare_draws(a,b,7,19)
        self.assertTrue(result['texture_correspondence_verified'])
        for key in ('capability_admitted','geometry_correspondence_verified','presentation_ownership_verified'):
            self.assertFalse(result[key])

    def test_rejects_reordered_missing_extra_and_misdeclared_draws(self):
        a,b=fixture()
        mutations=[lambda v:v['draws'].reverse(),lambda v:v['draws'].pop(),
            lambda v:v['semantic_instances'].append(copy.deepcopy(v['semantic_instances'][0])),
            lambda v:v['draws'][1].update(depth_write=False),
            lambda v:v['draws'][1].update(material_mode=True),
            lambda v:v['draws'][1].update(mesh_key=True),
            lambda v:v['draws'][1].update(texture_id=1),
            lambda v:v['draws'][1].update(command_index=1),
            lambda v:v['semantic_instances'][2].update(model_submission_order=True),
            lambda v:v['semantic_instances'][2].update(model_submission_order=0),
            lambda v:v['draws'][1]['view_matrix'].__setitem__(0,1.000001)]
        for mutate in mutations:
            bad=copy.deepcopy(b);mutate(bad)
            with self.assertRaises(ValueError):compare_draws(a,bad,7,19)
        bad=copy.deepcopy(a);bad['completedDraws'][1:]=reversed(bad['completedDraws'][1:])
        with self.assertRaises(ValueError):compare_draws(bad,b,7,19)

    def test_rejects_stale_frames_and_corrupt_or_excessive_sources(self):
        a,b=fixture()
        for frame in (True,0,20):
            with self.assertRaises(ValueError):compare_draws(a,b,7,frame)
        for values in (dict(width=True),dict(width=4096),dict(png_hex='00'),dict(png_hex='00'*16385),dict(texture_id=2)):
            bad=copy.deepcopy(b);bad['texture_sources'][0].update(values)
            with self.assertRaises((ValueError,OSError)):compare_draws(a,bad,7,19)

if __name__=='__main__':unittest.main()
