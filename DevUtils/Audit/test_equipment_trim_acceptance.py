"""Trim admission keeps the original control and independent evidence gates."""
import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import equipment_reference as ref
from equipment_trim_anchors import TRIM_REFERENCES
import test_equipment_reference as native_test

class TrimAcceptanceTest(unittest.TestCase):
    def test_trim_fixture_requires_exact_components_and_explicit_scope(self):
        receipt=dict(schema='equipped-zombie-moving-v1',requested=True,mode='foil',ready=True,
            speed=.5,strength=.5,complete=True,x=146.6949012917378,y=99.57557514877936,
            z=529.4806875161336,yaw=-75.9375,bodyYaw=-75.9375,headYaw=-75.9375,
            trimVariant='gold-spire-decal',equipment=[dict(slot=slot,item='minecraft:diamond_'+item,
                count=1,foil=True,trim=dict(material='minecraft:gold',patternAsset='minecraft:spire',
                decal=True,patternKind='inline')) for slot,item in
                [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]])
        options=dict(phase=10000,stepped=True,trim='gold-spire-decal')
        ref.validate_fixture(receipt,'foil',**options)
        ordinary=copy.deepcopy(receipt);ordinary['trimVariant']='gold-spire'
        for piece in ordinary['equipment']:piece['trim'].update(decal=False,patternKind='registry')
        ref.validate_fixture(ordinary,'foil',phase=10000,stepped=True,trim='gold-spire')
        for key,value in [('decal',True),('decal',0),('patternKind','inline')]:
            bad=copy.deepcopy(ordinary);bad['equipment'][0]['trim'][key]=value
            with self.assertRaises(ValueError):ref.validate_fixture(bad,'foil',phase=10000,stepped=True,trim='gold-spire')

        with self.assertRaises(ValueError):ref.validate_fixture(receipt,'foil',phase=10000,stepped=True)
        for key,value in [('material','minecraft:iron'),('decal',1),('patternKind','registry')]:
            bad=copy.deepcopy(receipt);bad['equipment'][0]['trim'][key]=value
            with self.assertRaises(ValueError):ref.validate_fixture(bad,'foil',**options)

    def test_ordinary_registry_components_and_independent_pixels(self):
        from equipment_ordinary_trim_anchors import ORDINARY_TRIM_REFERENCES
        for pose,reference in enumerate(ORDINARY_TRIM_REFERENCES):
            image=Image.new('RGB',(1280,720))
            for x,y,pixels in reference['probes']:
                for i,rgb in enumerate(pixels):image.putpixel((x-1+i%3,y-1+i//3),tuple(rgb))
            self.assertTrue(ref.compare_images(image,image,'foil',phase=10000,pose=pose,trim='gold-spire')['passed'])
            x,y,_=reference['probes'][0];image.putpixel((x,y),(255,255,255))
            with self.assertRaises(ValueError):ref.compare_images(image,image,'foil',phase=10000,pose=pose,trim='gold-spire')

    def test_launch_cannot_silently_ignore_unknown_or_duplicate_trim_options(self):
        args=['-Dmattmc.dev.graphicsAuditEquipment=foil','-Dmattmc.dev.equipmentFoilPhase=10000',
              '-Dmattmc.dev.equipmentFoilPoseStep=8000','-Dmattmc.dev.equipmentTickStepping=true',
              '-Dmattmc.dev.equipmentFoilTiming=true']
        for trims in [['unknown-trim'],['gold-spire-decal','gold-spire-decal']]:
            result=ref.report({'passed':True,'pairs':[{}]},args+[
                '-Dmattmc.dev.graphicsAuditEquipmentTrim='+trim for trim in trims])
            self.assertFalse(result['passed']);self.assertIn('trim request',result['reason'])

    def test_all_five_trim_anchors_reject_wrong_shared_pixels_and_unknown_scopes(self):
        for pose,reference in enumerate(TRIM_REFERENCES):
            image=Image.new('RGB',(1280,720))
            for x,y,pixels in reference['probes']:
                for i,rgb in enumerate(pixels):image.putpixel((x-1+i%3,y-1+i//3),tuple(rgb))
            self.assertTrue(ref.compare_images(image,image,'foil',phase=10000,pose=pose,trim='gold-spire-decal')['passed'])
            for x,y,_ in reference['probes']:
                bad=image.copy();bad.putpixel((x,y),(255,255,255))
                with self.assertRaises(ValueError):ref.compare_images(bad,bad,'foil',phase=10000,pose=pose,trim='gold-spire-decal')
            for trim,phase in [('unknown-trim',10000),('gold-spire-decal',40000),('gold-spire-decal',None)]:
                with self.assertRaises(ValueError):ref.compare_images(image,image,'foil',phase=phase,pose=pose,trim=trim)

    def test_trim_native_scope_keeps_all_control_and_depth_checks(self):
        native,owner,ack,capture=native_test.EquipmentNativeReferenceTest().evidence()
        for semantic in native['semantic_instances'][4:]:semantic['foil'].update(clock_millis=2505,speed=0.5)
        for draw in native['draws'][:4]:draw['uploaded_instances'][0]['material']=[.1,0,0,114]
        for draw in native['draws'][4:]:
            foil=draw['uploaded_instances'][0]['foil'];foil[2]=-10020/110000;foil[6]=10020/30000
        for i in range(4):
            semantic=copy.deepcopy(native['semantic_instances'][i]);semantic['mesh_key']=100+i
            draw=copy.deepcopy(native['draws'][i]);draw.update(mesh_key=100+i,command_index=100+i,
                depth_policy=3,depth_compare='Equal',texture_width=2048,texture_height=1024,index_count=108 if i<2 else 72)
            native['semantic_instances'].append(semantic);native['draws'].append(draw)
        options=dict(phase=10000,per_face=True,trim='gold-spire-decal',trim_sources={'test':'source'})
        with patch('equipment_trim_reference.native_atlas_sources',return_value={'passed':True}) as source:
            self.assertTrue(ref.validate_native(native,owner,ack,capture,'foil',**options)['passed'])
            source.assert_called_once_with(native,options['trim_sources'],native['deterministic_rendered_frame_index'])
            for index,key,value in [(8,'depth_write',False),(8,'depth_compare','LessOrEqual'),(8,'material_mode',3),
                                    (0,'depth_policy',3),(4,'standard_foil',False)]:
                bad=copy.deepcopy(native);bad['draws'][index][key]=value
                with self.assertRaises(ValueError):ref.validate_native(bad,owner,ack,capture,'foil',**options)
            bad=copy.deepcopy(native);bad['semantic_instances'].pop()
            with self.assertRaises(ValueError):ref.validate_native(bad,owner,ack,capture,'foil',**options)
        ordinary=copy.deepcopy(native);ordinary['texture_sources']=[{'texture_id':4242}]
        for draw in ordinary['draws'][8:]:draw.update(depth_policy=1,depth_compare='LessOrEqual',texture_id=4242)
        ordinary_options=dict(options,trim='gold-spire')
        with patch('equipment_trim_reference.native_atlas_sources',return_value={'passed':True}) as source:
            self.assertTrue(ref.validate_native(ordinary,owner,ack,capture,'foil',**ordinary_options)['passed'])
            source.assert_called_once_with(ordinary,options['trim_sources'],ordinary['deterministic_rendered_frame_index'],decal=False)
            for index,key,value in [(8,'depth_policy',3),(8,'depth_compare','Equal'),(8,'texture_id',9999),
                                    (8,'depth_write',False),(0,'texture_id',4242),(8,'material_mode',3)]:
                bad=copy.deepcopy(ordinary);bad['draws'][index][key]=value
                with self.assertRaises(ValueError):ref.validate_native(bad,owner,ack,capture,'foil',**ordinary_options)
        with patch('equipment_trim_reference.native_atlas_sources',side_effect=ValueError('wrong source')):
            with self.assertRaises(ValueError):ref.validate_native(native,owner,ack,capture,'foil',**options)
        with self.assertRaises(ValueError):ref.validate_native(native,owner,ack,capture,'foil',phase=10000,per_face=True)

if __name__=='__main__':unittest.main()
