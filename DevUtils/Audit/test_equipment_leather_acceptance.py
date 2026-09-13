"""Private leather acceptance must retain independent pixels and every pose."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image
import equipment_leather_acceptance as ref
import equipment_reference as equipment


class LeatherAcceptanceTest(unittest.TestCase):
    def test_every_pose_rejects_identical_wrong_pixels_and_region_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = [Path(directory)/name for name in ('frozen.png', 'current.png')]
            for age,pose,row in [(age,i,row) for age in (None,'baby')
                                 for i,row in enumerate(ref.reference(age)['poses'])]:
                image = Image.new('RGB', (1280, 720))
                for x, y, pixels in row['probes']:
                    for i, pixel in enumerate(pixels):
                        image.putpixel((x-1+i%3, y-1+i//3), tuple(pixel))
                image.save(a); image.save(b)
                self.assertFalse(ref.pixels(a,b,pose,age=age)['capability_admitted'])
                for x,y,_ in row['probes']:
                    wrong=image.copy(); wrong.putpixel((x,y),(255,255,255)); wrong.save(b)
                    with self.assertRaises(ValueError): ref.pixels(a,b,pose,age=age)
                    with self.assertRaises(ValueError): ref.pixels(b,b,pose,age=age)
                wrong=image.copy(); wrong.paste((255,255,255),tuple(ref.reference(age)['region'])); wrong.save(b)
                with self.assertRaises(ValueError): ref.pixels(a,b,pose,age=age)

    def test_baby_fixture_requires_exact_age_and_its_own_reference(self):
        fixture=copy.deepcopy(ref.BABY_REFERENCE['sourceFixture'])
        args=dict(phase=10000,stepped=True,material='leather-dyed',age='baby')
        equipment.validate_fixture(fixture,'foil',**args)
        for value in (False,1,None):
            bad=copy.deepcopy(fixture)
            if value is None:bad.pop('isBaby')
            else:bad['isBaby']=value
            with self.assertRaises(ValueError):equipment.validate_fixture(bad,'foil',**args)
        with self.assertRaises(ValueError):equipment.validate_fixture(fixture,'foil',**(args|dict(age=None)))
        with self.assertRaises(ValueError):equipment.validate_fixture(fixture,'foil',**(args|dict(material=None)))
        for age in ('adult','unknown',True):
            with self.assertRaises(ValueError):ref.reference(age)
        for pose in range(5):
            baby=ref.frozen_reference(pose,age='baby');adult=ref.frozen_reference(pose)
            self.assertNotEqual(baby['modelGeometry']['models'],adult['modelGeometry']['models'])
            self.assertTrue(all(t['state'][5]==0.5 for t in baby['entityTransforms']))
            baby['modelGeometry']['models'][0]['quads'].clear()
            self.assertTrue(ref.frozen_reference(pose,age='baby')['modelGeometry']['models'][0]['quads'])

    def test_reference_expansion_is_independent_and_complete(self):
        for pose in range(5):
            a=ref.frozen_reference(pose)
            self.assertEqual(12,len(a['modelGeometry']['models']))
            a['modelGeometry']['models'][0]['quads'].clear()
            self.assertTrue(ref.frozen_reference(pose)['modelGeometry']['models'][0]['quads'])
        for pose in (True,-1,5,0.0):
            with self.assertRaises(ValueError): ref.frozen_reference(pose)

    def test_capture_identity_rejects_duplicate_frames_images_and_untyped_camera(self):
        rows=[dict(index=i+1,renderedFrameIndex=i+10,gameTime=6000,
                   requestedYaw=105.0,observedYaw=105.0,requestedPitch=10.0,
                   observedPitch=10.0,screenshot=f'{i}.png') for i in range(5)]
        with patch.object(ref,'validate_document',return_value=rows):
            self.assertEqual(rows,ref.document({}))
        for key,value in [('index',True),('renderedFrameIndex',10),('gameTime',6000.0),
                          ('requestedYaw',105),('screenshot','0.png')]:
            bad=copy.deepcopy(rows);bad[-1][key]=value
            with patch.object(ref,'validate_document',return_value=bad):
                with self.assertRaises(ValueError):ref.document({})

    def test_local_requires_fifth_actual_draw_proof(self):
        rows=[dict(renderedFrameIndex=i+1,screenshot=f'{i}.png') for i in range(5)]
        with patch.object(ref,'document',return_value=rows), \
             patch.object(ref,'selected',return_value=({}, {}, {}, {})), \
             patch.object(ref,'validate_clocks',return_value={}), \
             patch.object(ref,'pixels',return_value={}), \
             patch.object(ref,'validate_draws',side_effect=[{}, {}, {}, {}, ValueError('missing upload')]) as draws:
            with self.assertRaisesRegex(ValueError,'missing upload'):ref.validate_local({})
            self.assertEqual(5,draws.call_count)

    def test_report_requires_pair_and_rejects_scope_substitution(self):
        args=['-Dmattmc.dev.graphicsAuditEquipment=foil',
              '-Dmattmc.dev.equipmentFoilPhase=10000',
              '-Dmattmc.dev.equipmentFoilPoseStep=8000',
              '-Dmattmc.dev.equipmentFoilTiming=true',
              '-Dmattmc.dev.equipmentTickStepping=true',
              '-Dmattmc.dev.graphicsAuditEquipmentMaterial=leather-dyed']
        pair={'baseline_artifact':'missing','current_artifact':'missing'}
        visual=dict(passed=True,pairs=[pair])
        self.assertFalse(equipment.report(dict(passed=True,pairs=[]),args)['passed'])
        self.assertFalse(equipment.report(visual,args)['passed'])
        with patch.object(ref,'validate_pair',return_value=dict(passed=True,capability_admitted=False)) as validate:
            result=equipment.report(visual,args)
            self.assertTrue(result['passed']);self.assertFalse(result['capability_admitted'])
            validate.assert_called_once_with(pair)
            for bad in (args+[args[-1]], args[:-1]+[args[-1].replace('leather-dyed','unknown')],
                        args+['-Dmattmc.dev.graphicsAuditEquipmentAge=unknown'],
                        args+['-Dmattmc.dev.graphicsAuditEquipmentAge=baby']*2,
                        args[:-1]+['-Dmattmc.dev.graphicsAuditEquipmentAge=baby'],
                        args+['-Dmattmc.dev.graphicsAuditEquipmentTrim=gold-spire'],
                        [v.replace('10000','40000') for v in args]):
                self.assertFalse(equipment.report(visual,bad)['passed'])
            validate.reset_mock()
            baby_args=args+['-Dmattmc.dev.graphicsAuditEquipmentAge=baby']
            result=equipment.report(visual,baby_args)
            self.assertTrue(result['passed']);self.assertFalse(result['capability_admitted'])
            validate.assert_called_once_with(pair,age='baby')
        with patch.object(ref,'validate_pair',side_effect=ValueError('bad ownership')):
            self.assertFalse(equipment.report(visual,args)['passed'])
            self.assertFalse(equipment.report(visual,baby_args)['passed'])
