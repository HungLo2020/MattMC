"""Reject plausible pixels when pose, provenance or presentation is wrong."""
import copy
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import shield_static_blocking_reference as ref


class StaticBlockingReferenceTest(unittest.TestCase):
    def test_pixels_require_frozen_anchor_and_both_regions(self):
        from PIL import Image
        a=Image.new('RGB',(1280,720))
        for _,x,y,colors in ref.PROBES:
            a.paste(tuple(colors[0]),(x-1,y-1,x+2,y+2))
            for index,color in enumerate(colors):
                a.putpixel((x-1+index%3,y-1+index//3),tuple(color))
        def check(baseline,current):
            return ref._compare_images(baseline,current,ref.PROBES,'test',ref.REGIONS)['passed']
        self.assertTrue(check(a,a.copy()))
        b=a.copy();b.putpixel((700,480),(255,255,255));self.assertFalse(check(a,b))
        self.assertFalse(check(b,b))  # identical wrong outputs cannot establish parity
        b=a.copy();b.paste((255,255,255),(850,500,1000,630));self.assertFalse(check(a,b))

    def test_offhand_pixels_require_both_shields_and_icons(self):
        from PIL import Image
        a=Image.new('RGB',(1280,720))
        for _,x,y,colors in ref.OFFHAND_PROBES:
            for index,color in enumerate(colors):a.putpixel((x-1+index%3,y-1+index//3),tuple(color))
        def check(b,c):return ref._compare_images(b,c,ref.OFFHAND_PROBES,'test',ref.OFFHAND_REGIONS)['passed']
        self.assertTrue(check(a,a.copy()))
        for pos in ((400,460),(980,610),(307,684),(394,684)):
            b=a.copy();b.putpixel(pos,(255,255,255))
            self.assertFalse(check(a,b));self.assertFalse(check(b,b))
        b=a.copy();b.paste((255,255,255),(850,550,950,630));self.assertFalse(check(a,b))

    def test_base_and_foil_references_cannot_substitute_for_each_other(self):
        from PIL import Image
        def raster(probes):
            image=Image.new('RGB',(1280,720))
            for _,x,y,colors in probes:
                for index,color in enumerate(colors):image.putpixel((x-1+index%3,y-1+index//3),tuple(color))
            return image
        for pose in ('blocking','blocking-offhand'):
            base,regions=ref.REFERENCES['shield-patterns',pose]
            foil,_=ref.REFERENCES['shield-patterns-foil',pose]
            a,b=raster(base),raster(foil)
            self.assertTrue(ref._compare_images(a,a,base,'test',regions)['passed'])
            self.assertFalse(ref._compare_images(b,b,base,'test',regions)['passed'])
            self.assertFalse(ref._compare_images(a,a,foil,'test',regions)['passed'])

    def test_plain_and_patterned_icon_references_cannot_substitute(self):
        from PIL import Image
        def raster(probes):
            image=Image.new('RGB',(1280,720))
            for _,x,y,colors in probes:
                for index,color in enumerate(colors):image.putpixel((x-1+index%3,y-1+index//3),tuple(color))
            return image
        for pose in ('blocking','blocking-offhand'):
            plain,regions=ref.REFERENCES['shield',pose]
            patterned,_=ref.REFERENCES['shield-patterns',pose]
            a,b=raster(plain),raster(patterned)
            self.assertTrue(ref._compare_images(a,a,plain,'test',regions)['passed'])
            self.assertFalse(ref._compare_images(b,b,plain,'test',regions)['passed'])
            self.assertFalse(ref._compare_images(a,a,patterned,'test',regions)['passed'])

    def test_complete_static_grid_rejects_wrong_variant_anchors(self):
        from PIL import Image
        def raster(probes):
            image=Image.new('RGB',(1280,720))
            for _,x,y,colors in probes:
                for index,color in enumerate(colors):image.putpixel((x-1+index%3,y-1+index//3),tuple(color))
            return image
        fixtures=('shield','shield-foil','shield-patterns','shield-patterns-foil')
        for pose in ('blocking','blocking-offhand'):
            images={fixture:raster(ref.REFERENCES[fixture,pose][0]) for fixture in fixtures}
            for expected in fixtures:
                probes,regions=ref.REFERENCES[expected,pose]
                for observed in fixtures:
                    with self.subTest(pose=pose,expected=expected,observed=observed):
                        image=images[observed]
                        self.assertEqual(ref._compare_images(image,image,probes,'test',regions)['passed'],expected==observed)

    def test_selected_owner_cannot_be_substituted_or_incomplete(self):
        owner=dict(artifact_class='rust_vulkan_whole_frame_gameplay_correlation',
            gameplay_frame_id=8,correlation_id=8,gal_submission_id=9,
            acquired_swapchain_image=12,presented_swapchain_image=12,
            deterministic_rendered_frame_index=7,rust_whole_frame_presenter=True,
            java_vulkan_frame_execution=False,same_acquired_presented_image=True,
            world_lod_instances=0,world_lod_route_selected=False,present_completed_submission_id=9)
        ack=dict(renderedFrameIndex=7,wholeFramePresentationCorrelation=dict(
            gameplayFrameId=8,correlationId=8,submissionId=9,acquiredSwapchainImage=12,presentedSwapchainImage=12))
        capture=dict(renderedFrameIndex=7)
        ref.validate_owner(owner,ack,capture)
        for key,value in (('correlation_id',10),('gal_submission_id',True),
                          ('present_completed_submission_id',8),('world_lod_instances',1),
                          ('java_vulkan_frame_execution',True),('rust_whole_frame_presenter',False),
                          ('presented_swapchain_image',13),('deterministic_rendered_frame_index',6)):
            with self.subTest(key=key),self.assertRaises(ValueError):
                ref.validate_owner(dict(owner,**{key:value}),ack,capture)
        with self.assertRaises(ValueError):ref.validate_owner(owner,ack,dict(renderedFrameIndex=6))

    def test_execution_requires_actual_healthy_pair_and_memory(self):
        a=dict(mode=dict(name='current-rust-vulkan-shaders-off'),tool='capture',capture=dict(exit_code=0),
            validation=dict(complete=True,crash_free=True,device_loss_free=True,rss_guard_triggered=False,
                            orphan_process_detected=False,vulkan_validation_clean=True),
            metrics=dict(rss_and_native_memory=dict(client_observation=dict(complete=True,
                provider='linux-proc-status',sample_count=4,peak_rss_kb=100,peak_hwm_kb=101))))
        ref.validate_execution(a,'current-rust-vulkan-shaders-off')
        for field,value in (('complete',False),('orphan_process_detected',True),('vulkan_validation_clean',False)):
            b=copy.deepcopy(a);b['validation'][field]=value
            with self.assertRaises(ValueError):ref.validate_execution(b,'current-rust-vulkan-shaders-off')
        b=copy.deepcopy(a);b['capture']['exit_code']=False
        with self.assertRaises(ValueError):ref.validate_execution(b,'current-rust-vulkan-shaders-off')
        b=copy.deepcopy(a);b['metrics']['rss_and_native_memory']['client_observation']['sample_count']=True
        with self.assertRaises(ValueError):ref.validate_execution(b,'current-rust-vulkan-shaders-off')
        with self.assertRaises(ValueError):ref.validate_execution(a,'frozen-opengl-shaders-off')

    def test_document_requires_blocking_fixture_and_completed_reload(self):
        c=dict(window=dict(width=1280,height=720),gameTime=6000,dimension='minecraft:overworld',
               position=dict(x=150.5,y=100.0,z=530.5),shaderEnabled='false',
               requestedYaw=105.0,observedYaw=105.0,requestedPitch=10.0,observedPitch=10.0)
        d=dict(hotbarItemFixture='shield-patterns-foil',shieldPose='blocking',shieldUseHand='MAIN_HAND',
               cameraType='FIRST_PERSON',selectedHotbarSlot=1,captures=[c],
               shieldPatternFixture=dict(fixture='held-shield-patterns-foil-v1',selectedSlot=1,
                   mainHand='minecraft:shield',count=1,foil=True,usingItem=True,baseColor='yellow',
                   patterns='minecraft:cross:red,minecraft:border:blue',complete=True,speed=0.0,strength=0.5),
               worldResourceReload=dict(schema='normal-world-resource-reload-v1',requested=True,
                   futureComplete=True,complete=True,presentations=2,selectedBefore=['vanilla'],selectedAtCapture=['vanilla']))
        def check(value):return ref.validate_document(value,'shield-patterns-foil','blocking')
        check(d)  # Frozen has an observed position but no separate requestedPosition.
        base=copy.deepcopy(d);base['hotbarItemFixture']='shield-patterns'
        base['shieldPatternFixture'].update(fixture='held-shield-patterns-v1',foil=False)
        del base['shieldPatternFixture']['speed'],base['shieldPatternFixture']['strength']
        ref.validate_document(base,'shield-patterns','blocking')
        plain=copy.deepcopy(d);plain['hotbarItemFixture']='shield'
        plain['shieldFoilFixture']=plain.pop('shieldPatternFixture')
        plain['shieldFoilFixture'].update(fixture='held-shield-v1',foil=False)
        del plain['shieldFoilFixture']['baseColor'],plain['shieldFoilFixture']['patterns']
        ref.validate_document(plain,'shield','blocking')
        plain_foil=copy.deepcopy(plain);plain_foil['hotbarItemFixture']='shield-foil'
        plain_foil['shieldFoilFixture']['foil']=True
        ref.validate_document(plain_foil,'shield-foil','blocking')
        plain_foil['shieldFoilFixture']['foil']=False
        with self.assertRaises(ValueError):ref.validate_document(plain_foil,'shield-foil','blocking')
        del plain['shieldFoilFixture']['speed']
        with self.assertRaises(ValueError):ref.validate_document(plain,'shield','blocking')
        base['shieldPatternFixture']['foil']=True
        with self.assertRaises(ValueError):ref.validate_document(base,'shield-patterns','blocking')
        off=copy.deepcopy(d);off.update(shieldPose='blocking-offhand',shieldUseHand='OFF_HAND')
        ref.validate_document(off,'shield-patterns-foil','blocking-offhand')
        off['shieldUseHand']='MAIN_HAND'
        with self.assertRaises(ValueError):ref.validate_document(off,'shield-patterns-foil','blocking-offhand')
        for field,value in (('shieldUseHand','OFF_HAND'),('shieldPose','idle'),('selectedHotbarSlot',True)):
            with self.subTest(field=field),self.assertRaises(ValueError):check(dict(d,**{field:value}))
        for field,value in (('usingItem',False),('count',True),('strength',0.0)):
            b=copy.deepcopy(d);b['shieldPatternFixture'][field]=value
            with self.assertRaises(ValueError):check(b)
        for field,value in (('complete',False),('presentations',1),('selectedAtCapture',['vanilla','other'])):
            b=copy.deepcopy(d);b['worldResourceReload'][field]=value
            with self.assertRaises(ValueError):check(b)
        b=copy.deepcopy(d);b['captures'][0]['requestedPosition']=dict(x=0,y=100,z=530.5)
        with self.assertRaises(ValueError):check(b)

    def test_uninspected_poses_and_materials_remain_closed(self):
        for fixture,scenario,pose in (('shield-unknown','','blocking'),('shield-patterns','','idle'),
                                     ('shield-foil','shield-alpha','blocking-offhand'),
                                     ('shield-patterns-foil','shield-alpha','blocking')):
            result=ref.report(dict(passed=True,pairs=[{}]),fixture,scenario,pose)
            self.assertFalse(result['passed']);self.assertFalse(result['capability_admitted'])


if __name__ == '__main__':unittest.main()
